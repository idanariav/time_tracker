package com.idanariav.timetracker.domain

import com.idanariav.timetracker.data.ActivityItem
import com.idanariav.timetracker.data.Goal

/** A section of the activity grid. [goal] == null is the "Other" (or only, when ungrouped) section. */
data class ActivityGroup(val goal: Goal?, val activities: List<ActivityItem>)

/**
 * Splits [activities] (already in grid order) into one section per goal, in goal order, followed by
 * activities without a (known) goal. Ungrouped, or with no goals defined, it's a single section.
 * Empty sections are dropped.
 */
fun groupActivities(activities: List<ActivityItem>, goals: List<Goal>, grouped: Boolean): List<ActivityGroup> {
    if (!grouped || goals.isEmpty()) {
        return if (activities.isEmpty()) emptyList() else listOf(ActivityGroup(null, activities))
    }
    val ids = goals.map { it.id }.toSet()
    val sections = goals.map { g -> ActivityGroup(g, activities.filter { it.goalId == g.id }) }
    val other = ActivityGroup(null, activities.filter { it.goalId == null || it.goalId !in ids })
    return (sections + other).filter { it.activities.isNotEmpty() }
}
