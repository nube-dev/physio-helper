package com.nube.physiohelper.analysis

import com.nube.physiohelper.data.CheckIn
import com.nube.physiohelper.data.Category
import com.nube.physiohelper.data.Completion
import com.nube.physiohelper.data.Effort
import com.nube.physiohelper.data.Exercise
import com.nube.physiohelper.data.ExerciseLog
import com.nube.physiohelper.data.Session
import com.nube.physiohelper.data.SessionStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

data class ExerciseStats(
    val exerciseId: String,
    val name: String,
    val count: Int,
    val full: Int,
    val partial: Int,
    val skipped: Int,
    val avgPain: Double,
    val maxPain: Int,
    val easy: Int,
    val moderate: Int,
    val hard: Int,
    val topTags: List<Pair<String, Int>>,
    val problemScore: Double,
    val advice: Advice?,
) {
    val fullPct: Int get() = if (count == 0) 0 else (full * 100.0 / count).roundToInt()
}

data class Advice(val kind: Kind, val text: String) {
    enum class Kind { LEVEL_UP, ASK_PHYSIO, REDUCE }
}

data class Flare(val date: String, val morningPain: Int, val previousPeak: Int?, val reason: String)

data class Adherence(
    val thisWeek: Int,
    val target: Int,
    val lastWeeks: List<Pair<LocalDate, Int>>,   // week start (Monday) to completed-session count
    val streak: Int,
    val completedDays: Int,
    val windowDays: Int,
)

object Analysis {
    private const val LEVEL_UP_SESSIONS = 6

    fun completed(sessions: List<Session>) = sessions.filter { it.status == SessionStatus.COMPLETED }

    /** Completed shoulder-rehab sessions; leg days have their own tracking. */
    fun rehabDone(sessions: List<Session>) = completed(sessions).filter { it.type != com.nube.physiohelper.data.SessionType.STRENGTH }

    /** Completed leg-strength sessions this week (Monday start). */
    fun strengthThisWeek(sessions: List<Session>, today: LocalDate = LocalDate.now()): Int {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return completed(sessions).count {
            it.type == com.nube.physiohelper.data.SessionType.STRENGTH && !LocalDate.parse(it.date).isBefore(monday)
        }
    }

    fun streak(sessions: List<Session>, today: LocalDate = LocalDate.now()): Int {
        val days = rehabDone(sessions).map { it.date }.toSet()
        var d = if (days.contains(today.toString())) today else today.minusDays(1)
        var n = 0
        while (days.contains(d.toString())) { n++; d = d.minusDays(1) }
        return n
    }

    fun adherence(sessions: List<Session>, target: Int, windowDays: Int, today: LocalDate = LocalDate.now()): Adherence {
        val done = rehabDone(sessions)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeks = (0 until 4).map { w ->
            val start = monday.minusWeeks(w.toLong())
            val end = start.plusDays(6)
            start to done.count { s -> val d = LocalDate.parse(s.date); !d.isBefore(start) && !d.isAfter(end) }
        }
        val since = today.minusDays(windowDays.toLong() - 1)
        val daysDone = done.map { it.date }.toSet().count { !LocalDate.parse(it).isBefore(since) }
        return Adherence(
            thisWeek = weeks.first().second,
            target = target,
            lastWeeks = weeks,
            streak = streak(sessions, today),
            completedDays = daysDone,
            windowDays = windowDays,
        )
    }

    /**
     * A flare is a morning where pain is above 3, or worse than the peak pain of the
     * last completed session before that morning.
     */
    fun flares(sessions: List<Session>, checkIns: List<CheckIn>): List<Flare> {
        val done = rehabDone(sessions).sortedBy { it.startedAt }
        return checkIns.sortedBy { it.date }.mapNotNull { c ->
            val prev = done.lastOrNull { it.date < c.date }
            val prevPeak = prev?.peakPain
            when {
                prev != null && prevPeak != null && c.morningPain > prevPeak && prev.date == LocalDate.parse(c.date).minusDays(1).toString() ->
                    Flare(c.date, c.morningPain, prevPeak, "Morning pain ${c.morningPain} > last session peak $prevPeak")
                c.morningPain > 3 -> Flare(c.date, c.morningPain, prevPeak, "Morning pain ${c.morningPain} above 3")
                else -> null
            }
        }
    }

