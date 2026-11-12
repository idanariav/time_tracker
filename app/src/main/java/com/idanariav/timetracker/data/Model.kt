package com.idanariav.timetracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A goal groups activities. [targetMinutes] is a daily target (0 = none); [isLimit] flips it to "stay under". */
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val targetMinutes: Int = 0,
    val isLimit: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(tableName = "activities")
data class ActivityItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val color: Int,
    val goalId: Long? = null,
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
 * Smart-widget rule: suggest [activityId] on the given [days] (bit 0 = Monday … bit 6 = Sunday)
 * between [startMinute] and [endMinute] (minutes of day; the window may wrap past midnight;
 * equal values mean all day), optionally only when [afterActivityId] is currently running.
 */
@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long,
    val days: Int = ALL_DAYS,
    val startMinute: Int,
    val endMinute: Int,
    val afterActivityId: Long? = null,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
) {
    companion object {
        const val ALL_DAYS = 0b1111111
    }
}
