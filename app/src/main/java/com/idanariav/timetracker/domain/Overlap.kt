package com.idanariav.timetracker.domain

import com.idanariav.timetracker.data.Session

data class OverlapPlan(
    val updates: List<Session> = emptyList(),
    val deletes: List<Session> = emptyList(),
    val inserts: List<Session> = emptyList(),
)

/**
 * The edited entry wins: other entries it overlaps are trimmed, split around it, or removed.
 * A running entry (endTime == null) is treated as extending forever, so saving one also
 * ends any earlier running entry and keeps the "one activity at a time" invariant.
 */
fun planOverlaps(edited: Session, others: List<Session>): OverlapPlan {
    val eStart = edited.startTime
    val eEnd = edited.endTime ?: Long.MAX_VALUE
    val updates = mutableListOf<Session>()
    val deletes = mutableListOf<Session>()
    val inserts = mutableListOf<Session>()
    for (o in others) {
        if (o.id == edited.id) continue
        val oEnd = o.endTime ?: Long.MAX_VALUE
        if (o.startTime >= eEnd || oEnd <= eStart) continue // no overlap
        when {
            o.startTime >= eStart && oEnd <= eEnd -> deletes += o
            o.startTime < eStart && oEnd > eEnd -> {
                // Edited entry sits inside o: split o around it.
                updates += o.copy(endTime = eStart)
                inserts += o.copy(id = 0, startTime = eEnd, endTime = o.endTime)
            }
            o.startTime < eStart -> updates += o.copy(endTime = eStart)
            else -> updates += o.copy(startTime = eEnd)
        }
    }
    return OverlapPlan(updates, deletes, inserts)
}
