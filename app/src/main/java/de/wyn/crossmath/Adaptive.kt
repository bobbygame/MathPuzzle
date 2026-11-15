package de.wyn.crossmath

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

fun adaptiveParams(level: Int): GenParams {
    val l = level.coerceAtLeast(ADAPTIVE_START_LEVEL)
    val opHide = if (l < LEVEL_HIDE_OPS) {
        0f
    } else {
        (0.1f + 0.004f * (l - LEVEL_HIDE_OPS)).coerceAtMost(0.5f)
    }
    return GenParams(
        maxNum = minOf(99, 10 + l),
        ops = adaptiveOps(l),
        hideRatio = (0.3f + 0.006f * l).coerceAtMost(0.75f),
        opHideRatio = opHide,
        equations = minOf(10, 3 + l / 8),
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