    /** Dates (yyyy-mm-dd) of sessions followed by a flare the next morning. */
    private fun flaredSessionDates(flares: List<Flare>): Set<String> =
        flares.map { LocalDate.parse(it.date).minusDays(1).toString() }.toSet()

    fun exerciseStats(
        logs: List<ExerciseLog>,
        exercises: List<Exercise>,
        sessions: List<Session>,
        checkIns: List<CheckIn>,
        windowDays: Int?,
        today: LocalDate = LocalDate.now(),
    ): List<ExerciseStats> {
        // Only logs from saved sessions count; a discarded session's logs are noise.
        val savedIds = completed(sessions).map { it.id }.toSet()
        val saved = logs.filter { it.sessionId in savedIds }
        val since = windowDays?.let { today.minusDays(it.toLong() - 1).toString() }
        val inWindow = if (since == null) saved else saved.filter { it.date >= since }
        val flareDates = flaredSessionDates(flares(sessions, checkIns))
        val byId = inWindow.groupBy { it.exerciseId }
        val exMap = exercises.associateBy { it.id }
        return byId.map { (id, list) ->
            val graded = list.filter { it.completion != Completion.SKIPPED }
            val avg = if (graded.isEmpty()) 0.0 else graded.map { it.pain }.average()
            val tagCounts = list.flatMap { it.tagList }.groupingBy { it }.eachCount()
                .toList().sortedByDescending { it.second }
            val partial = list.count { it.completion == Completion.PARTIAL }
            val skipped = list.count { it.completion == Completion.SKIPPED }
            val hard = list.count { it.effort == Effort.HARD }
            val n = list.size
            val score = avg +
                5.0 * partial / n +
                5.0 * skipped / n +
                3.0 * hard / n +
                2.0 * (tagCounts.sumOf { it.second }.toDouble() / n).coerceAtMost(2.0)
            val allForEx = saved.filter { it.exerciseId == id }.sortedByDescending { it.loggedAt }
            ExerciseStats(
                exerciseId = id,
                name = exMap[id]?.name ?: list.last().exerciseName,
                count = n,
                full = list.count { it.completion == Completion.FULL },
                partial = partial,
                skipped = skipped,
                avgPain = avg,
                maxPain = graded.maxOfOrNull { it.pain } ?: 0,
                easy = list.count { it.effort == Effort.EASY },
                moderate = list.count { it.effort == Effort.MODERATE },
                hard = hard,
                topTags = tagCounts.take(3),
                problemScore = score,
                advice = exMap[id]?.let { advice(it, allForEx, flareDates) },
            )
        }.sortedByDescending { it.problemScore }
    }

