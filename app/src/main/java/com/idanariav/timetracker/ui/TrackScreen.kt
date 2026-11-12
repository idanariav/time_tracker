package com.idanariav.timetracker.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Square
import com.idanariav.timetracker.data.ActivityItem
import com.idanariav.timetracker.data.Session
import com.idanariav.timetracker.domain.OTHER_GROUP
import com.idanariav.timetracker.domain.formatElapsed
import com.idanariav.timetracker.domain.hasUngrouped
import com.idanariav.timetracker.tracking.Change
import com.idanariav.timetracker.tracking.Graph
import kotlinx.coroutines.launch

/**
 * Pinned header (running bar, search, group chips) above a scrolling tile grid, so finding one
 * of dozens of activities is a search or a group tap away. Search spans all groups.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackScreen(padding: PaddingValues, snackbar: SnackbarHostState, onEditActivity: (id: Long?, groupId: Long?) -> Unit) {
    val all by remember { Graph.dao.allActivities() }.collectAsState(emptyList())
    val running by remember { Graph.dao.running() }.collectAsState(null)
    val suggestions by remember { Graph.suggestionFlow(6) }.collectAsState(emptyList())
    val groups by remember { Graph.dao.groups() }.collectAsState(emptyList())
    val settings by Graph.prefs.settings.collectAsState()
    val compact = settings.compactGrid
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    var editing by remember { mutableStateOf<Session?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var groupFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    val gridState = rememberLazyGridState()
    val active = all.filter { !it.archived }
    val current = all.firstOrNull { it.id == running?.activityId }
    val sections = browseSections(active, groups, query, groupFilter, settings.groupActivities)
    // Jump back to the top when the filter changes, so results are visible.
    LaunchedEffect(query, groupFilter) { gridState.scrollToItem(0) }

    fun notify(text: String, change: Change?) = scope.launch {
        snackbar.currentSnackbarData?.dismiss()
        val r = snackbar.showSnackbar(text, actionLabel = change?.let { "Undo" }, duration = SnackbarDuration.Short)
        if (r == SnackbarResult.ActionPerformed && change != null) Graph.tracker.undo(change)
    }

    fun switch(a: ActivityItem) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (query.isNotEmpty()) { query = ""; focus.clearFocus() }
        scope.launch {
            val change = Graph.tracker.switchTo(a.id)
            if (change == null) notify("${a.name} is already running", null) else notify("${a.name} started", change)
        }
    }

    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        RunningBar(
            running, current,
            onStop = { scope.launch { Graph.tracker.stop()?.let { notify("Stopped", it) } } },
            onAdjust = { editing = running },
        )
        SearchField(
            query, { query = it },
            modifier = Modifier.padding(horizontal = 12.dp),
            onDone = { sections.singleOrNull()?.activities?.singleOrNull()?.let { switch(it) } ?: focus.clearFocus() },
        )
        if (query.isBlank() && groups.isNotEmpty()) {
            GroupFilterRow(
                groups, hasUngrouped(active, groups), groupFilter, { groupFilter = it },
                Modifier.padding(top = 6.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (compact) 64.dp else 96.dp),
            state = gridState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = padding.calculateBottomPadding() + 12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Always present (possibly empty) so the grid stays anchored at the top when suggestions
            // load after the first frame; otherwise the row would be inserted above the viewport.
            item(span = { GridItemSpan(maxLineSpan) }, key = "suggested") {
                if (query.isBlank() && groupFilter == null && suggestions.isNotEmpty()) {
                    Column(Modifier.padding(vertical = 2.dp)) {
                        Text(
                            "Suggested now", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp),
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(suggestions.size, key = { suggestions[it].id }) { i ->
                                val a = suggestions[i]
                                AssistChip(onClick = { switch(a) }, label = { Text(a.name) }, leadingIcon = { ChipIcon(a) })
                            }
                        }
                    }
                }
            }
            if (sections.all { it.activities.isEmpty() }) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "nomatch") {
                    Text(
                        if (query.isNotBlank()) "No activity matches “${query.trim()}”" else "No activities here yet",
                        Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            sections.forEach { section ->
                val headed = query.isBlank() && groupFilter == null && (sections.size > 1 || section.group != null)
                if (headed) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "group-${section.group?.id ?: OTHER_GROUP}") {
                        SectionHeader(section.group, Modifier.padding(start = 0.dp))
                    }
                }
                items(section.activities, key = { it.id }) { a ->
                    ActivityTile(
                        a, a.id == running?.activityId, compact,
                        onClick = { switch(a) }, onLongClick = { onEditActivity(a.id, null) },
                    )
                }
            }
            item(key = "new") {
                NewTile(compact) { onEditActivity(null, groupFilter?.takeIf { it != OTHER_GROUP }) }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "hint") {
                Text(
                    "Tap to switch · long-press to edit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }

    editing?.let { SessionEditorSheet(it, onDismiss = { editing = null }) }
}

/** One-row status: icon, name, live elapsed; edit (adjust) and stop as icon buttons. */
@Composable
private fun RunningBar(running: Session?, act: ActivityItem?, onStop: () -> Unit, onAdjust: () -> Unit) {
    val tint = act?.let { Color(it.color).copy(alpha = 0.22f) } ?: MaterialTheme.colorScheme.surfaceVariant
    Card(
        colors = CardDefaults.cardColors(containerColor = tint),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        if (running == null || act == null) {
            Text(
                "Not tracking — tap an activity to start",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            )
            return@Card
        }
        val now = rememberNow(1000)
        Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(act.icon, act.color, 40.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(act.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${formatElapsed(now - running.startTime)} · since ${TimeFmt.dayTime(running.startTime)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onAdjust) { Icon(Lucide.Pencil, "Adjust") }
            FilledTonalIconButton(onClick = onStop) { Icon(Lucide.Square, "Stop") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NewTile(compact: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(if (compact) 12.dp else 16.dp)
    Column(
        Modifier.padding(if (compact) 3.dp else 4.dp).clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .combinedClickable(onClick = onClick)
            .padding(vertical = if (compact) 8.dp else 12.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Lucide.Plus, null, modifier = Modifier.size(if (compact) 20.dp else 28.dp))
        Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
        Text("New", style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActivityTile(a: ActivityItem, active: Boolean, compact: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = Color(a.color)
    val shape = RoundedCornerShape(if (compact) 12.dp else 16.dp)
    Column(
        Modifier.padding(if (compact) 3.dp else 4.dp).clip(shape)
            .background(if (active) c else c.copy(alpha = 0.18f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = if (compact) 8.dp else 12.dp, horizontal = 4.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            activityIcon(a.icon), null, modifier = Modifier.size(if (compact) 20.dp else 28.dp),
            tint = if (active) onColor(c) else readableTint(a.color),
        )
        Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
        Text(
            a.name,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (active) onColor(c) else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
