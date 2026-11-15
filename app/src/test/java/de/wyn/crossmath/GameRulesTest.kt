package de.wyn.crossmath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRulesTest {

    @Test
    fun newGameWithoutArgumentsContinuesAdaptive() {
        assertEquals(StartMode.ADAPTIVE, resolveStartMode(null, null, 5))
    }

    @Test
    fun newGameWithoutArgumentsStaysFixedOutsideAdaptive() {
        assertEquals(StartMode.FIXED, resolveStartMode(null, null, null))
    }

    @Test
    fun explicitDifficultyOrSeedLeavesAdaptiveMode() {
        assertEquals(StartMode.FIXED, resolveStartMode(Difficulty.EASY, null, 5))
        assertEquals(StartMode.FIXED, resolveStartMode(null, 12345L, 5))
    }

    @Test
    fun unsolvedAdaptivePuzzleCountsAsAbandoned() {
        assertTrue(isAbandonedAdaptive(true, 7, loading = false, solved = false, pendingAdaptive = false))
    }

    @Test
    fun solvedOrLoadingPuzzleIsNotAbandoned() {
        assertFalse(isAbandonedAdaptive(true, 7, loading = false, solved = true, pendingAdaptive = false))
        assertFalse(isAbandonedAdaptive(true, 7, loading = true, solved = false, pendingAdaptive = false))
    }

    @Test
    fun fixedModePuzzleIsNeverAbandonedAdaptive() {
        assertFalse(isAbandonedAdaptive(true, null, loading = false, solved = false, pendingAdaptive = false))
        assertFalse(isAbandonedAdaptive(false, null, loading = false, solved = false, pendingAdaptive = false))
    }

    @Test
    fun pendingSavedAdaptivePuzzleCountsAsAbandoned() {
        assertTrue(isAbandonedAdaptive(false, null, loading = false, solved = false, pendingAdaptive = true))
    }

    @Test
    fun hintsComeFromLevelInAdaptiveAndFromDifficultyOtherwise() {
        assertEquals(2, maxHintsFor(41, Difficulty.EASY))
        assertEquals(0, maxHintsFor(1, Difficulty.EXPERT))
        assertEquals(Difficulty.HARD.maxHints, maxHintsFor(null, Difficulty.HARD))
    }

    @Test
    fun hintsLeftNeverGoesNegative() {
        assertEquals(2, hintsLeftFor(3, 1))
        assertEquals(0, hintsLeftFor(3, 3))
        assertEquals(0, hintsLeftFor(3, 5))
    }

    @Test
    fun timerRunsOnlyWhenGameIsActive() {
        assertTrue(timerShouldRun(true, true, false, false, false, false))
    }

    @Test
    fun timerStopsWhenAppIsPausedOrSettingsOpen() {
        assertFalse(timerShouldRun(true, true, false, paused = true, settingsOpen = false, solved = false))
        assertFalse(timerShouldRun(true, true, false, paused = false, settingsOpen = true, solved = false))
    }

    @Test
    fun timerStopsOutsideGameLoadingOrWhenSolved() {
        assertFalse(timerShouldRun(false, true, false, false, false, false))
        assertFalse(timerShouldRun(true, false, false, false, false, false))
        assertFalse(timerShouldRun(true, true, true, false, false, false))
        assertFalse(timerShouldRun(true, true, false, false, false, true))
    }

    @Test
    fun maxLevelNeverFallsBelowStoredValues() {
        assertEquals(ADAPTIVE_START_LEVEL, nextMaxLevel(null, null, ADAPTIVE_START_LEVEL))
        assertEquals(10, nextMaxLevel(10, 8, 6))
        assertEquals(12, nextMaxLevel(10, 8, 12))
        assertEquals(8, nextMaxLevel(null, 8, 3))
    }

    @Test
    fun levelAfterFollowsWinAndLossRules() {
        assertEquals(6, levelAfter(5, won = true))
        assertEquals(3, levelAfter(5, won = false))
        assertEquals(1, levelAfter(1, won = false))
    }

    @Test
    fun lifetimeSequenceKeepsLevelAboveStartAndTracksMax() {
        var level = ADAPTIVE_START_LEVEL
        var max = ADAPTIVE_START_LEVEL
        val results = listOf(true, true, true, false, false, false, false, true)
        for (won in results) {
            val next = levelAfter(level, won)
            max = nextMaxLevel(max, level, next)
            level = next
            assertTrue(level >= ADAPTIVE_START_LEVEL)
        }
        assertEquals(2, level)
        assertEquals(4, max)
    }

    @Test
    fun modeLabelDescribesAllModes() {
        assertEquals("Adaptiv · Level 7", modeLabel(Difficulty.EASY, false, 7))
        assertEquals("Mittel (Tagesrätsel)", modeLabel(Difficulty.MEDIUM, true, null))
        assertEquals("Schwer", modeLabel(Difficulty.HARD, false, null))
    }
}
