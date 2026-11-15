package de.wyn.crossmath

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OperatorsTest {

    @Test
    fun applyOp_basicOperations() {
        assertEquals(7, applyOp(3, "+", 4))
        assertEquals(-2, applyOp(3, "-", 5))
        assertEquals(12, applyOp(3, "×", 4))
        assertEquals(3, applyOp(12, "÷", 4))
    }

    @Test
    fun applyOp_divisionRequiresExactResult() {
        assertNull(applyOp(7, "÷", 2))
        assertNull(applyOp(5, "÷", 0))
    }

    @Test
    fun applyOp_unknownOperatorReturnsNull() {
        assertNull(applyOp(1, "?", 1))
        assertNull(applyOp(1, "", 1))
    }

    @Test
    fun isOp_recognisesOperatorsOnly() {
        for (c in OPERATORS) assertTrue(isOp(c.toString()))
        assertFalse(isOp("="))
        assertFalse(isOp("5"))
        assertFalse(isOp(""))
    }
}

class GeneratorTest {

    private fun textOf(pz: Puzzle, p: Pos): String = pz.cells.getValue(p)

    private fun assertWellFormed(pz: Puzzle, d: Difficulty) {
        assertEquals(11, pz.size)
        assertEquals(d.equations, pz.equations.size)

        for (p in pz.cells.keys) {
            assertTrue(p.first in 0 until pz.size)
            assertTrue(p.second in 0 until pz.size)
        }

        for (eq in pz.equations) {
            assertEquals(5, eq.cells.size)
            val t = eq.cells.map { textOf(pz, it) }
            assertEquals("=", t[3])
            assertTrue(isOp(t[1]))
            val a = t[0].toInt()
            val b = t[2].toInt()
            val c = t[4].toInt()
            assertEquals("Gleichung stimmt nicht: $t", c, applyOp(a, t[1], b))
            assertTrue(maxOf(a, b, c) <= 99)
        }

        val usage = HashMap<Pos, Int>()
        for (eq in pz.equations) for (p in eq.cells) usage.merge(p, 1, Int::plus)
        assertTrue(usage.values.all { it <= 2 })
        assertEquals(usage.keys, pz.cells.keys)
    }

    private fun assertHiddenAndTilesConsistent(pz: Puzzle) {
        assertTrue(pz.hidden.all { it in pz.cells })
        assertTrue(pz.hidden.none { textOf(pz, it) == "=" })
        assertEquals(pz.hidden.size, pz.tiles.size)
        val expected = pz.hidden.map { textOf(pz, it) }.sorted()
        assertEquals(expected, pz.tiles.sorted())
    }

    @Test
    fun create_producesWellFormedPuzzles_forAllDifficulties() {
        for (d in Difficulty.values()) {
            val pz = Generator.create(d, Random(42))
            assertWellFormed(pz, d)
            assertHiddenAndTilesConsistent(pz)
        }
    }

    @Test
    fun create_hidesAtLeastThreeNumbers() {
        for (d in Difficulty.values()) {
            val pz = Generator.create(d, Random(7))
            val hiddenNumbers = pz.hidden.count { !isOp(textOf(pz, it)) }
            assertTrue(hiddenNumbers >= 3)
        }
    }

    @Test
    fun create_easyNeverHidesOperators() {
        repeat(5) { seed ->
            val pz = Generator.create(Difficulty.EASY, Random(seed))
            assertTrue(pz.hidden.none { isOp(textOf(pz, it)) })
        }
    }

    @Test
    fun create_easyUsesOnlyPlusAndMinus() {
        val pz = Generator.create(Difficulty.EASY, Random(3))
        val ops = pz.cells.values.filter { isOp(it) }.toSet()
        assertTrue(ops.all { it == "+" || it == "-" })
    }

    @Test
    fun create_sameSeedGivesSamePuzzle() {
        val a = Generator.create(Difficulty.MEDIUM, Random(2024))
        val b = Generator.create(Difficulty.MEDIUM, Random(2024))
        assertEquals(a.cells, b.cells)
        assertEquals(a.hidden, b.hidden)
        assertEquals(a.tiles, b.tiles)
    }

    @Test
    fun create_differentSeedsGiveDifferentPuzzles() {
        val a = Generator.create(Difficulty.MEDIUM, Random(1))
        val b = Generator.create(Difficulty.MEDIUM, Random(2))
        assertFalse(a.cells == b.cells && a.hidden == b.hidden)
    }

    @Test
    fun puzzle_evaluateDelegatesToApplyOp() {
        val pz = Generator.create(Difficulty.EASY, Random(5))
        assertEquals(9, pz.evaluate(4, "+", 5))
        assertNull(pz.evaluate(7, "÷", 2))
    }
}
