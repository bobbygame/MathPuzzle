package de.wyn.crossmath

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val dayMs = 86_400_000L

    private fun at(day: Long): Long = day * dayMs + 12 * 3_600_000L

    private fun rec(day: Long) = SolveRecord(at(day), Difficulty.EASY, 60, 0)

    private fun streak(days: List<Long>, today: Long): StreakInfo =
        computeStreak(days.map { rec(it) }, at(today), utc)

    private fun utcTime(y: Int, m: Int, d: Int, h: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(y, m, d, h, 0)
        }.timeInMillis

    @Test
    fun emptyHistoryHasNoStreak() {
        assertEquals(StreakInfo(0, 0), streak(emptyList(), 100))
    }

    @Test
    fun solvedTodayGivesOne() {
        assertEquals(StreakInfo(1, 1), streak(listOf(100), 100))
    }

    @Test
    fun todayAndYesterdayGiveTwo() {
        assertEquals(StreakInfo(2, 2), streak(listOf(99, 100), 100))
    }

    @Test
    fun streakSurvivesUntilEndOfNextDay() {
        assertEquals(StreakInfo(2, 2), streak(listOf(98, 99), 100))
    }

    @Test
    fun streakBreaksAfterMissedDay() {
        assertEquals(StreakInfo(0, 2), streak(listOf(97, 98), 100))
    }

    @Test
    fun multipleSolvesOnSameDayCountOnce() {
        assertEquals(StreakInfo(1, 1), streak(listOf(100, 100, 100), 100))
    }

    @Test
    fun longestStreakIsKeptAfterGap() {
        assertEquals(StreakInfo(2, 3), streak(listOf(1, 2, 3, 5, 6), 6))
    }

    @Test
    fun dayNumberCrossesMonthBoundary() {
        val jan = utcTime(2026, Calendar.JANUARY, 31, 23)
        val feb = utcTime(2026, Calendar.FEBRUARY, 1, 1)
        assertEquals(dayNumber(jan, utc) + 1, dayNumber(feb, utc))
    }

    @Test
    fun dayNumberUsesLocalTimeZone() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        val evening = utcTime(2026, Calendar.NOVEMBER, 15, 22)
        val midnight = utcTime(2026, Calendar.NOVEMBER, 15, 23)
        assertEquals(dayNumber(evening, berlin) + 1, dayNumber(midnight, berlin))
    }
}
