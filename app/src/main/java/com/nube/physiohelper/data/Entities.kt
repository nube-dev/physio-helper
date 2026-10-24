package com.nube.physiohelper.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object Mode {
    const val REPS = "REPS"     // up / hold / down / rest cycle per rep, timed by the app
    const val TIMED = "TIMED"   // one block of time (heat, warm-up)
    const val SETS = "SETS"     // self-paced sets: do the reps, tap Done, timed rest between sets
}

object Program {
    const val REHAB = 0
    const val STRENGTH = 1
}

object Category {
    const val HEAT = "HEAT"
    const val STRETCH = "STRETCH"
    const val WALL = "WALL"
    const val PRONE = "PRONE"
    const val BAND = "BAND"
    const val LEGS = "LEGS"
    const val CORE = "CORE"
    const val OTHER = "OTHER"
    val all = listOf(HEAT, STRETCH, WALL, PRONE, BAND, LEGS, CORE, OTHER)
}

object Completion {
    const val FULL = "FULL"
    const val PARTIAL = "PARTIAL"
    const val SKIPPED = "SKIPPED"
}

object Effort {
    const val EASY = "EASY"
    const val MODERATE = "MODERATE"
    const val HARD = "HARD"
}

object SessionStatus {
    const val IN_PROGRESS = "IN_PROGRESS"
    const val COMPLETED = "COMPLETED"
    const val ABANDONED = "ABANDONED"
}

object SessionType {
    const val FULL = "FULL"
    const val SHORT = "SHORT"
    const val STRENGTH = "STRENGTH"
}

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val orderIndex: Int,
    val enabled: Boolean = true,
    val pendingPhysio: Boolean = false,
    val mode: String = Mode.REPS,
    val sets: Int = 1,
    val reps: Int = 10,
    val upSec: Int = 0,
    val holdSec: Int = 10,
    val downSec: Int = 0,
    val restSec: Int = 5,
    val setRestSec: Int = 30,
    val durationSec: Int = 0,
    val bandColor: String = "",
    val level: Int = 1,
    val inShortSession: Boolean = false,
    val steps: String = "",
    val cues: String = "",
    val avoid: String = "",
    val videoUrl: String = "",
    val mediaUri: String = "",
    /** Run every set on the right arm, then again on the left. */
    @ColumnInfo(defaultValue = "0") val bothSides: Boolean = false,
    /** Top of the rep range for self-paced sets (reps is the bottom); 0 = a single target. */
    @ColumnInfo(defaultValue = "0") val repsMax: Int = 0,
    /** [Program.REHAB] or [Program.STRENGTH]. */
    @ColumnInfo(defaultValue = "0") val program: Int = Program.REHAB,
) {
    val sides: Int get() = if (bothSides && mode != Mode.TIMED) 2 else 1
    val repsTop: Int get() = if (repsMax > reps) repsMax else reps
    /** "arm" for the shoulder exercises, "leg" for leg work. */
    val sideWord: String get() = if (category == Category.LEGS) "leg" else "arm"
}

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val date: String,
    val type: String,
    val status: String,
    val peakPain: Int? = null,
    val note: String = "",
)

@Entity(
    tableName = "exercise_logs",
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class ExerciseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val exerciseName: String,
    val date: String,
    val loggedAt: Long,
    val completion: String,
    val setsDone: Int,
    val repsDone: Int,
    val setsPlanned: Int,
    val repsPlanned: Int,
    val pain: Int,
    val effort: String,
    val tags: String,
    val note: String,
    val bandColor: String,
    val level: Int,
    /** Reps done in each set, comma-separated, for self-paced sets. */
    val setReps: String? = null,
) {
    val tagList: List<String> get() = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(tableName = "checkins")
data class CheckIn(
    @PrimaryKey val date: String,
    val morningPain: Int,
    val stiffnessMin: Int? = null,
    val nightPain: Boolean? = null,
    val redFlags: String = "",
    val note: String = "",
    val loggedAt: Long,
)

@Entity(tableName = "spadi")
data class SpadiResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val answers: String,        // 13 comma-separated values, -1 = not applicable
    val painScore: Double,
    val disabilityScore: Double,
    val totalScore: Double,
    val loggedAt: Long,
)

@Entity(tableName = "questions")
data class PhysioQuestion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val exerciseId: String = "",
    val createdAt: Long,
    val resolved: Boolean = false,
    val answer: String = "",
)

object Tags {
    val all = listOf("pinch", "ache", "weak", "couldn't hold", "shrugged", "limited range", "clicking", "shaky")
}

object RedFlags {
    val all = listOf(
        "Numbness or tingling in the arm or hand",
        "Swelling in the arm",
        "Arm cold, pale or changing colour",
        "Shaking episodes like before",
        "Pain lasting hours after exercise",
    )
}
