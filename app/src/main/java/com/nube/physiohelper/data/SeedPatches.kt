package com.nube.physiohelper.data

/**
 * Corrections to the default program for installs that were seeded before the fix.
 * Only text fields are replaced; the user's dose, order, enabled state and clips are kept.
 */
object SeedPatches {
    private const val LATEST = 2

    suspend fun apply(dao: AppDao, settings: Settings) {
        val from = settings.seedPatch
        if (from >= LATEST) return
        val fresh = Seed.program().associateBy { it.id }

        suspend fun refreshText(id: String) {
            val current = dao.exercise(id) ?: return
            val f = fresh[id] ?: return
            dao.upsertExercise(current.copy(name = f.name, steps = f.steps, cues = f.cues, avoid = f.avoid, videoUrl = f.videoUrl))
        }

        // 1: wall 5 is a straight-arm palm push forward (mirror of wall 1), not a bent-elbow fist push.
        if (from < 1) refreshText("wall_5_flexion")

        // 2: the leg strength program.
        if (from < 2) {
            for (e in Seed.strength()) if (dao.exercise(e.id) == null) dao.upsertExercise(e)
        }

        settings.seedPatch = LATEST
    }
}
