package com.nube.physiohelper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nube.physiohelper.App
import com.nube.physiohelper.data.PhysioQuestion
import com.nube.physiohelper.data.RedFlags

@Composable
fun MoreScreen(nav: (Route) -> Unit) {
    val ctx = LocalContext.current
    val questions by rememberFlow(emptyList<PhysioQuestion>()) { App.dao.questionsFlow() }
    val open = questions.count { !it.resolved }
    var showFlags by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    }

    Screen("More", onBack = null, inTabs = true) {
        Group {
            MoreRow(Icons.Filled.DateRange, "Weekly score", "SPADI questionnaire, 2 minutes") { nav(Route.Spadi) }
            MoreRow(Icons.Filled.Star, "Questions for the physio", if (open > 0) "$open open" else "Nothing open") { nav(Route.Questions) }
            MoreRow(Icons.Filled.Share, "Export & backup", "Copy for Claude, CSV, backup and restore") { nav(Route.Data) }
        }
        Group {
            MoreRow(Icons.Filled.Notifications, "Reminders & session", "Times, voice cues, screen awake") { nav(Route.Settings) }
            MoreRow(Icons.Filled.Build, "Reminder setup (iQOO)", if (App.settings.setupGuideSeen) "Done" else "Not done yet") { nav(Route.Battery) }
        }
        Group {
            MoreRow(Icons.Filled.Warning, "Warning signs", "When to stop and get it checked", tint = MaterialTheme.colorScheme.error) { showFlags = true }
            MoreRow(Icons.Filled.Info, "About", "Version $version") { showAbout = true }
        }
    }

    if (showFlags) {
        AlertDialog(
            onDismissRequest = { showFlags = false },
            title = { Text("Stop and get it checked if:") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RedFlags.all.forEach { Text("• $it") }
                    Text("Tick them in the morning check-in so they show up in your export.", modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { showFlags = false }) { Text("OK") } },
        )
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("Physio Helper $version") },
            text = {
                Text(
                    "A tracker for a home physio routine. It is not medical advice: the default program is one person's " +
                        "prescription. Use the exercises, doses and limits your own physio gives you.\n\n" +
                        "Your data stays on this phone. Source: github.com/nube-dev/physio-helper"
                )
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer),
    ) { content() }
}

@Composable
private fun MoreRow(icon: ImageVector, title: String, sub: String, tint: Color? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background((tint ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tint ?: MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Muted(sub)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
