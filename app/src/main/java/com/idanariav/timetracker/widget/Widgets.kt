package com.idanariav.timetracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyVerticalGrid
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.idanariav.timetracker.data.ActivityItem
import com.idanariav.timetracker.data.Session
import com.idanariav.timetracker.tracking.Graph
import com.idanariav.timetracker.ui.MainActivity
import com.idanariav.timetracker.ui.TimeFmt

suspend fun refreshWidgets(context: Context) {
    ActivityWidget().updateAll(context)
    SmartWidget().updateAll(context)
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

@Composable
private fun WidgetFrame(content: @Composable () -> Unit) {
    Column(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(16.dp)
            .background(GlanceTheme.colors.widgetBackground).padding(8.dp),
    ) { content() }
}

@Composable
private fun CurrentHeader(running: Session?, current: ActivityItem?) {
    Row(GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (running != null && current != null) "${current.emoji} ${current.name}" else "Not tracking",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
        )
        if (running != null) {
            Text(
                "since ${TimeFmt.clock(running.startTime)}",
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
private fun Tile(a: ActivityItem, active: Boolean, height: Int = 58) {
    val c = Color(a.color)
    val bg = if (active) c else c.copy(alpha = 0.28f)
    Box(GlanceModifier.fillMaxWidth().padding(3.dp)) {
        Column(
            GlanceModifier.fillMaxWidth().height(height.dp).cornerRadius(12.dp).background(bg).clickable(switchAction(a.id)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(a.emoji, style = TextStyle(fontSize = 20.sp))
            Text(
                a.name, maxLines = 1,
                style = TextStyle(
                    fontSize = 11.sp,
                    color = if (active) contentColor(c) else GlanceTheme.colors.onSurface,
                ),
            )
        }
    }
}

/** Header with the current activity + a grid of every active activity; tap to switch. */
class ActivityWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val acts by remember { Graph.dao.allActivities() }.collectAsState(emptyList())
            val running by remember { Graph.dao.running() }.collectAsState(null)
            val current = acts.firstOrNull { it.id == running?.activityId }
            val cols = (LocalSize.current.width.value / 76f).toInt().coerceIn(2, 8)
            GlanceTheme {
                WidgetFrame {
                    CurrentHeader(running, current)
                    Spacer(GlanceModifier.height(4.dp))
                    LazyVerticalGrid(GridCells.Fixed(cols), GlanceModifier.fillMaxSize()) {
                        items(acts.filter { !it.archived }, itemId = { it.id }) { a ->
                            Tile(a, a.id == running?.activityId)
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
            val acts by remember { Graph.dao.allActivities() }.collectAsState(emptyList())
            val running by remember { Graph.dao.running() }.collectAsState(null)
            val suggestions by remember { Graph.suggestionFlow(8) }.collectAsState(emptyList())
            val current = acts.firstOrNull { it.id == running?.activityId }
            val size = LocalSize.current
            val cols = (size.width.value / 84f).toInt().coerceIn(1, 4)
            val rows = ((size.height.value - 40f) / 64f).toInt().coerceIn(1, 3)
            GlanceTheme {
                WidgetFrame {
                    CurrentHeader(running, current)
                    Spacer(GlanceModifier.height(4.dp))
                    suggestions.take(cols * rows).chunked(cols).forEach { line ->
                        Row(GlanceModifier.fillMaxWidth()) {
                            line.forEach { a ->
                                Box(GlanceModifier.defaultWeight()) { Tile(a, active = false, height = 54) }
                            }
                            repeat(cols - line.size) { Spacer(GlanceModifier.defaultWeight()) }
                        }
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
