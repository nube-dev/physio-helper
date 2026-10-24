package com.nube.physiohelper.session

import android.content.Context
import com.nube.physiohelper.App
import com.nube.physiohelper.data.Completion
import com.nube.physiohelper.data.Exercise
import com.nube.physiohelper.data.ExerciseLog
import com.nube.physiohelper.data.Mode
import com.nube.physiohelper.data.PhysioQuestion
import com.nube.physiohelper.data.Session
import com.nube.physiohelper.data.SessionStatus
import com.nube.physiohelper.data.SessionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Phase(val label: String) {
    READY("Ready"),
    GET_READY("Get ready"),
    UP("Lift"),
    HOLD("Hold"),
    DOWN("Lower"),
    REST("Rest"),
    SET_REST("Set rest"),
    SWITCH("Switch sides"),
    WORK("Go"),
    TIMED("Timer"),
    LOG("Log"),
    SUMMARY("Summary");

    val isTiming: Boolean get() = this in setOf(GET_READY, UP, HOLD, DOWN, REST, SET_REST, SWITCH, TIMED)
}

data class LogDraft(
    val completion: String = Completion.FULL,
    val setsDone: Int = 0,
    val repsDone: Int = 0,
    val pain: Int = 0,
    val effort: String = "",
    val tags: Set<String> = emptySet(),
    val note: String = "",
    val askPhysio: Boolean = false,
)

data class EngineState(
    val active: Boolean = false,
    val sessionId: Long = 0,
    val type: String = SessionType.FULL,
    val items: List<Exercise> = emptyList(),
    val index: Int = 0,
    val phase: Phase = Phase.READY,
    val secondsLeft: Int = 0,
    val phaseTotal: Int = 0,
    val set: Int = 1,
    val rep: Int = 1,
    /** 0 = right arm, 1 = left arm (exercises done on both sides). */
    val side: Int = 0,
    /** Self-paced sets: reps entered for each finished set, and the value for the current one. */
    val setReps: List<Int> = emptyList(),
    val pendingReps: Int = 0,
    val paused: Boolean = false,
    val repsCompleted: Int = 0,
    val setsCompleted: Int = 0,
    val draft: LogDraft = LogDraft(),
    val loggedPains: List<Int> = emptyList(),
    val loggedCount: Int = 0,
) {
    val current: Exercise? get() = items.getOrNull(index)
}

/**
 * Application-wide session timer. Lives outside the UI so it keeps running while the
 * screen is off; [SessionService] keeps the process in the foreground meanwhile.
 */
object SessionEngine {
    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private var speaker: Speaker? = null

    private val s get() = _state.value
    private fun update(new: EngineState) { _state.value = new }

