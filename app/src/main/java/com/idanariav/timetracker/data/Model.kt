package com.idanariav.timetracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Organizes activities into sections (grid, widgets, analysis). Each activity is in at most one group. */
@Entity(tableName = "activity_groups")
data class Group(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val sortOrder: Int = 0,
)

/**
 * A daily target over any set of activities (linked via [GoalActivity]), independent of groups.
 * [targetMinutes] is the daily target (0 = none); [isLimit] flips it to "stay under".
 */
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val targetMinutes: Int = 0,
    val isLimit: Boolean = false,
    val sortOrder: Int = 0,
)

/** Many-to-many: which activities count toward which goals. */
@Entity(
    tableName = "goal_activities",
    primaryKeys = ["goalId", "activityId"],
    indices = [Index("activityId")],
)
data class GoalActivity(val goalId: Long, val activityId: Long)

@Entity(tableName = "activities")
data class ActivityItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Lucide icon name (see ui/Icons.kt). */
    val icon: String,
    val color: Int,
    val groupId: Long? = null,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

/** One log entry. [endTime] == null means it is the currently running activity (at most one). */
@Entity(
    tableName = "sessions",
    indices = [Index("startTime"), Index("endTime"), Index("activityId")],
)
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long,
    val startTime: Long,
    val endTime: Long? = null,
    val note: String = "",
)

/**
 * Suggestion rule: when it matches, suggest [suggestIds] (in that order). Two kinds of trigger:
 *  - time: the given [days] (bit 0 = Monday … bit 6 = Sunday) between [startMinute] and [endMinute]
 *    (minutes of day; may wrap past midnight; equal values = all day);
 *  - activity: [afterActivityId] is currently running (stored as all-day, every day).
 */
@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val suggestIds: List<Long>,
    val days: Int = ALL_DAYS,
    val startMinute: Int = 0,
    val endMinute: Int = 0,
    val afterActivityId: Long? = null,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
) {
    val isActivityTrigger: Boolean get() = afterActivityId != null

    companion object {
        const val ALL_DAYS = 0b1111111
    }
}

/** Room storage for id lists ("3,5,8"). */
class Converters {
    @androidx.room.TypeConverter
    fun idsToString(ids: List<Long>): String = ids.joinToString(",")

    @androidx.room.TypeConverter
    fun stringToIds(s: String): List<Long> = s.split(',').mapNotNull { it.trim().toLongOrNull() }
}
