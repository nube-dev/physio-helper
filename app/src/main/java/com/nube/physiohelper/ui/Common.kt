@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.nube.physiohelper.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import kotlin.math.min

/** Collect a DAO flow without re-creating it on every recomposition. */
@Composable
fun <T> rememberFlow(initial: T, vararg keys: Any?, factory: () -> Flow<T>): State<T> {
    val flow = remember(*keys) { factory() }
    return flow.collectAsState(initial)
}

/**
 * Standard screen: title bar, scrolling column. Screens shown inside the tab bar pass
 * [inTabs] = true so the bottom inset isn't applied twice.
 */
@Composable
fun Screen(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    scroll: Boolean = true,
    inTabs: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        contentWindowInsets = if (inTabs) WindowInsets(0.dp) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        val base = Modifier.padding(pad).fillMaxSize()
        Column(
            modifier = (if (scroll) base.verticalScroll(rememberScrollState()) else base)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                content()
                Spacer(Modifier.height(24.dp))
            },
        )
    }
}

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    container: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container ?: MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Eleven tappable pain numbers. Faster and more precise than a slider with a thumb. */
@Composable
fun PainPicker(value: Int?, onPick: (Int) -> Unit, showWord: Boolean = true, circle: Dp = 30.dp) {
    val haptic = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 0..10) {
                val selected = value == i
                val scale by animateFloatAsState(if (selected) 1.18f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "s")
                val bg by animateColorAsState(
                    if (selected) painColor(i) else painColor(i).copy(alpha = 0.14f),
                    tween(180), label = "bg",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(bg)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPick(i)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$i",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        if (showWord && value != null) {
            Text(painWord(value), color = painColor(value), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Big selectable tiles, e.g. effort. */
@Composable
fun <T> TileChoice(options: List<Triple<T, String, String>>, selected: T?, onSelect: (T) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((v, title, sub) in options) {
            val isSel = v == selected
            val bg by animateColorAsState(
                if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                tween(180), label = "tile",
            )
            val fg = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Column(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(bg)
                    .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(v) }
                    .padding(vertical = 14.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, color = fg, fontWeight = FontWeight.Bold)
                Text(sub, color = fg.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun <T> SingleChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((v, label) in options) {
            FilterChip(
                selected = v == selected,
                onClick = { onSelect(v) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
fun MultiChoice(options: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in options) {
            FilterChip(
                selected = o in selected,
                onClick = { onToggle(o) },
                label = { Text(o) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            )
        }
    }
}

@Composable
fun Stepper(label: String, value: Int, onChange: (Int) -> Unit, min: Int = 0, max: Int = 999, step: Int = 1, suffix: String = "") {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(
            onClick = { onChange((value - step).coerceAtLeast(min)) },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
        ) { Text("−", fontSize = 18.sp) }
        Text("$value$suffix", Modifier.width(70.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        OutlinedButton(
            onClick = { onChange((value + step).coerceAtMost(max)) },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
        ) { Text("+", fontSize = 18.sp) }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            if (sub != null) Muted(sub)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun PrefSwitch(label: String, initial: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    SwitchRow(label, v, sub) { v = it; onChange(it) }
}

@Composable
fun PrefTime(label: String, initialMin: Int, onChange: (Int) -> Unit) {
    val ctx = LocalContext.current
    var v by remember { mutableStateOf(initialMin) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        TextButton(onClick = {
            TimePickerDialog(ctx, { _, h, m -> v = h * 60 + m; onChange(v) }, v / 60, v % 60, true).show()
        }) { Text(minToText(v), fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun PrefStepper(label: String, initial: Int, min: Int, max: Int, step: Int = 1, suffix: String = "", onChange: (Int) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    Stepper(label, v, { v = it; onChange(it) }, min, max, step, suffix)
}

val dayNames = listOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")

@Composable
fun PrefDay(initial: Int, onChange: (Int) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    SingleChoice(dayNames, v) { v = it; onChange(it) }
}

/** Animated ring. [progress] 0..1. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 10.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    animate: Boolean = true,
    content: @Composable () -> Unit = {},
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), if (animate) tween(900) else tween(0), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val d = min(size.width, size.height) - w
            val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            drawArc(track, -90f, 360f, false, topLeft = tl, size = Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
            if (p > 0f) drawArc(color, -90f, 360f * p, false, topLeft = tl, size = Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Thin horizontal meter, e.g. average pain out of 10. */
@Composable
fun MeterBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "meter")
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(RoundedCornerShape(4.dp)).background(color))
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color? = null) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(14.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = valueColor ?: MaterialTheme.colorScheme.onSurface)
        Muted(label)
    }
}

@Composable
fun Pill(text: String, color: Color, filled: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (filled) Color.White else color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(if (filled) Modifier.background(color) else Modifier.border(BorderStroke(1.dp, color.copy(alpha = 0.6f)), RoundedCornerShape(50)))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

fun minToText(min: Int) = "%02d:%02d".format(min / 60, min % 60)

fun pickDate(ctx: Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(ctx, { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}

fun pickTime(ctx: Context, initialMin: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> onPicked(h * 60 + m) }, initialMin / 60, initialMin % 60, true).show()
}

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

fun openUrl(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { toast(ctx, "No app to open the link") }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun KeyValue(key: String, value: String, valueColor: Color? = null) {
    Row(Modifier.fillMaxWidth()) {
        Text(key, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, color = valueColor ?: MaterialTheme.colorScheme.onSurface)
    }
}
