package com.idanariav.timetracker.ui

import com.idanariav.timetracker.domain.moveWithinGroup
import com.idanariav.timetracker.domain.hasUngrouped
import com.idanariav.timetracker.domain.OTHER_GROUP
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.saveable.rememberSaveable
import com.composables.icons.lucide.Clock
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Trash2
import android.Manifest
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.provider.Settings as SysSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.idanariav.timetracker.data.ActivityItem
import com.idanariav.timetracker.data.Goal
import com.idanariav.timetracker.data.Group
import com.idanariav.timetracker.data.Rule
import com.idanariav.timetracker.domain.formatDuration
import com.idanariav.timetracker.link.Links
import com.idanariav.timetracker.link.NfcTags
import com.idanariav.timetracker.notify.TrackingNotification
import com.idanariav.timetracker.tracking.CsvExport
import com.idanariav.timetracker.tracking.Graph
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    fab: (() -> Unit)? = null,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, "Back") } },
                actions = { actions() },
            )
        },
        floatingActionButton = {
            if (fab != null) FloatingActionButton(onClick = fab) { Icon(Lucide.Plus, "Add") }
        },
        content = content,
    )
}

// ---------------------------------------------------------------- Settings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, push: (Route) -> Unit) {
    val s by Graph.prefs.settings.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Graph.tracker.refreshSurfaces()
    }
    SubScreen("Settings", onBack) { p ->
        LazyColumn(contentPadding = p) {
            item {
                ListItem(
                    headlineContent = { Text("Activities") },
                    supportingContent = { Text("Add, edit, reorder, archive · write NFC tags") },
                    modifier = Modifier.clickable { push(Route.Activities) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Groups") },
                    supportingContent = { Text("Organize activities into sections (each activity is in at most one)") },
                    modifier = Modifier.clickable { push(Route.Groups) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Goals") },
                    supportingContent = { Text("Daily targets or limits over any activities you choose") },
                    modifier = Modifier.clickable { push(Route.Goals) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Smart suggestions") },
                    supportingContent = { Text("e.g. “after 22:00 → Sleep” or “while Work runs → Email, Commute”") },
                    modifier = Modifier.clickable { push(Route.Rules) },
                )
            }
            item { HorizontalDivider() }
            item {
                ListItem(
                    headlineContent = { Text("Section activities by group") },
                    supportingContent = { Text("On the Track tab and quick pick; ungrouped activities go under “Other”") },
                    trailingContent = {
                        Switch(s.groupActivities, onCheckedChange = { v -> Graph.prefs.update { it.copy(groupActivities = v) } })
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Compact activity tiles") },
                    supportingContent = { Text("Smaller tiles so more fit on screen (also toggled from the Track tab)") },
                    trailingContent = {
                        Switch(s.compactGrid, onCheckedChange = { v -> Graph.prefs.update { it.copy(compactGrid = v) } })
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Ignore short entries") },
                    supportingContent = {
                        Text("Entries shorter than ${formatDuration(s.minFilterSeconds * 1000L)} don't count in goals and analysis (they stay in the log)")
                    },
                    trailingContent = {
                        Switch(s.minFilterEnabled, onCheckedChange = { v -> Graph.prefs.update { it.copy(minFilterEnabled = v) } })
                    },
                )
            }
            if (s.minFilterEnabled) item {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60, 120, 300).forEach { sec ->
                        FilterChip(
                            selected = s.minFilterSeconds == sec,
                            onClick = { Graph.prefs.update { it.copy(minFilterSeconds = sec) } },
                            label = { Text(formatDuration(sec * 1000L)) },
                        )
                    }
                }
            }
            item {
                ListItem(
                    headlineContent = { Text("Ongoing notification") },
                    supportingContent = { Text("Live timer with Stop and suggested switches") },
                    trailingContent = {
                        Switch(s.notificationEnabled, onCheckedChange = { v ->
                            Graph.prefs.update { it.copy(notificationEnabled = v) }
                            if (v && Build.VERSION.SDK_INT >= 33 && !TrackingNotification.canPost(ctx)) {
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            Graph.tracker.refreshSurfaces()
                        })
                    },
                )
            }
            item { HorizontalDivider() }
            item {
                ListItem(
                    headlineContent = { Text("Export all data (CSV)") },
                    supportingContent = { Text("Share via email, Drive, etc. Per-period export is on the Analysis tab.") },
                    modifier = Modifier.clickable { scope.launch { CsvExport.share(ctx, null, null, "all data") } },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("NFC tags") },
                    supportingContent = {
                        Text("Open an activity and tap “Write NFC tag”. Tapping the tag later toggles that activity, even when the app is closed.")
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Privacy") },
                    supportingContent = { Text("All data stays on this device. The app has no internet permission and no cloud backup.") },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Activities

@Composable
fun ActivitiesScreen(onBack: () -> Unit, push: (Route) -> Unit) {
    val all by remember { Graph.dao.allActivities() }.collectAsState(emptyList())
    val groups by remember { Graph.dao.groups() }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var groupFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    val active = all.filter { !it.archived }
    val archived = all.filter { it.archived }
    val sections = browseSections(active, groups, query, groupFilter, sectioned = true)

    fun move(a: ActivityItem, delta: Int) {
        val reordered = moveWithinGroup(active, a, delta)
        if (reordered != active) scope.launch { Graph.tracker.reorderActivities(reordered) }
    }

    SubScreen(
        "Activities", onBack,
        fab = { push(Route.EditActivity(null, groupFilter?.takeIf { it != OTHER_GROUP })) },
    ) { p ->
        Column(Modifier.padding(top = p.calculateTopPadding())) {
            SearchField(query, { query = it }, Modifier.padding(horizontal = 12.dp))
            if (query.isBlank() && groups.isNotEmpty()) {
                GroupFilterRow(groups, hasUngrouped(active, groups), groupFilter, { groupFilter = it }, Modifier.padding(top = 6.dp))
            }
            LazyColumn(contentPadding = PaddingValues(bottom = p.calculateBottomPadding() + 88.dp)) {
                sections.forEach { section ->
                    if (query.isBlank() && groupFilter == null && (section.group != null || sections.size > 1)) {
                        item(key = "h${section.group?.id ?: OTHER_GROUP}") { SectionHeader(section.group) }
                    }
                    items(section.activities, key = { it.id }) { a ->
                        val pos = section.activities.indexOf(a)
                        ListItem(
                            modifier = Modifier.clickable { push(Route.EditActivity(a.id)) },
                            leadingContent = { IconBadge(a.icon, a.color) },
                            headlineContent = { Text(a.name) },
                            trailingContent = {
                                // Reorder within the group (hidden while searching, where order isn't meaningful).
                                if (query.isBlank()) Row {
                                    IconButton(onClick = { move(a, -1) }, enabled = pos > 0) { Icon(Lucide.ChevronUp, "Move up") }
                                    IconButton(onClick = { move(a, 1) }, enabled = pos < section.activities.lastIndex) {
                                        Icon(Lucide.ChevronDown, "Move down")
                                    }
                                }
                            },
                        )
                    }
                }
                if (archived.isNotEmpty() && query.isBlank() && groupFilter == null) {
                    item { SectionTitle("Archived", Modifier.padding(horizontal = 16.dp)) }
                    items(archived, key = { it.id }) { a ->
                        ListItem(
                            modifier = Modifier.clickable { push(Route.EditActivity(a.id)) },
                            leadingContent = { IconBadge(a.icon, a.color) },
                            headlineContent = { Text(a.name, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityEditorScreen(id: Long?, initialGroupId: Long? = null, onBack: () -> Unit) {
    val goals by remember { Graph.dao.goals() }.collectAsState(emptyList())
    val groups by remember { Graph.dao.groups() }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<ActivityItem?>(null) }
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("Star") }
    var color by remember { mutableIntStateOf(Palette[5]) }
    var groupId by remember { mutableStateOf(initialGroupId) }
    var goalIds by remember { mutableStateOf(emptySet<Long>()) }
    var archived by remember { mutableStateOf(false) }
    var sessionCount by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var writingNfc by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != null) Graph.dao.activity(id)?.let { a ->
            original = a; name = a.name; icon = a.icon; color = a.color; groupId = a.groupId; archived = a.archived
            goalIds = Graph.dao.goalIdsFor(a.id).toSet()
            sessionCount = Graph.dao.sessionCount(a.id)
        }
    }

    SubScreen(
        if (id == null) "New activity" else "Edit activity", onBack,
        actions = {
            if (id != null) IconButton(onClick = { confirmDelete = true }) { Icon(Lucide.Trash2, "Delete") }
        },
    ) { p ->
        Column(Modifier.padding(p).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, color, 56.dp)
                OutlinedTextField(
                    name, { name = it }, label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
            }
            SectionTitle("Icon")
            IconPicker(icon, color) { icon = it }
            SectionTitle("Color")
            ColorPicker(color) { color = it }
            SectionTitle("Group")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(groupId == null, { groupId = null }, label = { Text("None") })
                groups.forEach { g -> FilterChip(groupId == g.id, { groupId = g.id }, label = { Text(g.name) }) }
            }
            if (goals.isNotEmpty()) {
                SectionTitle("Counts toward goals")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    goals.forEach { g ->
                        FilterChip(
                            g.id in goalIds,
                            { goalIds = if (g.id in goalIds) goalIds - g.id else goalIds + g.id },
                            label = { Text(g.name) },
                        )
                    }
                }
            }
            if (id != null) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Archived")
                        Text(
                            "Hidden from the grid and widgets; history is kept",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(archived, { archived = it })
                }
                OutlinedButton(onClick = { writingNfc = true }, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Write NFC tag")
                }
            }
            Button(
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                onClick = {
                    scope.launch {
                        val base = original ?: ActivityItem(name = "", icon = "", color = 0)
                        Graph.tracker.saveActivity(
                            base.copy(name = name.trim(), icon = icon, color = color, groupId = groupId, archived = archived),
                            goalIds,
                        )
                        onBack()
                    }
                },
            ) { Text("Save") }
        }
    }

    if (confirmDelete && id != null) {
        ConfirmDialog(
            "Delete “$name”?",
            "This also deletes its $sessionCount log entries and its suggestion rules. Archive it instead to keep history.",
            "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = { scope.launch { Graph.tracker.deleteActivity(id); onBack() } },
        )
    }
    if (writingNfc && id != null) NfcWriteDialog(Links.activity(id, toggle = true)) { writingNfc = false }
}

@Composable
private fun NfcWriteDialog(uri: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val activity = remember { ctx.findActivity() }
    val adapter = remember { NfcAdapter.getDefaultAdapter(ctx) }
    var status by remember {
        mutableStateOf(
            when {
                adapter == null -> "This device has no NFC."
                !adapter.isEnabled -> "NFC is turned off."
                else -> "Hold an NFC tag against the back of your phone…"
            },
        )
    }
    var done by remember { mutableStateOf(false) }
    DisposableEffect(adapter, activity) {
        if (adapter != null && adapter.isEnabled && activity != null) {
            NfcTags.startWriting(activity, adapter, uri) { ok, msg -> status = msg; done = ok }
        }
        onDispose { activity?.let { NfcTags.stop(it, adapter) } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Write NFC tag") },
        text = { Text(status) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (done) "Done" else "Cancel") } },
        dismissButton = {
            if (adapter != null && !adapter.isEnabled) {
                TextButton(onClick = { ctx.startActivity(Intent(SysSettings.ACTION_NFC_SETTINGS)) }) { Text("Open settings") }
            }
        },
    )
}

// ---------------------------------------------------------------- Goals

@Composable
fun GoalsManageScreen(onBack: () -> Unit) {
    val goals by remember { Graph.dao.goals() }.collectAsState(emptyList())
    val links by remember { Graph.dao.goalLinks() }.collectAsState(emptyList())
    val acts by remember { Graph.dao.allActivities() }.collectAsState(emptyList())
    val actName = acts.associate { it.id to it.name }
    var editing by remember { mutableStateOf<Goal?>(null) }
    SubScreen("Goals", onBack, fab = { editing = Goal(name = "", color = Palette[9]) }) { p ->
        LazyColumn(contentPadding = p) {
            if (goals.isEmpty()) item {
                Text(
                    "A goal is a daily target (or limit) over the activities you pick — e.g. “Screen time ≤ 2h” " +
                        "tracking only TV, even though Reading is in the same group.",
                    Modifier.padding(16.dp),
                )
            }
            items(goals, key = { it.id }) { g ->
                ListItem(
                    modifier = Modifier.clickable { editing = g },
                    leadingContent = { Box(Modifier.size(20.dp).clip(CircleShape).background(Color(g.color))) },
                    headlineContent = { Text(g.name) },
                    supportingContent = {
                        Text(
                            when {
                                g.targetMinutes == 0 -> "No target"
                                g.isLimit -> "Limit ${formatDuration(g.targetMinutes * 60_000L)} / day"
                                else -> "Target ${formatDuration(g.targetMinutes * 60_000L)} / day"
                            } + " · " + links.filter { it.goalId == g.id }.mapNotNull { actName[it.activityId] }
                                .ifEmpty { listOf("no activities") }.joinToString(", "),
                        )
                    },
                )
            }
        }
    }
    val groups by remember { Graph.dao.groups() }.collectAsState(emptyList())
    editing?.let { GoalDialog(it, acts.filter { a -> !a.archived }, groups) { editing = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalDialog(initial: Goal, activities: List<ActivityItem>, groups: List<Group>, onDismiss: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var tracked by remember { mutableStateOf(emptySet<Long>()) }
    LaunchedEffect(initial.id) { if (initial.id != 0L) tracked = Graph.dao.activityIdsFor(initial.id).toSet() }
    var name by remember { mutableStateOf(initial.name) }
    var color by remember { mutableIntStateOf(initial.color) }
    var target by remember { mutableIntStateOf(initial.targetMinutes) }
    var limit by remember { mutableStateOf(initial.isLimit) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New goal" else "Edit goal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                SectionTitle("Color")
                ColorPicker(color) { color = it }
                SectionTitle("Daily target")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = { target = (target - 15).coerceAtLeast(0) }) { Text("−15") }
                    Text(
                        if (target == 0) "None" else formatDuration(target * 60_000L),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    FilledTonalButton(onClick = { target += 15 }) { Text("+15") }
                }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Treat as a limit (stay under)", Modifier.weight(1f))
                    Switch(limit, { limit = it })
                }
                Spacer(Modifier.height(12.dp))
                val names = activities.filter { it.id in tracked }.map { it.name }
                PickerField(
                    "Tracks", null, onClick = { picking = true },
                    summary = if (names.isEmpty()) "Choose activities…" else "${names.size}: " + names.joinToString(", "),
                )
                if (initial.id != 0L) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete goal", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                scope.launch {
                    Graph.tracker.saveGoal(
                        initial.copy(name = name.trim(), color = color, targetMinutes = target, isLimit = limit),
                        tracked,
                    )
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (picking) {
        ActivityPickerSheet(
            "Goal tracks", activities, groups, tracked.toList(), multi = true,
            onDismiss = { picking = false }, onConfirm = { tracked = it.toSet() },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete “${initial.name}”?", "Only the goal is removed; activities and log entries stay.", "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = { scope.launch { Graph.tracker.deleteGoal(initial.id); onDismiss() } },
        )
    }
}

// ---------------------------------------------------------------- Groups

@Composable
fun GroupsManageScreen(onBack: () -> Unit) {
    val groups by remember { Graph.dao.groups() }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val acts by remember { Graph.dao.allActivities() }.collectAsState(emptyList())
    var editing by remember { mutableStateOf<Group?>(null) }
    SubScreen("Groups", onBack, fab = { editing = Group(name = "", color = Palette[5]) }) { p ->
        LazyColumn(contentPadding = p) {
            if (groups.isEmpty()) item {
                Text("Groups organize activities into sections on the Track tab, widgets and analysis.", Modifier.padding(16.dp))
            }
            items(groups.size, key = { groups[it].id }) { i ->
                val g = groups[i]
                ListItem(
                    modifier = Modifier.clickable { editing = g },
                    leadingContent = { Box(Modifier.size(20.dp).clip(CircleShape).background(Color(g.color))) },
                    headlineContent = { Text(g.name) },
                    supportingContent = {
                        Text(
                            acts.filter { it.groupId == g.id && !it.archived }.joinToString(", ") { it.name }.ifEmpty { "Empty" },
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { scope.launch { Graph.tracker.moveGroup(groups, i, -1) } }, enabled = i > 0) {
                                Icon(Lucide.ChevronUp, "Move up")
                            }
                            IconButton(onClick = { scope.launch { Graph.tracker.moveGroup(groups, i, 1) } }, enabled = i < groups.lastIndex) {
                                Icon(Lucide.ChevronDown, "Move down")
                            }
                        }
                    },
                )
            }
        }
    }
    editing?.let { GroupDialog(it) { editing = null } }
}

@Composable
private fun GroupDialog(initial: Group, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial.name) }
    var color by remember { mutableIntStateOf(initial.color) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New group" else "Edit group") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                SectionTitle("Color")
                ColorPicker(color) { color = it }
                Text(
                    "Assign activities to this group from each activity's edit page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (initial.id != 0L) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete group", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                scope.launch {
                    Graph.tracker.saveGroup(initial.copy(name = name.trim(), color = color))
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (confirmDelete) {
        ConfirmDialog(
            "Delete “${initial.name}”?", "Its activities move to “Other”; nothing else is deleted.", "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = { scope.launch { Graph.tracker.deleteGroup(initial.id); onDismiss() } },
        )
    }
}

// ---------------------------------------------------------------- Rules

private val DayLetters = listOf("M", "T", "W", "T", "F", "S", "S")

private fun hm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

/** "22:00 – 02:00 · every day" for time rules, "While Work is running" for activity rules. */
private fun Rule.triggerText(byId: Map<Long, ActivityItem>): String {
    afterActivityId?.let { return "While ${byId[it]?.name ?: "(deleted)"} is running" }
    val time = if (startMinute == endMinute) "All day" else TimeFmt.range(hm(startMinute), hm(endMinute))
    val d = when (days) {
        Rule.ALL_DAYS -> "every day"
        0b0011111 -> "weekdays"
        0b1100000 -> "weekends"
        else -> DayLetters.filterIndexed { i, _ -> days and (1 shl i) != 0 }.joinToString(" ")
    }
    return "$time · $d"
}

@Composable
fun RulesScreen(onBack: () -> Unit) {
    val rules by remember { Graph.dao.rules() }.collectAsState(emptyList())
    val acts by remember { Graph.dao.allActivities() }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val byId = acts.associateBy { it.id }
    var editing by remember { mutableStateOf<Rule?>(null) }
    SubScreen(
        "Smart suggestions", onBack,
        fab = { editing = Rule(suggestIds = emptyList(), startMinute = 22 * 60, endMinute = 6 * 60) },
    ) { p ->
        LazyColumn(contentPadding = p) {
            item {
                Text(
                    "The Track tab, widgets, quick pick and notification suggest activities from rules that match now — " +
                        "“while X is running” rules first — then fill in with what you usually do around this time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(rules, key = { it.id }) { r ->
                val trigger = r.afterActivityId?.let { byId[it] }
                ListItem(
                    modifier = Modifier.clickable { editing = r },
                    leadingContent = {
                        if (trigger != null) {
                            IconBadge(trigger.icon, trigger.color)
                        } else {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Lucide.Clock, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                        }
                    },
                    headlineContent = { Text(r.triggerText(byId)) },
                    supportingContent = {
                        Text("Suggest: " + r.suggestIds.mapNotNull { byId[it]?.name }.ifEmpty { listOf("nothing") }.joinToString(", "))
                    },
                    trailingContent = {
                        Switch(r.enabled, { v -> scope.launch { Graph.tracker.saveRule(r.copy(enabled = v)) } })
                    },
                )
            }
        }
    }
    val groups by remember { Graph.dao.groups() }.collectAsState(emptyList())
    editing?.let { RuleDialog(it, acts.filter { a -> !a.archived }, groups) { editing = null } }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RuleDialog(initial: Rule, acts: List<ActivityItem>, groups: List<Group>, onDismiss: () -> Unit) {
    var pickTrigger by remember { mutableStateOf(false) }
    var pickSuggest by remember { mutableStateOf(false) }
    val actById = acts.associateBy { it.id }
    val scope = rememberCoroutineScope()
    var byActivity by remember { mutableStateOf(initial.isActivityTrigger) }
    var after by remember { mutableStateOf(initial.afterActivityId) }
    var suggestIds by remember { mutableStateOf(initial.suggestIds) }
    var start by remember { mutableIntStateOf(initial.startMinute) }
    var end by remember { mutableIntStateOf(initial.endMinute) }
    var days by remember { mutableIntStateOf(initial.days) }
    var picking by remember { mutableStateOf<String?>(null) }
    val valid = suggestIds.isNotEmpty() && if (byActivity) after != null else days != 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New rule" else "Edit rule") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("Time of day", "After activity").forEachIndexed { i, l ->
                        SegmentedButton(
                            selected = byActivity == (i == 1), onClick = { byActivity = i == 1 },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(l) }
                    }
                }
                if (byActivity) {
                    Spacer(Modifier.height(12.dp))
                    PickerField("While this is running", after?.let { actById[it] }, onClick = { pickTrigger = true })
                } else {
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("From")
                        TextButton(onClick = { picking = "start" }) { Text(hm(start), style = MaterialTheme.typography.titleMedium) }
                        Text("to")
                        TextButton(onClick = { picking = "end" }) { Text(hm(end), style = MaterialTheme.typography.titleMedium) }
                    }
                    Text(
                        "Same start and end = all day. Windows can cross midnight.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("On", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayLetters.forEachIndexed { i, l ->
                            FilterChip(days and (1 shl i) != 0, { days = days xor (1 shl i) }, label = { Text(l) })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                PickerField(
                    "Suggest (in order)", null, onClick = { pickSuggest = true },
                    summary = suggestIds.mapNotNull { actById[it]?.name }
                        .mapIndexed { i, n -> "${i + 1}. $n" }.joinToString("  ").ifEmpty { "Choose…" },
                )
                if (initial.id != 0L) {
                    TextButton(onClick = { scope.launch { Graph.tracker.deleteRule(initial.id); onDismiss() } }) {
                        Text("Delete rule", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                scope.launch {
                    Graph.tracker.saveRule(
                        if (byActivity) {
                            initial.copy(suggestIds = suggestIds, afterActivityId = after, days = Rule.ALL_DAYS, startMinute = 0, endMinute = 0)
                        } else {
                            initial.copy(suggestIds = suggestIds, afterActivityId = null, days = days, startMinute = start, endMinute = end)
                        },
                    )
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickTrigger) {
        ActivityPickerSheet(
            "While this is running", acts, groups, listOfNotNull(after), multi = false,
            onDismiss = { pickTrigger = false },
            onConfirm = { ids -> after = ids.first(); suggestIds = suggestIds - ids.first() },
        )
    }
    if (pickSuggest) {
        ActivityPickerSheet(
            "Suggest (tap in order)", acts.filter { !byActivity || it.id != after }, groups, suggestIds, multi = true,
            showOrder = true, onDismiss = { pickSuggest = false }, onConfirm = { suggestIds = it },
        )
    }
    picking?.let { which ->
        val m = if (which == "start") start else end
        TimePickDialog(m / 60, m % 60, onDismiss = { picking = null }) { h, min ->
            if (which == "start") start = h * 60 + min else end = h * 60 + min
        }
    }
}
