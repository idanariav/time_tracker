package com.idanariav.timetracker.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

enum class PeriodKind { DAY, WEEK, MONTH }

fun defaultWeekStart(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

data class Period(
    val kind: PeriodKind,
    val anchor: LocalDate,
    val weekStart: DayOfWeek = defaultWeekStart(),
) {
    val first: LocalDate
        get() = when (kind) {
            PeriodKind.DAY -> anchor
            PeriodKind.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(weekStart))
            PeriodKind.MONTH -> anchor.withDayOfMonth(1)
        }

    val endExclusive: LocalDate
        get() = when (kind) {
            PeriodKind.DAY -> first.plusDays(1)
            PeriodKind.WEEK -> first.plusWeeks(1)
            PeriodKind.MONTH -> first.plusMonths(1)
        }

    fun shift(n: Long): Period = copy(
        anchor = when (kind) {
            PeriodKind.DAY -> anchor.plusDays(n)
            PeriodKind.WEEK -> anchor.plusWeeks(n)
            PeriodKind.MONTH -> anchor.plusMonths(n)
        },
    )

    fun days(): List<LocalDate> =
        generateSequence(first) { it.plusDays(1) }.takeWhile { it < endExclusive }.toList()

    operator fun contains(d: LocalDate) = d >= first && d < endExclusive

    fun startMillis(zone: ZoneId = ZoneId.systemDefault()) = first.startMillis(zone)
    fun endMillis(zone: ZoneId = ZoneId.systemDefault()) = endExclusive.startMillis(zone)

    fun label(today: LocalDate = LocalDate.now()): String = when (kind) {
        PeriodKind.DAY -> when (anchor) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> anchor.format(fmt(if (anchor.year == today.year) "EEE, MMM d" else "EEE, MMM d, yyyy"))
        }
        PeriodKind.WEEK -> {
            val last = endExclusive.minusDays(1)
            val tail = if (last.month == first.month) last.format(fmt("d")) else last.format(fmt("MMM d"))
            "${first.format(fmt("MMM d"))} – $tail"
        }
        PeriodKind.MONTH -> first.format(fmt("MMMM yyyy"))
    }

    private fun fmt(p: String) = DateTimeFormatter.ofPattern(p, Locale.getDefault())
}

fun LocalDate.startMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
    atStartOfDay(zone).toInstant().toEpochMilli()

fun Long.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

/** Same calendar date as [base], at [hour]:[minute]. */
fun withTimeOfDay(base: Long, hour: Int, minute: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
    base.toLocalDate(zone).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

/** An end time picked as hour:minute: on the start's date, or the next day if that would not be after [start]. */
fun endTimeAfter(start: Long, hour: Int, minute: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
    val sameDay = withTimeOfDay(start, hour, minute, zone)
    if (sameDay > start) return sameDay
    return start.toLocalDate(zone).plusDays(1).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
}

fun formatDuration(ms: Long): String {
    if (ms < 60_000) return "${(ms / 1000).coerceAtLeast(0)}s"
    val m = ms / 60_000
    return if (m < 60) "${m}m" else "${m / 60}h ${"%02d".format(m % 60)}m"
}

fun formatElapsed(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}
