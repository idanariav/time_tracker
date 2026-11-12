package com.idanariav.timetracker.ui

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Share2
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.idanariav.timetracker.data.Session
import com.idanariav.timetracker.domain.Period
import com.idanariav.timetracker.domain.PeriodKind
import com.idanariav.timetracker.domain.counts
import com.idanariav.timetracker.domain.formatDuration
import com.idanariav.timetracker.domain.goalTotals
import com.idanariav.timetracker.domain.startMillis
import com.idanariav.timetracker.domain.totalsBy
import com.idanariav.timetracker.tracking.CsvExport
import com.idanariav.timetracker.tracking.Graph
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class Slice(val key: Long, val label: String, val color: Int, val ms: Long, val icon: String? = null)

private const val NO_GROUP = -1L

private enum class View { ACTIVITIES, GROUPS, GOALS }
private const val GREY = 0xFF9E9E9E.toInt()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(padding: PaddingValues) {
    var kind by rememberSaveable { mutableStateOf(PeriodKind.DAY) }
    var anchor by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var view by rememberSaveable { mutableStateOf(View.ACTIVITIES) }
    var expanded by remember { mutableStateOf(setOf<Long>()) }
    val period = Period(kind, anchor)
    val from = period.startMillis()
    val to = period.endMillis()
    val sessions by remember(from, to) { Graph.dao.sessionsIn(from, to) }.collectAsState(emptyList())
    val acts by remember { Graph.dao.allActivities() }.collectAsState(emptyList())
    val goals by remember { Graph.dao.goals() }.collectAsState(emptyList())
    val groups by remember { Graph.dao.groups() }.collectAsState(emptyList())
    val links by remember { Graph.dao.goalLinks() }.collectAsState(emptyList())
    val settings by Graph.prefs.settings.collectAsState()
    val now = rememberNow(30_000)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()

    val actById = acts.associateBy { it.id }
    val groupById = groups.associateBy { it.id }
    val groupKey: (Session) -> Long = { s -> actById[s.activityId]?.groupId?.takeIf { it in groupById } ?: NO_GROUP }
    val counted = sessions.filter { it.counts(now, settings.minMs) }
    val perActivity = totalsBy(counted, from, to, now, 0) { it.activityId }
    val total = perActivity.values.sum()
    val slices = when (view) {
        View.ACTIVITIES -> perActivity.map { (id, ms) ->
            actById[id]?.let { Slice(id, it.name, it.color, ms, it.icon) } ?: Slice(id, "(deleted)", GREY, ms)
        }
        View.GROUPS -> totalsBy(counted, from, to, now, 0, groupKey).map { (id, ms) ->
            groupById[id]?.let { Slice(id, it.name, it.color, ms) } ?: Slice(id, "Other", GREY, ms)
        }
        // Goals can overlap, so their shares needn't add up to 100%.
        View.GOALS -> goalTotals(links, perActivity).filterValues { it > 0 }.mapNotNull { (id, ms) ->
            goals.firstOrNull { it.id == id }?.let { Slice(id, it.name, it.color, ms) }
        }
    }.sortedByDescending { it.ms }
    // Charts color by group in the Groups view, by activity otherwise (a session can belong to several goals).
    val chartKey: (Session) -> Long = if (view == View.GROUPS) groupKey else { s -> s.activityId }
    val chartColors: Map<Long, Color> = if (view == View.GROUPS) {
        groups.associate { it.id to Color(it.color) } + (NO_GROUP to Color(GREY))
    } else {
        acts.associate { it.id to Color(it.color) }
    }
    val elapsedDays = period.days().count { it <= today }.coerceAtLeast(1)

    LazyColumn(contentPadding = padding) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    PeriodKind.entries.forEachIndexed { i, k ->
                        SegmentedButton(
                            selected = kind == k, onClick = { kind = k },
                            shape = SegmentedButtonDefaults.itemShape(i, PeriodKind.entries.size),
                        ) { Text(k.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    PeriodHeader(
                        period.label(), onPrev = { anchor = period.shift(-1).anchor },
                        onNext = { anchor = period.shift(1).anchor }, onReset = { anchor = today },
                        canGoNext = period.endExclusive <= today,
                    )
                }
                IconButton(onClick = { scope.launch { CsvExport.share(ctx, from, to, period.label()) } }) {
                    Icon(Lucide.Share2, "Export this period as CSV")
                }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    View.entries.forEachIndexed { i, v ->
                        SegmentedButton(
                            selected = view == v, onClick = { view = v },
                            shape = SegmentedButtonDefaults.itemShape(i, View.entries.size),
                        ) { Text(v.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    }
                }
            }
        }
        item {
            val summary = buildString {
                append("Tracked ${formatDuration(total)}")
                if (kind == PeriodKind.DAY) {
                    val span = (minOf(to, now) - from).coerceAtLeast(0)
                    if (span > total) append(" · untracked ${formatDuration(span - total)}")
                } else {
                    append(" · ${formatDuration(total / elapsedDays)}/day")
                }
            }
            Text(
                summary, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                if (kind == PeriodKind.DAY) DayTimeline(counted, from, to, now, chartKey, chartColors)
                else DailyBars(period, counted, now, chartKey, chartColors)
            }
        }
        if (slices.isEmpty()) item { Text("No data for this period.", modifier = Modifier.padding(24.dp)) }
        items(slices, key = { it.key }) { s ->
            val pct = if (total > 0) s.ms * 100f / total else 0f
            val isOpen = view == View.GROUPS && s.key in expanded
            ListItem(
                modifier = if (view == View.GROUPS) {
                    Modifier.clickable { expanded = if (isOpen) expanded - s.key else expanded + s.key }
                } else Modifier,
                leadingContent = {
                    if (s.icon != null) IconBadge(s.icon, s.color, 32.dp)
                    else Box(Modifier.size(14.dp).clip(CircleShape).background(Color(s.color)))
                },
                headlineContent = { Text(s.label) },
                supportingContent = {
                    Column {
                        LinearProgressIndicator(
                            progress = { pct / 100f }, color = Color(s.color),
                            trackColor = Color(s.color).copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                        if (kind != PeriodKind.DAY) Text("${formatDuration(s.ms / elapsedDays)}/day avg")
                        if (view == View.GROUPS && !isOpen) Text("Tap for activities", style = MaterialTheme.typography.bodySmall)
                    }
                },
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatDuration(s.ms), style = MaterialTheme.typography.titleSmall)
                        Text("%.0f%%".format(pct), style = MaterialTheme.typography.bodySmall)
                    }
                },
            )
            if (isOpen) {
                // Drill-down: this group's activities and their share of the group.
                perActivity.filter { (id, _) -> (actById[id]?.groupId?.takeIf { it in groupById } ?: NO_GROUP) == s.key }
                    .entries.sortedByDescending { it.value }
                    .forEach { (id, ms) ->
                        val a = actById[id]
                        Row(
                            Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconBadge(a?.icon ?: "Circle", a?.color ?: GREY, 24.dp)
                            Text(a?.name ?: "(deleted)", Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 1)
                            Text(formatDuration(ms), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "%.0f%%".format(ms * 100f / s.ms), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
            }
        }
    }
}

/** 24h strip with each entry drawn in its color, plus hour ticks. */
@Composable
private fun DayTimeline(
    sessions: List<Session>, from: Long, to: Long, now: Long,
    keyOf: (Session) -> Long, colorOf: Map<Long, Color>,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column {
        Canvas(Modifier.fillMaxWidth().height(36.dp).clip(MaterialTheme.shapes.small)) {
            drawRect(track)
            val span = (to - from).toFloat()
            sessions.forEach { s ->
                val a = maxOf(s.startTime, from)
                val b = minOf(s.endTime ?: now, to)
                if (b > a) {
                    val x = (a - from) / span * size.width
                    val w = ((b - a) / span * size.width).coerceAtLeast(1f)
                    drawRect(colorOf[keyOf(s)] ?: Color.Gray, Offset(x, 0f), Size(w, size.height))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("0", "6", "12", "18", "24").forEachIndexed { i, l ->
                Text(
                    l, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f),
                    textAlign = when (i) { 0 -> TextAlign.Start; 4 -> TextAlign.End; else -> TextAlign.Center },
                )
            }
        }
    }
}

/** One stacked bar per day (week/month view), scaled to the busiest day. */
@Composable
private fun DailyBars(
    period: Period, sessions: List<Session>, now: Long,
    keyOf: (Session) -> Long, colorOf: Map<Long, Color>,
) {
    val days = period.days()
    val perDay = days.map { d ->
        val f = d.startMillis()
        val t = d.plusDays(1).startMillis()
        totalsBy(sessions, f, t, now, 0, keyOf)
    }
    val max = perDay.maxOfOrNull { it.values.sum() }?.coerceAtLeast(1) ?: 1
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val slot = size.width / days.size
            val barW = slot * 0.7f
            perDay.forEachIndexed { i, totals ->
                val x = i * slot + (slot - barW) / 2
                drawRect(track, Offset(x, 0f), Size(barW, size.height))
                var y = size.height
                totals.entries.sortedByDescending { it.value }.forEach { (k, ms) ->
                    val h = ms.toFloat() / max * size.height
                    y -= h
                    drawRect(colorOf[k] ?: Color.Gray, Offset(x, y), Size(barW, h))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEach { d ->
                val label = when {
                    period.kind == PeriodKind.WEEK -> d.dayOfWeek.name.take(2).lowercase().replaceFirstChar { it.uppercase() }
                    d.dayOfMonth == 1 || d.dayOfMonth % 5 == 0 -> d.dayOfMonth.toString()
                    else -> ""
                }
                Text(
                    label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
