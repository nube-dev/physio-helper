package com.nube.physiohelper.data

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/** Plain SharedPreferences-backed settings. Times are minutes since midnight. */
class Settings(context: Context) {
    private val p: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun bool(key: String, def: Boolean) = p.getBoolean(key, def)
    private fun int(key: String, def: Int) = p.getInt(key, def)
    private fun setBool(key: String, v: Boolean) = p.edit().putBoolean(key, v).apply()
    private fun setInt(key: String, v: Int) = p.edit().putInt(key, v).apply()

    var sessionReminderOn: Boolean
        get() = bool("session_on", true); set(v) = setBool("session_on", v)
    var sessionReminderMin: Int
        get() = int("session_min", 20 * 60); set(v) = setInt("session_min", v)

    var checkInReminderOn: Boolean
        get() = bool("checkin_on", true); set(v) = setBool("checkin_on", v)
    var checkInReminderMin: Int
        get() = int("checkin_min", 8 * 60); set(v) = setInt("checkin_min", v)

    var nudgeOn: Boolean
        get() = bool("nudge_on", true); set(v) = setBool("nudge_on", v)
    var nudgeMin: Int
        get() = int("nudge_min", 21 * 60 + 30); set(v) = setInt("nudge_min", v)

    var spadiOn: Boolean
        get() = bool("spadi_on", true); set(v) = setBool("spadi_on", v)
    /** 1 = Monday ... 7 = Sunday */
    var spadiDay: Int
        get() = int("spadi_day", 7); set(v) = setInt("spadi_day", v)
    var spadiMin: Int
        get() = int("spadi_min", 10 * 60); set(v) = setInt("spadi_min", v)

    var backupOn: Boolean
        get() = bool("backup_on", true); set(v) = setBool("backup_on", v)
    var backupDay: Int
        get() = int("backup_day", 7); set(v) = setInt("backup_day", v)
    var backupMin: Int
        get() = int("backup_min", 11 * 60); set(v) = setInt("backup_min", v)

    /** Epoch millis of the next physio appointment, 0 if none. */
    var physioApptAt: Long
        get() = p.getLong("physio_at", 0L); set(v) = p.edit().putLong("physio_at", v).apply()

    var weeklyTarget: Int
        get() = int("weekly_target", 5); set(v) = setInt("weekly_target", v)

    var keepScreenOn: Boolean
        get() = bool("keep_screen_on", true); set(v) = setBool("keep_screen_on", v)
    var voiceOn: Boolean
        get() = bool("voice_on", true); set(v) = setBool("voice_on", v)
    var beepsOn: Boolean
        get() = bool("beeps_on", true); set(v) = setBool("beeps_on", v)
    var vibrateOn: Boolean
        get() = bool("vibrate_on", true); set(v) = setBool("vibrate_on", v)
    var getReadySec: Int
        get() = int("get_ready_sec", 5); set(v) = setInt("get_ready_sec", v)

    var rehabStartDate: String
        get() = p.getString("rehab_start", null) ?: "2026-10-21"
        set(v) = p.edit().putString("rehab_start", v).apply()

    var lastBackupAt: Long
        get() = p.getLong("last_backup_at", 0L); set(v) = p.edit().putLong("last_backup_at", v).apply()

    var setupGuideSeen: Boolean
        get() = bool("setup_seen", false); set(v) = setBool("setup_seen", v)

    var notificationPermissionAsked: Boolean
        get() = bool("notif_asked", false); set(v) = setBool("notif_asked", v)

    fun rehabStart(): LocalDate = runCatching { LocalDate.parse(rehabStartDate) }.getOrElse { LocalDate.now() }

    fun toMap(): Map<String, Any?> = p.all.toMap()

    fun restore(map: Map<String, Any?>) {
        val e = p.edit()
        for ((k, v) in map) {
            when (v) {
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is String -> e.putString(k, v)
                is Float -> e.putFloat(k, v)
            }
        }
        e.apply()
    }
}
