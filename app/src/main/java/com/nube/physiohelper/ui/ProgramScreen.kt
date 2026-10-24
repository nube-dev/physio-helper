package com.nube.physiohelper.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nube.physiohelper.App
import com.nube.physiohelper.data.Category
import com.nube.physiohelper.data.Exercise
import com.nube.physiohelper.data.Mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProgramScreen(nav: (Route) -> Unit) {
    val scope = rememberCoroutineScope()
    val exercises by rememberFlow(emptyList<Exercise>()) { App.dao.exercisesFlow() }
    val enabled = exercises.count { it.enabled }

    fun move(i: Int, delta: Int) {
        val j = i + delta
        if (j !in exercises.indices) return
        val a = exercises[i]
        val b = exercises[j]
        scope.launch(Dispatchers.IO) {
            App.dao.upsertExercises(listOf(a.copy(orderIndex = b.orderIndex), b.copy(orderIndex = a.orderIndex)))
        }
    }

    Screen("Program", onBack = null, inTabs = true) {
        Muted("$enabled of ${exercises.size} exercises in your session, about ${com.nube.physiohelper.analysis.Analysis.estimateMin(exercises.filter { it.enabled }, App.settings.getReadySec)} min. Tap one to change its dose, cues or clip.")
        exercises.forEachIndexed { i, e ->
            val alpha = if (e.enabled) 1f else 0.55f
            Card(
                Modifier.fillMaxWidth().clickable { nav(Route.EditExercise(e.id)) },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(64.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).alpha(alpha)
                    ) {
                        ExerciseFigure(e.id, e.category, Modifier.size(64.dp).padding(4.dp))
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f).alpha(alpha), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(e.name, fontWeight = FontWeight.SemiBold)
                        Muted(doseText(e))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (e.pendingPhysio) Pill("pending physio", Palette.PainMid)
                            if (e.inShortSession) Pill("short", MaterialTheme.colorScheme.primary)
                            if (e.level > 1) Pill("level ${e.level}", MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    Column {
                        IconButton(onClick = { move(i, -1) }, enabled = i > 0, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { move(i, 1) }, enabled = i < exercises.size - 1, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
                    }
                    Switch(checked = e.enabled, onCheckedChange = { on -> scope.launch(Dispatchers.IO) { App.dao.upsertExercise(e.copy(enabled = on)) } })
                }
            }
        }
        Button(onClick = { nav(Route.EditExercise(null)) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.large) { Text("Add exercise") }
    }
}

@Composable
fun EditExerciseScreen(id: String?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var e by remember { mutableStateOf<Exercise?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        e = withContext(Dispatchers.IO) {
            id?.let { App.dao.exercise(it) } ?: Exercise(
                id = "custom_${System.currentTimeMillis()}",
                name = "",
                category = Category.OTHER,
                orderIndex = (App.dao.exercises().maxOfOrNull { it.orderIndex } ?: 0) + 1,
            )
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            e = e?.copy(mediaUri = uri.toString())
        }
    }

    val cur = e
    Screen(if (id == null) "New exercise" else "Edit exercise", onBack) {
        if (cur == null) return@Screen
        fun upd(x: Exercise) { e = x }

        OutlinedTextField(cur.name, { upd(cur.copy(name = it)) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        SectionCard("Animation") {
            ExerciseMedia(cur, height = 180.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.launch(arrayOf("image/*", "video/*")) }) { Text("Pick clip") }
                if (cur.mediaUri.isNotBlank()) OutlinedButton(onClick = { upd(cur.copy(mediaUri = "")) }) { Text("Use bundled") }
            }
            Muted("Bundled file name: ${cur.id}.mp4 or .gif")
        }
        SectionCard("Type") {
            SingleChoice(Category.all.map { it to it.lowercase().replaceFirstChar { c -> c.uppercase() } }, cur.category) { upd(cur.copy(category = it)) }
            SingleChoice(listOf(Mode.REPS to "Reps & holds", Mode.TIMED to "Single timer"), cur.mode) { upd(cur.copy(mode = it)) }
        }
        SectionCard("Dose") {
            if (cur.mode == Mode.TIMED) {
                Stepper("Duration", cur.durationSec / 60, { upd(cur.copy(durationSec = it * 60)) }, 1, 60, 1, " min")
            } else {
                Stepper("Sets", cur.sets, { upd(cur.copy(sets = it)) }, 1, 10)
                Stepper("Reps per set", cur.reps, { upd(cur.copy(reps = it)) }, 1, 50)
                Stepper("Lift (up)", cur.upSec, { upd(cur.copy(upSec = it)) }, 0, 30, 1, "s")
                Stepper("Hold", cur.holdSec, { upd(cur.copy(holdSec = it)) }, 0, 120, 1, "s")
                Stepper("Lower (down)", cur.downSec, { upd(cur.copy(downSec = it)) }, 0, 30, 1, "s")
                Stepper("Rest between reps", cur.restSec, { upd(cur.copy(restSec = it)) }, 0, 120, 1, "s")
                Stepper("Rest between sets", cur.setRestSec, { upd(cur.copy(setRestSec = it)) }, 0, 300, 5, "s")
            }
            OutlinedTextField(cur.bandColor, { upd(cur.copy(bandColor = it)) }, label = { Text("Band colour (blank if none)") }, modifier = Modifier.fillMaxWidth())
            Stepper("Level", cur.level, { upd(cur.copy(level = it)) }, 1, 20)
        }
        SectionCard("Session") {
            SwitchRow("Enabled", cur.enabled) { upd(cur.copy(enabled = it)) }
            SwitchRow("In the short (bad-day) session", cur.inShortSession) { upd(cur.copy(inShortSession = it)) }
            SwitchRow("Pending physio confirmation", cur.pendingPhysio) { upd(cur.copy(pendingPhysio = it)) }
        }
        SectionCard("Instructions") {
            OutlinedTextField(cur.steps, { upd(cur.copy(steps = it)) }, label = { Text("How to do it") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(cur.cues, { upd(cur.copy(cues = it)) }, label = { Text("Cues") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(cur.avoid, { upd(cur.copy(avoid = it)) }, label = { Text("Avoid / limits") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(cur.videoUrl, { upd(cur.copy(videoUrl = it)) }, label = { Text("Reference video link") }, modifier = Modifier.fillMaxWidth())
        }
        Button(
            enabled = cur.name.isNotBlank(),
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { App.dao.upsertExercise(cur.copy(name = cur.name.trim())) }
                    onBack()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save", modifier = Modifier.padding(6.dp)) }
        if (id != null) {
            TextButton(onClick = { confirmDelete = true }) { Text("Delete exercise", color = MaterialTheme.colorScheme.error) }
        }
    }

    if (confirmDelete && cur != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${cur.name}?") },
            text = { Text("Past logs stay in your history. Disabling it instead keeps it available for later.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) { App.dao.deleteExercise(cur) }
                        onBack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
