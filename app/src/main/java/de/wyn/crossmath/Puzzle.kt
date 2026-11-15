package de.wyn.crossmath

import kotlin.random.Random

typealias Pos = Pair<Int, Int>

enum class Difficulty(
    val label: String,
    val maxNum: Int,
    val ops: String,
    val hideRatio: Float,
    val opHideRatio: Float,
    val equations: Int,
    val maxHints: Int
) {
    EASY("Einfach", 20, "+-", 0.35f, 0f, 4, 0),
    MEDIUM("Mittel", 50, "+-×", 0.5f, 0f, 6, 1),
    HARD("Schwer", 99, "+-×÷", 0.6f, 0.3f, 8, 2),
    EXPERT("Experte", 99, "+-×÷", 0.75f, 0.5f, 10, 3)
}

data class Equation(val cells: List<Pos>)

const val OPERATORS = "+-×÷"

fun isOp(text: String): Boolean = text.isNotEmpty() && text[0] in OPERATORS

fun applyOp(a: Int, op: String, b: Int): Int? = when (op) {
    "+" -> a + b
    "-" -> a - b
    "×" -> a * b
    "÷" -> if (b != 0 && a % b == 0) a / b else null
    else -> null
}

class Puzzle(
    val size: Int,
    val cells: Map<Pos, String>,
    val equations: List<Equation>,
    val hidden: Set<Pos>,
    val tiles: List<String>
) {
    fun evaluate(a: Int, op: String, b: Int): Int? = applyOp(a, op, b)
}

object Generator {
    private const val N = 11

    fun create(d: Difficulty, rnd: Random = Random.Default): Puzzle {
        while (true) {
            val (grid, eqs) = buildGrid(d, rnd) ?: continue
            val numbers = grid.filter { !isOp(it.value) && it.value != "=" }.keys.shuffled(rnd)
            val opCells = grid.filter { isOp(it.value) }.keys.shuffled(rnd)
            val targetOp = (opCells.size * d.opHideRatio + 0.5f).toInt()
            val targetNum = (numbers.size * d.hideRatio).toInt().coerceAtLeast(3)

            val hidden = LinkedHashSet<Pos>()
            for (p in opCells) {
                if (hidden.size >= targetOp) break
                hidden.add(p)
                if (countSolutions(grid, eqs, hidden) != 1) hidden.remove(p)
            }
            val opCount = hidden.size
            for (p in numbers) {
                if (hidden.size - opCount >= targetNum) break
                hidden.add(p)
                if (countSolutions(grid, eqs, hidden) != 1) hidden.remove(p)
            }
            if (hidden.size - opCount < 3) continue

            val tiles = hidden.map { grid.getValue(it) }.sortedWith(
                compareBy({ isOp(it) }, { if (isOp(it)) OPERATORS.indexOf(it[0]) else it.toInt() })
            )
            return Puzzle(N, grid, eqs, hidden, tiles)
        }
    }

    private fun countSolutions(
        cells: Map<Pos, String>,
        eqs: List<Equation>,
        hidden: Set<Pos>,
        maxNodes: Int = 60_000
    ): Int {
        val order = ArrayList<Pos>()
        for (e in eqs) for (c in e.cells) if (c in hidden && c !in order) order.add(c)
        val idxOf = order.withIndex().associate { it.value to it.index }
        val checkAt = HashMap<Int, MutableList<Equation>>()
        for (e in eqs) {
            val last = e.cells.mapNotNull { idxOf[it] }.maxOrNull() ?: continue
            checkAt.getOrPut(last) { mutableListOf() }.add(e)
        }
        val pool = HashMap<String, Int>()
        for (h in hidden) pool.merge(cells.getValue(h), 1, Int::plus)
        val values = HashMap<Pos, String>()
        var count = 0
        var nodes = 0
        var aborted = false

        fun textOf(p: Pos): String = if (p in hidden) values.getValue(p) else cells.getValue(p)

        fun ok(e: Equation): Boolean {
            val a = textOf(e.cells[0]).toInt()
            val op = textOf(e.cells[1])
            val b = textOf(e.cells[2]).toInt()
            val c = textOf(e.cells[4]).toInt()
            return applyOp(a, op, b) == c
        }

        fun rec(i: Int) {
            if (aborted || count >= 2) return
            nodes++
            if (nodes > maxNodes) { aborted = true; return }
            if (i == order.size) { count++; return }
            val p = order[i]
            val wantOp = isOp(cells.getValue(p))
            for (v in pool.keys.toList()) {
                if (isOp(v) != wantOp) continue
                val n = pool[v] ?: 0
                if (n == 0) continue
                pool[v] = n - 1
                values[p] = v
                if (checkAt[i]?.all { ok(it) } != false) rec(i + 1)
                pool[v] = n
                if (aborted || count >= 2) return
            }
        }

        rec(0)
        return if (aborted) -1 else count
    }

