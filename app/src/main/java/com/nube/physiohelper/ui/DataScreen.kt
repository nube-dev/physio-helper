package com.nube.physiohelper.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nube.physiohelper.App
import com.nube.physiohelper.export.Exporter
import com.nube.physiohelper.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun DataScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var lastBackup by remember { mutableLongStateOf(App.settings.lastBackupAt) }
    var pendingImport by remember { mutableStateOf<String?>(null) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
                }
                if (text == null) toast(ctx, "Couldn't read that file") else pendingImport = text
            }
        }
    }

    fun copyForClaude(days: Int) = scope.launch {
        val text = withContext(Dispatchers.IO) { Exporter.claudeSummary(days) }
        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Physio Helper export", text))
        toast(ctx, "Copied ${text.length} characters. Paste it into Claude.")
    }

    Screen("Export & backup", onBack) {
        SectionCard("For Claude") {
            Muted("A compact summary: program, adherence, check-ins, per-exercise stats, session log, SPADI and open questions.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { copyForClaude(14) }, modifier = Modifier.weight(1f)) { Text("Copy 14 days") }
                Button(onClick = { copyForClaude(30) }, modifier = Modifier.weight(1f)) { Text("Copy 30 days") }
            }
            OutlinedButton(onClick = {
                scope.launch {
                    val text = withContext(Dispatchers.IO) { Exporter.claudeSummary(30) }
                    ctx.startActivity(Exporter.shareFileIntent(ctx, Exporter.claudeName(), "text/markdown", text))
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Share as a file (30 days)") }
        }

        SectionCard("Raw data") {
            OutlinedButton(onClick = {
                scope.launch {
                    val csv = withContext(Dispatchers.IO) { Exporter.logsCsv() }
                    ctx.startActivity(Exporter.shareFileIntent(ctx, Exporter.csvName(), "text/csv", csv))
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Export exercise logs (CSV)") }
            OutlinedButton(onClick = {
                scope.launch {
                    val json = withContext(Dispatchers.IO) { Exporter.backupJson() }
                    ctx.startActivity(Exporter.shareFileIntent(ctx, Exporter.jsonName(), "application/json", json))
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Share full backup (JSON)") }
        }

        SectionCard("Backup") {
            Text(if (lastBackup == 0L) "No backup saved yet." else "Last saved: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(lastBackup)))
            Muted("Saved to Downloads/PhysioHelper. Weekly automatic backups are set in Settings. Copy them off the phone now and then.")
            Button(onClick = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { Exporter.saveBackupToDownloads(ctx) }
                    lastBackup = App.settings.lastBackupAt
                    toast(ctx, if (ok) "Backup saved to Downloads/PhysioHelper" else "Backup failed")
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Save backup now") }
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/*", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("Restore from a backup file")
            }
        }
    }

    pendingImport?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Replace all data?") },
            text = { Text("Everything in the app is replaced with the backup's contents: program, sessions, logs, check-ins, scores, questions and settings.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { Exporter.restoreJson(text) } }
                        if (result.isSuccess) {
                            ReminderScheduler.scheduleAll(ctx)
                            toast(ctx, "Restored")
                        } else {
                            toast(ctx, "Restore failed: ${result.exceptionOrNull()?.message}")
                        }
                    }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
}
