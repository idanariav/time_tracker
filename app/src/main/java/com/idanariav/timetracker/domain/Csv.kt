package com.idanariav.timetracker.domain

import com.idanariav.timetracker.data.ActivityItem
import com.idanariav.timetracker.data.Group
import com.idanariav.timetracker.data.Session
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun csvField(v: String): String =
    if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

/** Raw export of every entry (nothing filtered). A running entry has an empty end and its duration so far. */
fun buildCsv(
    sessions: List<Session>,
    activities: Map<Long, ActivityItem>,
    groups: Map<Long, Group>,
    /** activityId → names of goals it counts toward. */
    goalNames: Map<Long, List<String>>,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val dt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    fun at(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone)
    val sb = StringBuilder("date,activity,group,goals,start,end,duration_minutes,note\n")
    for (s in sessions.sortedBy { it.startTime }) {
        val a = activities[s.activityId]
        val g = a?.groupId?.let { groups[it] }
        sb.append(
            listOf(
                at(s.startTime).format(date),
                a?.name ?: "(deleted)",
                g?.name ?: "",
                goalNames[s.activityId].orEmpty().joinToString("; "),
                at(s.startTime).format(dt),
                s.endTime?.let { at(it).format(dt) } ?: "",
                String.format(Locale.US, "%.2f", s.durationMs(now) / 60000.0),
                s.note,
            ).joinToString(",") { csvField(it) },
        ).append('\n')
    }
    return sb.toString()
}
