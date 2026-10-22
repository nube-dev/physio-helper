package com.nube.physiohelper.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.nube.physiohelper.App
import com.nube.physiohelper.data.Completion
import com.nube.physiohelper.data.ExerciseLog
import com.nube.physiohelper.data.Session
import com.nube.physiohelper.data.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HistoryScreen(nav: (Route) -> Unit, onBack: () -> Unit) {
    val sessions by rememberFlow(emptyList<Session>()) { App.dao.sessionsFlow() }
    val logs by rememberFlow(emptyList<ExerciseLog>()) { App.dao.logsFlow() }
    val counts = logs.groupingBy { it.sessionId }.eachCount()
    val visible = sessions.filter { it.status != SessionStatus.IN_PROGRESS }

    Screen("History", onBack) {
        if (visible.isEmpty()) Muted("No sessions yet.")
        visible.forEach { s ->
            SectionCard(modifier = Modifier.clickable { nav(Route.SessionDetail(s.id)) }) {
                Text(
                    "${s.date} · ${if (s.type == "SHORT") "Short" else "Full"} session",
                    style = MaterialTheme.typography.titleMedium,
                )
                KeyValue("Status", s.status.lowercase().replaceFirstChar { it.uppercase() })
                KeyValue("Exercises logged", (counts[s.id] ?: 0).toString())
                s.peakPain?.let { KeyValue("Peak pain", "$it / 10", painColor(it)) }
                s.endedAt?.let { KeyValue("Duration", "${(it - s.startedAt) / 60000} min") }
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

    Screen(session?.date ?: "Session", onBack) {
        if (session != null) {
            SectionCard {
                KeyValue("Type", session.type.lowercase())
                KeyValue("Status", session.status.lowercase())
                session.peakPain?.let { KeyValue("Peak pain", "$it / 10", painColor(it)) }
                if (session.note.isNotBlank()) Text(session.note)
            }
        }
        logs.forEach { l ->
            SectionCard {
                Text(l.exerciseName, fontWeight = FontWeight.SemiBold)
                KeyValue("Completion", l.completion.lowercase())
                if (l.completion != Completion.SKIPPED) {
                    KeyValue("Reps", "${l.repsDone} / ${l.repsPlanned}")
                    KeyValue("Pain", "${l.pain} / 10", painColor(l.pain))
                    if (l.effort.isNotBlank()) KeyValue("Effort", l.effort.lowercase())
                }
                if (l.tags.isNotBlank()) KeyValue("Tags", l.tags.replace(",", ", "))
                if (l.bandColor.isNotBlank()) KeyValue("Band", l.bandColor)
                if (l.note.isNotBlank()) Column { Muted("Note"); Text(l.note) }
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
