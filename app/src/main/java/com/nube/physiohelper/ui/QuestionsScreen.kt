package com.nube.physiohelper.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import com.nube.physiohelper.App
import com.nube.physiohelper.data.PhysioQuestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun QuestionsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val questions by rememberFlow(emptyList<PhysioQuestion>()) { App.dao.questionsFlow() }
    var draft by remember { mutableStateOf("") }
    val open = questions.filter { !it.resolved }
    val done = questions.filter { it.resolved }

    Screen("Questions for the physio", onBack) {
        Muted("Exercises you flag during a session land here. Tick them off at the appointment.")
        OutlinedTextField(draft, { draft = it }, label = { Text("New question") }, modifier = Modifier.fillMaxWidth())
        Button(
            enabled = draft.isNotBlank(),
            onClick = {
                val t = draft.trim()
                draft = ""
                scope.launch(Dispatchers.IO) { App.dao.insertQuestion(PhysioQuestion(text = t, createdAt = System.currentTimeMillis())) }
            },
        ) { Text("Add") }

        SectionCard("Open (${open.size})") {
            if (open.isEmpty()) Muted("Nothing open.")
            open.forEach { q -> QuestionRow(q, scope) }
        }
        if (done.isNotEmpty()) {
            SectionCard("Answered (${done.size})") {
                done.forEach { q -> QuestionRow(q, scope) }
            }
        }
    }
}

@Composable
private fun QuestionRow(q: PhysioQuestion, scope: kotlinx.coroutines.CoroutineScope) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = q.resolved, onCheckedChange = { r -> scope.launch(Dispatchers.IO) { App.dao.updateQuestion(q.copy(resolved = r)) } })
        Text(
            q.text,
            Modifier.weight(1f),
            textDecoration = if (q.resolved) TextDecoration.LineThrough else null,
            color = if (q.resolved) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        TextButton(onClick = { scope.launch(Dispatchers.IO) { App.dao.deleteQuestion(q) } }) { Text("✕") }
    }
}
