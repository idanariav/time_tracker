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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idanariav.timetracker.data.ActivityItem
import com.idanariav.timetracker.data.Session
import com.idanariav.timetracker.domain.formatElapsed
import com.idanariav.timetracker.tracking.Graph
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackScreen(padding: PaddingValues, snackbar: SnackbarHostState, onEditActivity: (Long?) -> Unit) {
    val all by remember { Graph.dao.allActivities() }.collectAsState(emptyList())
    val running by remember { Graph.dao.running() }.collectAsState(null)
    val suggestions by remember { Graph.suggestionFlow(5) }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var editing by remember { mutableStateOf<Session?>(null) }
    val active = all.filter { !it.archived }
    val current = all.firstOrNull { it.id == running?.activityId }

    fun notify(text: String, change: com.idanariav.timetracker.tracking.Change?) = scope.launch {
        snackbar.currentSnackbarData?.dismiss()
        val r = snackbar.showSnackbar(text, actionLabel = change?.let { "Undo" }, duration = SnackbarDuration.Short)
        if (r == SnackbarResult.ActionPerformed && change != null) Graph.tracker.undo(change)
    }

    fun switch(a: ActivityItem) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            val change = Graph.tracker.switchTo(a.id)
            if (change == null) notify("${a.emoji} ${a.name} is already running", null)
            else notify("${a.emoji} ${a.name} started", change)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(96.dp),
        contentPadding = PaddingValues(
            start = 12.dp, end = 12.dp,
            top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 12.dp,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            CurrentCard(
                running, current,
                onStop = { scope.launch { Graph.tracker.stop()?.let { notify("Stopped", it) } } },
                onAdjust = { editing = running },
            )
        }
        if (suggestions.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        "Suggested now", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems(suggestions, key = { it.id }) { a ->
                            AssistChip(onClick = { switch(a) }, label = { Text("${a.emoji} ${a.name}") })
                        }
                    }
                }
            }
        }
        items(active, key = { it.id }) { a ->
            ActivityTile(a, a.id == running?.activityId, onClick = { switch(a) }, onLongClick = { onEditActivity(a.id) })
        }
        item {
            Column(
                Modifier.padding(4.dp).clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .combinedClickable(onClick = { onEditActivity(null) })
                    .padding(vertical = 12.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("＋", fontSize = 28.sp)
                Spacer(Modifier.height(4.dp))
                Text("New", style = MaterialTheme.typography.labelMedium)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Tap to switch · long-press to edit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        }
    }

    editing?.let { SessionEditorSheet(it, onDismiss = { editing = null }) }
}

@Composable
private fun CurrentCard(running: Session?, act: ActivityItem?, onStop: () -> Unit, onAdjust: () -> Unit) {
    val tint = act?.let { Color(it.color).copy(alpha = 0.22f) } ?: MaterialTheme.colorScheme.surfaceVariant
    Card(
        colors = CardDefaults.cardColors(containerColor = tint),
        modifier = Modifier.fillMaxWidth().padding(4.dp),
    ) {
        if (running == null || act == null) {
            Text(
                "Not tracking — tap an activity to start",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(24.dp),
            )
            return@Card
        }
        val now = rememberNow(1000)
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(act.emoji, act.color, 56.dp)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(act.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    formatElapsed(now - running.startTime),
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium,
                )
                Text(
                    "since ${TimeFmt.dayTime(running.startTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                FilledTonalButton(onClick = onAdjust) { Text("Adjust") }
                OutlinedButton(onClick = onStop) { Text("Stop") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActivityTile(a: ActivityItem, active: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = Color(a.color)
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.padding(4.dp).clip(shape)
            .background(if (active) c else c.copy(alpha = 0.18f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 12.dp, horizontal = 4.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(a.emoji, fontSize = 28.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            a.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (active) onColor(c) else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
