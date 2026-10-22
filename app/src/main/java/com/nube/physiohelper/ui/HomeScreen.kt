package com.nube.physiohelper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nube.physiohelper.App
import com.nube.physiohelper.analysis.Analysis
import com.nube.physiohelper.data.CheckIn
import com.nube.physiohelper.data.PhysioQuestion
import com.nube.physiohelper.data.RedFlags
import com.nube.physiohelper.data.Session
import com.nube.physiohelper.data.SpadiResult
import com.nube.physiohelper.data.SessionType
import com.nube.physiohelper.session.SessionEngine
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun HomeScreen(nav: (Route) -> Unit) {
    val ctx = LocalContext.current
    val sessions by rememberFlow(emptyList<Session>()) { App.dao.sessionsFlow() }
    val checkIns by rememberFlow(emptyList<CheckIn>()) { App.dao.checkInsFlow() }
    val spadi by rememberFlow(emptyList<SpadiResult>()) { App.dao.spadiFlow() }
    val questions by rememberFlow(emptyList<PhysioQuestion>()) { App.dao.questionsFlow() }
    val engine by SessionEngine.state.collectAsState()
    val s = App.settings
    val today = LocalDate.now()
    val todayStr = today.toString()
    var showFlags by remember { mutableStateOf(false) }

    val week = Analysis.rehabWeek(s.rehabStart(), today)
    val adh = Analysis.adherence(sessions, s.weeklyTarget, 7, today)
    val doneToday = sessions.any { it.date == todayStr && it.status == "COMPLETED" }
    val todayCheck = checkIns.firstOrNull { it.date == todayStr }
    val lastSpadi = spadi.firstOrNull()
    val spadiDue = lastSpadi == null || ChronoUnit.DAYS.between(LocalDate.parse(lastSpadi.date), today) >= 7
    val openQuestions = questions.count { !it.resolved }
    val backupDays = if (s.lastBackupAt == 0L) null else ((System.currentTimeMillis() - s.lastBackupAt) / 86_400_000L).toInt()

    Screen("Physio Helper", onBack = null) {
        Text("Week $week of rehab", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Muted(Analysis.phaseText(week))

        if (todayCheck != null && todayCheck.redFlags.isNotBlank()) {
            SectionCard("Warning signs logged today", container = MaterialTheme.colorScheme.errorContainer) {
                Text(todayCheck.redFlags)
                Text("Stop the routine and get it checked by the doctor.", fontWeight = FontWeight.Bold)
            }
        }

        if (!s.setupGuideSeen) {
            SectionCard("Reminders need setup on iQOO", container = MaterialTheme.colorScheme.tertiaryContainer) {
                Text("iQOO kills background apps, which silently stops reminders. Takes 2 minutes.")
                Button(onClick = { nav(Route.Battery) }) { Text("Set up now") }
            }
        }

        SectionCard("Today") {
            KeyValue("Session", if (doneToday) "Done" else "Not yet", if (doneToday) painColor(0) else null)
            KeyValue(
                "Morning check-in",
                todayCheck?.let { "Pain ${it.morningPain}/10" } ?: "Not logged",
                todayCheck?.let { painColor(it.morningPain) },
            )
            KeyValue("This week", "${adh.thisWeek} / ${adh.target} sessions")
            KeyValue("Streak", "${adh.streak} day${if (adh.streak == 1) "" else "s"}")
        }

        if (engine.active) {
            Button(onClick = { nav(Route.Session) }, modifier = Modifier.fillMaxWidth()) {
                Text("Resume session", modifier = Modifier.padding(8.dp))
            }
        } else {
            Button(
                onClick = {
                    SessionEngine.start(ctx, SessionType.FULL) { toast(ctx, "No exercises enabled. Check the program.") }
                    nav(Route.Session)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start full session (35–40 min)", modifier = Modifier.padding(8.dp)) }
            FilledTonalButton(
                onClick = {
                    SessionEngine.start(ctx, SessionType.SHORT) { toast(ctx, "No exercises marked for the short session.") }
                    nav(Route.Session)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Bad day: short session (~15 min)") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { nav(Route.CheckIn) }, modifier = Modifier.weight(1f)) {
                Text(if (todayCheck == null) "Morning check-in" else "Edit check-in")
            }
            OutlinedButton(onClick = { nav(Route.Spadi) }, modifier = Modifier.weight(1f)) {
                Text(if (spadiDue) "Weekly score (due)" else "Weekly score")
            }
        }

        if (backupDays == null || backupDays > 8) {
            SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                Text(if (backupDays == null) "No backup yet. If the app is uninstalled, your log is gone." else "Last backup $backupDays days ago.")
                TextButton(onClick = { nav(Route.Data) }) { Text("Back up now") }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { nav(Route.Analysis) }, modifier = Modifier.weight(1f)) { Text("Analysis") }
                OutlinedButton(onClick = { nav(Route.History) }, modifier = Modifier.weight(1f)) { Text("History") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { nav(Route.Program) }, modifier = Modifier.weight(1f)) { Text("Program") }
                OutlinedButton(onClick = { nav(Route.Questions) }, modifier = Modifier.weight(1f)) {
                    Text(if (openQuestions > 0) "Physio Qs ($openQuestions)" else "Physio Qs")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { nav(Route.Data) }, modifier = Modifier.weight(1f)) { Text("Export") }
                OutlinedButton(onClick = { nav(Route.Settings) }, modifier = Modifier.weight(1f)) { Text("Settings") }
            }
        }

        TextButton(onClick = { showFlags = true }) { Text("Warning signs: when to stop", color = MaterialTheme.colorScheme.error) }
    }

    if (showFlags) {
        AlertDialog(
            onDismissRequest = { showFlags = false },
            title = { Text("Stop and get it checked if:") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RedFlags.all.forEach { Text("• $it") }
                    Text("Log them in the morning check-in so they show up in your export.", modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { showFlags = false }) { Text("OK") } },
        )
    }
}
