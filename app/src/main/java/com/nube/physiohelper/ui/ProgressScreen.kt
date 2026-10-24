package com.nube.physiohelper.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nube.physiohelper.App
import com.nube.physiohelper.analysis.Advice
import com.nube.physiohelper.analysis.Analysis
import com.nube.physiohelper.analysis.ExerciseStats
import com.nube.physiohelper.data.CheckIn
import com.nube.physiohelper.data.Completion
import com.nube.physiohelper.data.Exercise
import com.nube.physiohelper.data.ExerciseLog
import com.nube.physiohelper.data.Session
import com.nube.physiohelper.data.SessionStatus
import com.nube.physiohelper.data.SessionType
import com.nube.physiohelper.data.SpadiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun ProgressScreen(nav: (Route) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val sessions by rememberFlow(emptyList<Session>()) { App.dao.sessionsFlow() }
    val logs by rememberFlow(emptyList<ExerciseLog>()) { App.dao.logsFlow() }
    val exercises by rememberFlow(emptyList<Exercise>()) { App.dao.exercisesFlow() }
    val checkIns by rememberFlow(emptyList<CheckIn>()) { App.dao.checkInsFlow() }
    val spadi by rememberFlow(emptyList<SpadiResult>()) { App.dao.spadiFlow() }

    Screen("Progress", onBack = null, inTabs = true) {
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            listOf("Overview", "Exercises", "History").forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
            }
        }
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
            label = "tab",
        ) { t ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (t) {
                    0 -> Overview(sessions, checkIns, spadi, nav)
                    1 -> ExercisesTab(logs, exercises, sessions, checkIns)
                    else -> HistoryTab(sessions, logs, nav)
                }
            }
        }
    }
}

// ---------------- Overview ----------------

