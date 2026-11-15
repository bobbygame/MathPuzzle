package de.wyn.crossmath

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CrossMathApp(vm)
        }
    }

    override fun onStart() {
        super.onStart()
        vm.paused = false
    }

    override fun onStop() {
        vm.paused = true
        vm.saveNow()

        super.onStop()
    }
}

fun fmtTime(s: Int): String = "%02d:%02d".format(s / 60, s % 60)

class DragState {
    var active by mutableStateOf(false)
    var tile by mutableIntStateOf(-1)
    var text by mutableStateOf("")
    var fromCell by mutableStateOf<Pos?>(null)

    // Position der schwebenden Kachel relativ zum Compose-Fenster.
    var topLeft by mutableStateOf(Offset.Zero)

    var sizePx by mutableFloatStateOf(0f)
    var hover by mutableStateOf<Pos?>(null)

    // Position des Containers, in dem die Kachel gezeichnet wird.
    var overlayOrigin by mutableStateOf(Offset.Zero)

    // Fingerposition relativ zum Compose-Fenster.
    private var fingerPosition = Offset.Zero
    private var fingerGapPx = 0f

    val cellBounds = HashMap<Pos, Rect>()

    fun start(
        vm: GameViewModel,
        tile: Int,
        text: String,
        sourceOrigin: Offset,
        touchPosition: Offset,
        sizePx: Float,
        gapPx: Float,
        from: Pos?
    ) {
        this.tile = tile
        this.text = text
        this.sizePx = sizePx
        this.fromCell = from
        this.fingerGapPx = gapPx

        fingerPosition = sourceOrigin + touchPosition

        active = true
        updateTilePosition()
        updateHover(vm)
    }

    fun move(vm: GameViewModel, delta: Offset) {
        fingerPosition += delta
        updateTilePosition()
        updateHover(vm)
    }

    private fun updateTilePosition() {
        // Rechte Kachelkante liegt knapp links neben dem Finger.
        // Die Kachelmitte bleibt auf Höhe des Fingers.
        topLeft = Offset(
            x = fingerPosition.x - sizePx - fingerGapPx,
            y = fingerPosition.y - sizePx / 2f
        )
    }

    private fun updateHover(vm: GameViewModel) {
        // Das Feld direkt unter dem Finger ist das Ziel.
        // Der Abstand der sichtbaren Kachel spielt dabei keine Rolle.
        hover = cellBounds.entries.firstOrNull { entry ->
            entry.key != fromCell &&
                    entry.value.contains(fingerPosition) &&
                    vm.canPlace(entry.key, tile)
        }?.key
    }

    fun end(vm: GameViewModel) {
        val target = hover

        if (active && target != null) {
            vm.dropTile(tile, fromCell, target)
        }

        reset()
    }

    fun reset() {
        active = false
        hover = null
        fromCell = null
        tile = -1
    }
}

@Composable
fun GameScreen(vm: GameViewModel) {
    val stats by vm.stats.collectAsState(initial = emptyMap())
    var showStats by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val drag = remember { DragState() }
    val settings = LocalAppSettings.current
    val currentWinHaptics by rememberUpdatedState(settings.winHaptics)
    val pz = vm.puzzle

    LaunchedEffect(vm.showWin) {
        if (vm.showWin && currentWinHaptics) {
            haptic.performHapticFeedback(
                HapticFeedbackType.LongPress
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                drag.overlayOrigin = it.positionInRoot()
            }
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Difficulty.values().forEach { d ->
                    FilterChip(
                        selected = vm.difficulty == d && !vm.isDaily,
                        onClick = { vm.newGame(d) },
                        label = { Text(d.label) }
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "⏱ ${fmtTime(vm.seconds)}" + if (vm.isDaily) "  · Tagesrätsel" else "",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Row {
                    TextButton(onClick = { vm.newDaily() }) { Text("Täglich") }
                    TextButton(onClick = { showStats = true }) { Text("Statistik") }
                }
            }

            if (pz == null || vm.loading) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    val cell = maxWidth / pz.size
                    Column {
                        for (r in 0 until pz.size) {
                            Row {
                                for (c in 0 until pz.size) GridCell(vm, pz, drag, Pair(r, c), cell)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            FlowTiles(vm, drag)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.undo() }) { Text("Zurück") }
                OutlinedButton(
                    onClick = { vm.hint() },
                    enabled = vm.hintsLeft > 0 && !vm.solved
                ) { Text("Hinweis (${vm.hintsLeft})") }
                Button(onClick = { vm.newGame() }) { Text("Neu") }
            }
        }

        if (drag.active) FloatingTile(drag)
    }

    if (vm.showWin) {
        AlertDialog(
            onDismissRequest = { vm.showWin = false },
            title = { Text("Geschafft! 🎉") },
            text = {
                Column {
                    Text("Zeit: ${fmtTime(vm.seconds)}")
                    Text("Hinweise: ${vm.hintsUsed} / ${vm.difficulty.maxHints}")
                    stats[vm.difficulty]?.bestSeconds?.let { Text("Bestzeit: ${fmtTime(it)}") }
                }
            },
            confirmButton = {
                Button(onClick = { vm.showWin = false; vm.newGame() }) { Text("Neues Rätsel") }
            },
            dismissButton = {
                TextButton(onClick = { vm.showWin = false }) { Text("Schließen") }
            }
        )
    }

    if (showStats) {
        AlertDialog(
            onDismissRequest = { showStats = false },
            title = { Text("Statistik") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Difficulty.values().forEach { d ->
                        val s = stats[d]
                        Text(
                            "${d.label}: ${s?.solved ?: 0} gelöst, Bestzeit " +
                                    (s?.bestSeconds?.let { fmtTime(it) } ?: "–")
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStats = false }) { Text("OK") } }
        )
    }
}

@Composable
fun FloatingTile(drag: DragState) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val op = isOp(drag.text)

    val localPosition = drag.topLeft - drag.overlayOrigin

    Box(
        modifier = Modifier
            .absoluteOffset {
                IntOffset(
                    x = localPosition.x.roundToInt(),
                    y = localPosition.y.roundToInt()
                )
            }
            .size(
                with(density) {
                    drag.sizePx.toDp()
                }
            )
            .alpha(0.95f)
            .background(
                color = if (op) {
                    scheme.secondary
                } else {
                    scheme.primary
                },
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = drag.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = if (op) {
                scheme.onSecondary
            } else {
                scheme.onPrimary
            }
        )
    }
}

