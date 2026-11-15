package de.wyn.crossmath

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtDate(time: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(time))

/** Liniendiagramm des Levelverlaufs; ältester Punkt links, neuester rechts. */
@Composable
fun LevelChart(points: List<LevelPoint>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val baseColor = MaterialTheme.colorScheme.outlineVariant
    val data = remember(points) { downsample(points, 120) }

    Canvas(modifier) {
        if (data.isEmpty()) return@Canvas
        val minLevel = data.minOf { it.level }
        val maxLevel = data.maxOf { it.level }
        val span = (maxLevel - minLevel).coerceAtLeast(1)
        val n = data.size
        val w = size.width
        val h = size.height

        fun xAt(i: Int): Float = if (n == 1) w / 2f else w * i / (n - 1)
        fun yAt(level: Int): Float = h - h * 0.05f - h * 0.9f * (level - minLevel) / span

        drawLine(baseColor, Offset(0f, h), Offset(w, h), strokeWidth = 2f)
        for (i in 1 until n) {
            drawLine(
                lineColor,
                Offset(xAt(i - 1), yAt(data[i - 1].level)),
                Offset(xAt(i), yAt(data[i].level)),
                strokeWidth = 4f
            )
        }
        drawCircle(lineColor, 7f, Offset(xAt(n - 1), yAt(data[n - 1].level)))
    }
}
