package de.wyn.crossmath

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class EqState { INCOMPLETE, OK, WRONG }

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = StatsRepository(app)
    val stats = repo.stats
    val solveHistory = repo.history
    val levelHistory = repo.levelHistory
    val maxLevel = repo.maxLevel

    var difficulty by mutableStateOf(Difficulty.EASY)
        private set
    var puzzle by mutableStateOf<Puzzle?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var isDaily by mutableStateOf(false)
        private set
    var seconds by mutableIntStateOf(0)
        private set
    var hintsUsed by mutableIntStateOf(0)
        private set
    var inGame by mutableStateOf(false)
        private set

    /** Gespeichertes Level des adaptiven Modus; wird beim nächsten adaptiven Rätsel verwendet. */
    var adaptiveLevel by mutableIntStateOf(ADAPTIVE_START_LEVEL)
        private set

    /** Kurvenstärke des adaptiven Modus in Prozent; nur im Hauptmenü änderbar. */
    var curvePercent by mutableIntStateOf(CURVE_DEFAULT_PERCENT)
        private set

    /** Level des gerade gespielten Rätsels; null, wenn es kein adaptives Rätsel ist. */
    var playLevel by mutableStateOf<Int?>(null)
        private set

    private var pendingSave by mutableStateOf<SavedGame?>(null)

    val placed = mutableStateMapOf<Pos, Int>()
    var selectedTile by mutableStateOf<Int?>(null)
    var selectedCell by mutableStateOf<Pos?>(null)
    var showWin by mutableStateOf(false)

    @Volatile
    var paused = false

    var settingsOpen = false

    private val history = ArrayDeque<Pos>()
    private var genJob: Job? = null
    private var timerJob: Job? = null
    private var recorded = false

    val maxHints: Int
        get() = maxHintsFor(playLevel, difficulty)

    val hintsLeft: Int
        get() = hintsLeftFor(maxHints, hintsUsed)

    val canResume: Boolean
        get() = (puzzle != null && !loading && !solved) || pendingSave != null

    init {
        startTimer()
        viewModelScope.launch {
            adaptiveLevel = repo.loadAdaptiveLevel()
            curvePercent = repo.loadCurve()
            val saved = repo.loadSave()?.let { SaveCodec.decode(it) }
            if (saved != null && puzzle == null && !inGame) pendingSave = saved
        }
    }

    fun setCurve(percent: Int) {
        curvePercent = percent
        viewModelScope.launch { repo.setCurve(percent) }
    }

    fun resumeText(): String? {
        val pz = puzzle
        return if (pz != null && !loading && !solved) {
            modeLabel(difficulty, isDaily, playLevel) + " · " + fmtTime(seconds)
        } else {
            pendingSave?.let {
                modeLabel(it.difficulty, it.daily, it.adaptiveLevel) + " · " + fmtTime(it.seconds)
            }
        }
    }

    fun resume() {
        val pz = puzzle
        if (pz != null && !loading && !solved) {
            inGame = true
        } else {
            pendingSave?.let {
                restore(it)
                pendingSave = null
                inGame = true
            }
        }
    }

    fun leaveToHome() {
        saveNow()
        inGame = false
    }

    private fun restore(s: SavedGame) {
        difficulty = s.difficulty
        isDaily = s.daily
        playLevel = s.adaptiveLevel
        puzzle = s.puzzle
        placed.clear()
        placed.putAll(s.placed)
        history.clear()
        history.addAll(s.history)
        seconds = s.seconds
        hintsUsed = s.hintsUsed
        selectedTile = null
        selectedCell = null
        recorded = false
        showWin = false
        loading = false
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (timerShouldRun(inGame, puzzle != null, loading, paused, settingsOpen, solved)) {
                    seconds++
                    if (seconds % 10 == 0) saveNow()
                }
            }
        }
    }

    /**
     * Neues Rätsel. Ohne Angaben wird im aktuellen Modus weitergespielt
     * (adaptiv bleibt adaptiv). Eine feste Stufe oder ein Seed wählt den Modus explizit.
     */
    fun newGame(d: Difficulty? = null, seed: Long? = null) {
        if (resolveStartMode(d, seed, playLevel) == StartMode.ADAPTIVE) {
            newAdaptive()
        } else {
            startGame(d ?: difficulty, seed, null)
        }
    }

    fun newAdaptive() {
        val level = adaptiveLevel
        startGame(adaptiveBucket(level), null, level)
    }

    fun newDaily() {
        newGame(Difficulty.MEDIUM, System.currentTimeMillis() / 86_400_000L)
    }

    /** Ein abgebrochenes, ungelöstes adaptives Rätsel gilt als verloren. */
    private fun penalizeIfAbandoned() {
        val abandoned = isAbandonedAdaptive(
            hasPuzzle = puzzle != null,
            playLevel = playLevel,
            loading = loading,
            solved = solved,
            pendingAdaptive = pendingSave?.adaptiveLevel != null
        )
        if (abandoned) {
            val next = levelAfter(adaptiveLevel, won = false)
            adaptiveLevel = next
            viewModelScope.launch { repo.recordAdaptiveLoss(next) }
        }
    }

    private fun startGame(d: Difficulty, seed: Long?, level: Int?) {
        penalizeIfAbandoned()
        genJob?.cancel()
        pendingSave = null
        inGame = true
        loading = true
        isDaily = seed != null
        val params = if (level != null) adaptiveParams(level, curvePercent) else d.params
        genJob = viewModelScope.launch {
            val pz = withContext(Dispatchers.Default) {
                Generator.create(params, if (seed != null) Random(seed) else Random.Default)
            }
            difficulty = d
            playLevel = level
            puzzle = pz
            placed.clear()
            history.clear()
            selectedTile = null
            selectedCell = null
            seconds = 0
            hintsUsed = 0
            recorded = false
            showWin = false
            loading = false
            saveNow()
        }
    }

    fun usedTiles(): Set<Int> = placed.values.toSet()

    fun canPlace(p: Pos, tile: Int): Boolean {
        val pz = puzzle ?: return false
        if (p !in pz.hidden || tile !in pz.tiles.indices) return false
        return isOp(pz.cells.getValue(p)) == isOp(pz.tiles[tile])
    }

    private fun put(p: Pos, tile: Int) {
        placed[p] = tile
        history.addLast(p)
        selectedTile = null
        selectedCell = null
        checkWin()
        saveNow()
    }

    fun tapTile(i: Int) {
        if (i in usedTiles()) return
        val c = selectedCell
        if (c != null) {
            if (canPlace(c, i)) put(c, i)
        } else {
            selectedTile = if (selectedTile == i) null else i
        }
    }

    fun tapCell(p: Pos) {
        val pz = puzzle ?: return
        if (p !in pz.hidden) return
        if (placed.containsKey(p)) {
            placed.remove(p)
            history.remove(p)
            saveNow()
            return
        }
        val t = selectedTile
        if (t != null) {
            if (canPlace(p, t)) put(p, t)
        } else {
            selectedCell = if (selectedCell == p) null else p
        }
    }

    fun dropTile(tile: Int, from: Pos?, to: Pos) {
        if (solved || !canPlace(to, tile)) return
        if (from == null) {
            if (tile in usedTiles()) return
            placed.remove(to)
            history.remove(to)
            placed[to] = tile
            history.addLast(to)
        } else {
            if (from == to || placed[from] != tile) return
            val displaced = placed[to]
            placed.remove(from)
            history.remove(from)
            if (displaced != null) {
                placed.remove(to)
                history.remove(to)
                if (canPlace(from, displaced)) {
                    placed[from] = displaced
                    history.addLast(from)
                }
            }
            placed[to] = tile
            history.addLast(to)
        }
        selectedTile = null
        selectedCell = null
        checkWin()
        saveNow()
    }

    fun undo() {
        history.removeLastOrNull()?.let { placed.remove(it) }
        saveNow()
    }

    fun hint() {
        val pz = puzzle ?: return
        if (solved || hintsLeft <= 0) return
        val p = pz.hidden.firstOrNull {
            val t = placed[it]
            t == null || pz.tiles[t] != pz.cells.getValue(it)
        } ?: return
        val want = pz.cells.getValue(p)
        placed.remove(p)
        history.remove(p)
        val free = pz.tiles.indices.firstOrNull { pz.tiles[it] == want && it !in usedTiles() }
        val idx = free ?: run {
            val key = placed.keys.firstOrNull { pz.tiles[placed.getValue(it)] == want }
            if (key == null) null else {
                val v = placed.getValue(key)
                placed.remove(key)
                history.remove(key)
                v
            }
        } ?: return
        hintsUsed++
        put(p, idx)
    }

    fun valueAt(p: Pos): String? {
        val pz = puzzle ?: return null
        return if (p !in pz.hidden) pz.cells[p] else placed[p]?.let { pz.tiles[it] }
    }

    fun stateOf(eq: Equation): EqState {
        val pz = puzzle ?: return EqState.INCOMPLETE
        val t = eq.cells.map { valueAt(it) }
        if (t.any { it == null }) return EqState.INCOMPLETE
        val a = t[0]!!.toInt()
        val b = t[2]!!.toInt()
        val c = t[4]!!.toInt()
        return if (pz.evaluate(a, t[1]!!, b) == c) EqState.OK else EqState.WRONG
    }

    val solved: Boolean
        get() = puzzle?.equations?.all { stateOf(it) == EqState.OK } ?: false

    private fun checkWin() {
        if (solved && !recorded) {
            recorded = true
            showWin = true
            val d = difficulty
            val s = seconds
            val clean = hintsUsed == 0
            val hints = hintsUsed
            val level = playLevel
            if (level != null) adaptiveLevel = levelAfter(level, won = true)
            viewModelScope.launch {
                if (level != null) {
                    repo.recordAdaptiveWin(level, s, hints)
                } else {
                    repo.recordWin(d, s, clean, hints)
                }
                repo.clearSave()
            }
        }
    }

    fun saveNow() {
        val pz = puzzle ?: return
        if (loading || solved) return
        val json = SaveCodec.encode(
            SavedGame(
                difficulty, isDaily, pz, placed.toMap(), history.toList(),
                seconds, hintsUsed, playLevel
            )
        )
        viewModelScope.launch { repo.saveGame(json) }
    }
}
