package com.idanariav.timetracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.idanariav.timetracker.data.Session
import com.idanariav.timetracker.domain.endTimeAfter
import com.idanariav.timetracker.domain.formatDuration
import com.idanariav.timetracker.domain.withTimeOfDay
import com.idanariav.timetracker.tracking.Graph
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

private const val MIN = 60_000L

/**
 * Add/edit a log entry. Start/end get ±15/30 nudges plus anchors:
 * "Last" = end of the previous entry, "Next" = start of the following entry, "Now".
 * Saving trims or splits whatever it overlaps (see planOverlaps).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SessionEditorSheet(initial: Session, onDismiss: () -> Unit) {
    val acts by remember { Graph.dao.activeActivities() }.collectAsState(emptyList())
    val groups by remember { Graph.dao.groups() }.collectAsState(emptyList())
    val suggestions by remember { Graph.suggestionFlow(6) }.collectAsState(emptyList())
    var picking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isNew = initial.id == 0L
    var activityId by remember { mutableLongStateOf(initial.activityId) }
    var start by remember { mutableLongStateOf(initial.startTime) }
    var end by remember { mutableStateOf(initial.endTime) }
    var note by remember { mutableStateOf(initial.note) }
    var prevEnd by remember { mutableStateOf<Long?>(null) }
    var nextStart by remember { mutableStateOf<Long?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(initial) {
        prevEnd = Graph.dao.prevEnd(initial.startTime, initial.id)
        nextStart = initial.endTime?.let { Graph.dao.nextStart(it, initial.id) }
        loaded = true
    }

    val now = rememberNow(1000)
    // "Still running" is only offered when nothing was logged after this entry.
    val canRun = initial.endTime == null || (loaded && nextStart == null)
    val error = when {
        start > now -> "Start is in the future"
        end != null && end!! <= start -> "End must be after start"
        end != null && end!! > now + MIN -> "End is in the future"
        else -> null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
        ) {
            Text(
                when {
                    isNew -> "New entry"
                    initial.endTime == null -> "Current activity"
                    else -> "Edit entry"
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            // One field opens the searchable, grouped picker; a few likely ones are a tap away.
            PickerField("Activity", acts.firstOrNull { it.id == activityId }, onClick = { picking = true })
            val quick = suggestions.filter { it.id != activityId }.take(5)
            if (quick.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    quick.forEach { a ->
                        FilterChip(false, { activityId = a.id }, label = { Text(a.name) }, leadingIcon = { ChipIcon(a) })
                    }
                }
            }

            TimeAdjuster(
                label = "Start", value = start, onChange = { start = it }, now = now,
                anchorLabel = "Last", anchor = prevEnd,
                onPicked = { h, m -> start = withTimeOfDay(start, h, m) },
            )

            if (end == null) {
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("End", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(52.dp))
                    Text("running", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
            } else {
                TimeAdjuster(
                    label = "End", value = end!!, onChange = { end = it }, now = now,
                    anchorLabel = "Next", anchor = nextStart,
                    onPicked = { h, m -> end = endTimeAfter(start, h, m) },
                )
            }
            if (canRun) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Still running", modifier = Modifier.weight(1f))
                    Switch(checked = end == null, onCheckedChange = { end = if (it) null else now })
                }
            }

            Text(
                "Duration  ${formatDuration(((end ?: now) - start).coerceAtLeast(0))}",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp),
            )
            OutlinedTextField(
                value = note, onValueChange = { note = it }, label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!isNew) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    enabled = error == null && activityId != 0L,
                    onClick = {
                        scope.launch {
                            Graph.tracker.saveSession(
                                Session(initial.id, activityId, start, end?.coerceAtMost(now), note.trim()),
                            )
                            onDismiss()
                        }
                    },
                ) { Text("Save") }
            }
        }
    }

    if (picking) {
        ActivityPickerSheet(
            "Choose activity", acts, groups, listOf(activityId), multi = false,
            onDismiss = { picking = false }, onConfirm = { activityId = it.first() },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete entry?", "This removes it from the log. The gap stays untracked.", "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = { scope.launch { Graph.tracker.deleteSession(initial); onDismiss() } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeAdjuster(
    label: String,
    value: Long,
    onChange: (Long) -> Unit,
    now: Long,
    anchorLabel: String,
    anchor: Long?,
    onPicked: (Int, Int) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(52.dp))
            TextButton(onClick = { picking = true }) {
                Text(TimeFmt.dayTime(value), style = MaterialTheme.typography.titleLarge)
            }
        }
        val small = PaddingValues(horizontal = 10.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (anchor != null) OutlinedButton(onClick = { onChange(anchor) }, contentPadding = small) { Text(anchorLabel) }
            listOf(-30, -15, 15, 30).forEach { m ->
                FilledTonalButton(onClick = { onChange(value + m * MIN) }, contentPadding = small) {
                    Text(if (m > 0) "+$m" else "−${-m}")
                }
            }
            OutlinedButton(onClick = { onChange(now) }, contentPadding = small) { Text("Now") }
        }
    }
    if (picking) {
        val t = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault())
        TimePickDialog(t.hour, t.minute, onDismiss = { picking = false }, onPick = onPicked)
    }
}
