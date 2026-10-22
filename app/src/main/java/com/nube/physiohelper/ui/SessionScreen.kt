package com.nube.physiohelper.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

fun doseText(e: Exercise): String = if (e.mode == Mode.TIMED) {
    "${e.durationSec / 60} min"
} else buildString {
    append("${e.sets} × ${e.reps} reps")
    val parts = mutableListOf<String>()
    if (e.upSec > 0) parts += "${e.upSec}s up"
    if (e.holdSec > 0) parts += "${e.holdSec}s hold"
    if (e.downSec > 0) parts += "${e.downSec}s down"
    if (e.restSec > 0) parts += "${e.restSec}s rest"
    if (parts.isNotEmpty()) append(" · " + parts.joinToString(", "))
    if (e.bandColor.isNotBlank()) append(" · ${e.bandColor} band")
}

@Composable
fun SessionScreen(onBack: () -> Unit) {
    val st by SessionEngine.state.collectAsState()
    val ctx = LocalContext.current
    var waited by remember { mutableStateOf(false) }
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
                Button(onClick = onBack) { Text("Back to home") }
            }
        }
        return
    }

    val ex = st.current
    val title = if (st.phase == Phase.SUMMARY) "Session summary" else "Exercise ${st.index + 1} of ${st.items.size}"
    var confirmQuit by remember { mutableStateOf(false) }

    Screen(title, onBack, actions = {
        TextButton(onClick = { confirmQuit = true }) { Text("End") }
    }) {
        LinearProgressIndicator(
            progress = { (st.index + if (st.phase == Phase.SUMMARY) 1 else 0).toFloat() / st.items.size.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            st.phase == Phase.SUMMARY -> SummaryPanel(st, onBack)
            ex == null -> Text("Nothing to do.")
            st.phase == Phase.READY -> ReadyPanel(ex, st)
            st.phase == Phase.LOG -> LogPanel(ex, st.draft)
            else -> RunningPanel(ex, st)
        }
    }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("End session?") },
            text = { Text("Save what you've logged so far, or discard the session.") },
            confirmButton = {
                TextButton(onClick = { confirmQuit = false; SessionEngine.goToSummary() }) { Text("Save so far") }
            },
            dismissButton = {
                TextButton(onClick = { confirmQuit = false; SessionEngine.abandon { onBack() } }) { Text("Discard") }
            },
        )
    }
}

