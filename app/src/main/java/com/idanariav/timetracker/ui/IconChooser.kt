package com.idanariav.timetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.idanariav.timetracker.domain.IconCatalogData
import com.idanariav.timetracker.domain.searchIcons

@Composable
private fun rememberIconCatalog(): IconCatalogData? {
    val ctx = LocalContext.current
    var data by remember { mutableStateOf<IconCatalogData?>(null) }
    LaunchedEffect(Unit) { data = IconCatalog.load(ctx) }
    return data
}

/** One tappable icon cell; the selected one is filled with the activity color. */
@Composable
private fun IconCell(name: String, selected: Boolean, color: Int, onClick: () -> Unit) {
    val c = Color(color)
    Box(
        Modifier.size(44.dp).clip(CircleShape)
            .background(if (selected) c else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            activityIcon(name), name, Modifier.size(22.dp),
            tint = if (selected) onColor(c) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Icon choice for the activity editor: up to 14 icons matching the activity's [activityName]
 * (by icon name and Lucide tags; the common set while the name is empty) plus "Browse all".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconChooser(activityName: String, selected: String, color: Int, onSelect: (String) -> Unit) {
    val catalog = rememberIconCatalog()
    var browsing by remember { mutableStateOf(false) }
    val matches = catalog?.let { searchIcons(it.icons, activityName) }.orEmpty()
    // Current icon first, then name matches, topped up with common icons.
    val suggestions = (listOf(selected) + (if (activityName.isBlank()) emptyList() else matches) + CommonIcons)
        .distinct().take(14)
    Column {
        if (activityName.isNotBlank() && matches.isNotEmpty()) {
            Text(
                "Suggested for “${activityName.trim()}”",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            suggestions.forEach { n -> IconCell(n, n == selected, color) { onSelect(n) } }
        }
        OutlinedButton(onClick = { browsing = true }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Browse all ${LucideCatalog.COUNT} icons")
        }
    }
    if (browsing) {
        IconPickerSheet(selected, color, onDismiss = { browsing = false }, onPick = onSelect)
    }
}

private const val COMMON = "_common"
private const val ALL = "_all"

/** Full Lucide browser: search (names + tags), category chips, grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerSheet(selected: String, color: Int, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val catalog = rememberIconCatalog()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(COMMON) }
    val icons = catalog?.icons.orEmpty()
    val categoryId = category.takeIf { it != COMMON && it != ALL }
    val results = when {
        query.isNotBlank() -> searchIcons(icons, query) // search always spans every icon
        category == COMMON -> CommonIcons
        category == ALL -> icons.map { it.name }
        else -> searchIcons(icons, "", categoryId)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.92f)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Icon", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(if (results.size == 1) "1 icon" else "${results.size} icons", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SearchField(query, { query = it }, Modifier.padding(horizontal = 12.dp), placeholder = "Search icons, e.g. money")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                // While searching, results cover everything, so "All" shows as active; picking a chip clears the search.
                val searching = query.isNotBlank()
                fun pick(c: String) { category = c; query = "" }
                item { FilterChip(!searching && category == COMMON, { pick(COMMON) }, label = { Text("Common") }) }
                item { FilterChip(searching || category == ALL, { pick(ALL) }, label = { Text("All") }) }
                items(catalog?.categories.orEmpty(), key = { it.first }) { (id, title) ->
                    FilterChip(!searching && category == id, { pick(if (category == id) ALL else id) }, label = { Text(title) })
                }
            }
            if (results.isEmpty()) {
                Text("No icon matches", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyVerticalGrid(
                GridCells.Adaptive(52.dp),
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(results, key = { it }) { n ->
                    Box(Modifier.padding(2.dp), contentAlignment = Alignment.Center) {
                        IconCell(n, n == selected, color) { onPick(n); onDismiss() }
                    }
                }
            }
        }
    }
}