@Composable
private fun Overview(sessions: List<Session>, checkIns: List<CheckIn>, spadi: List<SpadiResult>, nav: (Route) -> Unit) {
    val today = LocalDate.now()
    val adh = Analysis.adherence(sessions, App.settings.weeklyTarget, 30, today)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("this week", "${adh.thisWeek}/${adh.target}", Modifier.weight(1f))
        StatTile("day streak", "${adh.streak}", Modifier.weight(1f))
        StatTile("days in 30", "${adh.completedDays}", Modifier.weight(1f))
    }
    SectionCard("Calendar") { Calendar(sessions, checkIns, today) }

    val recent = checkIns.take(14).reversed()
    SectionCard("Mornings") {
        if (recent.isEmpty()) {
            Muted("No morning check-ins yet.")
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                recent.forEach { c ->
                    Box(
                        Modifier.weight(1f).aspectRatio(0.8f).clip(RoundedCornerShape(6.dp)).background(painColor(c.morningPain)),
                        contentAlignment = Alignment.Center,
                    ) { Text("${c.morningPain}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                }
            }
            Muted("Last ${recent.size} mornings, oldest first. Green is ≤3, the physio's limit.")
        }
        val flares = Analysis.flares(sessions, checkIns).takeLast(5).reversed()
        if (flares.isNotEmpty()) {
            Text("Flares", fontWeight = FontWeight.SemiBold)
            flares.forEach { Text("${pretty(it.date)} · ${it.reason}", style = MaterialTheme.typography.bodySmall) }
        }
    }

    SectionCard("Weekly score (SPADI)", modifier = Modifier.clickable { nav(Route.Spadi) }) {
        if (spadi.isEmpty()) {
            Muted("No score yet. Tap to take the 2-minute questionnaire.")
        } else {
            val latest = spadi.first()
            val first = spadi.last()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("%.0f".format(latest.totalScore), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(12.dp))
                Column {
                    Text("out of 100, lower is better")
                    if (spadi.size > 1) {
                        val diff = latest.totalScore - first.totalScore
                        Text(
                            "%+.0f since ${pretty(first.date)}".format(diff),
                            color = if (diff <= -13) Palette.PainLow else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            MeterBar(latest.totalScore.toFloat() / 100f, painColor((latest.totalScore / 10).toInt()))
        }
    }
}

@Composable
private fun Calendar(sessions: List<Session>, checkIns: List<CheckIn>, today: LocalDate) {
    val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(4)
    val done = sessions.filter { it.status == SessionStatus.COMPLETED }.groupBy { it.date }
    val checks = checkIns.associateBy { it.date }
    var selected by remember { mutableStateOf<LocalDate?>(today) }

    Row(Modifier.fillMaxWidth()) {
        dayNames.forEach { (_, n) ->
            Text(n.take(1), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    for (w in 0 until 5) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (i in 0 until 7) {
                val d = start.plusDays((w * 7 + i).toLong())
                val ds = d.toString()
                val day = done[ds]
                val future = d.isAfter(today)
                val bg = when {
                    day != null && day.any { it.type == SessionType.FULL } -> MaterialTheme.colorScheme.primary
                    day != null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    future -> Color.Transparent
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                }
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .then(
                            when {
                                d == selected -> Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp))
                                d == today -> Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                else -> Modifier
                            }
                        )
                        .clickable(enabled = !future) { selected = d },
                ) {
                    Text(
                        "${d.dayOfMonth}",
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = 12.sp,
                        color = when {
                            day != null -> MaterialTheme.colorScheme.onPrimary
                            future -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    checks[ds]?.let { c ->
                        Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(7.dp).clip(CircleShape).background(painColor(c.morningPain)))
                    }
                }
            }
        }
    }
    selected?.let { d ->
        val ds = d.toString()
        val day = done[ds].orEmpty()
        val c = checks[ds]
        val parts = mutableListOf<String>()
        parts += if (day.isEmpty()) "no session" else day.joinToString { (if (it.type == SessionType.SHORT) "short session" else "full session") + (it.peakPain?.let { p -> ", peak pain $p" } ?: "") }
        c?.let { parts += "morning pain ${it.morningPain}" }
        Text("${pretty(ds)}: " + parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Legend(MaterialTheme.colorScheme.primary, "full")
        Legend(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), "short")
        Legend(Palette.PainLow, "morning pain dot", round = true)
    }
}

@Composable
private fun Legend(color: Color, label: String, round: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(if (round) CircleShape else RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.size(4.dp))
        Muted(label)
    }
}

// ---------------- Exercises ----------------

@Composable
private fun ExercisesTab(logs: List<ExerciseLog>, exercises: List<Exercise>, sessions: List<Session>, checkIns: List<CheckIn>) {
    var window by rememberSaveable { mutableStateOf<Int?>(14) }
    val stats = remember(logs, exercises, sessions, checkIns, window) {
        Analysis.exerciseStats(logs, exercises, sessions, checkIns, window)
    }
    val byId = exercises.associateBy { it.id }
    SingleChoice(listOf<Pair<Int?, String>>(7 to "7 days", 14 to "14 days", 30 to "30 days", null to "All"), window) { window = it }
    if (stats.isEmpty()) Muted("Nothing logged in this period yet. Problem exercises show up here first.")
    stats.forEach { st -> StatCard(st, byId[st.exerciseId]) }
    Muted("Level-up rule: last 6 logs complete, pain ≤2, effort easy, no flare the next morning. Suggestions only. Anything new needs the physio's OK.")
}

@Composable
private fun StatCard(st: ExerciseStats, ex: Exercise?) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (ex != null) {
                Box(Modifier.size(56.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    ExerciseFigure(ex.id, ex.category, Modifier.size(56.dp).padding(4.dp))
                }
                Spacer(Modifier.size(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(st.name, fontWeight = FontWeight.SemiBold)
                Muted("${st.count} logs · ${st.fullPct}% complete · ${st.partial} partial · ${st.skipped} skipped")
            }
        }
        if (st.full + st.partial == 0) {
            Muted("Skipped every time")
            return@SectionCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pain", Modifier.size(width = 64.dp, height = 20.dp), style = MaterialTheme.typography.bodySmall)
            Box(Modifier.weight(1f)) { MeterBar((st.avgPain / 10).toFloat(), painColor(st.avgPain.toInt())) }
            Text("  %.1f · max %d".format(st.avgPain, st.maxPain), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Complete", Modifier.size(width = 64.dp, height = 20.dp), style = MaterialTheme.typography.bodySmall)
            Box(Modifier.weight(1f)) { MeterBar(st.fullPct / 100f, MaterialTheme.colorScheme.primary) }
            Text("  ${st.fullPct}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        if (st.easy + st.moderate + st.hard > 0) {
            Muted("Effort: ${st.easy} easy · ${st.moderate} moderate · ${st.hard} hard")
        }
        if (st.topTags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                st.topTags.forEach { (tag, n) -> Pill("$tag ×$n", MaterialTheme.colorScheme.tertiary) }
            }
        }
        st.advice?.let { a ->
            val color = when (a.kind) {
                Advice.Kind.LEVEL_UP -> Palette.PainLow
                Advice.Kind.ASK_PHYSIO -> MaterialTheme.colorScheme.primary
                Advice.Kind.REDUCE -> MaterialTheme.colorScheme.error
            }
            Text(a.text, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------------- History ----------------

@Composable
private fun HistoryTab(sessions: List<Session>, logs: List<ExerciseLog>, nav: (Route) -> Unit) {
    val counts = logs.groupingBy { it.sessionId }.eachCount()
    val visible = sessions.filter { it.status != SessionStatus.IN_PROGRESS }
    if (visible.isEmpty()) Muted("No sessions yet.")
    visible.forEach { s ->
        SectionCard(modifier = Modifier.clickable { nav(Route.SessionDetail(s.id)) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(pretty(s.date), fontWeight = FontWeight.SemiBold)
                    Muted(
                        listOfNotNull(
                            if (s.type == SessionType.SHORT) "short" else "full",
                            "${counts[s.id] ?: 0} exercises",
                            s.endedAt?.let { "${(it - s.startedAt) / 60000} min" },
                        ).joinToString(" · ")
                    )
                }
                if (s.status == SessionStatus.ABANDONED) Pill("discarded", MaterialTheme.colorScheme.onSurfaceVariant)
                s.peakPain?.let { Pill("peak $it", painColor(it), filled = true) }
            }
        }
    }
}

@Composable
fun SessionDetailScreen(id: Long, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val sessions by rememberFlow(emptyList<Session>()) { App.dao.sessionsFlow() }
    val logs by rememberFlow(emptyList<ExerciseLog>(), id) { App.dao.logsForSessionFlow(id) }
    val session = sessions.firstOrNull { it.id == id }
    var confirmDelete by remember { mutableStateOf(false) }

    Screen(session?.let { pretty(it.date) } ?: "Session", onBack) {
        if (session != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("exercises", "${logs.size}", Modifier.weight(1f))
                StatTile("peak pain", session.peakPain?.toString() ?: "–", Modifier.weight(1f), session.peakPain?.let { painColor(it) })
                StatTile("minutes", session.endedAt?.let { "${(it - session.startedAt) / 60000}" } ?: "–", Modifier.weight(1f))
            }
            if (session.note.isNotBlank()) SectionCard { Text(session.note) }
        }
        logs.forEach { l ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(l.exerciseName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    when (l.completion) {
                        Completion.SKIPPED -> Pill("skipped", MaterialTheme.colorScheme.onSurfaceVariant)
                        Completion.PARTIAL -> Pill("partial", Palette.PainMid)
                        else -> Pill("full", Palette.PainLow)
                    }
                }
                if (l.completion != Completion.SKIPPED) {
                    Muted(
                        listOfNotNull(
                            "${l.repsDone}/${l.repsPlanned} reps",
                            "pain ${l.pain}",
                            l.effort.takeIf { it.isNotBlank() }?.lowercase(),
                            l.bandColor.takeIf { it.isNotBlank() }?.let { "$it band" },
                        ).joinToString(" · ")
                    )
                }
                if (l.tags.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { l.tagList.forEach { Pill(it, MaterialTheme.colorScheme.tertiary) } }
                }
                if (l.note.isNotBlank()) Text(l.note, style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Delete this session", color = MaterialTheme.colorScheme.error)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete session?") },
            text = { Text("Its exercise logs are deleted too. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            App.dao.deleteLogsForSession(id)
                            App.dao.deleteSession(id)
                        }
                        onBack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

fun pretty(date: String): String = runCatching {
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE d MMM"))
}.getOrDefault(date)
