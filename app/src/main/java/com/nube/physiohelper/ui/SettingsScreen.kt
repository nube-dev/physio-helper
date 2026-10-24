package com.nube.physiohelper.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nube.physiohelper.App
import com.nube.physiohelper.reminders.ReminderScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.provider.Settings as AndroidSettings

@Composable
fun SettingsScreen(nav: (Route) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s = App.settings
    fun resched() = ReminderScheduler.scheduleAll(ctx)

    Screen("Settings", onBack) {
        SectionCard("Daily reminders") {
            PrefSwitch("Session reminder", s.sessionReminderOn) { s.sessionReminderOn = it; resched() }
            PrefTime("Time", s.sessionReminderMin) { s.sessionReminderMin = it; resched() }
            PrefSwitch("Morning check-in", s.checkInReminderOn, "Reply with a pain score from the notification") { s.checkInReminderOn = it; resched() }
            PrefTime("Time", s.checkInReminderMin) { s.checkInReminderMin = it; resched() }
            PrefSwitch("Evening nudge if skipped", s.nudgeOn) { s.nudgeOn = it; resched() }
            PrefTime("Time", s.nudgeMin) { s.nudgeMin = it; resched() }
        }
        SectionCard("Weekly") {
            PrefSwitch("Weekly score (SPADI) reminder", s.spadiOn) { s.spadiOn = it; resched() }
            PrefDay(s.spadiDay) { s.spadiDay = it; resched() }
            PrefTime("Time", s.spadiMin) { s.spadiMin = it; resched() }
            PrefSwitch("Automatic backup to Downloads", s.backupOn, "Downloads/PhysioHelper") { s.backupOn = it; resched() }
            PrefDay(s.backupDay) { s.backupDay = it; resched() }
            PrefTime("Time", s.backupMin) { s.backupMin = it; resched() }
        }
        PhysioAppointment()
        SectionCard("Leg strength days") {
            var days by remember { mutableStateOf(s.strengthDays) }
            MultiChoice(dayNames.map { it.second }, days.map { dayNames[it - 1].second }.toSet()) { name ->
                val d = dayNames.first { it.second == name }.first
                days = if (d in days) days - d else days + d
                s.strengthDays = days
            }
            Muted("Non-consecutive days give the legs a rest day in between.")
        }
        SectionCard("Session") {
            PrefStepper("Shoulder sessions per week", s.weeklyTarget, 1, 7, suffix = " /wk") { s.weeklyTarget = it }
            PrefStepper("Get-ready countdown", s.getReadySec, 0, 20, suffix = "s") { s.getReadySec = it }
            PrefStepper("Hold countdown from", s.countdownFrom, 0, 10, suffix = "s") { s.countdownFrom = it }
            PrefSwitch("Keep screen on during session", s.keepScreenOn) { s.keepScreenOn = it }
            PrefSwitch("Voice cues", s.voiceOn, "Rep counts, hold countdown, rest calls") { s.voiceOn = it }
            PrefSwitch("Beeps", s.beepsOn) { s.beepsOn = it }
            PrefSwitch("Vibration", s.vibrateOn) { s.vibrateOn = it }
        }
        RehabStart()
        Button(onClick = { nav(Route.Battery) }, modifier = Modifier.fillMaxWidth()) { Text("Reminder & battery setup (iQOO)") }
        OutlinedButton(onClick = { ReminderScheduler.testIn(ctx, 60); toast(ctx, "Test reminder in 1 minute. Lock the phone and wait.") }, modifier = Modifier.fillMaxWidth()) {
            Text("Send a test reminder in 1 minute")
        }
    }
}

