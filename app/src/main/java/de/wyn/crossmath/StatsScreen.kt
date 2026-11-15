package de.wyn.crossmath

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.Locale

enum class Period(val label: String) {
    DAY("Tag"),
    WEEK("Woche"),
    MONTH("Monat"),
    YEAR("Jahr")
}

data class PeriodStats(
    val key: Long,
    val label: String,
    val solved: Int,
    val totalSeconds: Int,
    val hintsUsed: Int,
    val noHint: Int,
    val perDifficulty: Map<Difficulty, Int>
)

private val monthNames = listOf(
    "Januar", "Februar", "März", "April", "Mai", "Juni",
    "Juli", "August", "September", "Oktober", "November", "Dezember"
)

fun fmtPlay(s: Int): String =
    if (s >= 3600) "${s / 3600} h ${(s % 3600) / 60} min"
    else "${s / 60} min ${s % 60} s"

fun groupRecords(records: List<SolveRecord>, period: Period): List<PeriodStats> {
    val cal = Calendar.getInstance(Locale.GERMANY)
    val groups = LinkedHashMap<Long, MutableList<SolveRecord>>()
    val labels = HashMap<Long, String>()

    for (r in records) {
        cal.timeInMillis = r.time
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH)
        val d = cal.get(Calendar.DAY_OF_MONTH)

        val key: Long
        val label: String
        when (period) {
            Period.DAY -> {
                key = y * 10000L + (m + 1) * 100L + d
                label = "%02d.%02d.%d".format(d, m + 1, y)
            }
            Period.WEEK -> {
                val wy = cal.getWeekYear()
                val w = cal.get(Calendar.WEEK_OF_YEAR)
                key = wy * 100L + w
                label = "KW $w / $wy"
            }
            Period.MONTH -> {
                key = y * 100L + (m + 1)
                label = "${monthNames[m]} $y"
            }
            Period.YEAR -> {
                key = y.toLong()
                label = "$y"
            }
        }
        groups.getOrPut(key) { mutableListOf() }.add(r)
        labels[key] = label
    }

    return groups.map { (key, list) ->
        PeriodStats(
            key = key,
            label = labels.getValue(key),
            solved = list.size,
            totalSeconds = list.sumOf { it.seconds },
            hintsUsed = list.sumOf { it.hints },
            noHint = list.count { it.hints == 0 },
            perDifficulty = list.groupingBy { it.difficulty }.eachCount()
        )
    }.sortedByDescending { it.key }
}

@Composable
fun StatsScreen(vm: GameViewModel, onBack: () -> Unit) {
    val records by vm.solveHistory.collectAsState(initial = emptyList())
    var period by remember { mutableStateOf(Period.DAY) }
    var filter by remember { mutableStateOf<Difficulty?>(null) }

    val filtered = remember(records, filter) {
        records.filter { filter == null || it.difficulty == filter }
    }
    val stats = remember(filtered, period) { groupRecords(filtered, period) }
    val barColor = MaterialTheme.colorScheme.primary

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("Zurück") }
        }
        item {
            Text("Statistik", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Period.values().forEach { p ->
                    FilterChip(
                        selected = period == p,
                        onClick = { period = p },
                        label = { Text(p.label) }
                    )
                }
            }
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filter == null,
                    onClick = { filter = null },
                    label = { Text("Alle") }
                )
                Difficulty.values().forEach { d ->
                    FilterChip(
                        selected = filter == d,
                        onClick = { filter = d },
                        label = { Text(d.label) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Text(
                    "Noch keine gelösten Rätsel im Protokoll. " +
                            "Sobald du eines löst, erscheint es hier."
                )
            }
        } else {
            item {
                val total = filtered.size
                val seconds = filtered.sumOf { it.seconds }
                val noHint = filtered.count { it.hints == 0 }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Gesamt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Gelöst: $total")
                        Text("Spielzeit: ${fmtPlay(seconds)}")
                        Text("Durchschnitt: ${fmtTime(seconds / total)}")
                        Text("Ohne Hinweis gelöst: $noHint")
                    }
                }
            }

            item {
                val data = stats.take(12).reversed()
                val max = data.maxOf { it.solved }.coerceAtLeast(1)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Gelöste Rätsel pro ${period.label}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Canvas(
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        ) {
                            val n = data.size
                            val gap = 8.dp.toPx()
                            val w = (size.width - gap * (n - 1)) / n
                            data.forEachIndexed { i, p ->
                                val h = size.height * p.solved / max
                                drawRoundRect(
                                    color = barColor,
                                    topLeft = Offset(i * (w + gap), size.height - h),
                                    size = Size(w, h),
                                    cornerRadius = CornerRadius(4.dp.toPx())
                                )
                            }
                        }
                        Text(
                            "Höchster Balken: $max · neueste Zeiträume rechts (max. 12)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            items(stats, key = { it.key }) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(p.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${p.solved} gelöst · Ø ${fmtTime(p.totalSeconds / p.solved)}")
                        Text("Spielzeit: ${fmtPlay(p.totalSeconds)}")
                        Text("Ohne Hinweis: ${p.noHint} · Hinweise genutzt: ${p.hintsUsed}")
                        val parts = Difficulty.values().mapNotNull { d ->
                            p.perDifficulty[d]?.let { "${d.label} $it" }
                        }
                        if (parts.isNotEmpty()) {
                            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}