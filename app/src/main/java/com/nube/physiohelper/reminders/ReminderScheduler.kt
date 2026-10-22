package com.nube.physiohelper.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nube.physiohelper.App
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object ReminderScheduler {
    const val T_SESSION = 1
    const val T_CHECKIN = 2
    const val T_NUDGE = 3
    const val T_SPADI = 4
    const val T_BACKUP = 5
    const val T_PHYSIO = 6
    const val T_TEST = 7
    private val ALL = listOf(T_SESSION, T_CHECKIN, T_NUDGE, T_SPADI, T_BACKUP, T_PHYSIO)

    fun scheduleAll(context: Context) = ALL.forEach { schedule(context, it) }

    fun schedule(context: Context, type: Int) {
        val s = App.settings
        val at: Long? = when (type) {
            T_SESSION -> if (s.sessionReminderOn) nextDaily(s.sessionReminderMin) else null
            T_CHECKIN -> if (s.checkInReminderOn) nextDaily(s.checkInReminderMin) else null
            T_NUDGE -> if (s.nudgeOn) nextDaily(s.nudgeMin) else null
            T_SPADI -> if (s.spadiOn) nextWeekly(s.spadiDay, s.spadiMin) else null
            T_BACKUP -> if (s.backupOn) nextWeekly(s.backupDay, s.backupMin) else null
            T_PHYSIO -> {
                val remindAt = s.physioApptAt - 2 * 60 * 60 * 1000L
                if (s.physioApptAt > 0 && remindAt > System.currentTimeMillis()) remindAt else null
            }
            else -> null
        }
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, type)
        am.cancel(pi)
        if (at != null) setExact(context, at, pi)
    }

    fun testIn(context: Context, seconds: Int) {
        setExact(context, System.currentTimeMillis() + seconds * 1000L, pending(context, T_TEST))
    }

    private fun pending(context: Context, type: Int): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .putExtra(ReminderReceiver.EXTRA_TYPE, type)
        return PendingIntent.getBroadcast(context, 1000 + type, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun setExact(context: Context, at: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun toMillis(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun nextDaily(minuteOfDay: Int): Long {
        val now = LocalDateTime.now()
        var t = now.toLocalDate().atTime(minuteOfDay / 60, minuteOfDay % 60)
        if (!t.isAfter(now)) t = t.plusDays(1)
        return toMillis(t)
    }

    private fun nextWeekly(dayOfWeek: Int, minuteOfDay: Int): Long {
        val now = LocalDateTime.now()
        val dow = DayOfWeek.of(dayOfWeek.coerceIn(1, 7))
        var t = now.toLocalDate().with(TemporalAdjusters.nextOrSame(dow)).atTime(minuteOfDay / 60, minuteOfDay % 60)
        if (!t.isAfter(now)) t = t.plusWeeks(1)
        return toMillis(t)
    }
}
