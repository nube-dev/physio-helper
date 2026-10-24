package com.nube.physiohelper.data

/**
 * Default program from the physio prescription (Sports Injury Centre, 21 Oct 2026)
 * as written up in the "Right Shoulder Rehab Plan" doc. Everything here is editable in-app.
 */
object Seed {
    private const val WALL_STEPS_COMMON = "Push at 30–50% effort without moving the arm. Breathe normally."
    private const val PRONE_SETUP = "Lie face-down on a mat, forehead on a folded towel. Pull the shoulder blades down and back first, then lift the arms 5–10 cm, thumbs up."
    private const val BAND_TEMPO = "About 2 s up and 2–3 s down. Control matters more than resistance."

    fun program(): List<Exercise> {
        var i = 0
        fun next() = i++
        return listOf(
            Exercise(
                id = "heat", name = "Heat (hot water bag)", category = Category.HEAT, orderIndex = next(),
                mode = Mode.TIMED, durationSec = 12 * 60, sets = 1, reps = 1, holdSec = 0, restSec = 0,
                steps = "Hot water bag or warm towel on the right shoulder for 10–15 minutes before exercising.",
                cues = "Warm, never burning. Keep a cloth between the bag and your skin.",
                avoid = "Skip if the shoulder is swollen or hot after an injury.",
            ),
            Exercise(
                id = "stretch_cross_body", name = "Cross-body stretch", category = Category.STRETCH, orderIndex = next(),
                sets = 1, reps = 3, holdSec = 30, restSec = 10,
                steps = "Bring the right arm across the chest at shoulder height. With the left hand, pull the right elbow gently toward the left shoulder.",
                cues = "Keep the right shoulder down, away from the ear.",
                avoid = "A gentle pull only. Never a sharp pain or pinch.",
                videoUrl = "https://library.theprehabguys.com/vimeo-video/horizontal-adduction-cross-body-stretch/",
            ),
            Exercise(
                id = "stretch_overhead", name = "Overhead capsular stretch", category = Category.STRETCH, orderIndex = next(),
                enabled = false, pendingPhysio = true, sets = 1, reps = 3, holdSec = 30, restSec = 10,
                steps = "Elbow bent behind the head; gently ease it further with the other hand.",
                cues = "Only as far as is pain-free.",
                avoid = "Not confirmed by the physio yet. Keep disabled until she says so.",
            ),
            Exercise(
                id = "wall_1_extension_straight", name = "Wall 1: straight arm push back", category = Category.WALL, orderIndex = next(),
                inShortSession = true,
                steps = "Back to the wall, stand tall, arm straight by your side. Push the hand backward into the wall. $WALL_STEPS_COMMON",
                cues = "Head straight, not leaning toward the wall. Don't shrug.",
                videoUrl = "https://ca.physitrack.com/home-exercise-video/isometric-shoulder-extension-against-a-wall",
            ),
            Exercise(
                id = "wall_2_extension_elbow", name = "Wall 2: elbow push back", category = Category.WALL, orderIndex = next(),
                inShortSession = true,
                steps = "Back to the wall, elbows bent 90° and tucked at your sides. Push the elbows back into the wall. $WALL_STEPS_COMMON",
                cues = "Elbows stay tucked.",
                videoUrl = "https://library.theprehabguys.com/vimeo-video/shoulder-extension-isometrics-wall-support/",
            ),
            Exercise(
                id = "wall_3_abduction_elbow", name = "Wall 3: elbow push outward", category = Category.WALL, orderIndex = next(),
                inShortSession = true,
                steps = "Elbow bent 90°, push the elbow outward into the wall or door frame. $WALL_STEPS_COMMON",
                cues = "Keep both shoulders level.",
                videoUrl = "https://au.physitrack.com/home-exercise-video/isometric-shoulder-abduction-against-wall",
            ),
            Exercise(
                id = "wall_4_abduction_straight", name = "Wall 4: straight arm push out", category = Category.WALL, orderIndex = next(),
                inShortSession = true,
                steps = "Side-on to the wall, arm straight by your side. Push it outward into the wall. $WALL_STEPS_COMMON",
                cues = "Don't hike the working shoulder up.",
                videoUrl = "https://au.physitrack.com/home-exercise-video/isometric-shoulder-abduction-against-wall",
            ),
            Exercise(
                id = "wall_5_flexion", name = "Wall 5: palm push forward", category = Category.WALL, orderIndex = next(),
                inShortSession = true,
                steps = "Face the wall, standing close. Right arm straight down by your side, palm flat on the wall in front of you. Push the palm forward into the wall — the mirror of wall 1. $WALL_STEPS_COMMON",
                cues = "Arm stays straight. Don't lean into the wall or shrug.",
                videoUrl = "",
            ),
            Exercise(
                id = "wall_6a_external_rotation", name = "Wall 6a: external rotation", category = Category.WALL, orderIndex = next(),
                enabled = false, pendingPhysio = true,
                steps = "In a doorway, elbow bent 90° at your side. Push the back of the wrist outward against the frame. $WALL_STEPS_COMMON",
                cues = "Elbow stays against your side.",
                avoid = "Position 6 not confirmed by the physio yet.",
                videoUrl = "https://us.physitrack.com/home-exercise-video/isometric-shoulder-external-rotation-against-wall",
            ),
            Exercise(
                id = "wall_6b_internal_rotation", name = "Wall 6b: internal rotation", category = Category.WALL, orderIndex = next(),
                enabled = false, pendingPhysio = true,
                steps = "In a doorway, elbow bent 90° at your side. Push the palm inward against the frame. $WALL_STEPS_COMMON",
                cues = "Elbow stays against your side.",
                avoid = "Position 6 not confirmed by the physio yet.",
                videoUrl = "https://na.physitrack.com/home-exercise-video/isometric-shoulder-medial-rotation",
            ),
            Exercise(
                id = "prone_y", name = "Prone Y", category = Category.PRONE, orderIndex = next(),
                inShortSession = true,
                steps = "$PRONE_SETUP Arms forward overhead, angled out like a Y.",
                cues = "Lift only as high as is pain-free.",
                videoUrl = "https://au.physitrack.com/home-exercise-video/scapular-control%252c--prone-y-arm-lift",
            ),
            Exercise(
                id = "prone_t", name = "Prone T", category = Category.PRONE, orderIndex = next(),
                inShortSession = true,
                steps = "$PRONE_SETUP Arms straight out to the sides at shoulder level.",
                cues = "Squeeze the shoulder blades together. Don't shrug.",
                videoUrl = "https://us.physitrack.com/home-exercise-video/prone-scapular-t%2527s",
            ),
            Exercise(
                id = "prone_w", name = "Prone W", category = Category.PRONE, orderIndex = next(),
                inShortSession = true,
                steps = "$PRONE_SETUP Elbows bent, hands near the head. Lift the elbows.",
                cues = "Actually lift the arms off the surface. Don't let them rest.",
                videoUrl = "https://us.physitrack.com/home-exercise-video/prone-shoulder-extension%252c-t%252c-y%252c-w--combo",
            ),
            band("band_front_raise", "Band front raise", next(),
                "Stand on the band, lift straight arms forward.", "Stop at shoulder height."),
            band("band_side_raise", "Band side raise", next(),
                "Stand on the band, lift arms out to the sides.", "Shoulder height at most. Thumbs slightly up, never down."),
            band("band_shrug", "Band shrug", next(),
                "Stand on the band, arms straight, lift the shoulders slowly.", "Hands stay below the chest. Never turn it into an upright row."),
            band("band_bicep_curl", "Band bicep curl", next(),
                "Stand on the band, elbows tucked at your sides, curl up.", "Low risk. Keep elbows still."),
            band("band_row_shoulder", "Band pull at shoulder height", next(),
                "Band tied to a window or door. Pull back with the elbow bent.", "Elbow at or just below shoulder level. Slow."),
            band("band_external_rotation", "Band external rotation", next(),
                "Elbow bent 90° at your side, towel between elbow and body. Rotate the forearm outward against the band.",
                "Elbow stays against your side.").copy(
                enabled = false, pendingPhysio = true,
                avoid = "Not prescribed yet. Ask the physio before enabling.",
            ),
        )
    }

    private fun band(id: String, name: String, order: Int, steps: String, cue: String) = Exercise(
        id = id, name = name, category = Category.BAND, orderIndex = order,
        sets = 1, reps = 10, upSec = 2, holdSec = 10, downSec = 3, restSec = 5,
        bandColor = "Yellow",
        steps = steps,
        cues = "$cue $BAND_TEMPO",
        avoid = "Raises at or below shoulder height. Move up to red only when 10 reps feel easy and pain-free.",
        videoUrl = "https://hss.edu/sutton/shoulder-strengthening-program.pdf",
    )
}
