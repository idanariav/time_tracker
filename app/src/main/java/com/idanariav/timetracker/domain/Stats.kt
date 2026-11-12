package com.idanariav.timetracker.domain

import com.idanariav.timetracker.data.Session
import kotlin.math.max
import kotlin.math.min

fun Session.durationMs(now: Long): Long = (endTime ?: now) - startTime

/** False for entries shorter than the "ignore short entries" threshold. */
fun Session.counts(now: Long, minMs: Long): Boolean = minMs <= 0 || durationMs(now) >= minMs

/** Portion of this session inside [from, to), never extending past [now]. */
fun Session.clippedMs(from: Long, to: Long, now: Long): Long =
    (min(endTime ?: now, to) - max(startTime, from)).coerceAtLeast(0)

/** Total ms per key inside [from, to), skipping too-short entries. Keys with zero time are dropped. */
fun <K> totalsBy(
    sessions: List<Session>,
    from: Long,
    to: Long,
    now: Long,
    minMs: Long,
    key: (Session) -> K,
): Map<K, Long> {
    val out = LinkedHashMap<K, Long>()
    for (s in sessions) {
        if (!s.counts(now, minMs)) continue
        val ms = s.clippedMs(from, to, now)
        if (ms > 0) out[key(s)] = (out[key(s)] ?: 0L) + ms
    }
    return out
}

fun totalsByActivity(sessions: List<Session>, from: Long, to: Long, now: Long, minMs: Long): Map<Long, Long> =
    totalsBy(sessions, from, to, now, minMs) { it.activityId }
