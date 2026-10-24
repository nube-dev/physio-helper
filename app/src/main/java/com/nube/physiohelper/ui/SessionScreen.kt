package com.nube.physiohelper.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nube.physiohelper.App
import com.nube.physiohelper.data.Completion
import com.nube.physiohelper.data.Effort
import com.nube.physiohelper.data.Exercise
import com.nube.physiohelper.data.Mode
import com.nube.physiohelper.data.Tags
import com.nube.physiohelper.reminders.Notifications
import com.nube.physiohelper.session.EngineState
import com.nube.physiohelper.session.LogDraft
import com.nube.physiohelper.session.Phase
import com.nube.physiohelper.session.SessionEngine
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

fun doseText(e: Exercise): String = if (e.mode == Mode.TIMED) {
    "${e.durationSec / 60} min"
} else if (e.mode == Mode.SETS) {
    "${e.sets} × " + (if (e.repsMax > e.reps) "${e.reps}–${e.repsMax}" else "${e.reps}") +
        (if (e.sides == 2) " each ${e.sideWord}" else "") + " · ${e.setRestSec}s rest"
} else buildString {
    append("${e.sets} × ${e.reps}")
    val parts = mutableListOf<String>()
    if (e.upSec > 0) parts += "${e.upSec}s up"
    if (e.holdSec > 0) parts += "${e.holdSec}s hold"
    if (e.downSec > 0) parts += "${e.downSec}s down"
    if (parts.isNotEmpty()) append(" · " + parts.joinToString(" · "))
    if (e.bandColor.isNotBlank()) append(" · ${e.bandColor.lowercase()} band")
    if (e.sides == 2) append(" · both sides")
}

@Composable
fun SessionScreen(onBack: () -> Unit) {
    val st by SessionEngine.state.collectAsState()
    val ctx = LocalContext.current
    var waited by remember { mutableStateOf(false) }
    var confirmQuit by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(2000); waited = true }

    if (App.settings.keepScreenOn) {
        DisposableEffect(Unit) {
            val window = (ctx as? Activity)?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }

    if (!st.active) {
        Screen("Session", onBack) {
            if (!waited) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                Text("No active session.")
                Button(onClick = onBack) { Text("Back") }
            }
        }
        return
    }

    val ex = st.current
    val running = st.phase.isTiming
    val bg by animateColorAsState(
        when {
            !running -> MaterialTheme.colorScheme.background
            st.paused -> Palette.Slate
            else -> phaseColor(st.phase)
        },
        tween(500), label = "bg",
    )

    Box(Modifier.fillMaxSize().background(bg)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
            TopStrip(st, onDark = running, onClose = { confirmQuit = true })
            when {
                st.phase == Phase.SUMMARY -> Scrolling { SummaryPanel(st, onBack) }
                ex == null -> Text("Nothing to do.")
                st.phase == Phase.READY -> Scrolling { ReadyPanel(ex, st) }
                st.phase == Phase.LOG -> Scrolling { LogPanel(ex, st) }
                st.phase == Phase.WORK -> Scrolling { WorkPanel(ex, st) }
                else -> RunningPanel(ex, st)
            }
        }
    }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("End session?") },
            text = { Text("Save what you've logged so far, or discard the whole session.") },
            confirmButton = { TextButton(onClick = { confirmQuit = false; SessionEngine.goToSummary() }) { Text("Save so far") } },
            dismissButton = { TextButton(onClick = { confirmQuit = false; SessionEngine.abandon { onBack() } }) { Text("Discard") } },
        )
    }
}

@Composable
private fun ColumnScope.Scrolling(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        content()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TopStrip(st: EngineState, onDark: Boolean, onClose: () -> Unit) {
    val fg = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    val done = if (st.phase == Phase.SUMMARY) st.items.size else st.index
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "End session", tint = fg) }
            Text(
                if (st.phase == Phase.SUMMARY) "Session complete" else "Exercise ${st.index + 1} of ${st.items.size}",
                color = fg, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
            )
            if (st.type == "SHORT") Pill("Short session", fg)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            st.items.forEachIndexed { i, _ ->
                val c by animateColorAsState(
                    when {
                        i < done -> if (onDark) Color.White else MaterialTheme.colorScheme.primary
                        i == done -> (if (onDark) Color.White else MaterialTheme.colorScheme.primary).copy(alpha = 0.5f)
                        else -> fg.copy(alpha = 0.15f)
                    },
                    tween(400), label = "seg",
                )
                Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c))
            }
        }
    }
}

