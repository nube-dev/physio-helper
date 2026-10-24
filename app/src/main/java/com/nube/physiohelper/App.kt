package com.nube.physiohelper

import android.app.Application
import com.nube.physiohelper.data.AppDao
import com.nube.physiohelper.data.AppDatabase
import com.nube.physiohelper.data.Seed
import com.nube.physiohelper.data.SeedPatches
import com.nube.physiohelper.data.SessionStatus
import com.nube.physiohelper.data.Settings
import com.nube.physiohelper.reminders.Notifications
import com.nube.physiohelper.reminders.ReminderScheduler
import com.nube.physiohelper.session.SessionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class App : Application() {
    lateinit var db: AppDatabase
        private set
    lateinit var settings: Settings
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        db = AppDatabase.build(this)
        settings = Settings(this)
        Notifications.createChannels(this)
        appScope.launch {
            if (dao.exerciseCount() == 0) dao.upsertExercises(Seed.program())
            SeedPatches.apply(dao, settings)
            // A session left IN_PROGRESS by a killed process can't be resumed; close it out.
            if (!SessionEngine.state.value.active) {
                for (s in dao.inProgressSessions()) {
                    dao.updateSession(s.copy(status = SessionStatus.ABANDONED, endedAt = s.endedAt ?: s.startedAt))
                }
            }
            ReminderScheduler.scheduleAll(this@App)
        }
    }

    companion object {
        lateinit var instance: App
            private set
        val dao: AppDao get() = instance.db.dao()
        val settings: Settings get() = instance.settings
        fun today(): String = LocalDate.now().toString()
    }
}
