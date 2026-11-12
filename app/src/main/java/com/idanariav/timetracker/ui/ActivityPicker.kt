package com.idanariav.timetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.idanariav.timetracker.data.ActivityItem
import com.idanariav.timetracker.data.Group
import com.idanariav.timetracker.domain.OTHER_GROUP
import com.idanariav.timetracker.domain.Section
import com.idanariav.timetracker.domain.filterActivities
import com.idanariav.timetracker.domain.filterByGroup
import com.idanariav.timetracker.domain.groupActivities
import com.idanariav.timetracker.domain.hasUngrouped

/** Horizontally scrolling "All · group · group · … · Other" filter. `null` = All. Tapping the selected group clears it. */
@Composable
fun GroupFilterRow(
    groups: List<Group>,
    showOther: Boolean,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "all") { FilterChip(selected == null, { onSelect(null) }, label = { Text("All") }) }
        items(groups, key = { it.id }) { g ->
            FilterChip(
                selected == g.id, { onSelect(if (selected == g.id) null else g.id) },
                label = { Text(g.name) },
                leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(Color(g.color))) },
            )
        }
        if (showOther) {
            item(key = "other") {
                FilterChip(
                    selected == OTHER_GROUP, { onSelect(if (selected == OTHER_GROUP) null else OTHER_GROUP) },
                    label = { Text("Other") },
                )
            }
        }
    }
}

/** Sections to show for a search query (global, flat) or a group filter (sectioned only under "All"). */
fun browseSections(activities: List<ActivityItem>, groups: List<Group>, query: String, groupFilter: Long?, sectioned: Boolean): List<Section> =
    if (query.isNotBlank()) {
        listOf(Section(null, filterActivities(activities, groups, query)))
    } else {
        groupActivities(filterByGroup(activities, groups, groupFilter), groups, sectioned && groupFilter == null)
    }

@Composable
fun SectionHeader(group: Group?, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(group?.color ?: 0xFF9E9E9E.toInt())))
        Text(
            group?.name ?: "Other",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        trailing()
    }
}

/**
 * Search + group chips + grouped list. Single mode: rows show a check on the selected one.
 * Multi mode: checkboxes (or order numbers with [showOrder]) and per-group All/None.
 */
@Composable
fun ActivityBrowser(
    activities: List<ActivityItem>,
    groups: List<Group>,
    selectedIds: List<Long>,
    multi: Boolean,
    onToggle: (ActivityItem) -> Unit,
    modifier: Modifier = Modifier,
    listModifier: Modifier = Modifier,
    showOrder: Boolean = false,
    onSetMany: ((List<ActivityItem>, Boolean) -> Unit)? = null,
    autoFocus: Boolean = false,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var groupFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    val sections = browseSections(activities, groups, query, groupFilter, sectioned = true)

    Column(modifier) {
        SearchField(
            query, { query = it },
            modifier = Modifier.padding(horizontal = 12.dp).focusRequester(focus),
            onDone = { if (!multi) sections.singleOrNull()?.activities?.singleOrNull()?.let(onToggle) },
        )
        if (query.isBlank() && groups.isNotEmpty()) {
            GroupFilterRow(
                groups, hasUngrouped(activities, groups), groupFilter, { groupFilter = it },
                Modifier.padding(top = 6.dp),
            )
        }
        LazyColumn(listModifier) {
            if (sections.all { it.activities.isEmpty() }) {
                item { Text("No activity matches", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            sections.forEach { section ->
                if (query.isBlank() && groupFilter == null && (section.group != null || sections.size > 1)) {
                    item(key = "h${section.group?.id ?: OTHER_GROUP}") {
                        SectionHeader(section.group) {
                            if (multi && onSetMany != null) {
                                val all = section.activities.all { it.id in selectedIds }
                                TextButton(onClick = { onSetMany(section.activities, !all) }) { Text(if (all) "None" else "All") }
                            }
                        }
                    }
                }
                items(section.activities, key = { it.id }) { a ->
                    PickerRow(a, selectedIds.indexOf(a.id), multi, showOrder) { onToggle(a) }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(a: ActivityItem, index: Int, multi: Boolean, showOrder: Boolean, onClick: () -> Unit) {
    val selected = index >= 0
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(a.icon, a.color, 32.dp)
        Text(
            a.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected && !multi) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        when {
            multi && showOrder && selected -> Box(
                Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) { Text("${index + 1}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge) }
            multi -> Checkbox(checked = selected, onCheckedChange = null)
            selected -> Icon(Lucide.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Bottom sheet around [ActivityBrowser]. Single mode returns on tap; multi mode returns the
 * selection (in tap order) on Done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityPickerSheet(
    title: String,
    activities: List<ActivityItem>,
    groups: List<Group>,
    selectedIds: List<Long>,
    multi: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit,
    showOrder: Boolean = false,
) {
    var sel by remember { mutableStateOf(selectedIds) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.92f)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (multi) {
                    Text("${sel.size} selected", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { onConfirm(sel); onDismiss() }) { Text("Done") }
                }
            }
            ActivityBrowser(
                activities, groups, sel, multi,
                onToggle = { a ->
                    if (multi) sel = if (a.id in sel) sel - a.id else sel + a.id
                    else { onConfirm(listOf(a.id)); onDismiss() }
                },
                showOrder = showOrder,
                onSetMany = { list, on ->
                    val ids = list.map { it.id }
                    sel = if (on) sel + ids.filter { it !in sel } else sel - ids.toSet()
                },
                listModifier = Modifier.fillMaxSize().padding(top = 4.dp),
            )
        }
    }
}

/** Outlined, tappable field showing a chosen activity (or a summary text) with a chevron. */
@Composable
fun PickerField(label: String, activity: ActivityItem?, onClick: () -> Unit, modifier: Modifier = Modifier, summary: String? = null) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (activity != null) IconBadge(activity.icon, activity.color, 32.dp)
        Column(Modifier.weight(1f).padding(horizontal = if (activity != null) 12.dp else 0.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                summary ?: activity?.name ?: "Choose…",
                style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Lucide.ChevronDown, null)
    }
}
