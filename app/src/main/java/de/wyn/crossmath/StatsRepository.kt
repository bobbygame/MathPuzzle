package de.wyn.crossmath

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stats")

data class DiffStats(val solved: Int, val bestSeconds: Int?)

data class SolveRecord(
    val time: Long,
    val difficulty: Difficulty,
    val seconds: Int,
    val hints: Int
)

private const val MAX_RECORDS = 5000

private fun encodeRecords(list: List<SolveRecord>): String =
    list.joinToString(";") {
        "${it.time},${it.difficulty.ordinal},${it.seconds},${it.hints}"
    }

private fun decodeRecords(text: String?): List<SolveRecord> {
    if (text.isNullOrBlank()) return emptyList()
    return text.split(";").mapNotNull { part ->
        val f = part.split(",")
        if (f.size != 4) {
            null
        } else {
            try {
                SolveRecord(
                    time = f[0].toLong(),
                    difficulty = Difficulty.values()[f[1].toInt()],
                    seconds = f[2].toInt(),
                    hints = f[3].toInt()
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

class StatsRepository(private val ctx: Context) {

    private val saveKey = stringPreferencesKey("savegame")
    private val historyKey = stringPreferencesKey("history")
    private val adaptiveLevelKey = intPreferencesKey("adaptive_level")

    val stats: Flow<Map<Difficulty, DiffStats>> = ctx.dataStore.data.map { prefs ->
        Difficulty.values().associateWith { d ->
            DiffStats(
                solved = prefs[intPreferencesKey("solved_${d.name}")] ?: 0,
                bestSeconds = prefs[intPreferencesKey("best_${d.name}")]
            )
        }
    }

    val history: Flow<List<SolveRecord>> = ctx.dataStore.data.map { prefs ->
        decodeRecords(prefs[historyKey])
    }

    suspend fun recordWin(d: Difficulty, seconds: Int, countBest: Boolean, hints: Int) {
        ctx.dataStore.edit { p ->
            val sk = intPreferencesKey("solved_${d.name}")
            p[sk] = (p[sk] ?: 0) + 1
            if (countBest) {
                val bk = intPreferencesKey("best_${d.name}")
                val old = p[bk]
                if (old == null || seconds < old) p[bk] = seconds
            }
            val list = decodeRecords(p[historyKey]) +
                    SolveRecord(System.currentTimeMillis(), d, seconds, hints)
            p[historyKey] = encodeRecords(list.takeLast(MAX_RECORDS))
        }
    }

    suspend fun loadAdaptiveLevel(): Int =
        ctx.dataStore.data.first()[adaptiveLevelKey] ?: ADAPTIVE_START_LEVEL

    suspend fun setAdaptiveLevel(level: Int) {
        ctx.dataStore.edit { it[adaptiveLevelKey] = level }
    }

    /**
     * Adaptiver Sieg: Eintrag im Protokoll (zählt für Statistik und Serie) und neues Level,
     * in einem Schritt gespeichert. Zähler und Bestzeiten der festen Stufen bleiben unberührt.
     */
    suspend fun recordAdaptiveWin(level: Int, seconds: Int, hints: Int) {
        ctx.dataStore.edit { p ->
            val list = decodeRecords(p[historyKey]) +
                    SolveRecord(System.currentTimeMillis(), adaptiveBucket(level), seconds, hints)
            p[historyKey] = encodeRecords(list.takeLast(MAX_RECORDS))
            p[adaptiveLevelKey] = levelAfterWin(level)
        }
    }

    suspend fun loadSave(): String? = ctx.dataStore.data.first()[saveKey]

    suspend fun saveGame(json: String) {
        ctx.dataStore.edit { it[saveKey] = json }
    }

    suspend fun clearSave() {
        ctx.dataStore.edit { it.remove(saveKey) }
    }
}
