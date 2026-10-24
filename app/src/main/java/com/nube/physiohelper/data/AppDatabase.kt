package com.nube.physiohelper.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Exercise::class,
        Session::class,
        ExerciseLog::class,
        CheckIn::class,
        SpadiResult::class,
        PhysioQuestion::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN bothSides INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "physio.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