@Composable
private fun PhysioAppointment() {
    val ctx = LocalContext.current
    val s = App.settings
    var at by remember { mutableLongStateOf(s.physioApptAt) }
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")
    SectionCard("Next physio appointment") {
        Text(
            if (at == 0L) "None set" else LocalDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault()).format(fmt),
            fontWeight = FontWeight.SemiBold,
        )
        Muted("You get a reminder 2 hours before, with your open questions.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                pickDate(ctx, LocalDate.now()) { d ->
                    pickTime(ctx, 10 * 60) { m ->
                        val t = d.atTime(m / 60, m % 60).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        at = t
                        s.physioApptAt = t
                        ReminderScheduler.schedule(ctx, ReminderScheduler.T_PHYSIO)
                    }
                }
            }) { Text("Set") }
            if (at != 0L) TextButton(onClick = {
                at = 0L
                s.physioApptAt = 0L
                ReminderScheduler.schedule(ctx, ReminderScheduler.T_PHYSIO)
            }) { Text("Clear") }
        }
    }
}

@Composable
private fun RehabStart() {
    val ctx = LocalContext.current
    val s = App.settings
    var date by remember { mutableStateOf(s.rehabStartDate) }
    SectionCard("Rehab start date") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(date, Modifier.weight(1f))
            TextButton(onClick = { pickDate(ctx, s.rehabStart()) { d -> date = d.toString(); s.rehabStartDate = date } }) { Text("Change") }
        }
        Muted("Used for the week counter and recovery phases.")
    }
}

// ---------------- Battery / reminder setup ----------------

private fun isIgnoringBattery(ctx: Context): Boolean =
    ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

private fun startFirst(ctx: Context, intents: List<Intent>): Boolean {
    for (i in intents) {
        val ok = runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (ok) return true
    }
    return false
}

private fun appDetails(ctx: Context) =
    Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

@Composable
fun BatteryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, ev -> if (ev == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val notifOk = remember(refresh) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val batteryOk = remember(refresh) { isIgnoringBattery(ctx) }
    val exactOk = remember(refresh) { ReminderScheduler.canScheduleExact(ctx) }

    Screen("Reminder setup", onBack) {
        Muted("iQOO (Funtouch / OriginOS) stops background apps aggressively. Without these steps, reminders work for a few days and then silently stop.")

        SectionCard("1. Notifications  ${if (notifOk) "✓" else "✗"}") {
            Text("Allow notifications so reminders can show.")
            OutlinedButton(onClick = {
                startFirst(ctx, listOf(
                    Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName),
                    appDetails(ctx),
                ))
            }) { Text("Open notification settings") }
        }

        SectionCard("2. Battery: no restrictions  ${if (batteryOk) "✓" else "✗"}") {
            Text("Let the app run in the background. On iQOO also set Battery → Background power consumption → Allow.")
            OutlinedButton(onClick = {
                @Suppress("BatteryLife")
                val req = Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                startFirst(ctx, listOf(req, Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), appDetails(ctx)))
            }) { Text("Allow background running") }
        }

        SectionCard("3. Autostart") {
            Text("In i Manager → App manager → Autostart (or Background startup), turn Physio Helper on. Without it, reminders don't come back after a restart.")
            OutlinedButton(onClick = {
                val opened = startFirst(ctx, listOf(
                    Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                    Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
                    Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
                    appDetails(ctx),
                ))
                if (!opened) toast(ctx, "Open i Manager manually and look for Autostart.")
            }) { Text("Open autostart settings") }
        }

        SectionCard("4. Lock it in recent apps") {
            Text("Open recent apps, long-press (or swipe down on) Physio Helper and tap the lock icon, so 'clear all' doesn't kill it.")
        }

        SectionCard("5. Exact alarms  ${if (exactOk) "✓" else "✗"}") {
            Text(if (exactOk) "Reminders fire on time." else "Reminders may arrive late. Allow 'Alarms & reminders' in the app's settings.")
            if (!exactOk) OutlinedButton(onClick = { startFirst(ctx, listOf(appDetails(ctx))) }) { Text("Open app settings") }
        }

        OutlinedButton(onClick = { ReminderScheduler.testIn(ctx, 60); toast(ctx, "Test in 1 minute. Lock the phone and wait.") }, modifier = Modifier.fillMaxWidth()) {
            Text("Test: reminder in 1 minute")
        }
        Button(onClick = { App.settings.setupGuideSeen = true; ReminderScheduler.scheduleAll(ctx); onBack() }, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }
}
