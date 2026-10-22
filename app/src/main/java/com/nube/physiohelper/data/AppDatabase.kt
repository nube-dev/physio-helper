package com.nube.physiohelper.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Exercise::class,
        Session::class,
        ExerciseLog::class,
        CheckIn::class,
        SpadiResult::class,
        PhysioQuestion::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "physio.db").build()
    }
}
