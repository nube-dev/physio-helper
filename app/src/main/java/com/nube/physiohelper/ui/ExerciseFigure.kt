package com.nube.physiohelper.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.nube.physiohelper.data.Category
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Line-drawn exercise demonstration. Everything is drawn in a 100×100 box.
 *
 * @param progress movement position 0 (start) → 1 (end of lift). Null = loop on its own.
 * @param push force intensity 0..1 for isometrics. Null = follow the loop.
 */
@Composable
fun ExerciseFigure(
    exerciseId: String,
    category: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    push: Float? = null,
) {
    val inf = rememberInfiniteTransition(label = "figure")
    val loop by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 4200
                0f at 0
                0f at 500
                1f at 1800 using FastOutSlowInEasing
                1f at 3000
                0f at 4100 using FastOutSlowInEasing
            },
            RepeatMode.Restart,
        ),
        label = "loop",
    )
    val pulse by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val t = progress ?: loop
    val force = push ?: (0.15f + 0.85f * loop)

    val cs = MaterialTheme.colorScheme
    val colors = FigColors(
        body = cs.onSurface.copy(alpha = 0.78f),
        accent = cs.primary,
        force = Palette.Force,
        band = Palette.Band,
        guide = Palette.PainHigh.copy(alpha = 0.7f),
        muted = cs.onSurface.copy(alpha = 0.25f),
    )

    Canvas(modifier) {
        val s = min(size.width, size.height) / 100f
        val pen = Pen(this, s, (size.width - 100f * s) / 2f, (size.height - 100f * s) / 2f, colors)
        val id = exerciseId
        when {
            id == "heat" -> pen.heat(pulse)
            id == "stretch_cross_body" -> pen.stretchCross(t, force)
            id == "stretch_overhead" -> pen.stretchOverhead(t, force)
            id == "wall_1_extension_straight" -> pen.wallBackStraight(force, pulse)
            id == "wall_2_extension_elbow" -> pen.wallBackElbow(force, pulse)
            id == "wall_3_abduction_elbow" -> pen.wallSideElbow(force, pulse)
            id == "wall_4_abduction_straight" -> pen.wallSideStraight(force, pulse)
            id == "wall_5_flexion" -> pen.wallFront(force, pulse)
            id == "wall_6a_external_rotation" -> pen.doorRotation(outward = true, push = force, pulse = pulse)
            id == "wall_6b_internal_rotation" -> pen.doorRotation(outward = false, push = force, pulse = pulse)
            id == "prone_y" -> pen.prone(ProneShape.Y, t)
            id == "prone_t" -> pen.prone(ProneShape.T, t)
            id == "prone_w" -> pen.prone(ProneShape.W, t)
            id == "band_front_raise" -> pen.bandFrontRaise(t)
            id == "band_side_raise" -> pen.bandSideRaise(t)
            id == "band_shrug" -> pen.bandShrug(t)
            id == "band_bicep_curl" -> pen.bandCurl(t)
            id == "band_row_shoulder" -> pen.bandRow(t)
            id == "band_external_rotation" -> pen.bandExternalRotation(t)
            category == Category.HEAT -> pen.heat(pulse)
            category == Category.STRETCH -> pen.stretchCross(t, force)
            category == Category.WALL -> pen.wallBackStraight(force, pulse)
            category == Category.PRONE -> pen.prone(ProneShape.Y, t)
            category == Category.BAND -> pen.bandFrontRaise(t)
            else -> pen.idle(t)
        }
    }
}

private class FigColors(
    val body: Color,
    val accent: Color,
    val force: Color,
    val band: Color,
    val guide: Color,
    val muted: Color,
)

