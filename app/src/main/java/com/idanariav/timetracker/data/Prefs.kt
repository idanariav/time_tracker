package com.idanariav.timetracker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Settings(
    val minFilterEnabled: Boolean = true,
    val minFilterSeconds: Int = 60,
    val notificationEnabled: Boolean = true,
    /** Section the activity grid (Track tab, quick pick) by group. */
    val groupActivities: Boolean = true,
    /** Track tab: smaller tiles so more fit on screen. */
    val compactGrid: Boolean = false,
) {
    /** Entries shorter than this many ms are ignored in goals/analysis (0 = keep all). */
    val minMs: Long get() = if (minFilterEnabled) minFilterSeconds * 1000L else 0L
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: StateFlow<Settings> = state

    private fun read() = Settings(
        minFilterEnabled = sp.getBoolean("minFilterEnabled", true),
        minFilterSeconds = sp.getInt("minFilterSeconds", 60),
        notificationEnabled = sp.getBoolean("notificationEnabled", true),
        groupActivities = sp.getBoolean("groupActivities", true),
        compactGrid = sp.getBoolean("compactGrid", false),
    )

    fun update(change: (Settings) -> Settings) {
        val s = change(state.value)
        sp.edit()
            .putBoolean("minFilterEnabled", s.minFilterEnabled)
            .putInt("minFilterSeconds", s.minFilterSeconds)
            .putBoolean("notificationEnabled", s.notificationEnabled)
            .putBoolean("groupActivities", s.groupActivities)
            .putBoolean("compactGrid", s.compactGrid)
            .apply()
        state.value = s
    }

    var seeded: Boolean
        get() = sp.getBoolean("seeded", false)
        set(v) = sp.edit().putBoolean("seeded", v).apply()
}
