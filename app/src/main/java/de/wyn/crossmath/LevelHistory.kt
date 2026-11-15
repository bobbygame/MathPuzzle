package de.wyn.crossmath

import kotlin.math.roundToInt

/** Ein Eintrag im Levelverlauf: Zeitpunkt und Level nach einem Gewinn oder Abbruch. */
data class LevelPoint(val time: Long, val level: Int)

const val MAX_LEVEL_POINTS = 3000

fun encodeLevelPoints(list: List<LevelPoint>): String =
    list.joinToString(";") { "${it.time},${it.level}" }

fun decodeLevelPoints(text: String?): List<LevelPoint> {
    if (text.isNullOrBlank()) return emptyList()
    return text.split(";").mapNotNull { part ->
        val f = part.split(",")
        if (f.size != 2) {
            null
        } else {
            val t = f[0].toLongOrNull()
            val l = f[1].toIntOrNull()
            if (t == null || l == null) null else LevelPoint(t, l)
        }
    }
}

fun appendLevelPoint(list: List<LevelPoint>, point: LevelPoint): List<LevelPoint> =
    (list + point).takeLast(MAX_LEVEL_POINTS)

/** Reduziert auf höchstens max Punkte; erster und letzter Punkt bleiben erhalten. */
fun downsample(points: List<LevelPoint>, max: Int): List<LevelPoint> {
    if (max < 2 || points.size <= max) return points
    val step = (points.size - 1).toDouble() / (max - 1)
    return (0 until max).map { points[(it * step).roundToInt()] }
}
