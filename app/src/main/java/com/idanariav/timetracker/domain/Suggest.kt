package com.idanariav.timetracker.domain

import com.idanariav.timetracker.data.ActivityItem
import com.idanariav.timetracker.data.Rule
import com.idanariav.timetracker.data.Session
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.min

private fun dayBit(dayOfWeekValue: Int) = 1 shl (dayOfWeekValue - 1) // Monday = bit 0

fun ruleMatches(rule: Rule, now: LocalDateTime, runningActivityId: Long?): Boolean {
    if (!rule.enabled) return false
    if (rule.afterActivityId != null && rule.afterActivityId != runningActivityId) return false
    val m = now.hour * 60 + now.minute
    val today = now.dayOfWeek.value
    return when {
        rule.startMinute == rule.endMinute -> rule.days and dayBit(today) != 0
        rule.startMinute < rule.endMinute ->
            m >= rule.startMinute && m < rule.endMinute && rule.days and dayBit(today) != 0
        // Wraps past midnight: the after-midnight part belongs to the previous day's rule.
        m >= rule.startMinute -> rule.days and dayBit(today) != 0
        m < rule.endMinute -> rule.days and dayBit(now.minusDays(1).dayOfWeek.value) != 0
        else -> false
    }
}

/**
 * Ordered suggestions: activities whose rules match now ("while X is running" rules first, each in its listed order),
 * then what was usually started around this time of day over [history], then the user's grid order.
 * The running activity is never suggested.
 */
fun suggest(
    activities: List<ActivityItem>,
    rules: List<Rule>,
    history: List<Session>,
    running: Session?,
    now: LocalDateTime,
    limit: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): List<ActivityItem> {
    val byId = activities.filter { !it.archived }.associateBy { it.id }
    val out = LinkedHashSet<Long>()
    rules.filter { ruleMatches(it, now, running?.activityId) }
        .sortedBy { if (it.afterActivityId != null) 0 else 1 }
        .forEach { out += it.suggestIds }

    val nowMin = now.hour * 60 + now.minute
    val score = HashMap<Long, Int>()
    for (s in history) {
        val t = Instant.ofEpochMilli(s.startTime).atZone(zone).toLocalDateTime()
        val diff = abs(t.hour * 60 + t.minute - nowMin)
        if (min(diff, 1440 - diff) <= 60) {
            score[s.activityId] = (score[s.activityId] ?: 0) + if (t.dayOfWeek == now.dayOfWeek) 2 else 1
        }
    }
    score.entries.sortedByDescending { it.value }.forEach { out += it.key }
    activities.forEach { out += it.id }

    return out.asSequence()
        .filter { it != running?.activityId }
        .mapNotNull { byId[it] }
        .take(limit)
        .toList()
}
