package com.nube.physiohelper.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.nube.physiohelper.R
import com.nube.physiohelper.session.EngineState
import com.nube.physiohelper.session.Phase
import com.nube.physiohelper.ui.MainActivity

object Notifications {
    const val CH_REMINDERS = "reminders"
    const val CH_SESSION = "session"
    const val SESSION_ID = 1
    const val CHECKIN_ID = 2
    const val REMINDER_ID = 3
    const val SPADI_ID = 4
    const val PHYSIO_ID = 5
    const val BACKUP_ID = 6
    const val KEY_PAIN = "pain"
    const val EXTRA_ROUTE = "route"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Session, check-in and weekly reminders"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SESSION, "Active session", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the running exercise timer"
                setSound(null, null)
            }
        )
    }

    fun openApp(context: Context, route: String?, requestCode: Int): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (route != null) putExtra(EXTRA_ROUTE, route)
        }
        return PendingIntent.getActivity(context, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun sessionNotification(context: Context, st: EngineState): Notification {
        val ex = st.current
        val text = when {
            ex == null -> "Starting…"
            st.phase == Phase.READY -> "Next: ${ex.name}"
            st.phase == Phase.LOG -> "Log ${ex.name}"
            st.phase == Phase.SUMMARY -> "Session done. Save it."
            st.phase == Phase.TIMED -> "${ex.name} · ${fmt(st.secondsLeft)} left"
            st.phase == Phase.WORK -> "${ex.name} · set ${st.set} of ${ex.sets} · tap Done when finished"
            else -> "${ex.name} · ${st.phase.label} ${st.secondsLeft}s · rep ${st.rep}/${ex.reps}" +
                (if (ex.sets > 1) " · set ${st.set}/${ex.sets}" else "")
        }
        return NotificationCompat.Builder(context, CH_SESSION)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(if (st.paused) "Paused · exercise ${st.index + 1} of ${st.items.size}" else "Exercise ${st.index + 1} of ${st.items.size}")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp(context, "session", 10))
            .build()
    }

    fun simple(context: Context, id: Int, title: String, text: String, route: String?) {
        val n = NotificationCompat.Builder(context, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(context, route, 20 + id))
            .build()
        notify(context, id, n)
    }

    /** Morning check-in with an inline reply box so the pain score can be logged without opening the app. */
    fun checkIn(context: Context) {
        val remoteInput = RemoteInput.Builder(KEY_PAIN).setLabel("Pain right now, 0–10").build()
        val replyIntent = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_CHECKIN_REPLY)
        val replyPi = PendingIntent.getBroadcast(
            context, 101, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val action = NotificationCompat.Action.Builder(R.drawable.ic_stat_notify, "Log pain", replyPi)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()
        val n = NotificationCompat.Builder(context, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("Morning shoulder check")
            .setContentText("How's the shoulder after yesterday? Reply with 0–10, or tap to add stiffness and sleep.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("How's the shoulder after yesterday? Reply with 0–10, or tap to add stiffness and sleep."))
            .addAction(action)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(context, "checkin", 30))
            .build()
        notify(context, CHECKIN_ID, n)
    }

    fun notify(context: Context, id: Int, n: Notification) {
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    fun fmt(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)
}
