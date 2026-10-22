package com.nube.physiohelper.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var pain by remember { mutableIntStateOf(0) }
    var stiffness by remember { mutableIntStateOf(0) }
    var night by remember { mutableStateOf<Boolean?>(null) }
    var flags by remember { mutableStateOf(setOf<String>()) }
    var note by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var showStop by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { App.dao.checkIn(date) }?.let { c ->
            pain = c.morningPain
            stiffness = c.stiffnessMin ?: 0
            night = c.nightPain
            flags = c.redFlags.split(";").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            note = c.note
        }
        loaded = true
    }

    Screen("Morning check-in", onBack) {
        Muted("Logged for $date. This pairs with yesterday's session: if mornings get worse, the load was too much.")
        SectionCard {
            PainSlider("Shoulder pain right now", pain) { pain = it }
        }
        SectionCard("Morning stiffness") {
            Stepper("Minutes until it loosens", stiffness, { stiffness = it }, 0, 240, 5, " min")
        }
        SectionCard("Last night") {
            SingleChoice(listOf<Pair<Boolean?, String>>(false to "Slept OK", true to "Woke from shoulder pain"), night) {
                night = if (night == it) null else it
            }
        }
        SectionCard("Warning signs", container = MaterialTheme.colorScheme.errorContainer) {
            RedFlags.all.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = f in flags, onCheckedChange = { flags = if (it) flags + f else flags - f })
                    Text(f)
                }
            }
        }
        OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
        Button(
            enabled = loaded,
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        App.dao.upsertCheckIn(
                            CheckIn(
                                date = date, morningPain = pain, stiffnessMin = stiffness, nightPain = night,
                                redFlags = flags.joinToString("; "), note = note.trim(), loggedAt = System.currentTimeMillis(),
                            )
                        )
                    }
                    if (flags.isNotEmpty()) showStop = true else onBack()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save", modifier = Modifier.padding(6.dp)) }
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
