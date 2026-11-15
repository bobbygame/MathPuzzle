package de.wyn.crossmath

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource

private fun describe(d: Difficulty): String {
    val hints = when (d.maxHints) {
        0 -> "keine Hinweise"
        1 -> "1 Hinweis"
        else -> "${d.maxHints} Hinweise"
    }
    val ops = if (d.opHideRatio > 0f) " · Operatoren versteckt" else ""
    return "$hints$ops"
}

@Composable
fun HomeScreen(
    vm: GameViewModel,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit
) {
    val stats by vm.stats.collectAsState(initial = emptyMap())
    var confirmDifficulty by remember { mutableStateOf<Difficulty?>(null) }
    var confirmDaily by remember { mutableStateOf(false) }
    var confirmOpen by remember { mutableStateOf(false) }

    fun requestStart(d: Difficulty?, daily: Boolean) {
        if (vm.canResume) {
            confirmDifficulty = d
            confirmDaily = daily
            confirmOpen = true
        } else if (daily) {
            vm.newDaily()
        } else if (d != null) {
            vm.newGame(d)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "MathPuzzle Logo",
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(24.dp))
        )

        Text(
            text = "MathPuzzle",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        val resume = vm.resumeText()
        if (resume != null) {
            Button(
                onClick = { vm.resume() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Fortsetzen", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(resume, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Text(
            text = "Neues Spiel",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )

        Difficulty.values().forEach { d ->
            val s = stats[d]
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { requestStart(d, false) }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = d.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = describe(d),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Gelöst: ${s?.solved ?: 0} · Bestzeit: " +
                                (s?.bestSeconds?.let { fmtTime(it) } ?: "–"),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { requestStart(null, true) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Tagesrätsel")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onOpenStats) { Text("Statistik") }
            TextButton(onClick = onOpenSettings) { Text("Einstellungen") }
        }
    }

    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text("Neues Rätsel starten?") },
            text = {
                Text(
                    "Dein laufendes Rätsel geht dabei verloren.",
                    textAlign = TextAlign.Start
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmOpen = false
                    if (confirmDaily) vm.newDaily()
                    else confirmDifficulty?.let { vm.newGame(it) }
                }) { Text("Neu starten") }
            },
            dismissButton = {
                TextButton(onClick = { confirmOpen = false }) { Text("Abbrechen") }
            }
        )
    }
}