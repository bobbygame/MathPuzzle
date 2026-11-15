package de.wyn.crossmath

import org.json.JSONArray
import org.json.JSONObject

data class SavedGame(
    val difficulty: Difficulty,
    val daily: Boolean,
    val puzzle: Puzzle,
    val placed: Map<Pos, Int>,
    val history: List<Pos>,
    val seconds: Int,
    val hintsUsed: Int,
    /** Level eines adaptiven Rätsels, null bei festen Stufen und Tagesrätsel. */
    val adaptiveLevel: Int? = null
)

object SaveCodec {

    private fun posArray(p: Pos) = JSONArray().put(p.first).put(p.second)

    fun encode(s: SavedGame): String {
        val o = JSONObject()
        o.put("d", s.difficulty.name)
        o.put("daily", s.daily)
        o.put("sec", s.seconds)
        o.put("hints", s.hintsUsed)
        o.put("size", s.puzzle.size)
        s.adaptiveLevel?.let { o.put("lvl", it) }

        val cells = JSONArray()
        for ((p, t) in s.puzzle.cells) cells.put(JSONArray().put(p.first).put(p.second).put(t))
        o.put("cells", cells)

        val eqs = JSONArray()
        for (e in s.puzzle.equations) {
            val a = JSONArray()
            for (c in e.cells) a.put(posArray(c))
            eqs.put(a)
        }
        o.put("eqs", eqs)

        val hidden = JSONArray()
        for (p in s.puzzle.hidden) hidden.put(posArray(p))
        o.put("hidden", hidden)

        val tiles = JSONArray()
        for (t in s.puzzle.tiles) tiles.put(t)
        o.put("tiles", tiles)

        val placed = JSONArray()
        for ((p, i) in s.placed) placed.put(JSONArray().put(p.first).put(p.second).put(i))
        o.put("placed", placed)

        val hist = JSONArray()
        for (p in s.history) hist.put(posArray(p))
        o.put("hist", hist)
        return o.toString()
    }

    fun decode(text: String): SavedGame? = try {
        val o = JSONObject(text)
        val cells = HashMap<Pos, String>()
        val ca = o.getJSONArray("cells")
        for (i in 0 until ca.length()) {
            val a = ca.getJSONArray(i)
            cells[Pair(a.getInt(0), a.getInt(1))] = a.getString(2)
        }
        val eqs = ArrayList<Equation>()
        val ea = o.getJSONArray("eqs")
        for (i in 0 until ea.length()) {
            val row = ea.getJSONArray(i)
            val list = (0 until row.length()).map {
                val a = row.getJSONArray(it)
                Pair(a.getInt(0), a.getInt(1))
            }
            eqs.add(Equation(list))
        }
        val hidden = LinkedHashSet<Pos>()
        val ha = o.getJSONArray("hidden")
        for (i in 0 until ha.length()) {
            val a = ha.getJSONArray(i)
            hidden.add(Pair(a.getInt(0), a.getInt(1)))
        }
        val tiles = ArrayList<String>()
        val ta = o.getJSONArray("tiles")
        for (i in 0 until ta.length()) tiles.add(ta.getString(i))

        val placed = HashMap<Pos, Int>()
        val pa = o.getJSONArray("placed")
        for (i in 0 until pa.length()) {
            val a = pa.getJSONArray(i)
            placed[Pair(a.getInt(0), a.getInt(1))] = a.getInt(2)
        }
        val hist = ArrayList<Pos>()
        val ra = o.getJSONArray("hist")
        for (i in 0 until ra.length()) {
            val a = ra.getJSONArray(i)
            hist.add(Pair(a.getInt(0), a.getInt(1)))
        }
        SavedGame(
            difficulty = Difficulty.valueOf(o.getString("d")),
            daily = o.getBoolean("daily"),
            puzzle = Puzzle(o.getInt("size"), cells, eqs, hidden, tiles),
            placed = placed,
            history = hist,
            seconds = o.getInt("sec"),
            hintsUsed = o.getInt("hints"),
            adaptiveLevel = if (o.has("lvl")) o.getInt("lvl") else null
        )
    } catch (e: Exception) {
        null
    }
}