    fun start(context: Context, type: String, onEmpty: () -> Unit = {}) {
        if (s.active) return
        val appCtx = context.applicationContext
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                App.dao.exercises().filter {
                    it.enabled && when (type) {
                        SessionType.STRENGTH -> it.program == com.nube.physiohelper.data.Program.STRENGTH
                        SessionType.SHORT -> it.program == com.nube.physiohelper.data.Program.REHAB && it.inShortSession
                        else -> it.program == com.nube.physiohelper.data.Program.REHAB
                    }
                }
            }
            if (items.isEmpty()) { onEmpty(); return@launch }
            val id = withContext(Dispatchers.IO) {
                App.dao.insertSession(
                    Session(startedAt = System.currentTimeMillis(), date = App.today(), type = type, status = SessionStatus.IN_PROGRESS)
                )
            }
            speaker = Speaker(appCtx, App.settings)
            update(EngineState(active = true, sessionId = id, type = type, items = items))
            SessionService.start(appCtx)
        }
    }

    /** From READY: count down, then run the exercise. */
    fun beginExercise() {
        val ex = s.current ?: return
        val gr = App.settings.getReadySec.coerceAtLeast(0)
        update(s.copy(repsCompleted = 0, setsCompleted = 0, set = 1, rep = 1, side = 0, setReps = emptyList(), paused = false, draft = LogDraft()))
        if (gr > 0) {
            enter(Phase.GET_READY, gr)
            speaker?.say("${ex.name}. Get ready.", flush = true)
        } else {
            firstPhase(ex)
        }
        startTicker()
    }

    fun togglePause() {
        if (!s.phase.isTiming) return
        val p = !s.paused
        update(s.copy(paused = p))
        speaker?.say(if (p) "Paused" else "Go", flush = true)
    }

    fun pause() { if (s.phase.isTiming && !s.paused) update(s.copy(paused = true)) }
    fun resume() { if (s.phase.isTiming && s.paused) update(s.copy(paused = false)) }

    fun restartExercise() {
        stopTicker()
        update(s.copy(phase = Phase.READY, paused = false, repsCompleted = 0, setsCompleted = 0, set = 1, rep = 1, side = 0, setReps = emptyList()))
    }

    fun skip() = finishExercise(Completion.SKIPPED, pain = 0)

    fun stopForPain(pain: Int) = finishExercise(Completion.PARTIAL, pain = pain)

    /** Finish the current exercise early as partial (e.g. couldn't continue). */
    fun endExerciseEarly() = finishExercise(Completion.PARTIAL, pain = null)

    fun updateDraft(d: LogDraft) = update(s.copy(draft = d))

    fun saveLogAndContinue() {
        val st = s
        val ex = st.current ?: return
        val d = st.draft
        val log = ExerciseLog(
            sessionId = st.sessionId,
            exerciseId = ex.id,
            exerciseName = ex.name,
            date = App.today(),
            loggedAt = System.currentTimeMillis(),
            completion = d.completion,
            setsDone = d.setsDone,
            repsDone = d.repsDone,
            setsPlanned = ex.sets * ex.sides,
            repsPlanned = if (ex.mode == Mode.TIMED) 1 else ex.repsTop * ex.sets * ex.sides,
            pain = d.pain,
            effort = d.effort,
            tags = d.tags.joinToString(","),
            note = d.note.trim(),
            bandColor = ex.bandColor,
            level = ex.level,
            setReps = if (ex.mode == Mode.SETS && st.setReps.isNotEmpty()) st.setReps.joinToString(",") else null,
        )
        scope.launch(Dispatchers.IO) {
            App.dao.insertLog(log)
            if (d.askPhysio) {
                val text = if (log.note.isNotBlank()) "${ex.name}: ${log.note}" else "${ex.name}: check form / pain (pain ${d.pain}/10)"
                App.dao.insertQuestion(PhysioQuestion(text = text, exerciseId = ex.id, createdAt = System.currentTimeMillis()))
            }
        }
        val pains = if (d.completion == Completion.SKIPPED) st.loggedPains else st.loggedPains + d.pain
        val nextIndex = st.index + 1
        if (nextIndex < st.items.size) {
            update(st.copy(index = nextIndex, phase = Phase.READY, loggedPains = pains, loggedCount = st.loggedCount + 1, draft = LogDraft()))
        } else {
            update(st.copy(phase = Phase.SUMMARY, loggedPains = pains, loggedCount = st.loggedCount + 1))
            speaker?.say("Session complete. Well done.", flush = true)
        }
    }

    /** Jump to the summary, leaving the remaining exercises unlogged. */
    fun goToSummary() {
        stopTicker()
        update(s.copy(phase = Phase.SUMMARY, paused = false))
    }

    fun finishSession(note: String, onDone: () -> Unit = {}) {
        val st = s
        val peak = st.loggedPains.maxOrNull()
        scope.launch {
            withContext(Dispatchers.IO) {
                App.dao.session(st.sessionId)?.let {
                    App.dao.updateSession(
                        it.copy(
                            status = if (st.loggedCount > 0) SessionStatus.COMPLETED else SessionStatus.ABANDONED,
                            endedAt = System.currentTimeMillis(),
                            peakPain = peak,
                            note = note.trim(),
                        )
                    )
                }
            }
            reset()
            onDone()
        }
    }

    fun abandon(onDone: () -> Unit = {}) {
        val st = s
        scope.launch {
            withContext(Dispatchers.IO) {
                App.dao.session(st.sessionId)?.let {
                    App.dao.updateSession(it.copy(status = SessionStatus.ABANDONED, endedAt = System.currentTimeMillis()))
                }
            }
            reset()
            onDone()
        }
    }

    private fun reset() {
        stopTicker()
        speaker?.shutdown()
        speaker = null
        update(EngineState())
    }

    // --- timing internals ---

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(1000)
                tick()
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun tick() {
        val st = s
        if (!st.active || st.paused || !st.phase.isTiming) return
        val left = st.secondsLeft - 1
        if (left > 0) {
            update(st.copy(secondsLeft = left))
            countdownCue(st.phase, left)
        } else {
            advance()
        }
    }

    private fun countdownCue(phase: Phase, left: Int) {
        when (phase) {
            Phase.HOLD -> if (left <= App.settings.countdownFrom) {
                speaker?.beep()
                speaker?.say(numberWord(left))
            }
            Phase.GET_READY, Phase.REST, Phase.SET_REST -> if (left <= 3) speaker?.beep()
            Phase.TIMED -> when {
                left == 60 -> speaker?.say("One minute left")
                left % 300 == 0 -> speaker?.say("${left / 60} minutes left")
                left <= 3 -> speaker?.beep()
                else -> {}
            }
            else -> {}
        }
    }

    private fun numberWord(n: Int): String = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
    ).getOrElse(n) { n.toString() }

    private fun enter(phase: Phase, seconds: Int) {
        update(s.copy(phase = phase, secondsLeft = seconds, phaseTotal = seconds))
        speaker?.beep(long = phase == Phase.HOLD || phase == Phase.TIMED)
        speaker?.buzz()
    }

    private fun firstPhase(ex: Exercise) {
        if (ex.mode == Mode.TIMED) {
            enter(Phase.TIMED, ex.durationSec.coerceAtLeast(1))
            val min = ex.durationSec / 60
            speaker?.say(if (min > 0) "${ex.name}. $min minutes." else ex.name)
        } else if (ex.mode == Mode.SETS) {
            enterWork(1, 0)
        } else {
            startRep(1, 1)
        }
    }

    // --- self-paced sets ---

    private fun sideLabel(ex: Exercise, side: Int) = (if (side == 0) "Right " else "Left ") + ex.sideWord

    private fun enterWork(set: Int, side: Int) {
        val ex = s.current ?: return
        val target = s.setReps.lastOrNull() ?: ex.repsTop
        update(s.copy(phase = Phase.WORK, set = set, side = side, secondsLeft = 0, phaseTotal = 0, pendingReps = target, paused = false))
        speaker?.beep(long = true)
        speaker?.buzz()
        val range = if (ex.repsMax > ex.reps) "${ex.reps} to ${ex.repsMax} reps." else "${ex.reps} reps."
        val sideText = if (ex.sides == 2) " ${sideLabel(ex, side)}." else ""
        speaker?.say("Set $set of ${ex.sets}.$sideText $range")
    }

    fun setPendingReps(n: Int) {
        if (s.phase == Phase.WORK) update(s.copy(pendingReps = n.coerceIn(0, 200)))
    }

    /** The user finished the current set (or side). */
    fun completeSet() {
        val st = s
        val ex = st.current ?: return
        if (st.phase != Phase.WORK) return
        val done = st.copy(setReps = st.setReps + st.pendingReps, repsCompleted = st.repsCompleted + st.pendingReps)
        update(done)
        if (ex.sides == 2 && st.side == 0) {
            enterWork(st.set, 1)
            return
        }
        update(s.copy(setsCompleted = s.setsCompleted + 1))
        if (st.set < ex.sets) {
            if (ex.setRestSec > 0) {
                enter(Phase.SET_REST, ex.setRestSec)
                speaker?.say("Rest ${ex.setRestSec} seconds.")
            } else {
                enterWork(st.set + 1, 0)
            }
        } else {
            finishExercise(Completion.FULL, pain = null)
        }
    }

    private fun startRep(rep: Int, set: Int) {
        val ex = s.current ?: return
        update(s.copy(rep = rep, set = set))
        val arm = if (ex.sides == 2 && rep == 1 && set == 1) "${sideLabel(ex, s.side)}. " else ""
        val count = if (ex.reps == 1) "Set $set of ${ex.sets}." else (if (rep == 1 && set > 1) "Set $set. " else "") + "Rep $rep of ${ex.reps}."
        val prefix = arm + count
        when {
            ex.upSec > 0 -> { enter(Phase.UP, ex.upSec); speaker?.say("$prefix Lift.") }
            ex.holdSec > 0 -> { enter(Phase.HOLD, ex.holdSec); speaker?.say("$prefix Hold.") }
            ex.downSec > 0 -> { enter(Phase.DOWN, ex.downSec); speaker?.say("$prefix Lower.") }
            else -> { enter(Phase.HOLD, 1); speaker?.say(prefix) }
        }
    }

    private fun advance() {
        val st = s
        val ex = st.current ?: return
        if (ex.mode == Mode.TIMED) {
            when (st.phase) {
                Phase.GET_READY -> firstPhase(ex)
                else -> finishExercise(Completion.FULL, pain = null, setsDone = 1, repsDone = 1)
            }
            return
        }
        when (st.phase) {
            Phase.GET_READY -> firstPhase(ex)
            Phase.UP -> when {
                ex.holdSec > 0 -> { enter(Phase.HOLD, ex.holdSec); speaker?.say("Hold.") }
                ex.downSec > 0 -> { enter(Phase.DOWN, ex.downSec); speaker?.say("Lower slowly.") }
                else -> repDone()
            }
            Phase.HOLD -> if (ex.downSec > 0) { enter(Phase.DOWN, ex.downSec); speaker?.say("Lower slowly.") } else repDone()
            Phase.DOWN -> repDone()
            Phase.REST -> startRep(st.rep + 1, st.set)
            Phase.SET_REST -> if (ex.mode == Mode.SETS) enterWork(st.set + 1, 0) else startRep(1, st.set + 1)
            Phase.SWITCH -> {
                update(s.copy(side = 1))
                startRep(1, 1)
            }
            else -> {}
        }
    }

    private fun repDone() {
        val ex = s.current ?: return
        val st = s.copy(repsCompleted = s.repsCompleted + 1)
        update(st)
        if (st.rep < ex.reps) {
            if (ex.restSec > 0) { enter(Phase.REST, ex.restSec); speaker?.say("Rest.") } else startRep(st.rep + 1, st.set)
        } else {
            update(s.copy(setsCompleted = s.setsCompleted + 1))
            if (st.set < ex.sets) {
                if (ex.setRestSec > 0) {
                    enter(Phase.SET_REST, ex.setRestSec)
                    speaker?.say("Set ${st.set} done. Rest ${ex.setRestSec} seconds.")
                } else startRep(1, st.set + 1)
            } else if (ex.sides == 2 && st.side == 0) {
                enter(Phase.SWITCH, App.settings.getReadySec.coerceIn(5, 15))
                speaker?.say("Right ${ex.sideWord} done. Switch to your left ${ex.sideWord}.")
            } else {
                finishExercise(Completion.FULL, pain = null)
            }
        }
    }

    private fun finishExercise(completion: String, pain: Int?, setsDone: Int? = null, repsDone: Int? = null) {
        stopTicker()
        val st = s
        val ex = st.current ?: return
        val draft = LogDraft(
            completion = completion,
            setsDone = setsDone ?: if (ex.mode == Mode.TIMED) (if (completion == Completion.SKIPPED) 0 else 1) else st.setsCompleted,
            repsDone = repsDone ?: if (ex.mode == Mode.TIMED) (if (completion == Completion.SKIPPED) 0 else 1) else st.repsCompleted,
            pain = pain ?: 0,
        )
        update(st.copy(phase = Phase.LOG, paused = false, draft = draft))
        if (completion != Completion.SKIPPED) speaker?.say("${ex.name} done. Log it.", flush = true)
    }
}
