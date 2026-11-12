package com.idanariav.timetracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.idanariav.timetracker.data.ActivityItem
import com.idanariav.timetracker.data.Session
import com.idanariav.timetracker.domain.Period
import com.idanariav.timetracker.domain.PeriodKind
import com.idanariav.timetracker.domain.counts
import com.idanariav.timetracker.domain.durationMs
import com.idanariav.timetracker.tracking.Graph
import java.time.LocalDate

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
    // Entries that don't count (shorter than the minimum) are hidden; newest first.
    val shown = sessions.filter { it.counts(now, settings.minMs) }.sortedByDescending { it.startTime }

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
            if (shown.isEmpty()) {
                item { Text("Nothing logged.", modifier = Modifier.padding(24.dp)) }
            }
            items(shown, key = { it.id }) { s -> EntryRow(s, byId[s.activityId], now) { editing = s } }
        }
        FloatingActionButton(
            onClick = { if (defaultActivity != 0L) editing = newEntry() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(padding).padding(16.dp),
        ) { Icon(Lucide.Plus, "Add entry") }
    }

    editing?.let { SessionEditorSheet(it, onDismiss = { editing = null }) }
}

/** One line: icon · activity · start – end · minutes. Tap to edit (notes live in the editor). */
@Composable
private fun EntryRow(s: Session, a: ActivityItem?, now: Long, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(a?.icon ?: "Circle", a?.color ?: 0xFF9E9E9E.toInt(), 28.dp)
        Text(
            a?.name ?: "(deleted)",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (s.endTime == null) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Text(
            TimeFmt.range(TimeFmt.clock(s.startTime), s.endTime?.let { TimeFmt.clock(it) } ?: "now"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            "${s.durationMs(now) / 60_000} min",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(64.dp),
        )
    }
}
