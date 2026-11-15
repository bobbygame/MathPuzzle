package de.wyn.crossmath

import java.util.Calendar
import java.util.TimeZone

data class StreakInfo(val current: Int, val longest: Int)

/** Tagesnummer (Tage seit 1970) in der lokalen Zeitzone. */
fun dayNumber(time: Long, zone: TimeZone = TimeZone.getDefault()): Long {
    val local = Calendar.getInstance(zone).apply { timeInMillis = time }
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            local.get(Calendar.YEAR),
            local.get(Calendar.MONTH),
            local.get(Calendar.DAY_OF_MONTH)
        )
    }
    return utc.timeInMillis / 86_400_000L
}

/**
 * Serie aufeinanderfolgender Tage mit mindestens einem gelösten Rätsel.
 * Die aktuelle Serie bleibt bestehen, wenn heute noch nichts gelöst wurde,
 * aber gestern gelöst wurde.
 */
fun computeStreak(
    records: List<SolveRecord>,
    now: Long = System.currentTimeMillis(),
    zone: TimeZone = TimeZone.getDefault()
): StreakInfo {
    val days = records.map { dayNumber(it.time, zone) }.toSortedSet()
    if (days.isEmpty()) return StreakInfo(0, 0)

    var longest = 0
    var run = 0
    var prev: Long? = null
    for (d in days) {
        run = if (prev != null && d == prev + 1) run + 1 else 1
        if (run > longest) longest = run
        prev = d
    }

    val today = dayNumber(now, zone)
    var cursor = when {
        today in days -> today
        today - 1 in days -> today - 1
        else -> return StreakInfo(0, longest)
    }
    var current = 0
    while (cursor in days) {
        current++
        cursor--
    }
    return StreakInfo(current, longest)
}
