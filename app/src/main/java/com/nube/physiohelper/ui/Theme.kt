package com.nube.physiohelper.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nube.physiohelper.session.Phase

object Palette {
    val Teal = Color(0xFF1F6F5C)
    val TealSoft = Color(0xFF7FD3BB)
    val Blue = Color(0xFF2F6FB3)
    val Sand = Color(0xFFB7834A)
    val Slate = Color(0xFF5B6770)
    val Force = Color(0xFFE8732E)
    val Band = Color(0xFFE0A91E)
    val PainLow = Color(0xFF2E9D5B)
    val PainMid = Color(0xFFE0A100)
    val PainHigh = Color(0xFFD64541)
}

fun painColor(v: Int): Color = when {
    v <= 3 -> Palette.PainLow
    v <= 5 -> Palette.PainMid
    else -> Palette.PainHigh
}

fun painWord(v: Int): String = when {
    v == 0 -> "No pain"
    v <= 3 -> "Mild — fine"
    v <= 5 -> "Moderate — ease off"
    else -> "Strong — stop"
}

fun phaseColor(p: Phase): Color = when (p) {
    Phase.HOLD, Phase.TIMED -> Palette.Teal
    Phase.UP, Phase.DOWN -> Palette.Blue
    Phase.REST, Phase.SET_REST -> Palette.Sand
    else -> Palette.Slate
}

private val base = Typography()
private val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun PhysioTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Palette.TealSoft,
            onPrimary = Color(0xFF00382C),
            primaryContainer = Color(0xFF155244),
            onPrimaryContainer = Color(0xFFB7F0DC),
            secondary = Color(0xFFB4CCC3),
            tertiary = Color(0xFFF2B98A),
            tertiaryContainer = Color(0xFF5C3A1C),
            background = Color(0xFF0F1513),
            surface = Color(0xFF0F1513),
            surfaceVariant = Color(0xFF26302C),
            surfaceContainer = Color(0xFF1A2220),
            surfaceContainerHigh = Color(0xFF222B28),
            surfaceContainerHighest = Color(0xFF2B3532),
        )
    } else {
        lightColorScheme(
            primary = Palette.Teal,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFC6EEE0),
            onPrimaryContainer = Color(0xFF00382C),
            secondary = Color(0xFF4B635B),
            tertiary = Color(0xFF9A5B24),
            tertiaryContainer = Color(0xFFFFE2C7),
            background = Color(0xFFF6FAF8),
            surface = Color(0xFFF6FAF8),
            surfaceVariant = Color(0xFFDDE7E2),
            surfaceContainer = Color(0xFFEAF1EE),
            surfaceContainerHigh = Color(0xFFE3ECE8),
            surfaceContainerHighest = Color(0xFFDCE6E1),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
}
