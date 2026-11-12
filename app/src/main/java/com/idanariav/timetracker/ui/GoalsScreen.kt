package com.idanariav.timetracker.ui

import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.idanariav.timetracker.data.ActivityItem
import com.idanariav.timetracker.data.Goal
import com.idanariav.timetracker.domain.Period
import com.idanariav.timetracker.domain.PeriodKind
import com.idanariav.timetracker.domain.formatDuration
import com.idanariav.timetracker.domain.totalsByActivity
import com.idanariav.timetracker.tracking.Graph
import java.time.LocalDate

private data class GoalRow(val goal: Goal?, val total: Long, val items: List<Pair<ActivityItem, Long>>)

@Composable
fun GoalsScreen(padding: PaddingValues, onManage: () -> Unit) {
    var day by rememberSaveable { mutableStateOf(LocalDate.now()) }
    val period = Period(PeriodKind.DAY, day)
    val from = period.startMillis()
    val to = period.endMillis()
    val sessions by remember(from, to) { Graph.dao.sessionsIn(from, to) }.collectAsState(emptyList())
    val acts by remember { Graph.dao.allActivities() }.collectAsState(emptyList())
    val goals by remember { Graph.dao.goals() }.collectAsState(emptyList())
    val settings by Graph.prefs.settings.collectAsState()
    val now = rememberNow(15_000)

    val totals = totalsByActivity(sessions, from, to, now, settings.minMs)
    val byId = acts.associateBy { it.id }
    val goalIds = goals.map { it.id }.toSet()
    fun rowFor(goal: Goal?, match: (ActivityItem) -> Boolean): GoalRow {
        val items = totals.mapNotNull { (id, ms) -> byId[id]?.takeIf(match)?.let { it to ms } }
            .sortedByDescending { it.second }
        return GoalRow(goal, items.sumOf { it.second }, items)
    }
    val rows = goals.map { g -> rowFor(g) { it.goalId == g.id } } +
        listOf(rowFor(null) { it.goalId == null || it.goalId !in goalIds }).filter { it.total > 0 }

    LazyColumn(contentPadding = padding) {
        item {
            PeriodHeader(
                period.label(), onPrev = { day = day.minusDays(1) }, onNext = { day = day.plusDays(1) },
                onReset = { day = LocalDate.now() }, canGoNext = day < LocalDate.now(),
            )
        }
        item {
            Text(
                "Tracked ${formatDuration(totals.values.sum())}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        items(rows, key = { it.goal?.id ?: -1L }) { GoalCard(it) }
        item {
            TextButton(onClick = onManage, modifier = Modifier.padding(12.dp)) {
                Text(if (goals.isEmpty()) "Create goals to group activities" else "Edit goals & targets")
            }
        }
    }
}

@Composable
private fun GoalCard(row: GoalRow) {
    val g = row.goal
    val color = Color(g?.color ?: 0xFF9E9E9E.toInt())
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                Text(
                    g?.name ?: "No goal", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
                Text(formatDuration(row.total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (g != null && g.targetMinutes > 0) {
                val target = g.targetMinutes * 60_000L
                val over = row.total > target
                val barColor = if (g.isLimit && over) MaterialTheme.colorScheme.error else color
                LinearProgressIndicator(
                    progress = { (row.total.toFloat() / target).coerceIn(0f, 1f) },
                    color = barColor,
                    trackColor = color.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                val status = when {
                    g.isLimit && over -> "Over the ${formatDuration(target)} limit by ${formatDuration(row.total - target)}"
                    g.isLimit -> "${formatDuration(target - row.total)} left of ${formatDuration(target)} limit"
                    over || row.total == target -> "Goal of ${formatDuration(target)} reached ✓"
                    else -> "${formatDuration(target - row.total)} to go of ${formatDuration(target)}"
                }
                Text(
                    status, style = MaterialTheme.typography.bodySmall,
                    color = if (g.isLimit && over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            row.items.forEach { (a, ms) ->
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(activityIcon(a.icon), null, tint = readableTint(a.color), modifier = Modifier.size(18.dp))
                    Text(
                        a.name, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    Text(formatDuration(ms), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
