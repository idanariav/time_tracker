package com.idanariav.timetracker.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.idanariav.timetracker.ui.AppTheme
import com.idanariav.timetracker.ui.Palette
import kotlinx.coroutines.launch

private enum class WidgetKind(val title: String, val hasCompact: Boolean, val hasGrouping: Boolean) {
    ACTIVITIES("Activities widget", true, true),
    SMART("Smart suggestions widget", true, false),
    QUICK("Quick pick widget", false, false),
}

/**
 * Shown when a widget is placed and again from the widget's "edit/settings" long-press option
 * (the providers declare it as reconfigurable). Settings are per widget instance.
 */
class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(Activity.RESULT_CANCELED) // backing out of first placement adds no widget
        appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()

        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.className.orEmpty()
        val kind = when {
            provider.endsWith("QuickWidgetReceiver") -> WidgetKind.QUICK
            provider.endsWith("SmartWidgetReceiver") -> WidgetKind.SMART
            else -> WidgetKind.ACTIVITIES
        }

        lifecycleScope.launch {
            // Seed from the widget's current state so reconfiguring shows real values.
            val prefs = runCatching {
                val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
                getAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)
            }.getOrNull()
            val opacity = prefs?.get(WidgetPrefs.OPACITY) ?: WidgetPrefs.DEFAULT_OPACITY
            val compact = prefs?.get(WidgetPrefs.COMPACT) ?: false
            val grouped = prefs?.get(WidgetPrefs.GROUPED) ?: true
            setContent {
                AppTheme {
                    ConfigScreen(kind, opacity, compact, grouped, onCancel = { finish() }) { o, c, g ->
                        commit(kind, o, c, g)
                    }
                }
            }
        }
    }

    private fun commit(kind: WidgetKind, opacity: Int, compact: Boolean, grouped: Boolean) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { p: MutablePreferences ->
                p[WidgetPrefs.OPACITY] = opacity
                p[WidgetPrefs.COMPACT] = compact
                p[WidgetPrefs.GROUPED] = grouped
            }
            val widget: GlanceAppWidget = when (kind) {
                WidgetKind.ACTIVITIES -> ActivityWidget()
                WidgetKind.SMART -> SmartWidget()
                WidgetKind.QUICK -> QuickWidget()
            }
            widget.update(this@WidgetConfigActivity, glanceId)
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}

@Composable
private fun ConfigScreen(
    kind: WidgetKind,
    initOpacity: Int,
    initCompact: Boolean,
    initGrouped: Boolean,
    onCancel: () -> Unit,
    onSave: (opacity: Int, compact: Boolean, grouped: Boolean) -> Unit,
) {
    var opacity by remember { mutableIntStateOf(initOpacity) }
    var compact by remember { mutableStateOf(initCompact) }
    var grouped by remember { mutableStateOf(initGrouped) }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onCancel)
            .systemBarsPadding().padding(16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) {},
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState())) {
                Text(kind.title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                Preview(opacity, compact)
                Spacer(Modifier.height(16.dp))
                Text("Background opacity: $opacity%", style = MaterialTheme.typography.labelLarge)
                Slider(value = opacity.toFloat(), onValueChange = { opacity = it.toInt() }, valueRange = 0f..100f)
                if (kind.hasCompact) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Compact tiles", Modifier.weight(1f))
                        Switch(compact, { compact = it })
                    }
                }
                if (kind.hasGrouping) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Section by group", Modifier.weight(1f))
                        Switch(grouped, { grouped = it })
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Button(onClick = { onSave(opacity, compact, grouped) }) { Text("Save") }
                }
            }
        }
    }
}

/** Mock wallpaper with a widget surface at the chosen opacity on top. */
@Composable
private fun Preview(opacity: Int, compact: Boolean) {
    val surface = MaterialTheme.colorScheme.surfaceContainer
    Box(
        Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3F51B5), Color(0xFFE91E63), Color(0xFFFFC107)))),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp))
                .background(surface.copy(alpha = opacity / 100f)).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Palette.take(if (compact) 6 else 4).forEach { c ->
                Box(
                    Modifier.weight(1f).height(if (compact) 32.dp else 44.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color(c).copy(alpha = 0.5f)),
                )
            }
        }
    }
}
