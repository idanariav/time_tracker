package com.idanariav.timetracker.widget

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import com.idanariav.timetracker.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColor
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.layout.wrapContentWidth
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Timer
import com.idanariav.timetracker.data.ActivityItem
import com.idanariav.timetracker.data.Group
import com.idanariav.timetracker.data.Session
import com.idanariav.timetracker.domain.groupActivities
import com.idanariav.timetracker.domain.filterByGroup
import com.idanariav.timetracker.tracking.Graph
import com.idanariav.timetracker.ui.MainActivity
import com.idanariav.timetracker.ui.QuickPickActivity
import com.idanariav.timetracker.ui.TimeFmt
import com.idanariav.timetracker.ui.iconBitmap
import com.idanariav.timetracker.ui.vectorBitmap

suspend fun refreshWidgets(context: Context) {
    ActivityWidget().updateAll(context)
    SmartWidget().updateAll(context)
    QuickWidget().updateAll(context)
}

/** Per-widget settings, stored in each widget's Glance state (written by [WidgetConfigActivity]). */
object WidgetPrefs {
    val OPACITY = intPreferencesKey("opacity") // 0..100
    val COMPACT = booleanPreferencesKey("compact")
    val GROUPED = booleanPreferencesKey("grouped")
    /** Comma-separated group ids to show (Activities widget); empty = all groups. -1 = ungrouped. */
    val GROUP_IDS = stringPreferencesKey("groupIds")
    const val DEFAULT_OPACITY = 85
}

private val ActivityIdKey = ActionParameters.Key<Long>("activityId")

class SwitchActivityAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        parameters[ActivityIdKey]?.let { Graph.tracker.switchTo(it) }
    }
}

class StopAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Graph.tracker.stop()
    }
}

private fun switchAction(id: Long) = actionRunCallback<SwitchActivityAction>(actionParametersOf(ActivityIdKey to id))

private fun contentColor(bg: Color) = ColorProvider(if (bg.luminance() > 0.5f) Color.Black else Color.White)

/** Neutral widget surface at the chosen opacity, light/dark aware. */
private fun surface(opacity: Int): ColorProvider {
    val a = opacity.coerceIn(0, 100) / 100f
    return DayNightColor(day = Color(0xFFF4EFF4).copy(alpha = a), night = Color(0xFF1C1B1F).copy(alpha = a))
}

private class Tiles(val height: Int, val icon: Int, val text: Int, val cell: Float)

private val Normal = Tiles(height = 58, icon = 22, text = 11, cell = 76f)
private val Compact = Tiles(height = 44, icon = 18, text = 9, cell = 54f)

/** Lucide icon rendered once in white, then tinted by Glance. */
@Composable
private fun WidgetIcon(icon: String, tint: ColorProvider, sizeDp: Int) {
    Image(
        provider = ImageProvider(iconBitmap(icon, android.graphics.Color.WHITE, 64)),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = GlanceModifier.size(sizeDp.dp),
    )
}

@Composable
private fun WidgetFrame(opacity: Int, content: @Composable () -> Unit) {
    Column(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(16.dp)
            .background(surface(opacity)).padding(8.dp),
    ) { content() }
}

/**
 * Live elapsed time since [startTime]. A RemoteViews Chronometer ticks on the launcher side, so
 * the count stays right between (rate-limited) widget updates. Its base is on the elapsedRealtime
 * clock, derived from wall-clock elapsed time at render; a reboot re-renders widgets (BootReceiver).
 */
@Composable
private fun ElapsedChronometer(startTime: Long) {
    val context = LocalContext.current
    val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - startTime)
    val views = RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
        setChronometer(R.id.chrono, base, null, true)
    }
    // Without an explicit wrap, the embedded view fills the row and squeezes out the name/Stop.
    AndroidRemoteViews(views, GlanceModifier.wrapContentWidth().wrapContentHeight())
}

@Composable
private fun CurrentHeader(running: Session?, current: ActivityItem?) {
    Row(GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (running != null && current != null) {
            WidgetIcon(current.icon, GlanceTheme.colors.onSurface, 18)
            Spacer(GlanceModifier.width(6.dp))
        }
        Text(
            text = if (running != null && current != null) current.name else "Not tracking",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
        )
        if (running != null) {
            ElapsedChronometer(running.startTime)
            Text(
                " · since ${TimeFmt.clock(running.startTime)}",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                "■ Stop",
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.padding(4.dp).clickable(actionRunCallback<StopAction>()),
            )
        }
    }
}

@Composable
private fun Tile(a: ActivityItem, active: Boolean, t: Tiles) {
    val c = Color(a.color)
    val bg = if (active) c else c.copy(alpha = 0.28f)
    Box(GlanceModifier.fillMaxWidth().padding(2.dp)) {
        Column(
            GlanceModifier.fillMaxWidth().height(t.height.dp).cornerRadius(12.dp).background(bg)
                .clickable(switchAction(a.id)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetIcon(a.icon, if (active) contentColor(c) else GlanceTheme.colors.onSurface, t.icon)
            Text(
                a.name, maxLines = 1,
                style = TextStyle(
                    fontSize = t.text.sp,
                    color = if (active) contentColor(c) else GlanceTheme.colors.onSurface,
                ),
            )
        }
    }
}

/** A full-width row of [cols] equal cells (padding cells keep sizes equal on the last row). */
@Composable
private fun TileRow(items: List<ActivityItem>, cols: Int, runningId: Long?, t: Tiles) {
    Row(GlanceModifier.fillMaxWidth()) {
        items.forEach { a -> Box(GlanceModifier.defaultWeight()) { Tile(a, a.id == runningId, t) } }
        repeat(cols - items.size) { Spacer(GlanceModifier.defaultWeight()) }
    }
}

@Composable
private fun GroupLabel(group: Group?) {
    Row(GlanceModifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.size(7.dp).cornerRadius(4.dp).background(Color(group?.color ?: 0xFF9E9E9E.toInt()))) {}
        Spacer(GlanceModifier.width(6.dp))
        Text(
            group?.name ?: "Other",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium),
        )
    }
}

