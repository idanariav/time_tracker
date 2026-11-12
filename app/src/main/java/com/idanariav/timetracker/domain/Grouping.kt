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

/** Activities whose name or group name contains [query] (case-insensitive); all of them for a blank query. */
fun filterActivities(activities: List<ActivityItem>, groups: List<Group>, query: String): List<ActivityItem> {
    val q = query.trim()
    if (q.isEmpty()) return activities
    val groupNames = groups.associate { it.id to it.name }
    return activities.filter {
        it.name.contains(q, ignoreCase = true) || (groupNames[it.groupId]?.contains(q, ignoreCase = true) ?: false)
    }
}

/** Group filter value meaning "activities without a (known) group". `null` means all groups. */
const val OTHER_GROUP = -1L

/** Activities in the group selected by [groupFilter] (null = all, [OTHER_GROUP] = ungrouped). */
fun filterByGroup(activities: List<ActivityItem>, groups: List<Group>, groupFilter: Long?): List<ActivityItem> {
    val ids = groups.map { it.id }.toSet()
    return when (groupFilter) {
        null -> activities
        OTHER_GROUP -> activities.filter { it.groupId == null || it.groupId !in ids }
        else -> activities.filter { it.groupId == groupFilter }
    }
}

/** True when some activity has no (known) group, i.e. an "Other" section/filter is needed. */
fun hasUngrouped(activities: List<ActivityItem>, groups: List<Group>): Boolean =
    filterByGroup(activities, groups, OTHER_GROUP).isNotEmpty()

/** Moves [item] up/down past its neighbour *within the same group*; returns the new full order (unchanged if at an edge). */
fun moveWithinGroup(ordered: List<ActivityItem>, item: ActivityItem, delta: Int): List<ActivityItem> {
    val sameGroup = ordered.filter { it.groupId == item.groupId }
    val j = sameGroup.indexOfFirst { it.id == item.id } + delta
    if (j !in sameGroup.indices) return ordered
    val other = sameGroup[j]
    return ordered.map { if (it.id == item.id) other else if (it.id == other.id) item else it }
}