@Composable
private fun ExerciseInfo(ex: Exercise) {
    Text(ex.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(doseText(ex), color = MaterialTheme.colorScheme.primary)
    ExerciseMedia(ex)
}

@Composable
private fun ReadyPanel(ex: Exercise, st: EngineState) {
    ExerciseInfo(ex)
    if (ex.steps.isNotBlank()) SectionCard("How") { Text(ex.steps) }
    if (ex.cues.isNotBlank()) SectionCard("Cues") { Text(ex.cues) }
    if (ex.avoid.isNotBlank()) SectionCard("Avoid", container = MaterialTheme.colorScheme.errorContainer) { Text(ex.avoid) }
    Button(onClick = { SessionEngine.beginExercise() }, modifier = Modifier.fillMaxWidth()) {
        Text("Start", fontSize = 18.sp, modifier = Modifier.padding(8.dp))
    }
    OutlinedButton(onClick = { SessionEngine.skip() }, modifier = Modifier.fillMaxWidth()) { Text("Skip this exercise") }
    st.items.getOrNull(st.index + 1)?.let { Muted("Next: ${it.name}") }
}

private fun phaseColor(p: Phase): Color = when (p) {
    Phase.HOLD, Phase.TIMED -> Color(0xFF1F6F5C)
    Phase.UP, Phase.DOWN -> Color(0xFF1565C0)
    Phase.REST, Phase.SET_REST -> Color(0xFF6D4C41)
    else -> Color(0xFF616161)
}

@Composable
private fun RunningPanel(ex: Exercise, st: EngineState) {
    var painDialog by remember { mutableStateOf(false) }
    var painValue by remember { mutableIntStateOf(0) }

    Text(ex.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    ExerciseMedia(ex, height = 200.dp)
    SectionCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (st.paused) "PAUSED" else st.phase.label.uppercase(),
                color = phaseColor(st.phase),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (st.phase == Phase.TIMED) Notifications.fmt(st.secondsLeft) else st.secondsLeft.toString(),
                fontSize = 96.sp,
                fontWeight = FontWeight.Bold,
                color = phaseColor(st.phase),
            )
            LinearProgressIndicator(
                progress = { if (st.phaseTotal == 0) 0f else 1f - st.secondsLeft.toFloat() / st.phaseTotal },
                modifier = Modifier.fillMaxWidth(),
                color = phaseColor(st.phase),
            )
            if (ex.mode == Mode.REPS && st.phase != Phase.GET_READY) {
                Text(
                    "Rep ${st.rep} of ${ex.reps}" + if (ex.sets > 1) "  ·  Set ${st.set} of ${ex.sets}" else "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
    if (ex.cues.isNotBlank()) Muted(ex.cues)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { SessionEngine.togglePause() }, modifier = Modifier.weight(1f)) {
            Text(if (st.paused) "Resume" else "Pause", modifier = Modifier.padding(6.dp))
        }
        Button(
            onClick = { SessionEngine.pause(); painValue = 4; painDialog = true },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) { Text("Pain!", modifier = Modifier.padding(6.dp)) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { SessionEngine.endExerciseEarly() }, modifier = Modifier.weight(1f)) { Text("Stop here") }
        OutlinedButton(onClick = { SessionEngine.restartExercise() }, modifier = Modifier.weight(1f)) { Text("Restart") }
        OutlinedButton(onClick = { SessionEngine.skip() }, modifier = Modifier.weight(1f)) { Text("Skip") }
    }

    if (painDialog) {
        AlertDialog(
            onDismissRequest = { painDialog = false; SessionEngine.resume() },
            title = { Text("How bad is it?") },
            text = {
                Column {
                    PainSlider("Pain now", painValue) { painValue = it }
                    if (painValue > 5) {
                        Text("Above 5/10: stop this exercise. Log it and move on.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    } else if (painValue > 3) {
                        Text("Above your 3/10 limit: use less range or effort for the rest of this exercise.")
                    } else {
                        Text("Within the 3/10 limit. Fine to continue if it settles by tomorrow morning.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { painDialog = false; SessionEngine.stopForPain(painValue) }) { Text("Stop exercise") }
            },
            dismissButton = {
                TextButton(onClick = { painDialog = false; SessionEngine.resume() }) { Text("Continue") }
            },
        )
    }
}

@Composable
private fun LogPanel(ex: Exercise, d: LogDraft) {
    fun upd(nd: LogDraft) = SessionEngine.updateDraft(nd)
    Text("Log: ${ex.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    SectionCard("Completion") {
        SingleChoice(
            listOf(Completion.FULL to "Full", Completion.PARTIAL to "Partial", Completion.SKIPPED to "Skipped"),
            d.completion,
        ) { upd(d.copy(completion = it)) }
        if (ex.mode == Mode.REPS && d.completion != Completion.SKIPPED) {
            Stepper("Reps done (all sets)", d.repsDone, { upd(d.copy(repsDone = it)) }, 0, ex.reps * ex.sets)
            Stepper("Sets done", d.setsDone, { upd(d.copy(setsDone = it)) }, 0, ex.sets)
        }
    }
    if (d.completion != Completion.SKIPPED) {
        SectionCard("How did it feel?") {
            PainSlider("Peak pain during", d.pain) { upd(d.copy(pain = it)) }
            Text("Effort")
            SingleChoice(
                listOf(Effort.EASY to "Easy", Effort.MODERATE to "Moderate", Effort.HARD to "Hard"),
                d.effort,
            ) { upd(d.copy(effort = if (d.effort == it) "" else it)) }
            Muted("\"Easy\" = could have done more with good form. Needed for level-up suggestions.")
        }
        SectionCard("Anything off?") {
            MultiChoice(Tags.all, d.tags) { t -> upd(d.copy(tags = if (t in d.tags) d.tags - t else d.tags + t)) }
        }
    }
    OutlinedTextField(
        value = d.note,
        onValueChange = { upd(d.copy(note = it)) },
        label = { Text("Note (optional)") },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = d.askPhysio, onCheckedChange = { upd(d.copy(askPhysio = it)) })
        Text("Add to my questions for the physio")
    }
    Button(onClick = { SessionEngine.saveLogAndContinue() }, modifier = Modifier.fillMaxWidth()) {
        Text("Save & next", modifier = Modifier.padding(6.dp))
    }
}

@Composable
private fun SummaryPanel(st: EngineState, onBack: () -> Unit) {
    var note by remember { mutableStateOf("") }
    val peak = st.loggedPains.maxOrNull()
    SectionCard("Done") {
        KeyValue("Exercises logged", "${st.loggedCount} of ${st.items.size}")
        KeyValue("Peak pain", peak?.let { "$it / 10" } ?: "–", peak?.let { painColor(it) })
        Muted("Tomorrow morning, log how the shoulder feels. That decides whether today's load was right.")
    }
    OutlinedTextField(
        value = note,
        onValueChange = { note = it },
        label = { Text("Session note (optional)") },
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { SessionEngine.finishSession(note) { onBack() } }, modifier = Modifier.fillMaxWidth()) {
        Text("Save session", modifier = Modifier.padding(6.dp))
    }
    if (st.loggedCount < st.items.size) {
        val remaining = st.items.size - st.loggedCount
        Muted("$remaining exercise(s) not logged; they won't count against you, but they won't count for you either.")
    }
}
