package de.wyn.crossmath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelHistoryTest {

    private fun points(n: Int) = (0 until n).map { LevelPoint(it * 1000L, it + 1) }

    @Test
    fun encodeAndDecodeRoundTrip() {
        val list = listOf(LevelPoint(1000, 1), LevelPoint(2000, 2), LevelPoint(3000, 1))
        assertEquals(list, decodeLevelPoints(encodeLevelPoints(list)))
    }

    @Test
    fun decodeHandlesEmptyAndNull() {
        assertEquals(emptyList<LevelPoint>(), decodeLevelPoints(null))
        assertEquals(emptyList<LevelPoint>(), decodeLevelPoints(""))
        assertEquals(emptyList<LevelPoint>(), decodeLevelPoints("  "))
    }

    @Test
    fun decodeSkipsBrokenEntries() {
        val result = decodeLevelPoints("abc;1000,5;3;x,2;2000,6,7;4000,7")
        assertEquals(listOf(LevelPoint(1000, 5), LevelPoint(4000, 7)), result)
    }

    @Test
    fun appendKeepsOnlyNewestPoints() {
        val full = points(MAX_LEVEL_POINTS)
        val result = appendLevelPoint(full, LevelPoint(999_999, 42))
        assertEquals(MAX_LEVEL_POINTS, result.size)
        assertEquals(LevelPoint(999_999, 42), result.last())
        assertEquals(full[1], result.first())
    }

    @Test
    fun downsampleKeepsSmallListsUnchanged() {
        val list = points(10)
        assertEquals(list, downsample(list, 10))
        assertEquals(list, downsample(list, 50))
    }

    @Test
    fun downsampleReducesSizeAndKeepsEnds() {
        val list = points(1000)
        val result = downsample(list, 100)
        assertEquals(100, result.size)
        assertEquals(list.first(), result.first())
        assertEquals(list.last(), result.last())
        assertTrue(result.zipWithNext().all { (a, b) -> a.time < b.time })
    }
}