    private fun randomEq(d: Difficulty, rnd: Random, idx: Int?, v: String?): List<String>? {
        repeat(300) {
            val op = d.ops.random(rnd)
            val a: Int
            val b: Int
            val c: Int
            when (op) {
                '+' -> { a = rnd.nextInt(1, d.maxNum); b = rnd.nextInt(1, d.maxNum); c = a + b }
                '-' -> { a = rnd.nextInt(2, d.maxNum + 1); b = rnd.nextInt(1, a); c = a - b }
                '×' -> { a = rnd.nextInt(2, 13); b = rnd.nextInt(2, 13); c = a * b }
                else -> { b = rnd.nextInt(2, 13); c = rnd.nextInt(2, 13); a = b * c }
            }
            if (maxOf(a, b, c) <= 99) {
                val t = listOf("$a", "$op", "$b", "=", "$c")
                if (idx == null || t[idx] == v) return t
            }
        }
        return null
    }

    private fun buildGrid(d: Difficulty, rnd: Random): Pair<Map<Pos, String>, List<Equation>>? {
        val grid = HashMap<Pos, String>()
        val horiz = HashSet<Pos>()
        val vert = HashSet<Pos>()
        val eqs = ArrayList<Equation>()

        fun fits(start: Pos, h: Boolean, t: List<String>): Boolean {
            val dr = if (h) 0 else 1
            val dc = if (h) 1 else 0
            val pr = dc
            val pc = dr
            val before = Pair(start.first - dr, start.second - dc)
            val after = Pair(start.first + 5 * dr, start.second + 5 * dc)
            if (grid.containsKey(before) || grid.containsKey(after)) return false
            var crossings = 0
            for (i in 0 until 5) {
                val p = Pair(start.first + i * dr, start.second + i * dc)
                if (p.first !in 0 until N || p.second !in 0 until N) return false
                val ex = grid[p]
                if (ex != null) {
                    if (ex != t[i]) return false
                    if (if (h) horiz.contains(p) else vert.contains(p)) return false
                    crossings++
                } else {
                    val n1 = Pair(p.first + pr, p.second + pc)
                    val n2 = Pair(p.first - pr, p.second - pc)
                    if (grid.containsKey(n1) || grid.containsKey(n2)) return false
                }
            }
            return crossings == 1
        }

        fun place(start: Pos, h: Boolean, t: List<String>) {
            val cs = (0 until 5).map { i ->
                if (h) Pair(start.first, start.second + i) else Pair(start.first + i, start.second)
            }
            cs.forEachIndexed { i, p ->
                grid[p] = t[i]
                if (h) horiz.add(p) else vert.add(p)
            }
            eqs.add(Equation(cs))
        }

        val first = randomEq(d, rnd, null, null) ?: return null
        place(Pair(N / 2, rnd.nextInt(1, N - 5)), true, first)

        var attempts = 0
        while (eqs.size < d.equations && attempts++ < 400) {
            val base = eqs.random(rnd)
            val i = listOf(0, 2, 4).random(rnd)
            val p = base.cells[i]
            val h = !horiz.contains(p)
            val j = listOf(0, 2, 4).random(rnd)
            val t = randomEq(d, rnd, j, grid[p]) ?: continue
            val start = if (h) Pair(p.first, p.second - j) else Pair(p.first - j, p.second)
            if (fits(start, h, t)) place(start, h, t)
        }
        if (eqs.size < d.equations) return null
        return Pair(grid, eqs)
    }
}