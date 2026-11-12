package com.idanariav.timetracker.ui

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.idanariav.timetracker.data.ActivityItem
import com.idanariav.timetracker.data.Session
import com.idanariav.timetracker.domain.Period
import com.idanariav.timetracker.domain.PeriodKind
import com.idanariav.timetracker.domain.counts
import com.idanariav.timetracker.domain.durationMs
import com.idanariav.timetracker.domain.formatDuration
import com.idanariav.timetracker.domain.startMillis
import com.idanariav.timetracker.tracking.Graph
import java.time.LocalDate

private sealed interface LogRow {
    data class Entry(val s: Session) : LogRow
    data class Gap(val from: Long, val to: Long) : LogRow
}

/** Gaps of at least a minute between entries (within the day, up to now) become tappable "Untracked" rows. */
private fun buildRows(sessions: List<Session>, dayFrom: Long, dayTo: Long, now: Long): List<LogRow> {
    val rows = mutableListOf<LogRow>()
    var cursor = dayFrom
    for (s in sessions.sortedBy { it.startTime }) {
        if (s.startTime - cursor >= 60_000) rows += LogRow.Gap(cursor, s.startTime)
        rows += LogRow.Entry(s)
        cursor = maxOf(cursor, s.endTime ?: now)
    }
    val limit = minOf(dayTo, now)
    if (limit - cursor >= 60_000) rows += LogRow.Gap(cursor, limit)
    return rows.asReversed() // newest first
}

@Composable
fun LogScreen(padding: PaddingValues) {
    var day by rememberSaveable { mutableStateOf(LocalDate.now()) }
    val period = Period(PeriodKind.DAY, day)
    val from = period.startMillis()
    val to = period.endMillis()
    val sessions by remember(from, to) { Graph.dao.sessionsIn(from, to) }.collectAsState(emptyList())
    val acts by remember { Graph.dao.allActivities() }.collectAsState(emptyList())
    val settings by Graph.prefs.settings.collectAsState()
    val now = rememberNow(30_000)
    var editing by remember { mutableStateOf<Session?>(null) }
    val byId = acts.associateBy { it.id }
    val today = LocalDate.now()
    val defaultActivity = acts.firstOrNull { !it.archived }?.id ?: 0L

    fun newEntry(): Session {
        val lastEnd = sessions.mapNotNull { it.endTime }.filter { it <= minOf(to, now) }.maxOrNull()
        return if (day == today) {
            val start = lastEnd?.takeIf { now - it > 60_000 } ?: (now - 30 * 60_000)
            Session(activityId = defaultActivity, startTime = start, endTime = now)
        } else {
            val start = lastEnd ?: (from + 12 * 3600_000L)
            Session(activityId = defaultActivity, startTime = start, endTime = minOf(start + 30 * 60_000, now))
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp,
            ),
        ) {
            item {
                PeriodHeader(
                    period.label(), onPrev = { day = day.minusDays(1) }, onNext = { day = day.plusDays(1) },
                    onReset = { day = LocalDate.now() }, canGoNext = day < today,
                )
            }
            val rows = buildRows(sessions, from, to, now)
            if (rows.isEmpty()) {
                item { Text("Nothing logged.", modifier = Modifier.padding(24.dp)) }
            }
            items(rows, key = { r -> if (r is LogRow.Entry) "s${r.s.id}" else "g${(r as LogRow.Gap).from}" }) { r ->
                when (r) {
                    is LogRow.Entry -> EntryRow(r.s, byId[r.s.activityId], now, settings.minMs) { editing = r.s }
                    is LogRow.Gap -> GapRow(r) {
                        editing = Session(activityId = defaultActivity, startTime = r.from, endTime = r.to)
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { if (defaultActivity != 0L) editing = newEntry() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(padding).padding(16.dp),
        ) { Icon(Lucide.Plus, "Add entry") }
    }

    editing?.let { SessionEditorSheet(it, onDismiss = { editing = null }) }
}

@Composable
private fun EntryRow(s: Session, a: ActivityItem?, now: Long, minMs: Long, onClick: () -> Unit) {
    val ignored = !s.counts(now, minMs)
    val endText = s.endTime?.let { TimeFmt.dayTime(it) } ?: "now"
    ListItem(
        modifier = Modifier.clickable(onClick = onClick).alpha(if (ignored) 0.5f else 1f),
        leadingContent = { IconBadge(a?.icon ?: "Circle", a?.color ?: 0xFF9E9E9E.toInt()) },
        headlineContent = { Text((a?.name ?: "(deleted)") + if (s.endTime == null) "  •  running" else "") },
        supportingContent = {
            Text(
                "${TimeFmt.dayTime(s.startTime)} – $endText" +
                    (if (s.note.isNotBlank()) "\n${s.note}" else "") +
                    (if (ignored) "\nIgnored (shorter than the minimum)" else ""),
            )
        },
        trailingContent = { Text(formatDuration(s.durationMs(now)), style = MaterialTheme.typography.titleSmall) },
    )
}

@Composable
private fun GapRow(g: LogRow.Gap, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = { Icon(Lucide.Plus, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp)) },
        headlineContent = {
            Text("Untracked", fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        supportingContent = { Text("${TimeFmt.clock(g.from)} – ${TimeFmt.clock(g.to)} · tap to fill") },
        trailingContent = { Text(formatDuration(g.to - g.from), color = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}
