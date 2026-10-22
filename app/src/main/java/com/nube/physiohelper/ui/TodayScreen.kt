package com.nube.physiohelper.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nube.physiohelper.App
import com.nube.physiohelper.analysis.Advice
import com.nube.physiohelper.analysis.Analysis
import com.nube.physiohelper.data.CheckIn
import com.nube.physiohelper.data.Exercise
import com.nube.physiohelper.data.ExerciseLog
import com.nube.physiohelper.data.Session
import com.nube.physiohelper.data.SessionStatus
import com.nube.physiohelper.data.SessionType
import com.nube.physiohelper.data.SpadiResult
import com.nube.physiohelper.session.SessionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@Composable
fun TodayScreen(nav: (Route) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessions by rememberFlow(emptyList<Session>()) { App.dao.sessionsFlow() }
    val checkIns by rememberFlow(emptyList<CheckIn>()) { App.dao.checkInsFlow() }
    val spadi by rememberFlow(emptyList<SpadiResult>()) { App.dao.spadiFlow() }
    val logs by rememberFlow(emptyList<ExerciseLog>()) { App.dao.logsFlow() }
    val exercises by rememberFlow(emptyList<Exercise>()) { App.dao.exercisesFlow() }
    val engine by SessionEngine.state.collectAsState()
    val s = App.settings
    val today = LocalDate.now()
    val todayStr = today.toString()

    val week = Analysis.rehabWeek(s.rehabStart(), today)
    val adh = Analysis.adherence(sessions, s.weeklyTarget, 7, today)
    val doneToday = sessions.firstOrNull { it.date == todayStr && it.status == SessionStatus.COMPLETED }
    val todayCheck = checkIns.firstOrNull { it.date == todayStr }
    val enabledCount = exercises.count { it.enabled }
    val shortCount = exercises.count { it.enabled && it.inShortSession }
    val backupDays = if (s.lastBackupAt == 0L) null else ((System.currentTimeMillis() - s.lastBackupAt) / 86_400_000L).toInt()

    val insight = remember(sessions, checkIns, logs, exercises, spadi) {
        insightFor(sessions, checkIns, logs, exercises, spadi, today)
    }

    fun start(type: String) {
        SessionEngine.start(ctx, type) { toast(ctx, "No exercises enabled for this session. Check Program.") }
        nav(Route.Session)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(greeting(), style = MaterialTheme.typography.headlineLarge)
        Muted(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) + " · Week $week of rehab")

        if (todayCheck != null && todayCheck.redFlags.isNotBlank()) {
            SectionCard("Warning signs logged today", container = MaterialTheme.colorScheme.errorContainer) {
                Text(todayCheck.redFlags, color = MaterialTheme.colorScheme.onErrorContainer)
                Text("Stop the routine and get it checked by the doctor.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        // ---------- hero ----------
        SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    progress = adh.thisWeek.toFloat() / adh.target.coerceAtLeast(1),
                    modifier = Modifier.size(112.dp),
                    stroke = 11.dp,
                    track = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${adh.thisWeek}/${adh.target}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("this week", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.size(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (adh.streak > 0) "🔥 ${adh.streak}-day streak" else "Start a streak today",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        when {
                            doneToday != null -> "Done today" + (doneToday.peakPain?.let { " · peak pain $it" } ?: "")
                            engine.active -> "Session in progress"
                            else -> "$enabledCount exercises · 35–40 min"
                        },
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
            }
            if (engine.active) {
                BigButton("Resume session") { nav(Route.Session) }
            } else if (doneToday == null) {
                BigButton("Start session") { start(SessionType.FULL) }
                TextButton(onClick = { start(SessionType.SHORT) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Bad day? Short session · $shortCount exercises · ~15 min", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            } else {
                Text("Nice. Rest the shoulder; log the morning check tomorrow.", color = MaterialTheme.colorScheme.onPrimaryContainer)
                TextButton(onClick = { start(SessionType.FULL) }) { Text("Do another session", color = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
        }

        // ---------- morning check ----------
        SectionCard {
            AnimatedContent(
                targetState = todayCheck,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "check",
            ) { c ->
                if (c == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("How's the shoulder this morning?", style = MaterialTheme.typography.titleMedium)
                        PainPicker(null, { pain ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    App.dao.upsertCheckIn(CheckIn(date = todayStr, morningPain = pain, loggedAt = System.currentTimeMillis()))
                                }
                            }
                        })
                        Muted("One tap logs it. Add stiffness and sleep in details.")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(painColor(c.morningPain)),
                            contentAlignment = Alignment.Center,
                        ) { Text("${c.morningPain}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Morning check logged", fontWeight = FontWeight.SemiBold)
                            Muted(
                                listOfNotNull(
                                    painWord(c.morningPain),
                                    c.stiffnessMin?.let { "stiff $it min" },
                                    c.nightPain?.let { if (it) "woke from pain" else "slept OK" },
                                ).joinToString(" · ")
                            )
                        }
                        TextButton(onClick = { nav(Route.CheckIn) }) { Text("Details") }
                    }
                }
            }
        }

        // ---------- this week ----------
        SectionCard("This week") { WeekStrip(sessions, today) }

        insight?.let { (title, body, route) ->
            SectionCard(
                container = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = if (route != null) Modifier.clickable { nav(route) } else Modifier,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(body, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }

        if (!s.setupGuideSeen) {
            SectionCard("Reminders need setup on iQOO", container = MaterialTheme.colorScheme.secondaryContainer) {
                Text("iQOO kills background apps, which silently stops reminders. It takes 2 minutes.")
                Button(onClick = { nav(Route.Battery) }) { Text("Set up reminders") }
            }
        }
        if (backupDays == null || backupDays > 8) {
            SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                Text(if (backupDays == null) "No backup yet. Uninstall the app and your log is gone." else "Last backup was $backupDays days ago.")
                TextButton(onClick = { nav(Route.Data) }) { Text("Back up now") }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun BigButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null)
        Text("  $text", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WeekStrip(sessions: List<Session>, today: LocalDate) {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val byDate = sessions.filter { it.status == SessionStatus.COMPLETED }.groupBy { it.date }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0 until 7) {
            val d = monday.plusDays(i.toLong())
            val done = byDate[d.toString()]
            val isToday = d == today
            val future = d.isAfter(today)
            val appear by animateFloatAsState(
                if (started) 1f else 0f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "day$i",
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(dayNames[i].second.take(1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                val bg = when {
                    done != null && done.any { it.type == SessionType.FULL } -> MaterialTheme.colorScheme.primary
                    done != null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    future -> Color.Transparent
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                }
                Box(
                    Modifier
                        .size(34.dp)
                        .scale(if (done != null) appear else 1f)
                        .clip(CircleShape)
                        .background(bg)
                        .then(if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (done != null) "✓" else d.dayOfMonth.toString(),
                        color = if (done != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

private fun greeting(): String {
    val h = LocalTime.now().hour
    return when {
        h < 5 -> "Late night"
        h < 12 -> "Good morning"
        h < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

/** One useful sentence for today, most urgent first. */
private fun insightFor(
    sessions: List<Session>,
    checkIns: List<CheckIn>,
    logs: List<ExerciseLog>,
    exercises: List<Exercise>,
    spadi: List<SpadiResult>,
    today: LocalDate,
): Triple<String, String, Route?>? {
    val flares = Analysis.flares(sessions, checkIns)
    flares.lastOrNull()?.takeIf { it.date == today.toString() }?.let {
        return Triple("Go lighter today", "${it.reason}. Use less range or effort, or do the short session.", null)
    }
    val stats = Analysis.exerciseStats(logs, exercises, sessions, checkIns, 14, today)
    stats.firstOrNull { it.advice?.kind == Advice.Kind.REDUCE }?.let {
        return Triple("Step back: ${it.name}", it.advice!!.text, Route.Progress)
    }
    stats.firstOrNull { it.advice?.kind == Advice.Kind.LEVEL_UP }?.let {
        return Triple("Ready to level up: ${it.name}", it.advice!!.text + " Change it in Program.", Route.Program)
    }
    stats.firstOrNull { it.topTags.isNotEmpty() && it.count >= 3 }?.let {
        val (tag, n) = it.topTags.first()
        return Triple("Watch: ${it.name}", "\"$tag\" in $n of the last ${it.count} logs. Worth asking the physio.", Route.Progress)
    }
    val lastSpadi = spadi.firstOrNull()
    if (logs.isNotEmpty() && (lastSpadi == null || ChronoUnit.DAYS.between(LocalDate.parse(lastSpadi.date), today) >= 7)) {
        return Triple("Weekly score due", "Two minutes. It shows progress daily pain scores hide.", Route.Spadi)
    }
    return null
}
