package com.idanariav.timetracker.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.idanariav.timetracker.data.ActivityItem
import com.idanariav.timetracker.domain.toLocalDate
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeFmt {
    private var is24 = true
    private val f24 = DateTimeFormatter.ofPattern("HH:mm")
    private val f12 = DateTimeFormatter.ofPattern("h:mm a")
    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM")

    fun init(context: Context) {
        is24 = DateFormat.is24HourFormat(context)
    }

    val is24Hour get() = is24

    fun clock(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(if (is24) f24 else f12)

    /** "14:05", "Yesterday 23:10" or "Tue 11 Nov 09:00" relative to today. */
    fun dayTime(ms: Long, today: LocalDate = LocalDate.now()): String {
        val d = ms.toLocalDate()
        return when (d) {
            today -> clock(ms)
            today.minusDays(1) -> "Yesterday ${clock(ms)}"
            else -> "${d.format(dayFmt)} ${clock(ms)}"
        }
    }
}

val Palette = listOf(
    0xFFE57373, 0xFFF06292, 0xFFBA68C8, 0xFF9575CD, 0xFF7986CB, 0xFF64B5F6, 0xFF4FC3F7, 0xFF4DD0E1,
    0xFF4DB6AC, 0xFF81C784, 0xFFAED581, 0xFFDCE775, 0xFFFFD54F, 0xFFFFB74D, 0xFFFF8A65, 0xFFA1887F, 0xFF90A4AE,
).map { it.toInt() }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Current time, re-emitted every [periodMs] (aligned to the period boundary). */
@Composable
fun rememberNow(periodMs: Long = 1000): Long {
    val now by produceState(System.currentTimeMillis(), periodMs) {
        while (true) {
            value = System.currentTimeMillis()
            delay(periodMs - value % periodMs)
        }
    }
    return now
}

/** Solid color circle with the activity's Lucide icon in black or white. */
@Composable
fun IconBadge(icon: String, color: Int, size: Dp = 40.dp) {
    val c = Color(color)
    Box(Modifier.size(size).clip(CircleShape).background(c), contentAlignment = Alignment.Center) {
        Icon(activityIcon(icon), null, tint = onColor(c), modifier = Modifier.size(size * 0.5f))
    }
}

/** The activity color pulled toward the text color so it stays readable on light and dark surfaces. */
@Composable
fun readableTint(color: Int): Color = lerp(Color(color), MaterialTheme.colorScheme.onSurface, 0.35f)

/** Small colored icon for chips (FilterChip/AssistChip leadingIcon). */
@Composable
fun ChipIcon(a: ActivityItem) {
    Icon(activityIcon(a.icon), null, tint = readableTint(a.color), modifier = Modifier.size(18.dp))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

/** ‹ label › navigator; tapping the label jumps back to the current period. */
@Composable
fun PeriodHeader(label: String, onPrev: () -> Unit, onNext: () -> Unit, onReset: () -> Unit, canGoNext: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Lucide.ChevronLeft, "Previous") }
        TextButton(onClick = onReset, modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = onNext, enabled = canGoNext) { Icon(Lucide.ChevronRight, "Next") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Palette.forEach { c ->
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color(c))
                    .then(
                        if (c == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        else Modifier,
                    )
                    .clickable { onSelect(c) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPicker(selected: String, color: Int, onSelect: (String) -> Unit) {
    val c = Color(color)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ActivityIcons.forEach { (name, vector) ->
            val isSel = name == selected
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(if (isSel) c else Color.Transparent)
                    .clickable { onSelect(name) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    vector, name, modifier = Modifier.size(22.dp),
                    tint = if (isSel) onColor(c) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(hour, minute, TimeFmt.is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(state.hour, state.minute); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state) },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Black or white, whichever reads better on [bg]. */
fun onColor(bg: Color): Color =
    if (0.299 * bg.red + 0.587 * bg.green + 0.114 * bg.blue > 0.6) Color.Black else Color.White

fun onColorInt(color: Int): Int = onColor(Color(color)).toArgb()
