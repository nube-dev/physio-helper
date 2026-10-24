package com.nube.physiohelper.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.nube.physiohelper.App
import com.nube.physiohelper.R
import com.nube.physiohelper.data.CheckIn
import com.nube.physiohelper.export.Exporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context.applicationContext, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> {
                val type = intent.getIntExtra(EXTRA_TYPE, 0)
                fire(context, type)
                if (type != ReminderScheduler.T_TEST && type != ReminderScheduler.T_PHYSIO) {
                    ReminderScheduler.schedule(context, type)
                }
            }
            ACTION_CHECKIN_REPLY -> {
                val raw = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifications.KEY_PAIN)?.toString()?.trim()
                val pain = raw?.filter { it.isDigit() }?.toIntOrNull()
                if (pain == null || pain !in 0..10) {
                    replyDone(context, "Couldn't read \"${raw ?: ""}\". Open the app to log the check-in.")
                    return
                }
                val today = App.today()
                val existing = App.dao.checkIn(today)
                App.dao.upsertCheckIn(
                    (existing ?: CheckIn(date = today, morningPain = pain, loggedAt = System.currentTimeMillis()))
                        .copy(morningPain = pain, loggedAt = System.currentTimeMillis())
                )
                replyDone(context, "Saved: morning pain $pain/10. Tap to add stiffness and sleep.")
            }
        }
    }

    private fun replyDone(context: Context, text: String) {
        val n = NotificationCompat.Builder(context, Notifications.CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("Morning shoulder check")
            .setContentText(text)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .setContentIntent(Notifications.openApp(context, "checkin", 31))
            .build()
        Notifications.notify(context, Notifications.CHECKIN_ID, n)
    }

    private suspend fun fire(context: Context, type: Int) {
        val dao = App.dao
        val today = App.today()
        when (type) {
            ReminderScheduler.T_SESSION -> if (dao.completedSessionsOn(today) == 0) {
                val legDay = java.time.LocalDate.now().dayOfWeek.value in App.settings.strengthDays
                Notifications.simple(context, Notifications.REMINDER_ID, "Shoulder session time",
                    if (legDay) "Plus leg strength today. Short on time? The short shoulder session still counts."
                    else "Short on time? The short session still counts.", "home")
            }
            ReminderScheduler.T_CHECKIN -> if (dao.checkIn(today) == null) Notifications.checkIn(context)
            ReminderScheduler.T_NUDGE -> if (dao.completedSessionsOn(today) == 0) {
                Notifications.simple(context, Notifications.REMINDER_ID, "No session yet today",
                    "A short session now beats a skipped day. Isometrics + Y/T/W, about 15 minutes.", "home")
            }
            ReminderScheduler.T_SPADI -> Notifications.simple(context, Notifications.SPADI_ID, "Weekly shoulder score",
                "Two minutes: rate pain and everyday tasks. It shows progress daily pain scores hide.", "spadi")
            ReminderScheduler.T_BACKUP -> {
                val ok = Exporter.saveBackupToDownloads(context)
                if (!ok) Notifications.simple(context, Notifications.BACKUP_ID, "Backup failed",
                    "Weekly backup couldn't be saved. Open Data to export manually.", "data")
            }
            ReminderScheduler.T_PHYSIO -> {
                val open = dao.openQuestionCount()
                val at = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(App.settings.physioApptAt))
                Notifications.simple(context, Notifications.PHYSIO_ID, "Physio at $at",
                    if (open > 0) "$open question(s) on your list. Open it before you go." else "Your question list is empty.", "questions")
            }
            ReminderScheduler.T_TEST -> Notifications.simple(context, Notifications.REMINDER_ID, "Test reminder",
                "Reminders work. If this arrived late or not at all, recheck the battery setup.", "battery")
        }
    }

    companion object {
        const val ACTION_FIRE = "com.nube.physiohelper.FIRE"
        const val ACTION_CHECKIN_REPLY = "com.nube.physiohelper.CHECKIN_REPLY"
        const val EXTRA_TYPE = "type"
    }
}
