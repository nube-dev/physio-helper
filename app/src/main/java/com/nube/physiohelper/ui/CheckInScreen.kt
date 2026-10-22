package com.nube.physiohelper.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nube.physiohelper.App
import com.nube.physiohelper.data.CheckIn
import com.nube.physiohelper.data.RedFlags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CheckInScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val date = App.today()
    var pain by remember { mutableStateOf<Int?>(null) }
    var stiffness by remember { mutableStateOf<Int?>(null) }
    var night by remember { mutableStateOf<Boolean?>(null) }
    var flags by remember { mutableStateOf(setOf<String>()) }
    var note by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var showStop by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { App.dao.checkIn(date) }?.let { c ->
            pain = c.morningPain
            stiffness = c.stiffnessMin
            night = c.nightPain
            flags = c.redFlags.split(";").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            note = c.note
        }
        loaded = true
    }

    Screen("Morning check", onBack) {
        Muted("This pairs with yesterday's session. If mornings get worse, the load was too much.")
        SectionCard("Pain right now") {
            PainPicker(pain, { pain = it })
        }
        SectionCard("Morning stiffness") {
            SingleChoice(
                listOf<Pair<Int?, String>>(0 to "None", 5 to "5 min", 15 to "15 min", 30 to "30 min", 60 to "1 hour", 120 to "2+ hours"),
                stiffness,
            ) { stiffness = if (stiffness == it) null else it }
        }
        SectionCard("Last night") {
            TileChoice(
                listOf(Triple(false, "Slept OK", "no pain at night"), Triple(true, "Woke up", "from shoulder pain")),
                night,
            ) { night = if (night == it) null else it }
        }
        SectionCard("Warning signs", container = MaterialTheme.colorScheme.errorContainer) {
            RedFlags.all.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = f in flags, onCheckedChange = { flags = if (it) flags + f else flags - f })
                    Text(f, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
        Button(
            enabled = loaded && pain != null,
            onClick = {
                val p = pain ?: return@Button
                scope.launch {
                    withContext(Dispatchers.IO) {
                        App.dao.upsertCheckIn(
                            CheckIn(
                                date = date, morningPain = p, stiffnessMin = stiffness, nightPain = night,
                                redFlags = flags.joinToString("; "), note = note.trim(), loggedAt = System.currentTimeMillis(),
                            )
                        )
                    }
                    if (flags.isNotEmpty()) showStop = true else onBack()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.large,
        ) { Text(if (pain == null) "Pick a pain number" else "Save", fontWeight = FontWeight.Bold) }
    }

    if (showStop) {
        AlertDialog(
            onDismissRequest = { showStop = false; onBack() },
            title = { Text("Stop the routine") },
            text = { Text("You logged a warning sign. Your plan says to stop and get it checked. Don't exercise through this.") },
            confirmButton = { TextButton(onClick = { showStop = false; onBack() }) { Text("Understood") } },
        )
    }
}
