@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.nube.physiohelper.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import kotlin.math.roundToInt

private val Brand = Color(0xFF1F6F5C)

@Composable
fun PhysioTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF7FD3BB), secondary = Color(0xFFB4CCC3))
    } else {
        lightColorScheme(primary = Brand, secondary = Color(0xFF4B635B))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Collect a DAO flow without re-creating it on every recomposition. */
@Composable
fun <T> rememberFlow(initial: T, vararg keys: Any?, factory: () -> Flow<T>): State<T> {
    val flow = remember(*keys) { factory() }
    return flow.collectAsState(initial)
}

@Composable
fun Screen(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                },
                actions = actions,
            )
        }
    ) { pad ->
        val base = Modifier.padding(pad).fillMaxSize()
        Column(
            modifier = (if (scroll) base.verticalScroll(rememberScrollState()) else base)
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
        colors = if (container != null) CardDefaults.cardColors(containerColor = container) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

fun painColor(v: Int): Color = when {
    v <= 3 -> Color(0xFF2E7D32)
    v <= 5 -> Color(0xFFE09B00)
    else -> Color(0xFFC62828)
}

@Composable
fun PainSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            Text("$value / 10", color = painColor(value), fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
        )
    }
}

@Composable
fun <T> SingleChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((v, label) in options) {
            FilterChip(selected = v == selected, onClick = { onSelect(v) }, label = { Text(label) })
        }
    }
}

@Composable
fun MultiChoice(options: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (o in options) {
            FilterChip(selected = o in selected, onClick = { onToggle(o) }, label = { Text(o) })
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
            modifier = Modifier.size(44.dp),
        ) { Text("−") }
        Text("$value$suffix", Modifier.width(72.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        OutlinedButton(
            onClick = { onChange((value + step).coerceAtMost(max)) },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(44.dp),
        ) { Text("+") }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        }) { Text(minToText(v)) }
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
fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun KeyValue(key: String, value: String, valueColor: Color? = null) {
    Row(Modifier.fillMaxWidth()) {
        Text(key, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, color = valueColor ?: MaterialTheme.colorScheme.onSurface)
    }
}
