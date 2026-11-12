package com.idanariav.timetracker.domain

import com.idanariav.timetracker.data.ActivityItem
import com.idanariav.timetracker.data.Goal
import com.idanariav.timetracker.data.Rule
import com.idanariav.timetracker.data.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

private const val M = 60_000L
private val UTC = ZoneOffset.UTC

class OverlapTest {
    @Test fun editedInsideRunningSplitsIt() {
        val running = Session(1, 10, startTime = 0, endTime = null)
        val plan = planOverlaps(Session(2, 20, 30 * M, 60 * M), listOf(running))
        assertEquals(listOf(running.copy(endTime = 30 * M)), plan.updates)
        assertEquals(listOf(Session(0, 10, 60 * M, null)), plan.inserts)
        assertTrue(plan.deletes.isEmpty())
    }

    @Test fun trimsBothNeighboursAndDeletesCovered() {
        val before = Session(1, 10, 0, 40 * M)
        val covered = Session(2, 11, 45 * M, 50 * M)
        val after = Session(3, 12, 55 * M, 90 * M)
        val plan = planOverlaps(Session(9, 20, 30 * M, 60 * M), listOf(before, covered, after))
        assertEquals(listOf(before.copy(endTime = 30 * M), after.copy(startTime = 60 * M)), plan.updates)
        assertEquals(listOf(covered), plan.deletes)
    }

    @Test fun savingRunningEndsEarlierRunning() {
        val old = Session(1, 10, 0, null)
        val plan = planOverlaps(Session(2, 20, 30 * M, null), listOf(old))
        assertEquals(listOf(old.copy(endTime = 30 * M)), plan.updates)
        assertTrue(plan.inserts.isEmpty())
    }

    @Test fun touchingEntriesAreUntouched() {
        val plan = planOverlaps(Session(2, 20, 30 * M, 60 * M), listOf(Session(1, 10, 0, 30 * M)))
        assertEquals(OverlapPlan(), plan)
    }
}

class StatsTest {
    @Test fun clipsToRangeAndNow() {
        val s = Session(1, 1, startTime = 0, endTime = null)
        assertEquals(50 * M, s.clippedMs(10 * M, 100 * M, now = 60 * M))
        assertEquals(0, s.clippedMs(100 * M, 200 * M, now = 60 * M))
    }

    @Test fun shortEntriesAreIgnored() {
        val sessions = listOf(Session(1, 1, 0, 30_000), Session(2, 2, 30_000, 10 * M))
        assertEquals(mapOf(2L to 10 * M - 30_000), totalsByActivity(sessions, 0, 60 * M, 60 * M, minMs = 60_000))
        assertEquals(2, totalsByActivity(sessions, 0, 60 * M, 60 * M, minMs = 0).size)
    }

    @Test fun crossMidnightSplitsAcrossDays() {
        val d1 = LocalDate.of(2026, 11, 10).startMillis(UTC)
        val d2 = d1 + 24 * 60 * M
        val sleep = Session(1, 1, d2 - 60 * M, d2 + 7 * 60 * M)
        assertEquals(60 * M, sleep.clippedMs(d1, d2, Long.MAX_VALUE))
        assertEquals(7 * 60 * M, sleep.clippedMs(d2, d2 + 24 * 60 * M, Long.MAX_VALUE))
    }
}

class PeriodTest {
    @Test fun weekStartsOnConfiguredDay() {
        val p = Period(PeriodKind.WEEK, LocalDate.of(2026, 11, 12), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 11, 9), p.first)
        assertEquals(7, p.days().size)
        assertEquals(LocalDate.of(2026, 11, 2), p.shift(-1).first)
    }

    @Test fun monthBounds() {
        val p = Period(PeriodKind.MONTH, LocalDate.of(2026, 2, 14))
        assertEquals(28, p.days().size)
        assertEquals(LocalDate.of(2026, 3, 1), p.endExclusive)
    }

    @Test fun endPickedBeforeStartRollsToNextDay() {
        val start = LocalDateTime.of(2026, 11, 10, 23, 0).toInstant(UTC).toEpochMilli()
        val end = endTimeAfter(start, 7, 0, UTC)
        assertEquals(LocalDateTime.of(2026, 11, 11, 7, 0).toInstant(UTC).toEpochMilli(), end)
        assertEquals(start + 30 * M, endTimeAfter(start, 23, 30, UTC))
    }

    @Test fun durationFormatting() {
        assertEquals("45s", formatDuration(45_000))
        assertEquals("12m", formatDuration(12 * M))
        assertEquals("1h 05m", formatDuration(65 * M))
    }
}

class SuggestTest {
    private val acts = listOf(
        ActivityItem(1, "Sleep", "😴", 0), ActivityItem(2, "Work", "💼", 0), ActivityItem(3, "TV", "📺", 0),
    )

    @Test fun wrappingWindowUsesStartDay() {
        // Fri 22:00 → 02:00 rule (Friday only)
        val r = Rule(activityId = 1, days = 1 shl 4, startMinute = 22 * 60, endMinute = 2 * 60)
        assertTrue(ruleMatches(r, LocalDateTime.of(2026, 11, 13, 23, 0), null)) // Fri 23:00
        assertTrue(ruleMatches(r, LocalDateTime.of(2026, 11, 14, 1, 0), null)) // Sat 01:00
        assertFalse(ruleMatches(r, LocalDateTime.of(2026, 11, 15, 1, 0), null)) // Sun 01:00
        assertFalse(ruleMatches(r, LocalDateTime.of(2026, 11, 13, 12, 0), null))
    }

    @Test fun afterActivityCondition() {
        val r = Rule(activityId = 3, startMinute = 0, endMinute = 0, afterActivityId = 2)
        val now = LocalDateTime.of(2026, 11, 12, 18, 0)
        assertTrue(ruleMatches(r, now, 2))
        assertFalse(ruleMatches(r, now, 1))
    }

    @Test fun rulesFirstThenHistoryAndRunningExcluded() {
        val now = LocalDateTime.of(2026, 11, 12, 22, 30)
        val rule = Rule(activityId = 1, startMinute = 22 * 60, endMinute = 6 * 60)
        val historyStart = LocalDateTime.of(2026, 11, 11, 22, 15).toInstant(UTC).toEpochMilli()
        val history = listOf(Session(1, 3, historyStart, historyStart + 60 * M))
        val out = suggest(acts, listOf(rule), history, running = Session(9, 2, 0, null), now, 5, UTC)
        assertEquals(listOf(1L, 3L), out.map { it.id })
    }
}

class CsvTest {
    @Test fun escapesAndIncludesGoal() {
        val a = ActivityItem(1, "Work, deep", "💼", 0, goalId = 5)
        val csv = buildCsv(
            listOf(Session(1, 1, 0, 90 * M, "said \"hi\"")),
            mapOf(1L to a), mapOf(5L to Goal(5, "Job", 0)), now = 0, zone = UTC,
        )
        val line = csv.lines()[1]
        assertEquals("1970-01-01,\"Work, deep\",Job,1970-01-01 00:00:00,1970-01-01 01:30:00,90.00,\"said \"\"hi\"\"\"", line)
    }
}