    /**
     * Progression rule (v1): the last 6 logs of this exercise were all complete,
     * pain <= 2, effort "easy", and none of those sessions was followed by a flare.
     * The app only suggests; changing the program is always the user's call.
     */
    fun advice(ex: Exercise, logsNewestFirst: List<ExerciseLog>, flareSessionDates: Set<String>): Advice? {
        if (ex.mode == com.nube.physiohelper.data.Mode.SETS) return setsAdvice(ex, logsNewestFirst)
        if (ex.mode != com.nube.physiohelper.data.Mode.REPS) return null
        val recent2 = logsNewestFirst.take(2)
        if (recent2.isNotEmpty() && (recent2.any { it.pain >= 5 } || flareSessionDates.contains(recent2.first().date))) {
            return Advice(
                Advice.Kind.REDUCE,
                "Recent pain ≥5 or a morning flare. Step back: less range, less effort, fewer reps" +
                    (if (ex.sets > 1) " or 1 set" else "") + (if (ex.bandColor.isNotBlank()) " or a lighter band" else "") + ".",
            )
        }
        val last = logsNewestFirst.take(LEVEL_UP_SESSIONS)
        if (last.size < LEVEL_UP_SESSIONS) return null
        val clean = last.all {
            it.completion == Completion.FULL && it.pain <= 2 && it.effort == Effort.EASY && !flareSessionDates.contains(it.date)
        }
        if (!clean) return null
        return when {
            ex.sets < 2 -> Advice(Advice.Kind.LEVEL_UP, "Eligible: move to 2 sets.")
            ex.category == Category.BAND && ex.bandColor.equals("Yellow", ignoreCase = true) ->
                Advice(Advice.Kind.LEVEL_UP, "Eligible: move to the red band (drop back to 1 set).")
            else -> Advice(Advice.Kind.ASK_PHYSIO, "Comfortable at the current level. Ask the physio for the next step.")
        }
    }

    /** Self-paced sets: top of the range on every set, easy effort, two sessions running. */
    private fun setsAdvice(ex: Exercise, logsNewestFirst: List<ExerciseLog>): Advice? {
        val last = logsNewestFirst.take(2)
        if (last.size < 2) return null
        val clean = last.all { l ->
            val reps = l.setReps.orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }
            l.completion == Completion.FULL && l.effort == Effort.EASY &&
                reps.size >= ex.sets * ex.sides && reps.all { it >= ex.repsTop }
        }
        return if (clean) Advice(
            Advice.Kind.LEVEL_UP,
            "Top of the range on every set: make it harder. Slow it down (3 s down, 1 s pause), or move to the harder version.",
        ) else null
    }

    /** Rough seconds an exercise takes in the session runner, including get-ready and logging. */
    fun estimateSec(e: Exercise, getReadySec: Int): Int =
        if (e.mode == com.nube.physiohelper.data.Mode.TIMED) {
            e.durationSec + getReadySec + 15
        } else if (e.mode == com.nube.physiohelper.data.Mode.SETS) {
            e.sides * e.sets * (e.repsTop * 5 / 2 + 8) + (e.sets - 1).coerceAtLeast(0) * e.setRestSec + getReadySec + 20
        } else {
            e.sides * (e.sets * e.reps * (e.upSec + e.holdSec + e.downSec + e.restSec) +
                (e.sets - 1).coerceAtLeast(0) * e.setRestSec) + (e.sides - 1) * 8 + getReadySec + 20
        }

    fun estimateMin(list: List<Exercise>, getReadySec: Int): Int =
        (list.sumOf { estimateSec(it, getReadySec) } + 30) / 60

    fun spadiScores(answers: List<Int>): Triple<Double, Double, Double> {
        val pain = answers.take(5).filter { it >= 0 }
        val dis = answers.drop(5).filter { it >= 0 }
        fun pct(v: List<Int>) = if (v.isEmpty()) 0.0 else v.sum() * 100.0 / (v.size * 10)
        val total = pct(pain + dis)
        return Triple(pct(pain), pct(dis), total)
    }

    fun rehabWeek(start: LocalDate, today: LocalDate = LocalDate.now()): Int =
        (ChronoUnit.DAYS.between(start, today) / 7).toInt() + 1

    fun phaseText(week: Int): String = when {
        week <= 8 -> "Phase 1 (weeks 1–8): pain in everyday use should start easing by week 6–8."
        week <= 16 -> "Phase 2 (months 2–4): aim for most tasks without flare-ups by month 3–4."
        week <= 26 -> "Phase 3 (months 4–6): building toward heavier lifting around month 6."
        else -> "Past 6 months: keep the routine as maintenance; review progress with the physio."
    }
}
