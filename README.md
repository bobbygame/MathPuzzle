# MathPuzzle

Android-App im Stil von Cross Math: Ein Gitter aus Gleichungen ist teilweise leer, die fehlenden Zahlen (und später auch Rechenzeichen) werden aus einem Vorrat eingesetzt, bis alle Gleichungen stimmen.

Paket: `de.wyn.crossmath` · minSdk 24 · Kotlin, Jetpack Compose (Material 3), DataStore

## Spielmodi

- **Feste Stufen:** Easy, Medium, Hard und Expert mit jeweils festen Vorgaben und Hinweisen.
- **Tagesrätsel:** Pro Tag ein festes Rätsel, das über einen Seed erzeugt wird.
- **Adaptiv:** Stufenloses Level. Ein gelöstes Rätsel erhöht das Level um 1, ein verlorenes senkt es um 2 (nie unter 1). Ein Rätsel gilt als verloren, wenn es angefangen, aber nicht gelöst wurde und ein neues gestartet wird.

## Adaptive Schwierigkeit

Die Parameter stehen in `Adaptive.kt`.

| Level | Änderung |
|---|---|
| 1 | Start, nur + und − |
| ab 30 | Rechenzeichen können versteckt sein |
| ab 60 | × kommt hinzu |
| ab 120 | ÷ kommt hinzu |
| ab 200 | Anteil von + und − sinkt von 50 % schrittweise auf mindestens 10 % |

- Hinweise: alle 20 Level einer mehr (Level 1–20: 0, 21–40: 1, …), höchstens 6.
- Zahlenraum, Anzahl der Gleichungen und Anteil versteckter Felder wachsen mit dem Level. Der Zahlenraum ist auf 99 und die Zahl der Gleichungen auf 10 begrenzt.
- Die Kurvenstärke (Gemächlich 50 %, Normal 100 %, Steil 150 %, Sehr steil 200 %) skaliert, wie schnell diese Größen wachsen. Die Operator-Schwellen und die Hinweise hängen am echten Level.

## Statistik

- Gelöste Rätsel werden gespeichert und nach Level und Stufe ausgewertet.
- Die Serie zählt aufeinanderfolgende Tage mit mindestens einem gelösten Rätsel. Sie bleibt bestehen, wenn heute noch nichts gelöst wurde, aber gestern.

## Projektstruktur

| Datei | Inhalt |
|---|---|
| `Puzzle.kt` | Rätselgenerator und Gitter |
| `Adaptive.kt` | Level, Kurven und Generator-Parameter des adaptiven Modus |
| `GameRules.kt` | Reine Spielregeln ohne Android-Abhängigkeit (Moduswahl, Abbruch, Hinweise, Timer, Höchstlevel) |
| `GameViewModel.kt` | Spielzustand und Ablauf |
| `SaveGame.kt` | Speicherstand (JSON) |
| `StatsRepository.kt`, `StatsScreen.kt`, `LevelHistory.kt`, `LevelChart.kt`, `Streak.kt` | Statistik, Verlauf und Serie |
| `Home.kt`, `MainActivity.kt`, `Settings.kt`, `ui/` | Oberfläche |

## Bauen und testen

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Die Unit-Tests laufen auf der JVM und brauchen kein Gerät. `GameRulesTest` und `SaveCodecTest` sichern die Regeln und das Speicherformat ab, unter anderem Speicherstände aus der Zeit vor dem adaptiven Modus.
