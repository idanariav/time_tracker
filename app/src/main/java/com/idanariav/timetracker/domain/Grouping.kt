package com.idanariav.timetracker.domain

import com.idanariav.timetracker.data.ActivityItem
import com.idanariav.timetracker.data.GoalActivity
import com.idanariav.timetracker.data.Group

/** A section of the activity grid. [group] == null is the "Other" (or only, when ungrouped) section. */
data class Section(val group: Group?, val activities: List<ActivityItem>)

/**
 * Splits [activities] (already in grid order) into one section per group, in group order, followed by
 * activities without a (known) group. Ungrouped, or with no groups defined, it's a single section.
 * Empty sections are dropped.
 */
fun groupActivities(activities: List<ActivityItem>, groups: List<Group>, grouped: Boolean): List<Section> {
    if (!grouped || groups.isEmpty()) {
        return if (activities.isEmpty()) emptyList() else listOf(Section(null, activities))
    }
    val ids = groups.map { it.id }.toSet()
    val sections = groups.map { g -> Section(g, activities.filter { it.groupId == g.id }) }
    val other = Section(null, activities.filter { it.groupId == null || it.groupId !in ids })
    return (sections + other).filter { it.activities.isNotEmpty() }
}

/** goalId → activity ids it tracks. */
fun goalMembers(links: List<GoalActivity>): Map<Long, Set<Long>> =
    links.groupBy({ it.goalId }, { it.activityId }).mapValues { it.value.toSet() }

/** Time per goal = sum of its member activities' time. Goals may overlap, so these don't add up to the total. */
fun goalTotals(links: List<GoalActivity>, perActivity: Map<Long, Long>): Map<Long, Long> =
    goalMembers(links).mapValues { (_, ids) -> ids.sumOf { perActivity[it] ?: 0L } }
