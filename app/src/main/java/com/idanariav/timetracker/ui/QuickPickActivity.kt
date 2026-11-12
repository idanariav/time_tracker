package com.idanariav.timetracker.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.idanariav.timetracker.data.ActivityItem
import com.idanariav.timetracker.domain.formatElapsed
import com.idanariav.timetracker.domain.groupActivities
import com.idanariav.timetracker.domain.filterActivities
import com.idanariav.timetracker.tracking.Graph
import kotlinx.coroutines.launch

/** Floating picker opened by the 1×1 widget: current activity, top 3 suggestions, searchable full list. */
class QuickPickActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { QuickPick(onDone = { finish() }) } }
    }
}

@Composable
private fun QuickPick(onDone: () -> Unit) {
    val all by remember { Graph.dao.allActivities() }.collectAsState(emptyList())
    val groups by remember { Graph.dao.groups() }.collectAsState(emptyList())
    val running by remember { Graph.dao.running() }.collectAsState(null)
    val suggestions by remember { Graph.suggestionFlow(3) }.collectAsState(emptyList())
    val settings by Graph.prefs.settings.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val current = all.firstOrNull { it.id == running?.activityId }
    val active = all.filter { !it.archived }

    fun pick(a: ActivityItem) = scope.launch {
        Graph.tracker.switchTo(a.id)
        Toast.makeText(ctx.applicationContext, "${a.name} started", Toast.LENGTH_SHORT).show()
        onDone()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDone)
            .systemBarsPadding().imePadding().padding(12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null) {},
        ) {
            Column(Modifier.padding(16.dp)) {
                // Current
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (running != null && current != null) {
                        IconBadge(current.icon, current.color, 40.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(current.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            val now = rememberNow(1000)
                            Text(
                                formatElapsed(now - running!!.startTime),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { scope.launch { Graph.tracker.stop(); onDone() } }) { Text("Stop") }
                    } else {
                        Text("Not tracking", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    }
                }

                // While the full list is open it gets the space (suggestions are in it anyway).
                if (suggestions.isNotEmpty() && !expanded) {
                    Text(
                        "Suggested", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    suggestions.forEach { a -> PickRow(a, big = true) { pick(a) } }
                }

                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("All activities", modifier = Modifier.weight(1f))
                    Icon(if (expanded) Lucide.ChevronUp else Lucide.ChevronDown, null)
                }

                if (expanded) {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focus.requestFocus() }
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("Search") },
                        leadingIcon = { Icon(Lucide.Search, null, Modifier.size(20.dp)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    val q = query.trim()
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp).padding(top = 8.dp)) {
                        if (q.isNotEmpty()) {
                            val hits = filterActivities(active, groups, q)
                            if (hits.isEmpty()) item { Text("No match", Modifier.padding(12.dp)) }
                            items(hits, key = { it.id }) { a -> PickRow(a, big = false) { pick(a) } }
                        } else {
                            groupActivities(active, groups, settings.groupActivities).forEach { section ->
                                if (section.group != null || groups.isNotEmpty() && settings.groupActivities) {
                                    item(key = "g${section.group?.id ?: -1}") {
                                        Text(
                                            section.group?.name ?: "Other",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
                                        )
                                    }
                                }
                                items(section.activities, key = { it.id }) { a -> PickRow(a, big = false) { pick(a) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickRow(a: ActivityItem, big: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = if (big) 8.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(a.icon, a.color, if (big) 40.dp else 30.dp)
        Text(
            a.name,
            style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (big) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
