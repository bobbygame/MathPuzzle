package de.wyn.crossmath

/** Reine Spielregeln ohne Android-Abhängigkeit; vom ViewModel und vom Speicher genutzt und getestet. */

enum class StartMode { ADAPTIVE, FIXED }

/**
 * Ohne ausdrückliche Stufe und ohne Seed wird im aktuellen Modus weitergespielt:
 * lief zuletzt ein adaptives Rätsel, bleibt es adaptiv.
 */
fun resolveStartMode(requested: Difficulty?, seed: Long?, playLevel: Int?): StartMode =
    if (requested == null && seed == null && playLevel != null) StartMode.ADAPTIVE else StartMode.FIXED

/**
 * Ein adaptives Rätsel gilt als verloren, wenn es angefangen, aber nicht gelöst wurde
 * und ein neues Rätsel gestartet wird. Auch ein gespeichertes, noch nicht fortgesetztes
 * adaptives Rätsel zählt dazu.
 */
fun isAbandonedAdaptive(
    hasPuzzle: Boolean,
    playLevel: Int?,
    loading: Boolean,
    solved: Boolean,
    pendingAdaptive: Boolean
): Boolean = (hasPuzzle && playLevel != null && !loading && !solved) || pendingAdaptive

fun maxHintsFor(playLevel: Int?, difficulty: Difficulty): Int =
    playLevel?.let { adaptiveHints(it) } ?: difficulty.maxHints

fun hintsLeftFor(maxHints: Int, used: Int): Int = (maxHints - used).coerceAtLeast(0)

/** Der Timer läuft nur, solange das Spiel sichtbar, aktiv und ungelöst ist. */
fun timerShouldRun(
    inGame: Boolean,
    hasPuzzle: Boolean,
    loading: Boolean,
    paused: Boolean,
    settingsOpen: Boolean,
    solved: Boolean
): Boolean = inGame && hasPuzzle && !loading && !paused && !settingsOpen && !solved

/** Höchstes Level: nie kleiner als bisher gespeichert, als das aktuelle Level oder als der Start. */
fun nextMaxLevel(storedMax: Int?, storedLevel: Int?, newLevel: Int): Int =
    maxOf(storedMax ?: 0, storedLevel ?: 0, ADAPTIVE_START_LEVEL, newLevel)

fun levelAfter(level: Int, won: Boolean): Int =
    if (won) levelAfterWin(level) else levelAfterLoss(level)

fun modeLabel(d: Difficulty, daily: Boolean, level: Int?): String =
    if (level != null) {
        "Adaptiv · Level $level"
    } else {
        d.label + (if (daily) " (Tagesrätsel)" else "")
    }
