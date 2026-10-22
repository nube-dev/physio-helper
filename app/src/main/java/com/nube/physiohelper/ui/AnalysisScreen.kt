package com.nube.physiohelper.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.nube.physiohelper.App
import com.nube.physiohelper.analysis.Advice
import com.nube.physiohelper.analysis.Analysis
import com.nube.physiohelper.data.CheckIn
import com.nube.physiohelper.data.Exercise
import com.nube.physiohelper.data.ExerciseLog
import com.nube.physiohelper.data.Session
import com.nube.physiohelper.data.SpadiResult
import java.time.LocalDate

@Composable
fun AnalysisScreen(onBack: () -> Unit) {
    val sessions by rememberFlow(emptyList<Session>()) { App.dao.sessionsFlow() }
    val logs by rememberFlow(emptyList<ExerciseLog>()) { App.dao.logsFlow() }
    val exercises by rememberFlow(emptyList<Exercise>()) { App.dao.exercisesFlow() }
    val checkIns by rememberFlow(emptyList<CheckIn>()) { App.dao.checkInsFlow() }
    val spadi by rememberFlow(emptyList<SpadiResult>()) { App.dao.spadiFlow() }
    var window by remember { mutableStateOf<Int?>(14) }
    val today = LocalDate.now()

    val stats = remember(logs, exercises, sessions, checkIns, window) {
        Analysis.exerciseStats(logs, exercises, sessions, checkIns, window, today)
    }
    val adh = remember(sessions, window) { Analysis.adherence(sessions, App.settings.weeklyTarget, window ?: 28, today) }
    val flares = remember(sessions, checkIns, window) {
        val since = window?.let { today.minusDays(it.toLong() - 1).toString() } ?: ""
        Analysis.flares(sessions, checkIns).filter { it.date >= since }
    }

    Screen("Analysis", onBack) {
        SingleChoice(listOf<Pair<Int?, String>>(7 to "7 days", 14 to "14 days", 30 to "30 days", null to "All"), window) { window = it }

        SectionCard("Consistency") {
            KeyValue("This week", "${adh.thisWeek} / ${adh.target}")
            KeyValue("Streak", "${adh.streak} days")
            KeyValue("Days with a session", "${adh.completedDays} of ${adh.windowDays}")
            adh.lastWeeks.forEach { (start, n) ->
                KeyValue("Week of $start", "$n", if (n >= adh.target) painColor(0) else null)
            }
        }

        SectionCard("Morning flares") {
            if (flares.isEmpty()) Muted("None in this period.")
            flares.reversed().forEach { Text("${it.date}: ${it.reason}") }
        }

        val recentChecks = checkIns.take(7)
        if (recentChecks.isNotEmpty()) {
            SectionCard("Recent mornings") {
                recentChecks.forEach { c ->
                    val extra = listOfNotNull(
                        c.stiffnessMin?.let { "stiff $it min" },
                        c.nightPain?.let { if (it) "woke from pain" else null },
                    ).joinToString(", ")
                    KeyValue(c.date, "pain ${c.morningPain}" + if (extra.isNotBlank()) " · $extra" else "", painColor(c.morningPain))
                }
            }
        }

        if (spadi.isNotEmpty()) {
            SectionCard("Weekly score (SPADI, lower is better)") {
                val first = spadi.last()
                val latest = spadi.first()
                KeyValue("Latest (${latest.date})", "%.0f".format(latest.totalScore))
                if (spadi.size > 1) {
                    KeyValue("First (${first.date})", "%.0f".format(first.totalScore))
                    val diff = latest.totalScore - first.totalScore
                    KeyValue("Change", "%+.0f".format(diff), if (diff <= -13) painColor(0) else null)
                }
            }
        }

        Text("Exercises, problem ones first", style = MaterialTheme.typography.titleMedium)
        if (stats.isEmpty()) Muted("Nothing logged in this period.")
        stats.forEach { st ->
            SectionCard {
                Text(st.name, fontWeight = FontWeight.SemiBold)
                KeyValue("Logged", "${st.count}× · full ${st.fullPct}%")
                KeyValue("Partial / skipped", "${st.partial} / ${st.skipped}")
                KeyValue("Pain avg / max", "%.1f / %d".format(st.avgPain, st.maxPain), painColor(st.avgPain.toInt()))
                KeyValue("Effort easy / mod / hard", "${st.easy} / ${st.moderate} / ${st.hard}")
                if (st.topTags.isNotEmpty()) KeyValue("Top issues", st.topTags.joinToString { "${it.first} ×${it.second}" })
                st.advice?.let { a ->
                    val color = when (a.kind) {
                        Advice.Kind.LEVEL_UP -> Color(0xFF2E7D32)
                        Advice.Kind.ASK_PHYSIO -> MaterialTheme.colorScheme.primary
                        Advice.Kind.REDUCE -> MaterialTheme.colorScheme.error
                    }
                    Text(a.text, color = color, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Muted("Level-up rule: last 6 logs full, pain ≤2, effort easy, no flare the next morning. Suggestions only; anything new needs the physio's OK.")
    }
}