@Composable
fun GridCell(vm: GameViewModel, pz: Puzzle, drag: DragState, p: Pos, size: Dp) {
    val dragGapPx = with(LocalDensity.current) { 32.dp.toPx() }
    if (pz.cells[p] == null) {
        Spacer(Modifier.size(size))
        return
    }
    val dark = LocalAppDarkTheme.current
    val settings = LocalAppSettings.current
    val scheme = MaterialTheme.colorScheme
    val isHidden = p in pz.hidden
    val placedTile = vm.placed[p]
    val states = pz.equations.filter { p in it.cells }.map { vm.stateOf(it) }
    val color = when {
        settings.showErrors &&
                states.any { it == EqState.WRONG } -> {
            scheme.errorContainer
        }

        settings.showCorrect &&
                states.isNotEmpty() &&
                states.all { it == EqState.OK } -> {
            if (dark) {
                Color(0xFF1B5E20)
            } else {
                Color(0xFFC8E6C9)
            }
        }

        isHidden -> scheme.primaryContainer

        else -> scheme.surfaceVariant
    }
    val borderColor = when {
        drag.hover == p -> scheme.primary
        vm.selectedCell == p -> scheme.tertiary
        else -> null
    }
    val shownText =
        if (drag.active && drag.fromCell == p) "" else (vm.valueAt(p) ?: "")

    var base = Modifier
        .size(size)
        .padding(1.dp)
        .background(color, RoundedCornerShape(4.dp))
    if (borderColor != null) base = base.border(2.dp, borderColor, RoundedCornerShape(4.dp))
    if (isHidden) {
        base = base
            .onGloballyPositioned { drag.cellBounds[p] = it.boundsInRoot() }
            .clickable { vm.tapCell(p) }
            .pointerInput(p, placedTile) {
                if (placedTile == null) return@pointerInput
                detectDragGestures(
                    onDragStart = { touchPosition ->
                        val sourceOrigin =
                            drag.cellBounds[p]?.topLeft ?: Offset.Zero

                        drag.start(
                            vm = vm,
                            tile = placedTile,
                            text = pz.tiles[placedTile],
                            sourceOrigin = sourceOrigin,
                            touchPosition = touchPosition,
                            sizePx = this.size.width.toFloat(),
                            gapPx = dragGapPx,
                            from = p
                        )
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        drag.move(vm, amount)
                    },
                    onDragEnd = { drag.end(vm) },
                    onDragCancel = { drag.reset() }
                )
            }
    }

    Box(base, contentAlignment = Alignment.Center) {
        Text(
            text = shownText,
            fontSize = (size.value * 0.45f).sp,
            fontWeight = if (isHidden) FontWeight.Bold else FontWeight.Normal,
            color = scheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowTiles(vm: GameViewModel, drag: DragState) {
    val dragGapPx = with(LocalDensity.current) { 32.dp.toPx() }
    val pz = vm.puzzle ?: return
    val scheme = MaterialTheme.colorScheme
    val used = vm.usedTiles()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        pz.tiles.forEachIndexed { i, v ->
            val isUsed = i in used
            val op = isOp(v)
            val selected = vm.selectedTile == i
            val dragging = drag.active && drag.fromCell == null && drag.tile == i
            val bg = when {
                isUsed -> scheme.surfaceVariant
                selected -> scheme.tertiary
                op -> scheme.secondary
                else -> scheme.primary
            }
            val fg = when {
                selected -> scheme.onTertiary
                op -> scheme.onSecondary
                else -> scheme.onPrimary
            }
            var origin by remember { mutableStateOf(Offset.Zero) }
            Box(
                Modifier
                    .size(48.dp)
                    .alpha(if (dragging) 0.3f else 1f)
                    .background(bg, RoundedCornerShape(8.dp))
                    .onGloballyPositioned { origin = it.positionInRoot() }
                    .clickable(enabled = !isUsed) { vm.tapTile(i) }
                    .pointerInput(i, isUsed) {
                        if (isUsed) return@pointerInput
                        detectDragGestures(
                            onDragStart = { touchPosition ->
                                drag.start(
                                    vm = vm,
                                    tile = i,
                                    text = v,
                                    sourceOrigin = origin,
                                    touchPosition = touchPosition,
                                    sizePx = this.size.width.toFloat(),
                                    gapPx = dragGapPx,
                                    from = null
                                )
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                drag.move(vm, amount)
                            },
                            onDragEnd = { drag.end(vm) },
                            onDragCancel = { drag.reset() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isUsed) "" else v,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = fg
                )
            }
        }
    }
}