package de.wyn.crossmath

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveCodecTest {

    private fun sampleGame(level: Int? = null, daily: Boolean = false): SavedGame {
        val pz = Generator.create(Difficulty.EASY, Random(11))
        val hidden = pz.hidden.toList()
        return SavedGame(
            difficulty = Difficulty.EASY,
            daily = daily,
            puzzle = pz,
            placed = mapOf(hidden[0] to 0, hidden[1] to 1),
            history = listOf(hidden[0], hidden[1]),
            seconds = 125,
            hintsUsed = 1,
            adaptiveLevel = level
        )
    }

    private fun assertSameGame(expected: SavedGame, actual: SavedGame) {
        assertEquals(expected.difficulty, actual.difficulty)
        assertEquals(expected.daily, actual.daily)
        assertEquals(expected.seconds, actual.seconds)
        assertEquals(expected.hintsUsed, actual.hintsUsed)
        assertEquals(expected.adaptiveLevel, actual.adaptiveLevel)
        assertEquals(expected.placed, actual.placed)
        assertEquals(expected.history, actual.history)
        assertEquals(expected.puzzle.size, actual.puzzle.size)
        assertEquals(expected.puzzle.cells, actual.puzzle.cells)
        assertEquals(expected.puzzle.equations, actual.puzzle.equations)
        assertEquals(expected.puzzle.hidden.toSet(), actual.puzzle.hidden.toSet())
        assertEquals(expected.puzzle.tiles, actual.puzzle.tiles)
    }

    @Test
    fun fixedGameSurvivesRoundTrip() {
        val game = sampleGame()
        val decoded = SaveCodec.decode(SaveCodec.encode(game))
        assertNotNull(decoded)
        assertSameGame(game, decoded!!)
        assertNull(decoded.adaptiveLevel)
    }

    @Test
    fun adaptiveGameKeepsItsLevel() {
        val game = sampleGame(level = 42)
        val decoded = SaveCodec.decode(SaveCodec.encode(game))
        assertNotNull(decoded)
        assertSameGame(game, decoded!!)
        assertEquals(42, decoded.adaptiveLevel)
    }

    @Test
    fun dailyFlagIsPreserved() {
        val decoded = SaveCodec.decode(SaveCodec.encode(sampleGame(daily = true)))
        assertNotNull(decoded)
        assertTrue(decoded!!.daily)
    }

    @Test
    fun fixedGameWritesNoLevelKey() {
        assertFalse(SaveCodec.encode(sampleGame()).contains("\"lvl\""))
        assertTrue(SaveCodec.encode(sampleGame(level = 3)).contains("\"lvl\""))
    }

    @Test
    fun saveFromBeforeAdaptiveModeStillLoads() {
        val legacy = """{"d":"EASY","daily":false,"sec":10,"hints":0,"size":11,
            "cells":[[5,1,"2"],[5,2,"+"],[5,3,"3"],[5,4,"="],[5,5,"5"]],
            "eqs":[[[5,1],[5,2],[5,3],[5,4],[5,5]]],
            "hidden":[[5,1]],"tiles":["2"],"placed":[],"hist":[]}"""
        val decoded = SaveCodec.decode(legacy)
        assertNotNull(decoded)
        assertNull(decoded!!.adaptiveLevel)
        assertEquals(10, decoded.seconds)
        assertEquals(5, decoded.puzzle.cells.size)
        assertEquals(listOf("2"), decoded.puzzle.tiles)
        assertEquals(1, decoded.puzzle.equations.size)
    }

    @Test
    fun brokenInputReturnsNull() {
        assertNull(SaveCodec.decode(""))
        assertNull(SaveCodec.decode("not json"))
        assertNull(SaveCodec.decode("{}"))
        val truncated = SaveCodec.encode(sampleGame()).take(40)
        assertNull(SaveCodec.decode(truncated))
    }

    @Test
    fun unknownDifficultyReturnsNull() {
        val json = SaveCodec.encode(sampleGame()).replace("\"EASY\"", "\"GODMODE\"")
        assertNull(SaveCodec.decode(json))
    }

    @Test
    fun invalidLevelValueReturnsNullInsteadOfGuessing() {
        val json = SaveCodec.encode(sampleGame(level = 5)).replace("\"lvl\":5", "\"lvl\":\"x\"")
        assertNull(SaveCodec.decode(json))
    }
}
