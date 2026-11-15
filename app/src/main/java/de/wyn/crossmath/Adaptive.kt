package de.wyn.crossmath

import kotlin.math.exp

/** Adaptiver Schwierigkeitsmodus: stufenloses Level statt fester Stufen. */

const val ADAPTIVE_START_LEVEL = 1
const val ADAPTIVE_WIN_STEP = 1
const val ADAPTIVE_LOSS_STEP = 2
const val ADAPTIVE_MAX_HINTS = 6
const val ADAPTIVE_HINT_INTERVAL = 20

const val LEVEL_HIDE_OPS = 30
const val LEVEL_MULTIPLY = 60
const val LEVEL_DIVIDE = 120
const val LEVEL_PLUS_MINUS_DECAY = 200

/** Kurvenstärke in Prozent: skaliert, wie schnell Zahlenraum, Gleichungen und Versteckanteil wachsen. */
const val CURVE_DEFAULT_PERCENT = 100
val CURVE_OPTIONS = listOf(50, 100, 150, 200)

fun curveLabel(percent: Int): String = when (percent) {
    50 -> "Gemächlich"
    100 -> "Normal"
    150 -> "Steil"
    200 -> "Sehr steil"
    else -> "$percent %"
}

fun levelAfterWin(level: Int): Int = level + ADAPTIVE_WIN_STEP

fun levelAfterLoss(level: Int): Int =
    (level - ADAPTIVE_LOSS_STEP).coerceAtLeast(ADAPTIVE_START_LEVEL)

/** Level 1-20: 0 Hinweise, 21-40: 1, 41-60: 2, ... maximal 6. */
fun adaptiveHints(level: Int): Int =
    ((level - 1) / ADAPTIVE_HINT_INTERVAL).coerceIn(0, ADAPTIVE_MAX_HINTS)

/** Operatoren, die in den Gleichungen vorkommen (und ab Level 30 versteckt werden können). */
fun adaptiveOps(level: Int): String = when {
    level >= LEVEL_DIVIDE -> "+-×÷"
    level >= LEVEL_MULTIPLY -> "+-×"
    else -> "+-"
}

/** Ab Level 200 sinkt der Anteil von + und - schrittweise von 50 % auf mindestens 10 %. */
fun adaptivePlusMinusShare(level: Int): Float? =
    if (level < LEVEL_PLUS_MINUS_DECAY) {
        null
    } else {
        (0.5f - 0.005f * (level - LEVEL_PLUS_MINUS_DECAY)).coerceAtLeast(0.1f)
    }

/** Sättigungskurve: 0 bei x = 0, nähert sich 1 an, ohne es je zu erreichen. */
private fun saturate(x: Float, scale: Float): Float =
    (1.0 - exp(-(x / scale).toDouble())).toFloat()

/**
 * Generator-Parameter für ein Level.
 * Operator-Schwellen und Hinweise nutzen das echte Level, die Größen unten das
 * wirksame Level e = Level · Kurvenstärke.
 */
fun adaptiveParams(level: Int, curvePercent: Int = CURVE_DEFAULT_PERCENT): GenParams {
    val l = level.coerceAtLeast(ADAPTIVE_START_LEVEL)
    val e = l * curvePercent / 100f
    val opHide = if (l < LEVEL_HIDE_OPS) {
        0f
    } else {
        (0.1f + 0.7f * saturate((e - LEVEL_HIDE_OPS).coerceAtLeast(0f), 200f)).coerceAtMost(0.8f)
    }
    return GenParams(
        maxNum = minOf(99, 10 + e.toInt()),
        ops = adaptiveOps(l),
        hideRatio = (0.3f + 0.6f * saturate(e, 150f)).coerceAtMost(0.9f),
        opHideRatio = opHide,
        equations = minOf(10, 3 + (e / 8f).toInt()),
        plusMinusShare = adaptivePlusMinusShare(l)
    )
}

/** Zuordnung zu einer festen Stufe, nur für Speicherstand und Protokoll. */
fun adaptiveBucket(level: Int): Difficulty = when {
    level < LEVEL_HIDE_OPS -> Difficulty.EASY
    level < LEVEL_MULTIPLY -> Difficulty.MEDIUM
    level < LEVEL_DIVIDE -> Difficulty.HARD
    else -> Difficulty.EXPERT
}