private enum class ProneShape { Y, T, W }

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private class Pen(val d: DrawScope, val s: Float, val ox: Float, val oy: Float, val c: FigColors) {

    fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)

    /** Point [len] units from [a] at angle [deg]: 0 = down, 90 = right, 180 = up, -90 = left. */
    fun seg(a: Offset, len: Float, deg: Float): Offset {
        val r = deg / 180f * PI.toFloat()
        return Offset(a.x + sin(r) * len * s, a.y + cos(r) * len * s)
    }

    fun line(a: Offset, b: Offset, color: Color = c.body, w: Float = 3f, alpha: Float = 1f) =
        d.drawLine(color, a, b, strokeWidth = w * s, cap = StrokeCap.Round, alpha = alpha.coerceIn(0f, 1f))

    fun dashed(a: Offset, b: Offset, color: Color = c.guide) =
        d.drawLine(
            color, a, b, strokeWidth = 1.1f * s, cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5f * s, 2.5f * s)),
        )

    fun dot(at: Offset, r: Float, color: Color = c.body, alpha: Float = 1f) =
        d.drawCircle(color, r * s, at, alpha = alpha.coerceIn(0f, 1f))

    fun arrow(from: Offset, to: Offset, alpha: Float = 1f, color: Color = c.force, w: Float = 2.4f) {
        line(from, to, color, w, alpha)
        val ang = atan2(to.y - from.y, to.x - from.x)
        val hl = 4f * s
        val a1 = ang + (PI * 0.8).toFloat()
        val a2 = ang - (PI * 0.8).toFloat()
        line(to, Offset(to.x + cos(a1) * hl, to.y + sin(a1) * hl), color, w, alpha)
        line(to, Offset(to.x + cos(a2) * hl, to.y + sin(a2) * hl), color, w, alpha)
    }

    /** Force arrow whose length and opacity follow the push intensity. */
    fun forceArrow(tip: Offset, dirX: Float, dirY: Float, push: Float, pulse: Float) {
        val len = (7f + 5f * push * (0.75f + 0.25f * pulse)) * s
        val from = Offset(tip.x - dirX * len, tip.y - dirY * len)
        arrow(from, tip, alpha = 0.3f + 0.7f * push)
    }

    fun wall(x: Float, top: Float = 6f, bottom: Float = 94f) {
        line(p(x, top), p(x, bottom), c.muted, 2.2f)
        var y = top + 4f
        while (y < bottom) {
            line(p(x, y), p(x - 3f, y + 3f), c.muted, 0.9f)
            y += 7f
        }
    }

    fun floor(y: Float = 93.5f) = line(p(14f, y), p(86f, y), c.muted, 1.4f)

    fun band(from: Offset, to: Offset) = line(from, to, c.band, 1.6f)

    // ---------- bodies ----------

    /** Side view facing right. Returns the shoulder. */
    fun sideBody(cx: Float): Offset {
        floor()
        dot(p(cx, 15f), 6f)
        line(p(cx, 22f), p(cx, 58f), w = 3.6f)
        line(p(cx, 58f), p(cx - 2f, 92f), w = 3.4f)
        line(p(cx, 58f), p(cx + 3f, 92f), w = 3.4f)
        return p(cx, 29f)
    }

    /** Front view. Returns (right shoulder = viewer's left, left shoulder). */
    fun frontBody(lift: Float = 0f): Pair<Offset, Offset> {
        floor()
        dot(p(50f, 14f), 6f)
        line(p(50f, 20f), p(50f, 26f - lift), w = 3f)
        line(p(41f, 27f - lift), p(59f, 27f - lift), w = 3.4f)
        line(p(50f, 26f - lift), p(50f, 57f), w = 3.6f)
        line(p(45f, 57f), p(55f, 57f), w = 3.4f)
        line(p(46f, 57f), p(44f, 92f), w = 3.4f)
        line(p(54f, 57f), p(56f, 92f), w = 3.4f)
        return p(41f, 27f - lift) to p(59f, 27f - lift)
    }

    /** Arm from [sh]. In front view the right arm is mirrored: positive angles point outward (left). */
    fun arm(sh: Offset, upper: Float, fore: Float, mirror: Boolean, color: Color = c.accent, w: Float = 3.4f): Pair<Offset, Offset> {
        val m = if (mirror) -1f else 1f
        val elbow = seg(sh, 15f, upper * m)
        val hand = seg(elbow, 14f, fore * m)
        line(sh, elbow, color, w)
        line(elbow, hand, color, w)
        dot(hand, 2.1f, color)
        return elbow to hand
    }

    // ---------- exercises ----------

    fun idle(t: Float) {
        val (r, l) = frontBody()
        arm(r, 6f + 6f * t, 6f + 6f * t, mirror = true)
        arm(l, 6f, 6f, mirror = false, color = c.body)
    }

    fun heat(pulse: Float) {
        val (r, l) = frontBody()
        arm(r, 6f, 6f, mirror = true, color = c.body)
        arm(l, 6f, 6f, mirror = false, color = c.body)
        val center = p(40f, 28f)
        d.drawOval(c.force, topLeft = Offset(center.x - 8f * s, center.y - 4.5f * s), size = Size(16f * s, 9f * s), alpha = 0.85f)
        for (i in 0 until 3) {
            val phase = (pulse + i / 3f) % 1f
            val r0 = (5f + 9f * phase) * s
            d.drawArc(
                c.force, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(center.x - r0, center.y - 2f * s - r0), size = Size(r0 * 2, r0 * 2),
                alpha = (1f - phase) * 0.7f, style = Stroke(1.4f * s, cap = StrokeCap.Round),
            )
        }
    }

    fun stretchCross(t: Float, push: Float) {
        val (r, l) = frontBody()
        val a = -85f - 12f * t
        val (elbow, _) = arm(r, a, a, mirror = true)
        // left hand pulls the right elbow
        val le = p(64f, 40f)
        line(l, le, c.body, 3.2f)
        line(le, Offset(elbow.x + 1f * s, elbow.y + 1.5f * s), c.body, 3.2f)
        dot(Offset(elbow.x + 1f * s, elbow.y + 1.5f * s), 2.1f, c.body)
        arrow(Offset(elbow.x + 2f * s, elbow.y + 8f * s), Offset(elbow.x + (10f + 4f * push) * s, elbow.y + 8f * s), alpha = 0.3f + 0.7f * push)
    }

    fun stretchOverhead(t: Float, push: Float) {
        val (r, l) = frontBody()
        val (elbow, _) = arm(r, 165f, -55f - 15f * t, mirror = true)
        line(l, p(62f, 12f), c.body, 3.2f)
        line(p(62f, 12f), Offset(elbow.x + 1f * s, elbow.y), c.body, 3.2f)
        arrow(Offset(elbow.x - 1f * s, elbow.y - 9f * s), Offset(elbow.x - 1f * s, elbow.y - (2f - 2f * push) * s), alpha = 0.3f + 0.7f * push)
    }

    fun wallBackStraight(push: Float, pulse: Float) {
        wall(38f)
        val sh = sideBody(50f)
        val (_, hand) = arm(sh, -10f, -10f, mirror = false)
        forceArrow(Offset(39.5f * s + ox, hand.y + 5f * s), -1f, 0f, push, pulse)
    }

    fun wallBackElbow(push: Float, pulse: Float) {
        wall(38f)
        val sh = sideBody(50f)
        val (elbow, _) = arm(sh, 0f, 90f, mirror = false)
        forceArrow(Offset(39.5f * s + ox, elbow.y + 4f * s), -1f, 0f, push, pulse)
    }

    fun wallSideElbow(push: Float, pulse: Float) {
        wall(27f)
        val (r, l) = frontBody()
        val elbow = seg(r, 15f, -8f)
        line(r, elbow, c.accent, 3.4f)
        val fist = Offset(elbow.x + 0.5f * s, elbow.y + 4f * s)
        line(elbow, fist, c.accent, 3.4f)
        dot(fist, 2.6f, c.accent)
        arm(l, 6f, 6f, mirror = false, color = c.body)
        forceArrow(Offset(28.5f * s + ox, elbow.y), -1f, 0f, push, pulse)
    }

    fun wallSideStraight(push: Float, pulse: Float) {
        wall(27f)
        val (r, l) = frontBody()
        val (_, hand) = arm(r, 7f, 7f, mirror = true)
        arm(l, 6f, 6f, mirror = false, color = c.body)
        forceArrow(Offset(28.5f * s + ox, hand.y), -1f, 0f, push, pulse)
    }

    fun wallFront(push: Float, pulse: Float) {
        wall(74f)
        val sh = sideBody(44f)
        val (_, hand) = arm(sh, 15f, 90f, mirror = false)
        dot(hand, 2.6f, c.accent)
        forceArrow(Offset(73f * s + ox, hand.y - 4f * s), 1f, 0f, push, pulse)
    }

    /** Top-down view: head, shoulders, the right elbow at the side and the forearm. */
    private fun topDown(): Offset {
        d.drawOval(c.body, topLeft = p(32f, 42f), size = Size(36f * s, 9f * s), alpha = 0.9f)
        dot(p(50f, 44f), 7f)
        dot(p(50f, 51.5f), 1.6f, c.muted)
        return p(35f, 52f)
    }

    fun doorRotation(outward: Boolean, push: Float, pulse: Float) {
        val elbow = topDown()
        val hand = seg(elbow, 18f, 0f)
        line(elbow, hand, c.accent, 3.6f)
        dot(elbow, 2.6f, c.accent)
        dot(hand, 2.4f, c.accent)
        if (outward) {
            line(p(27f, 58f), p(27f, 86f), c.muted, 3f)
            forceArrow(Offset(28.5f * s + ox, hand.y), -1f, 0f, push, pulse)
        } else {
            line(p(43f, 58f), p(43f, 86f), c.muted, 3f)
            forceArrow(Offset(41.5f * s + ox, hand.y), 1f, 0f, push, pulse)
        }
    }

    fun bandExternalRotation(t: Float) {
        val elbow = topDown()
        d.drawRoundRect(c.muted, topLeft = p(36.5f, 50f), size = Size(4f * s, 5f * s), cornerRadius = CornerRadius(1f * s))
        val hand = seg(elbow, 18f, -55f * t)
        band(hand, p(66f, 80f))
        dot(p(66f, 80f), 1.8f, c.muted)
        line(elbow, hand, c.accent, 3.6f)
        dot(elbow, 2.6f, c.accent)
        dot(hand, 2.4f, c.accent)
    }

    fun prone(shape: ProneShape, t: Float) {
        d.drawRoundRect(c.muted, topLeft = p(22f, 6f), size = Size(56f * s, 90f * s), cornerRadius = CornerRadius(6f * s), alpha = 0.35f)
        // body seen from above, head at top
        dot(p(50f, 18f), 6.2f)
        line(p(50f, 24f), p(50f, 60f), c.body, 4.4f)
        line(p(42f, 28f), p(58f, 28f), c.body, 3.6f)
        line(p(50f, 60f), p(45f, 92f), c.body, 3.4f)
        line(p(50f, 60f), p(55f, 92f), c.body, 3.4f)
        // shoulder blades squeeze together as the arms lift
        val sq = 3f * t
        line(p(45f + sq, 31f), p(46f + sq, 38f), c.accent, 1.6f, alpha = 0.3f + 0.7f * t)
        line(p(55f - sq, 31f), p(54f - sq, 38f), c.accent, 1.6f, alpha = 0.3f + 0.7f * t)

        val (up, fore) = when (shape) {
            ProneShape.Y -> 150f to 150f
            ProneShape.T -> 92f to 92f
            ProneShape.W -> 118f to 172f
        }
        val shadow = Offset(1.6f * t * s, 2.4f * t * s)
        for ((sh, mirror) in listOf(p(42f, 28f) to true, p(58f, 28f) to false)) {
            val m = if (mirror) -1f else 1f
            val elbow = seg(sh, 14f, up * m)
            val hand = seg(elbow, 13f, fore * m)
            // shadow stays on the mat, the arm lifts toward the viewer
            line(sh + shadow, elbow + shadow, c.muted, 3.4f, alpha = t)
            line(elbow + shadow, hand + shadow, c.muted, 3.4f, alpha = t)
            line(sh, elbow, c.accent, 3.2f + 1.2f * t)
            line(elbow, hand, c.accent, 3.2f + 1.2f * t)
            dot(hand, 2.1f + 0.6f * t, c.accent)
        }
    }

    fun bandFrontRaise(t: Float) {
        val sh = sideBody(46f)
        dashed(p(46f, 29f), p(84f, 29f))
        val a = lerp(4f, 90f, t)
        val (_, hand) = arm(sh, a, a, mirror = false)
        band(p(49f, 92f), hand)
    }

    fun bandSideRaise(t: Float) {
        val (r, l) = frontBody()
        dashed(p(12f, 27f), p(88f, 27f))
        val a = lerp(6f, 88f, t)
        val (_, rh) = arm(r, a, a, mirror = true)
        val (_, lh) = arm(l, a, a, mirror = false)
        band(p(44f, 92f), rh)
        band(p(56f, 92f), lh)
    }

    fun bandShrug(t: Float) {
        val lift = 4.5f * t
        val (r, l) = frontBody(lift)
        val (_, rh) = arm(r, 4f, 4f, mirror = true)
        val (_, lh) = arm(l, 4f, 4f, mirror = false)
        band(p(44f, 92f), rh)
        band(p(56f, 92f), lh)
        arrow(p(37f, 26f), p(37f, 17f), alpha = t)
        arrow(p(63f, 26f), p(63f, 17f), alpha = t)
    }

    fun bandCurl(t: Float) {
        val sh = sideBody(48f)
        val (_, hand) = arm(sh, 0f, lerp(8f, 155f, t), mirror = false)
        band(p(51f, 92f), hand)
    }

    fun bandRow(t: Float) {
        line(p(90f, 8f), p(90f, 94f), c.muted, 2.6f)
        val sh = sideBody(40f)
        dashed(p(22f, 29f), p(88f, 29f))
        val elbow = p(lerp(55f, 27f, t), lerp(29.5f, 31f, t))
        val hand = p(lerp(69f, 41f, t), 30f)
        line(sh, elbow, c.accent, 3.4f)
        line(elbow, hand, c.accent, 3.4f)
        dot(hand, 2.1f, c.accent)
        band(hand, p(90f, 30f))
    }
}
