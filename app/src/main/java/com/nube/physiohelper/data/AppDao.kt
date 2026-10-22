package com.nube.physiohelper.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // Exercises
    @Query("SELECT * FROM exercises ORDER BY orderIndex")
    fun exercisesFlow(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises ORDER BY orderIndex")
    suspend fun exercises(): List<Exercise>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun exercise(id: String): Exercise?

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun exerciseCount(): Int

    @Upsert
    suspend fun upsertExercise(e: Exercise)

    @Upsert
    suspend fun upsertExercises(list: List<Exercise>)

    @Delete
    suspend fun deleteExercise(e: Exercise)

    // Sessions
    @Insert
    suspend fun insertSession(s: Session): Long

    @Update
    suspend fun updateSession(s: Session)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun session(id: Long): Session?

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun sessionsFlow(): Flow<List<Session>>

    @Query("SELECT * FROM sessions ORDER BY startedAt")
    suspend fun allSessions(): List<Session>

    @Query("SELECT * FROM sessions WHERE status = 'IN_PROGRESS'")
    suspend fun inProgressSessions(): List<Session>

    @Query("SELECT COUNT(*) FROM sessions WHERE date = :date AND status = 'COMPLETED'")
    suspend fun completedSessionsOn(date: String): Int

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    // Logs
    @Insert
    suspend fun insertLog(l: ExerciseLog): Long

    @Query("SELECT * FROM exercise_logs ORDER BY loggedAt")
    fun logsFlow(): Flow<List<ExerciseLog>>

    @Query("SELECT * FROM exercise_logs ORDER BY loggedAt")
    suspend fun allLogs(): List<ExerciseLog>

    @Query("SELECT * FROM exercise_logs WHERE sessionId = :sessionId ORDER BY loggedAt")
    fun logsForSessionFlow(sessionId: Long): Flow<List<ExerciseLog>>

    @Query("DELETE FROM exercise_logs WHERE sessionId = :sessionId")
    suspend fun deleteLogsForSession(sessionId: Long)

    // Check-ins
    @Upsert
    suspend fun upsertCheckIn(c: CheckIn)

    @Query("SELECT * FROM checkins ORDER BY date DESC")
    fun checkInsFlow(): Flow<List<CheckIn>>

    @Query("SELECT * FROM checkins ORDER BY date")
    suspend fun allCheckIns(): List<CheckIn>

    @Query("SELECT * FROM checkins WHERE date = :date")
    suspend fun checkIn(date: String): CheckIn?

    // SPADI
    @Insert
    suspend fun insertSpadi(s: SpadiResult): Long

    @Delete
    suspend fun deleteSpadi(s: SpadiResult)

    @Query("SELECT * FROM spadi ORDER BY loggedAt DESC")
    fun spadiFlow(): Flow<List<SpadiResult>>

    @Query("SELECT * FROM spadi ORDER BY loggedAt")
    suspend fun allSpadi(): List<SpadiResult>

    // Questions
    @Insert
    suspend fun insertQuestion(q: PhysioQuestion): Long

    @Update
    suspend fun updateQuestion(q: PhysioQuestion)

    @Delete
    suspend fun deleteQuestion(q: PhysioQuestion)

    @Query("SELECT * FROM questions ORDER BY resolved, createdAt DESC")
    fun questionsFlow(): Flow<List<PhysioQuestion>>

    @Query("SELECT * FROM questions ORDER BY createdAt")
    suspend fun allQuestions(): List<PhysioQuestion>

    @Query("SELECT COUNT(*) FROM questions WHERE resolved = 0")
    suspend fun openQuestionCount(): Int

    // Bulk (import / restore)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(list: List<Session>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(list: List<ExerciseLog>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckIns(list: List<CheckIn>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpadis(list: List<SpadiResult>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(list: List<PhysioQuestion>)

    @Query("DELETE FROM exercises") suspend fun clearExercises()
    @Query("DELETE FROM sessions") suspend fun clearSessions()
    @Query("DELETE FROM exercise_logs") suspend fun clearLogs()
    @Query("DELETE FROM checkins") suspend fun clearCheckIns()
    @Query("DELETE FROM spadi") suspend fun clearSpadi()
    @Query("DELETE FROM questions") suspend fun clearQuestions()
}
