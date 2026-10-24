package com.nube.physiohelper.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.nube.physiohelper.App
import com.nube.physiohelper.analysis.Analysis
import com.nube.physiohelper.data.CheckIn
import com.nube.physiohelper.data.Completion
import com.nube.physiohelper.data.Effort
import com.nube.physiohelper.data.Exercise
import com.nube.physiohelper.data.ExerciseLog
import com.nube.physiohelper.data.Mode
import com.nube.physiohelper.data.PhysioQuestion
import com.nube.physiohelper.data.Session
import com.nube.physiohelper.data.SessionStatus
import com.nube.physiohelper.data.SpadiResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Exporter {
    private const val FORMAT_VERSION = 1

    // ---------- Claude summary ----------

    suspend fun claudeSummary(days: Int): String {
        val dao = App.dao
        val today = LocalDate.now()
        val since = today.minusDays(days.toLong() - 1).toString()
        val exercises = dao.exercises()
        val sessions = dao.allSessions()
        val logs = dao.allLogs()
        val checkIns = dao.allCheckIns()
        val spadi = dao.allSpadi()
        val questions = dao.allQuestions().filter { !it.resolved }
        val s = App.settings
        val sb = StringBuilder()

        sb.appendLine("# Physio Helper export — last $days days ($since to $today)")
        sb.appendLine("Right shoulder rehab. Rehab start ${s.rehabStartDate}, now week ${Analysis.rehabWeek(s.rehabStart(), today)}.")
        sb.appendLine("Pain scores are 0–10. Effort: E=easy, M=moderate, H=hard. Completion: F=full, P=partial, S=skipped.")
        sb.appendLine()

        sb.appendLine("## Current shoulder program (enabled)")
        val enabled = exercises.filter { it.enabled && it.program == com.nube.physiohelper.data.Program.REHAB }
        for (e in enabled) sb.appendLine("- ${e.name}: ${dose(e)}")
        sb.appendLine("Estimated full session: ~${Analysis.estimateMin(enabled, s.getReadySec)} min.")
        val legs = exercises.filter { it.enabled && it.program == com.nube.physiohelper.data.Program.STRENGTH }
        if (legs.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("## Leg strength program (days: ${s.strengthDays.sorted().joinToString { java.time.DayOfWeek.of(it).name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }})")
            for (e in legs) sb.appendLine("- ${e.name}: ${dose(e)}")
            sb.appendLine("Estimated leg session: ~${Analysis.estimateMin(legs, s.getReadySec)} min.")
        }
        val disabled = exercises.filter { !it.enabled }
        if (disabled.isNotEmpty()) sb.appendLine("Disabled: " + disabled.joinToString { it.name + if (it.pendingPhysio) " (pending physio)" else "" })
        sb.appendLine()

        val adh = Analysis.adherence(sessions, s.weeklyTarget, days, today)
        sb.appendLine("## Adherence")
        sb.appendLine("Target ${s.weeklyTarget}/week. Days with a completed session: ${adh.completedDays} of $days. Current streak: ${adh.streak} day${if (adh.streak == 1) "" else "s"}.")
        val legSessions = sessions.count { it.type == com.nube.physiohelper.data.SessionType.STRENGTH && it.status == SessionStatus.COMPLETED && it.date >= since }
        sb.appendLine("Leg strength sessions in this period: $legSessions (target ${s.strengthDays.size}/week).")
        sb.appendLine("Weekly counts (newest first): " + adh.lastWeeks.joinToString { "${it.first}: ${it.second}" })
        sb.appendLine()

        val recentChecks = checkIns.filter { it.date >= since }
        sb.appendLine("## Morning check-ins")
        if (recentChecks.isEmpty()) sb.appendLine("None.")
        for (c in recentChecks) {
            sb.append("- ${c.date}: pain ${c.morningPain}")
            c.stiffnessMin?.let { sb.append(", stiffness ${it} min") }
            c.nightPain?.let { sb.append(if (it) ", woke from pain at night" else ", slept OK") }
            if (c.redFlags.isNotBlank()) sb.append(", RED FLAGS: ${c.redFlags}")
            if (c.note.isNotBlank()) sb.append(" — ${c.note}")
            sb.appendLine()
        }
        val flares = Analysis.flares(sessions, checkIns).filter { it.date >= since }
        if (flares.isNotEmpty()) sb.appendLine("Flares: " + flares.joinToString { "${it.date} (${it.reason})" })
        sb.appendLine()

        sb.appendLine("## Per-exercise summary (problem exercises first)")
        val stats = Analysis.exerciseStats(logs, exercises, sessions, checkIns, days, today)
        if (stats.isEmpty()) sb.appendLine("No exercises logged.")
        for (st in stats) {
            sb.append("- ${st.name}: ${st.count} logs, F/P/S ${st.full}/${st.partial}/${st.skipped}")
            if (st.full + st.partial == 0) {
                sb.append(", skipped every time")
            } else {
                sb.append(", pain avg ${"%.1f".format(Locale.US, st.avgPain)} max ${st.maxPain}, effort E/M/H ${st.easy}/${st.moderate}/${st.hard}")
            }
            if (st.topTags.isNotEmpty()) sb.append(", tags: " + st.topTags.joinToString { "${it.first}×${it.second}" })
            st.advice?.let { sb.append(" → ${it.text}") }
            sb.appendLine()
        }
        sb.appendLine()

        sb.appendLine("## Session log")
        val logsBySession = logs.groupBy { it.sessionId }
        val recentSessions = sessions.filter {
            it.date >= since && it.status != SessionStatus.IN_PROGRESS &&
                !(it.status == SessionStatus.ABANDONED && logsBySession[it.id].isNullOrEmpty())
        }
        if (recentSessions.isEmpty()) sb.appendLine("No sessions.")
        for (sess in recentSessions) {
            val mins = sess.endedAt?.let { ((it - sess.startedAt) / 60000).toInt() }
            val status = if (sess.status == SessionStatus.ABANDONED) "discarded (not in stats)" else sess.status.lowercase()
            sb.append("### ${sess.date} ${sess.type.lowercase()} session, $status")
            if (mins != null) sb.append(", $mins min")
            sess.peakPain?.let { sb.append(", peak pain $it") }
            sb.appendLine()
            if (sess.note.isNotBlank()) sb.appendLine("Note: ${sess.note}")
            for (l in logsBySession[sess.id].orEmpty()) sb.appendLine("- " + compactLog(l))
        }
        sb.appendLine()

        if (spadi.isNotEmpty()) {
            sb.appendLine("## SPADI (0 best, 100 worst)")
            for (r in spadi.takeLast(8)) {
                sb.appendLine("- ${r.date}: total ${"%.0f".format(r.totalScore)}, pain ${"%.0f".format(r.painScore)}, disability ${"%.0f".format(r.disabilityScore)}")
            }
            sb.appendLine()
        }

        if (questions.isNotEmpty()) {
            sb.appendLine("## Open questions for the physio")
            for (q in questions) sb.appendLine("- ${q.text}")
        }
        return sb.toString().trim()
    }

    private fun dose(e: Exercise): String = if (e.mode == Mode.TIMED) {
        "${e.durationSec / 60} min"
    } else if (e.mode == Mode.SETS) {
        "${e.sets}×" + (if (e.repsMax > e.reps) "${e.reps}–${e.repsMax}" else "${e.reps}") +
            (if (e.sides == 2) " each ${e.sideWord}" else "") + ", ${e.setRestSec}s rest between sets"
    } else buildString {
        append("${e.sets}×${e.reps}")
        val parts = mutableListOf<String>()
        if (e.upSec > 0) parts += "${e.upSec}s up"
        if (e.holdSec > 0) parts += "${e.holdSec}s hold"
        if (e.downSec > 0) parts += "${e.downSec}s down"
        if (e.restSec > 0) parts += "${e.restSec}s rest"
        if (parts.isNotEmpty()) append(" (" + parts.joinToString(", ") + ")")
        if (e.bandColor.isNotBlank()) append(", ${e.bandColor.lowercase()} band")
        if (e.sides == 2) append(", both sides")
        append(", level ${e.level}")
    }

    private fun compactLog(l: ExerciseLog): String {
        val c = when (l.completion) { Completion.FULL -> "F"; Completion.PARTIAL -> "P"; else -> "S" }
        val eff = when (l.effort) { Effort.EASY -> "E"; Effort.MODERATE -> "M"; Effort.HARD -> "H"; else -> "-" }
        val sb = StringBuilder("${l.exerciseName}: $c")
        if (l.completion != Completion.SKIPPED) sb.append(" ${l.repsDone}/${l.repsPlanned} reps, pain ${l.pain}, effort $eff")
        if (!l.setReps.isNullOrBlank()) sb.append(", sets [${l.setReps}]")
        if (l.tags.isNotBlank()) sb.append(", [${l.tags}]")
        if (l.note.isNotBlank()) sb.append(" — ${l.note}")
        return sb.toString()
    }

    // ---------- CSV ----------

    suspend fun logsCsv(): String {
        val sb = StringBuilder("date,session_id,exercise_id,exercise,completion,sets_done,sets_planned,reps_done,reps_planned,pain,effort,tags,band,level,note\n")
        for (l in App.dao.allLogs()) {
            sb.append(listOf(
                l.date, l.sessionId, l.exerciseId, l.exerciseName, l.completion, l.setsDone, l.setsPlanned,
                l.repsDone, l.repsPlanned, l.pain, l.effort, l.tags, l.bandColor, l.level, l.note,
            ).joinToString(",") { csv(it.toString()) }).append('\n')
        }
        return sb.toString()
    }

    private fun csv(v: String) = if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    // ---------- JSON backup / restore ----------

    suspend fun backupJson(): String {
        val dao = App.dao
        val root = JSONObject()
        root.put("format", "physio-helper-backup")
        root.put("version", FORMAT_VERSION)
        root.put("exportedAt", LocalDateTime.now().toString())
        root.put("exercises", JSONArray(dao.exercises().map { exToJson(it) }))
        root.put("sessions", JSONArray(dao.allSessions().map { sessionToJson(it) }))
        root.put("logs", JSONArray(dao.allLogs().map { logToJson(it) }))
        root.put("checkIns", JSONArray(dao.allCheckIns().map { checkInToJson(it) }))
        root.put("spadi", JSONArray(dao.allSpadi().map { spadiToJson(it) }))
        root.put("questions", JSONArray(dao.allQuestions().map { questionToJson(it) }))
        val settings = JSONObject()
        for ((k, v) in App.settings.toMap()) if (v != null) settings.put(k, v)
        root.put("settings", settings)
        return root.toString(2)
    }

    /** Replaces all data with the backup's contents. Throws on an invalid file. */
    suspend fun restoreJson(text: String) {
        val root = JSONObject(text)
        require(root.optString("format") == "physio-helper-backup") { "Not a Physio Helper backup file" }
        val exercises = arr(root, "exercises").map { exFromJson(it) }
        val sessions = arr(root, "sessions").map { sessionFromJson(it) }
        val logs = arr(root, "logs").map { logFromJson(it) }
        val checkIns = arr(root, "checkIns").map { checkInFromJson(it) }
        val spadi = arr(root, "spadi").map { spadiFromJson(it) }
        val questions = arr(root, "questions").map { questionFromJson(it) }
        val db = App.instance.db
        val dao = db.dao()
        db.withTransaction {
            dao.clearLogs(); dao.clearSessions(); dao.clearCheckIns(); dao.clearSpadi(); dao.clearQuestions(); dao.clearExercises()
            dao.upsertExercises(exercises)
            dao.insertSessions(sessions)
            dao.insertLogs(logs)
            dao.insertCheckIns(checkIns)
            dao.insertSpadis(spadi)
            dao.insertQuestions(questions)
        }
        root.optJSONObject("settings")?.let { o ->
            val map = mutableMapOf<String, Any?>()
            for (k in o.keys()) {
                val v = o.get(k)
                map[k] = when {
                    k == "physio_at" || k == "last_backup_at" -> (v as? Number)?.toLong()
                    v is Int || v is Boolean || v is String -> v
                    v is Long -> v.toInt()
                    else -> null
                }
            }
            App.settings.restore(map)
        }
    }

    private fun arr(root: JSONObject, key: String): List<JSONObject> {
        val a = root.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    private fun exToJson(e: Exercise) = JSONObject().apply {
        put("id", e.id); put("name", e.name); put("category", e.category); put("orderIndex", e.orderIndex)
        put("enabled", e.enabled); put("pendingPhysio", e.pendingPhysio); put("mode", e.mode)
        put("sets", e.sets); put("reps", e.reps); put("upSec", e.upSec); put("holdSec", e.holdSec)
        put("downSec", e.downSec); put("restSec", e.restSec); put("setRestSec", e.setRestSec)
        put("durationSec", e.durationSec); put("bandColor", e.bandColor); put("level", e.level)
        put("inShortSession", e.inShortSession); put("steps", e.steps); put("cues", e.cues)
        put("avoid", e.avoid); put("videoUrl", e.videoUrl); put("mediaUri", e.mediaUri)
        put("bothSides", e.bothSides); put("repsMax", e.repsMax); put("program", e.program)
    }

    private fun exFromJson(o: JSONObject) = Exercise(
        id = o.getString("id"), name = o.getString("name"), category = o.optString("category", "OTHER"),
        orderIndex = o.optInt("orderIndex"), enabled = o.optBoolean("enabled", true),
        pendingPhysio = o.optBoolean("pendingPhysio"), mode = o.optString("mode", Mode.REPS),
        sets = o.optInt("sets", 1), reps = o.optInt("reps", 10), upSec = o.optInt("upSec"),
        holdSec = o.optInt("holdSec", 10), downSec = o.optInt("downSec"), restSec = o.optInt("restSec", 5),
        setRestSec = o.optInt("setRestSec", 30), durationSec = o.optInt("durationSec"),
        bandColor = o.optString("bandColor"), level = o.optInt("level", 1),
        inShortSession = o.optBoolean("inShortSession"), steps = o.optString("steps"),
        cues = o.optString("cues"), avoid = o.optString("avoid"), videoUrl = o.optString("videoUrl"),
        mediaUri = o.optString("mediaUri"),
        bothSides = o.optBoolean("bothSides"),
        repsMax = o.optInt("repsMax"),
        program = o.optInt("program"),
    )

    private fun sessionToJson(s: Session) = JSONObject().apply {
        put("id", s.id); put("startedAt", s.startedAt); put("endedAt", s.endedAt ?: JSONObject.NULL)
        put("date", s.date); put("type", s.type); put("status", s.status)
        put("peakPain", s.peakPain ?: JSONObject.NULL); put("note", s.note)
    }

    private fun sessionFromJson(o: JSONObject) = Session(
        id = o.getLong("id"), startedAt = o.getLong("startedAt"),
        endedAt = if (o.isNull("endedAt")) null else o.getLong("endedAt"),
        date = o.getString("date"), type = o.optString("type", "FULL"), status = o.optString("status", SessionStatus.COMPLETED),
        peakPain = if (o.isNull("peakPain")) null else o.getInt("peakPain"), note = o.optString("note"),
    )

    private fun logToJson(l: ExerciseLog) = JSONObject().apply {
        put("id", l.id); put("sessionId", l.sessionId); put("exerciseId", l.exerciseId); put("exerciseName", l.exerciseName)
        put("date", l.date); put("loggedAt", l.loggedAt); put("completion", l.completion)
        put("setsDone", l.setsDone); put("repsDone", l.repsDone); put("setsPlanned", l.setsPlanned); put("repsPlanned", l.repsPlanned)
        put("pain", l.pain); put("effort", l.effort); put("tags", l.tags); put("note", l.note)
        put("bandColor", l.bandColor); put("level", l.level)
        put("setReps", l.setReps ?: JSONObject.NULL)
    }

    private fun logFromJson(o: JSONObject) = ExerciseLog(
        id = o.getLong("id"), sessionId = o.getLong("sessionId"), exerciseId = o.getString("exerciseId"),
        exerciseName = o.optString("exerciseName"), date = o.getString("date"), loggedAt = o.getLong("loggedAt"),
        completion = o.optString("completion", Completion.FULL), setsDone = o.optInt("setsDone"),
        repsDone = o.optInt("repsDone"), setsPlanned = o.optInt("setsPlanned"), repsPlanned = o.optInt("repsPlanned"),
        pain = o.optInt("pain"), effort = o.optString("effort"), tags = o.optString("tags"), note = o.optString("note"),
        bandColor = o.optString("bandColor"), level = o.optInt("level", 1),
        setReps = if (o.isNull("setReps")) null else o.optString("setReps"),
    )

    private fun checkInToJson(c: CheckIn) = JSONObject().apply {
        put("date", c.date); put("morningPain", c.morningPain)
        put("stiffnessMin", c.stiffnessMin ?: JSONObject.NULL); put("nightPain", c.nightPain ?: JSONObject.NULL)
        put("redFlags", c.redFlags); put("note", c.note); put("loggedAt", c.loggedAt)
    }

    private fun checkInFromJson(o: JSONObject) = CheckIn(
        date = o.getString("date"), morningPain = o.getInt("morningPain"),
        stiffnessMin = if (o.isNull("stiffnessMin")) null else o.getInt("stiffnessMin"),
        nightPain = if (o.isNull("nightPain")) null else o.getBoolean("nightPain"),
        redFlags = o.optString("redFlags"), note = o.optString("note"), loggedAt = o.optLong("loggedAt"),
    )

    private fun spadiToJson(s: SpadiResult) = JSONObject().apply {
        put("id", s.id); put("date", s.date); put("answers", s.answers); put("painScore", s.painScore)
        put("disabilityScore", s.disabilityScore); put("totalScore", s.totalScore); put("loggedAt", s.loggedAt)
    }

    private fun spadiFromJson(o: JSONObject) = SpadiResult(
        id = o.getLong("id"), date = o.getString("date"), answers = o.getString("answers"),
        painScore = o.getDouble("painScore"), disabilityScore = o.getDouble("disabilityScore"),
        totalScore = o.getDouble("totalScore"), loggedAt = o.optLong("loggedAt"),
    )

    private fun questionToJson(q: PhysioQuestion) = JSONObject().apply {
        put("id", q.id); put("text", q.text); put("exerciseId", q.exerciseId); put("createdAt", q.createdAt)
        put("resolved", q.resolved); put("answer", q.answer)
    }

    private fun questionFromJson(o: JSONObject) = PhysioQuestion(
        id = o.getLong("id"), text = o.getString("text"), exerciseId = o.optString("exerciseId"),
        createdAt = o.optLong("createdAt"), resolved = o.optBoolean("resolved"), answer = o.optString("answer"),
    )

    // ---------- files ----------

    private fun stamp() = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"))

    /** Writes a weekly backup to Downloads/PhysioHelper. */
    suspend fun saveBackupToDownloads(context: Context): Boolean = runCatching {
        val json = backupJson()
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "physio-helper-backup_${stamp()}.json")
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PhysioHelper")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("insert failed")
        resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } ?: error("open failed")
        App.settings.lastBackupAt = System.currentTimeMillis()
        true
    }.getOrDefault(false)

    /** Writes [content] to a cache file and returns a share intent for it. */
    fun shareFileIntent(context: Context, name: String, mime: String, content: String): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, name)
        file.writeText(content)
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share $name")
    }

    fun shareTextIntent(text: String, title: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return Intent.createChooser(send, title)
    }

    fun csvName() = "physio-helper-logs_${stamp()}.csv"
    fun jsonName() = "physio-helper-backup_${stamp()}.json"
    fun claudeName() = "physio-helper-for-claude_${stamp()}.md"
}
