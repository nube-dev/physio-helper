package com.nube.physiohelper.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarms are wiped on reboot, app update and clock changes; put them back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.scheduleAll(context.applicationContext)
    }
}
