package com.idanariav.timetracker.ui

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Grid3x3
import androidx.compose.runtime.collectAsState
import com.composables.icons.lucide.Timer
import com.composables.icons.lucide.Target
import com.composables.icons.lucide.List
import com.composables.icons.lucide.ChartColumn
import com.composables.icons.lucide.Settings
import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.idanariav.timetracker.notify.TrackingNotification
import com.idanariav.timetracker.tracking.Graph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { AppRoot() } }
    }

    override fun onResume() {
        super.onResume()
        TimeFmt.init(this) // pick up 12/24h changes
    }
}

sealed interface Route {
    data object Settings : Route
    data object Activities : Route
    data class EditActivity(val id: Long?, val groupId: Long? = null) : Route
    data object Goals : Route
    data object Groups : Route
    data object Rules : Route
}

@Composable
private fun AppRoot() {
    val stack = remember { mutableStateListOf<Route>() }
    val push: (Route) -> Unit = { stack.add(it) }
    val pop: () -> Unit = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.isNotEmpty(), onBack = pop)
    RequestNotificationPermission()

    when (val top = stack.lastOrNull()) {
        null -> MainTabs(push)
        Route.Settings -> SettingsScreen(onBack = pop, push = push)
        Route.Activities -> ActivitiesScreen(onBack = pop, push = push)
        is Route.EditActivity -> ActivityEditorScreen(top.id, top.groupId, onBack = pop)
        Route.Goals -> GoalsManageScreen(onBack = pop)
        Route.Groups -> GroupsManageScreen(onBack = pop)
        Route.Rules -> RulesScreen(onBack = pop)
    }
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Graph.tracker.refreshSurfaces()
    }
    LaunchedEffect(Unit) {
        if (Graph.prefs.settings.value.notificationEnabled && !TrackingNotification.canPost(ctx)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private data class Tab(val title: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("Time Tracker", "Track", Lucide.Timer),
    Tab("Goals", "Goals", Lucide.Target),
    Tab("Log", "Log", Lucide.List),
    Tab("Analysis", "Analysis", Lucide.ChartColumn),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTabs(push: (Route) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tabs[tab].title) },
                actions = {
                    if (tab == 0) {
                        val compact = Graph.prefs.settings.collectAsState().value.compactGrid
                        IconButton(onClick = { Graph.prefs.update { it.copy(compactGrid = !compact) } }) {
                            Icon(
                                if (compact) Lucide.LayoutGrid else Lucide.Grid3x3,
                                if (compact) "Larger tiles" else "Smaller tiles",
                            )
                        }
                    }
                    IconButton(onClick = { push(Route.Settings) }) { Icon(Lucide.Settings, "Settings") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (tab) {
            0 -> TrackScreen(padding, snackbar, onEditActivity = { id, groupId -> push(Route.EditActivity(id, groupId)) })
            1 -> GoalsScreen(padding, onManage = { push(Route.Goals) })
            2 -> LogScreen(padding)
            else -> AnalysisScreen(padding)
        }
    }
}
