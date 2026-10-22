package com.nube.physiohelper.session

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nube.physiohelper.reminders.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps the app alive in the foreground while a session runs, and shows live progress. */
class SessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notifications.sessionNotification(this, SessionEngine.state.value)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifications.SESSION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifications.SESSION_ID, n)
        }
        if (watcher == null) {
            watcher = scope.launch {
                SessionEngine.state.collect { st ->
                    if (!st.active) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        runCatching {
                            NotificationManagerCompat.from(this@SessionService)
                                .notify(Notifications.SESSION_ID, Notifications.sessionNotification(this@SessionService, st))
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, SessionService::class.java))
        }
    }
}
