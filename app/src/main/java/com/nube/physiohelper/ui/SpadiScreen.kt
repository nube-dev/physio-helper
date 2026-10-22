package com.nube.physiohelper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nube.physiohelper.App
import com.nube.physiohelper.analysis.Analysis
import com.nube.physiohelper.data.Spadi
import com.nube.physiohelper.data.SpadiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SpadiScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val answers = remember { mutableStateListOf(*Array(13) { 0 }) }
    val na = remember { mutableStateListOf(*Array(13) { false }) }
    val history by rememberFlow(emptyList<SpadiResult>()) { App.dao.spadiFlow() }
    val values = answers.mapIndexed { i, v -> if (na[i]) -1 else v }
    val (pain, dis, total) = Analysis.spadiScores(values)

    Screen("Weekly score", onBack) {
        Muted("SPADI: rate the past week, 0 = no pain / no difficulty, 10 = worst imaginable / so difficult it needs help. Lower is better.")
        SectionCard("Pain: how severe was it when…") {
            Spadi.painItems.forEachIndexed { i, label -> Item(label, i, answers, na) }
        }
        SectionCard("Difficulty: how hard was it to…") {
            Spadi.disabilityItems.forEachIndexed { j, label -> Item(label, j + 5, answers, na) }
        }
        SectionCard("Score") {
            KeyValue("Total", "%.0f / 100".format(total))
            KeyValue("Pain", "%.0f".format(pain))
            KeyValue("Disability", "%.0f".format(dis))
            Muted("A drop of about 13 points or more is generally considered a meaningful change.")
        }
        Button(
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        App.dao.insertSpadi(
                            SpadiResult(
                                date = App.today(), answers = values.joinToString(","),
                                painScore = pain, disabilityScore = dis, totalScore = total,
                                loggedAt = System.currentTimeMillis(),
                            )
                        )
                    }
                    onBack()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save", modifier = Modifier.padding(6.dp)) }

        if (history.isNotEmpty()) {
            SectionCard("History") {
                history.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${r.date}: total %.0f (pain %.0f, disability %.0f)".format(r.totalScore, r.painScore, r.disabilityScore), Modifier.weight(1f))
                        TextButton(onClick = { scope.launch(Dispatchers.IO) { App.dao.deleteSpadi(r) } }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Item(label: String, i: Int, answers: MutableList<Int>, na: MutableList<Boolean>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { na[i] = !na[i] }) { Text(if (na[i]) "Rate it" else "N/A") }
        }
        if (na[i]) Muted("Not applicable") else PainPicker(answers[i], { answers[i] = it }, showWord = false)
    }
}
