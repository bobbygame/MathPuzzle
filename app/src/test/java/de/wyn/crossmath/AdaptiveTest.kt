package de.wyn.crossmath

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveTest {

    @Test
    fun winRaisesLevelByOne() {
        assertEquals(2, levelAfterWin(1))
        assertEquals(51, levelAfterWin(50))
    }

    @Test
    fun lossLowersLevelByTwoButNotBelowStart() {
        assertEquals(8, levelAfterLoss(10))
        assertEquals(1, levelAfterLoss(3))
        assertEquals(1, levelAfterLoss(2))
        assertEquals(1, levelAfterLoss(1))
    }

    @Test
    fun hintsRiseEveryTwentyLevelsAndAreCapped() {
        assertEquals(0, adaptiveHints(1))
        assertEquals(0, adaptiveHints(20))
        assertEquals(1, adaptiveHints(21))
        assertEquals(1, adaptiveHints(40))
        assertEquals(2, adaptiveHints(41))
        assertEquals(2, adaptiveHints(60))
        assertEquals(5, adaptiveHints(101))
        assertEquals(6, adaptiveHints(121))
        assertEquals(6, adaptiveHints(5000))
    }

    @Test
    fun operatorsUnlockAtThresholds() {
        assertEquals("+-", adaptiveOps(1))
        assertEquals("+-", adaptiveOps(59))
        assertEquals("+-×", adaptiveOps(60))
        assertEquals("+-×", adaptiveOps(119))
        assertEquals("+-×÷", adaptiveOps(120))
    }

    @Test
    fun operatorsAreHiddenOnlyFromLevel30() {
        assertEquals(0f, adaptiveParams(1).opHideRatio, 0f)
        assertEquals(0f, adaptiveParams(29).opHideRatio, 0f)
        assertTrue(adaptiveParams(30).opHideRatio > 0f)
        assertTrue(adaptiveParams(10_000).opHideRatio <= 0.8f)
    }

    @Test
    fun plusMinusShareDecaysFromLevel200() {
        assertNull(adaptivePlusMinusShare(199))
        assertEquals(0.5f, adaptivePlusMinusShare(200)!!, 0.0001f)
        assertTrue(adaptivePlusMinusShare(240)!! < adaptivePlusMinusShare(220)!!)
        assertEquals(0.1f, adaptivePlusMinusShare(1000)!!, 0.0001f)
    }

    @Test
    fun paramsStayWithinLimits() {
        for (level in listOf(1, 10, 50, 100, 500, 100_000)) {
            for (curve in CURVE_OPTIONS) {
                val p = adaptiveParams(level, curve)
                assertTrue(p.maxNum in 10..99)
                assertTrue(p.equations in 3..10)
                assertTrue(p.hideRatio in 0.3f..0.9f)
                assertTrue(p.opHideRatio in 0f..0.8f)
            }
        }
        assertEquals(99, adaptiveParams(500).maxNum)
        assertEquals(10, adaptiveParams(500).equations)
    }

    @Test
    fun paramsGrowWithLevel() {
        assertTrue(adaptiveParams(40).maxNum > adaptiveParams(5).maxNum)
        assertTrue(adaptiveParams(40).equations > adaptiveParams(5).equations)
        assertTrue(adaptiveParams(40).hideRatio > adaptiveParams(5).hideRatio)
    }

    @Test
    fun hideRatioKeepsGrowingAtHighLevelsWithoutReachingLimit() {
        val at300 = adaptiveParams(300).hideRatio
        val at1000 = adaptiveParams(1000).hideRatio
        assertTrue(at1000 > at300)
        assertTrue(at1000 < 0.9f)
        assertTrue(adaptiveParams(1000).opHideRatio > adaptiveParams(300).opHideRatio)
    }

    @Test
    fun curveStrengthScalesGrowthButNotThresholds() {
        assertTrue(adaptiveParams(40, 200).maxNum > adaptiveParams(40, 100).maxNum)
        assertTrue(adaptiveParams(40, 50).maxNum < adaptiveParams(40, 100).maxNum)
        assertTrue(adaptiveParams(40, 200).hideRatio > adaptiveParams(40, 100).hideRatio)
        assertEquals(0f, adaptiveParams(29, 200).opHideRatio, 0f)
        assertEquals(adaptiveOps(59), adaptiveParams(59, 200).ops)
        assertEquals(adaptiveOps(60), adaptiveParams(60, 50).ops)
    }

    @Test
    fun curveLabelsCoverAllOptions() {
        assertEquals("Gemächlich", curveLabel(50))
        assertEquals("Normal", curveLabel(100))
        assertEquals("Steil", curveLabel(150))
        assertEquals("Sehr steil", curveLabel(200))
        assertTrue(CURVE_DEFAULT_PERCENT in CURVE_OPTIONS)
    }

    @Test
    fun bucketFollowsOperatorThresholds() {
        assertEquals(Difficulty.EASY, adaptiveBucket(1))
        assertEquals(Difficulty.MEDIUM, adaptiveBucket(30))
        assertEquals(Difficulty.HARD, adaptiveBucket(60))
        assertEquals(Difficulty.EXPERT, adaptiveBucket(120))
    }

    @Test
    fun generatedPuzzlesRespectLevelRules() {
        for (level in listOf(1, 29, 30, 60, 120, 250)) {
            val params = adaptiveParams(level)
            val pz = Generator.create(params, Random(level))
            assertEquals(params.equations, pz.equations.size)

            val allowed = adaptiveOps(level)
            for (eq in pz.equations) {
                val op = pz.cells.getValue(eq.cells[1])
                assertTrue("Operator $op bei Level $level", op[0] in allowed)
            }
            if (level < LEVEL_HIDE_OPS) {
                assertTrue(pz.hidden.none { isOp(pz.cells.getValue(it)) })
            }
        }
    }
}