/** Header with the current activity + every active activity (optionally grouped by goal); tap to switch. */
class ActivityWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val opacity = prefs[WidgetPrefs.OPACITY] ?: WidgetPrefs.DEFAULT_OPACITY
            val t = if (prefs[WidgetPrefs.COMPACT] == true) Compact else Normal
            val grouped = prefs[WidgetPrefs.GROUPED] ?: true
            val acts by remember { Graph.dao.allActivities() }.collectAsState(emptyList())
            val groups by remember { Graph.dao.groups() }.collectAsState(emptyList())
            val running by remember { Graph.dao.running() }.collectAsState(null)
            val current = acts.firstOrNull { it.id == running?.activityId }
            val cols = ((LocalSize.current.width.value - 16f) / t.cell).toInt().coerceIn(1, 8)
            val shownGroups = prefs[WidgetPrefs.GROUP_IDS].orEmpty().split(',').mapNotNull { it.toLongOrNull() }.toSet()
            val visible = acts.filter { !it.archived }.let { list ->
                if (shownGroups.isEmpty()) list
                else shownGroups.flatMap { filterByGroup(list, groups, it) }.distinctBy { it.id }.sortedBy { list.indexOf(it) }
            }
            val sections = groupActivities(visible, groups, grouped)
            GlanceTheme {
                WidgetFrame(opacity) {
                    CurrentHeader(running, current)
                    Spacer(GlanceModifier.height(4.dp))
                    LazyColumn(GlanceModifier.fillMaxSize()) {
                        var itemId = 1L
                        sections.forEach { section ->
                            if (sections.size > 1 || section.group != null) {
                                item(itemId++) { GroupLabel(section.group) }
                            }
                            section.activities.chunked(cols).forEach { row ->
                                item(itemId++) { TileRow(row, cols, running?.activityId, t) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Header + activities suggested by the user's rules and recent habits for the current time. */
class SmartWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val opacity = prefs[WidgetPrefs.OPACITY] ?: WidgetPrefs.DEFAULT_OPACITY
            val t = if (prefs[WidgetPrefs.COMPACT] == true) Compact else Normal
            val acts by remember { Graph.dao.allActivities() }.collectAsState(emptyList())
            val running by remember { Graph.dao.running() }.collectAsState(null)
            val suggestions by remember { Graph.suggestionFlow(12) }.collectAsState(emptyList())
            val current = acts.firstOrNull { it.id == running?.activityId }
            val size = LocalSize.current
            val cols = ((size.width.value - 16f) / (t.cell + 8f)).toInt().coerceIn(1, 6)
            val rows = ((size.height.value - 40f) / (t.height + 6f)).toInt().coerceIn(1, 4)
            GlanceTheme {
                WidgetFrame(opacity) {
                    CurrentHeader(running, current)
                    Spacer(GlanceModifier.height(4.dp))
                    suggestions.take(cols * rows).chunked(cols).forEach { line ->
                        TileRow(line, cols, null, t)
                    }
                }
            }
        }
    }
}

/** 1×1: the current activity's icon; tap opens the quick picker (top suggestions + searchable list). */
class QuickWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val opacity = prefs[WidgetPrefs.OPACITY] ?: WidgetPrefs.DEFAULT_OPACITY
            val acts by remember { Graph.dao.allActivities() }.collectAsState(emptyList())
            val running by remember { Graph.dao.running() }.collectAsState(null)
            val current = acts.firstOrNull { it.id == running?.activityId }
            val showName = LocalSize.current.height.value >= 64f
            GlanceTheme {
                Column(
                    GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(16.dp)
                        .background(surface(opacity)).padding(4.dp)
                        .clickable(actionStartActivity<QuickPickActivity>()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (current != null) {
                        val c = Color(current.color)
                        Box(
                            GlanceModifier.size(40.dp).cornerRadius(20.dp).background(c),
                            contentAlignment = Alignment.Center,
                        ) { WidgetIcon(current.icon, contentColor(c), 22) }
                    } else {
                        Image(
                            provider = ImageProvider(vectorBitmap(Lucide.Timer, "Timer", android.graphics.Color.WHITE, 64)),
                            contentDescription = "Pick an activity",
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
                            modifier = GlanceModifier.size(28.dp),
                        )
                    }
                    if (showName) {
                        Text(
                            current?.name ?: "Track",
                            maxLines = 1,
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 10.sp),
                            modifier = GlanceModifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

class ActivityWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ActivityWidget()
}

class SmartWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmartWidget()
}

class QuickWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickWidget()
}