@Composable
private fun ReadyPanel(ex: Exercise, st: EngineState) {
    var showHow by remember(ex.id) { mutableStateOf(false) }
    Muted("UP NEXT")
    Text(ex.name, style = MaterialTheme.typography.headlineMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill(doseText(ex), MaterialTheme.colorScheme.primary)
        if (ex.pendingPhysio) Pill("pending physio", Palette.PainMid)
    }
    ExerciseMedia(ex, height = 260.dp)
    if (ex.cues.isNotBlank()) {
        SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
            Text(ex.cues, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
    if (ex.avoid.isNotBlank()) {
        SectionCard(container = MaterialTheme.colorScheme.errorContainer) {
            Text(ex.avoid, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
    if (ex.steps.isNotBlank()) {
        TextButton(onClick = { showHow = !showHow }) { Text(if (showHow) "Hide how-to" else "How to do it") }
        AnimatedVisibility(showHow, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            SectionCard { Text(ex.steps) }
        }
    }
    Button(
        onClick = { SessionEngine.beginExercise() },
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null)
        Text("  Start", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { SessionEngine.skip() }) { Text("Skip this one") }
        Spacer(Modifier.weight(1f))
        st.items.getOrNull(st.index + 1)?.let { Muted("Then: ${it.name}") }
    }
}

@Composable
private fun ColumnScope.RunningPanel(ex: Exercise, st: EngineState) {
    var painDialog by remember { mutableStateOf(false) }
    var painValue by remember { mutableIntStateOf(4) }

    // Drive the figure from the timer: lift during UP, hold at the top, lower during DOWN.
    val target = when (st.phase) {
        Phase.UP -> ((ex.upSec - st.secondsLeft + 1).toFloat() / ex.upSec.coerceAtLeast(1)).coerceIn(0f, 1f)
        Phase.HOLD -> 1f
        Phase.DOWN -> 1f - ((ex.downSec - st.secondsLeft + 1).toFloat() / ex.downSec.coerceAtLeast(1)).coerceIn(0f, 1f)
        else -> 0f
    }
    val linear = st.phase == Phase.UP || st.phase == Phase.DOWN
    val progress by animateFloatAsState(target, if (linear) tween(1000, easing = LinearEasing) else tween(450), label = "fig")
    val push = if (st.phase == Phase.HOLD && !st.paused) 1f else 0.15f

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(ex.name, color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (ex.sides == 2) Pill(if (st.side == 0) "Right arm" else "Left arm", Color.White)
    }
    Spacer(Modifier.height(10.dp))
    // The left side shows the demonstration mirrored.
    Box(Modifier.graphicsLayer { scaleX = if (st.side == 1) -1f else 1f }) {
        ExerciseMedia(ex, height = 200.dp, progress = progress, push = push, background = MaterialTheme.colorScheme.surface)
    }

    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        ProgressRing(
            progress = if (st.phaseTotal == 0) 0f else st.secondsLeft.toFloat() / st.phaseTotal,
            modifier = Modifier.size(210.dp),
            stroke = 12.dp,
            color = Color.White,
            track = Color.White.copy(alpha = 0.22f),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(
                    targetState = if (st.paused) "PAUSED" else st.phase.label.uppercase(),
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                    label = "phase",
                ) { label ->
                    Text(label, color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
                AnimatedContent(
                    targetState = st.secondsLeft,
                    transitionSpec = {
                        (slideInVertically(tween(250)) { it / 2 } + fadeIn(tween(250))) togetherWith
                            (slideOutVertically(tween(250)) { -it / 2 } + fadeOut(tween(250)))
                    },
                    label = "count",
                ) { secs ->
                    Text(
                        if (st.phase == Phase.TIMED) Notifications.fmt(secs) else secs.toString(),
                        color = Color.White,
                        fontSize = if (st.phase == Phase.TIMED) 56.sp else 84.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (ex.mode == Mode.REPS && st.phase != Phase.GET_READY) {
                    Text(
                        "Rep ${st.rep}/${ex.reps}" + if (ex.sets > 1) " · Set ${st.set}/${ex.sets}" else "",
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundAction("Pain", Palette.PainHigh, Color.White, 64) { SessionEngine.pause(); painValue = 4; painDialog = true }
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(Color.White).clickable { SessionEngine.togglePause() },
            contentAlignment = Alignment.Center,
        ) {
            if (st.paused) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Resume", tint = phaseColor(st.phase), modifier = Modifier.size(44.dp))
            } else {
                PauseGlyph(phaseColor(st.phase))
            }
        }
        RoundAction("Skip", Color.Transparent, Color.White, 64, outlined = true) { SessionEngine.skip() }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        TextButton(onClick = { SessionEngine.restartExercise() }) { Text("Restart", color = Color.White.copy(alpha = 0.85f)) }
        TextButton(onClick = { SessionEngine.endExerciseEarly() }) { Text("Stop here & log", color = Color.White.copy(alpha = 0.85f)) }
    }

    if (painDialog) {
        AlertDialog(
            onDismissRequest = { painDialog = false; SessionEngine.resume() },
            title = { Text("How bad is it right now?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PainPicker(painValue, { painValue = it })
                    Text(
                        when {
                            painValue > 5 -> "Above 5: stop this exercise now. Log it and move on."
                            painValue > 3 -> "Above your 3/10 limit: use less range or effort for the rest of it."
                            else -> "Within the limit. Fine to continue if it settles by tomorrow morning."
                        },
                        fontWeight = if (painValue > 5) FontWeight.Bold else FontWeight.Normal,
                        color = if (painValue > 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { painDialog = false; SessionEngine.stopForPain(painValue) }) { Text("Stop exercise") } },
            dismissButton = { TextButton(onClick = { painDialog = false; SessionEngine.resume() }) { Text("Continue") } },
        )
    }
}

@Composable
private fun PauseGlyph(color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(width = 9.dp, height = 32.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Box(Modifier.size(width = 9.dp, height = 32.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

@Composable
private fun RoundAction(label: String, bg: Color, fg: Color, sizeDp: Int, outlined: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (outlined) Modifier.border(2.dp, fg.copy(alpha = 0.8f), CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontWeight = FontWeight.Bold)
    }
}

/** Self-paced set: do the reps, adjust the count if needed, tap Done. */
@Composable
private fun WorkPanel(ex: Exercise, st: EngineState) {
    var painDialog by remember { mutableStateOf(false) }
    var painValue by remember { mutableIntStateOf(4) }
    val range = if (ex.repsMax > ex.reps) "${ex.reps}–${ex.repsMax}" else "${ex.reps}"

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(ex.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (ex.sides == 2) Pill(if (st.side == 0) "Right ${ex.sideWord}" else "Left ${ex.sideWord}", MaterialTheme.colorScheme.primary)
    }
    Box(Modifier.graphicsLayer { scaleX = if (st.side == 1) -1f else 1f }) {
        ExerciseMedia(ex, height = 220.dp)
    }
    SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Set ${st.set} of ${ex.sets}", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
            Text("$range reps", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (st.setReps.isNotEmpty()) Muted("Done so far: " + st.setReps.joinToString(" · "))
        }
    }
    if (ex.cues.isNotBlank()) Muted(ex.cues)
    SectionCard {
        Stepper("Reps you did", st.pendingReps, { SessionEngine.setPendingReps(it) }, 0, 200)
    }
    Button(
        onClick = { SessionEngine.completeSet() },
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = MaterialTheme.shapes.large,
    ) { Text("Done", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { painValue = 4; painDialog = true }, modifier = Modifier.weight(1f)) { Text("Pain", color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = { SessionEngine.endExerciseEarly() }, modifier = Modifier.weight(1f)) { Text("Stop & log") }
        OutlinedButton(onClick = { SessionEngine.skip() }, modifier = Modifier.weight(1f)) { Text("Skip") }
    }

    if (painDialog) {
        AlertDialog(
            onDismissRequest = { painDialog = false },
            title = { Text("How bad is it right now?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PainPicker(painValue, { painValue = it })
                    Text(if (painValue > 3) "Stop this exercise. Leg work shouldn't hurt your shoulder; check you're not bracing with the right arm." else "Within the limit. Carry on if it settles.")
                }
            },
            confirmButton = { TextButton(onClick = { painDialog = false; SessionEngine.stopForPain(painValue) }) { Text("Stop exercise") } },
            dismissButton = { TextButton(onClick = { painDialog = false }) { Text("Continue") } },
        )
    }
}

@Composable
private fun LogPanel(ex: Exercise, st: EngineState) {
    val d = st.draft
    fun upd(nd: LogDraft) = SessionEngine.updateDraft(nd)
    var adjust by remember(ex.id) { mutableStateOf(d.completion != Completion.FULL) }
    var noteOpen by remember(ex.id) { mutableStateOf(false) }
    val next = st.items.getOrNull(st.index + 1)

    Muted("LOG")
    Text(ex.name, style = MaterialTheme.typography.headlineSmall)

    if (d.completion != Completion.SKIPPED) {
        SectionCard("Peak pain during it") {
            PainPicker(d.pain, { upd(d.copy(pain = it)) })
        }
        SectionCard("Effort") {
            TileChoice(
                listOf(
                    Triple(Effort.EASY, "Easy", "could do more"),
                    Triple(Effort.MODERATE, "Moderate", "challenging"),
                    Triple(Effort.HARD, "Hard", "struggled"),
                ),
                d.effort.ifBlank { null },
            ) { upd(d.copy(effort = if (d.effort == it) "" else it)) }
        }
        SectionCard("Anything off?") {
            MultiChoice(Tags.all, d.tags) { t -> upd(d.copy(tags = if (t in d.tags) d.tags - t else d.tags + t)) }
        }
    }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (d.completion) {
                    Completion.FULL -> if (st.setReps.isNotEmpty()) "Sets: " + st.setReps.joinToString(" · ") else "Completed: ${d.repsDone} reps"
                    Completion.PARTIAL -> "Partial: ${d.repsDone} reps"
                    else -> "Skipped"
                },
                Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = { adjust = !adjust }) { Text(if (adjust) "Done" else "Adjust") }
        }
        AnimatedVisibility(adjust, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoice(
                    listOf(Completion.FULL to "Full", Completion.PARTIAL to "Partial", Completion.SKIPPED to "Skipped"),
                    d.completion,
                ) { upd(d.copy(completion = it)) }
                if (ex.mode == Mode.REPS && d.completion != Completion.SKIPPED) {
                    Stepper("Reps (all sets)", d.repsDone, { upd(d.copy(repsDone = it)) }, 0, ex.reps * ex.sets * ex.sides)
                    Stepper("Sets", d.setsDone, { upd(d.copy(setsDone = it)) }, 0, ex.sets * ex.sides)
                }
            }
        }
        SwitchRow("Ask the physio about this", d.askPhysio) { upd(d.copy(askPhysio = it)) }
        if (!noteOpen && d.note.isBlank()) {
            TextButton(onClick = { noteOpen = true }) { Text("+ Add a note") }
        } else {
            OutlinedTextField(d.note, { upd(d.copy(note = it)) }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
        }
    }

    Button(
        onClick = { SessionEngine.saveLogAndContinue() },
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(if (next != null) "Save · next: ${next.name}" else "Save · finish", fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun SummaryPanel(st: EngineState, onBack: () -> Unit) {
    var note by remember { mutableStateOf("") }
    val peak = st.loggedPains.maxOrNull()
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) { Celebration() }
    Text("Session done", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    Muted("Tomorrow morning's check-in tells you whether today's load was right.", Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("exercises logged", "${st.loggedCount}/${st.items.size}", Modifier.weight(1f))
        StatTile("peak pain", peak?.toString() ?: "–", Modifier.weight(1f), peak?.let { painColor(it) })
    }
    OutlinedTextField(note, { note = it }, label = { Text("How did it go? (optional)") }, modifier = Modifier.fillMaxWidth())
    Button(
        onClick = { SessionEngine.finishSession(note) { onBack() } },
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shape = MaterialTheme.shapes.large,
    ) { Text("Save session", fontWeight = FontWeight.Bold) }
}

/** Check mark that pops in with a burst of dots. Shown once per session. */
@Composable
private fun Celebration() {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(1300, easing = FastOutSlowInEasing)) }
    val t = anim.value
    val primary = MaterialTheme.colorScheme.primary
    val dots = listOf(Palette.Teal, Palette.Band, Palette.Force, Palette.Blue, Palette.PainLow)
    val pop by animateFloatAsState(if (t > 0.05f) 1f else 0.2f, tween(450, easing = FastOutSlowInEasing), label = "pop")
    Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val n = 16
            for (i in 0 until n) {
                val a = (i * 360f / n) / 180f * Math.PI.toFloat()
                val dist = (34.dp.toPx() + 46.dp.toPx() * t)
                drawCircle(
                    dots[i % dots.size],
                    radius = (5.dp.toPx() * (1f - t * 0.6f)),
                    center = Offset(c.x + cos(a) * dist, c.y + sin(a) * dist),
                    alpha = (1f - t).coerceIn(0f, 1f),
                )
            }
        }
        Box(
            Modifier.size(84.dp).scale(pop).clip(CircleShape).background(primary),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(40.dp)) {
                val w = size.width
                val h = size.height
                val sw = 5.dp.toPx()
                drawLine(Color.White, Offset(w * 0.12f, h * 0.55f), Offset(w * 0.4f, h * 0.82f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(Color.White, Offset(w * 0.4f, h * 0.82f), Offset(w * 0.9f, h * 0.2f), strokeWidth = sw, cap = StrokeCap.Round)
            }
        }
    }
}
