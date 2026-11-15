package de.wyn.crossmath

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException

private val Context.appSettingsStore by preferencesDataStore(
    name = "app_settings"
)

enum class AppThemeMode(val label: String) {
    SYSTEM("Systemdesign"),
    LIGHT("Hell"),
    DARK("Dunkel")
}

data class AppSettings(
    val theme: AppThemeMode = AppThemeMode.SYSTEM,
    val showErrors: Boolean = true,
    val showCorrect: Boolean = true,
    val winHaptics: Boolean = true
)

val LocalAppSettings = staticCompositionLocalOf {
    AppSettings()
}

val LocalAppDarkTheme = staticCompositionLocalOf {
    false
}

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.appSettingsStore

    private val themeKey = stringPreferencesKey("theme")
    private val errorsKey = booleanPreferencesKey("show_errors")
    private val correctKey = booleanPreferencesKey("show_correct")
    private val hapticsKey = booleanPreferencesKey("win_haptics")

    val settings = store.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs ->
            val theme = AppThemeMode.entries.firstOrNull {
                it.name == prefs[themeKey]
            } ?: AppThemeMode.SYSTEM

            AppSettings(
                theme = theme,
                showErrors = prefs[errorsKey] ?: true,
                showCorrect = prefs[correctKey] ?: true,
                winHaptics = prefs[hapticsKey] ?: true
            )
        }

    suspend fun setTheme(value: AppThemeMode) {
        store.edit { it[themeKey] = value.name }
    }

    suspend fun setShowErrors(value: Boolean) {
        store.edit { it[errorsKey] = value }
    }

    suspend fun setShowCorrect(value: Boolean) {
        store.edit { it[correctKey] = value }
    }

    suspend fun setWinHaptics(value: Boolean) {
        store.edit { it[hapticsKey] = value }
    }
}

@Composable
fun CrossMathApp(vm: GameViewModel) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) {
        SettingsRepository(context)
    }
    val settings by repository.settings.collectAsState(
        initial = AppSettings()
    )
    val scope = rememberCoroutineScope()

    var settingsOpen by rememberSaveable {
        mutableStateOf(false)
    }
    var statsOpen by rememberSaveable {
        mutableStateOf(false)
    }
    var saveError by remember {
        mutableStateOf<String?>(null)
    }
    var saving by remember {
        mutableStateOf(false)
    }

    fun saveSetting(action: suspend () -> Unit) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                action()
            } catch (error: IOException) {
                saveError = "Die Einstellung konnte nicht gespeichert werden."
            } finally {
                saving = false
            }
        }
    }

    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.theme) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    DisposableEffect(settingsOpen) {
        vm.settingsOpen = settingsOpen
        onDispose {
            vm.settingsOpen = false
        }
    }

    BackHandler(enabled = !settingsOpen && !statsOpen && vm.inGame) {
        vm.leaveToHome()
    }
    BackHandler(enabled = settingsOpen) {
        settingsOpen = false
    }
    BackHandler(enabled = statsOpen && !settingsOpen) {
        statsOpen = false
    }

    CompositionLocalProvider(
        LocalAppSettings provides settings,
        LocalAppDarkTheme provides dark
    ) {
        MaterialTheme(
            colorScheme = if (dark) darkColorScheme() else lightColorScheme()
        ) {
            Surface(Modifier.fillMaxSize()) {
                if (settingsOpen) {
                    SettingsScreen(
                        settings = settings,
                        enabled = !saving,
                        onBack = { settingsOpen = false },
                        onThemeChange = { value ->
                            saveSetting { repository.setTheme(value) }
                        },
                        onErrorsChange = { value ->
                            saveSetting { repository.setShowErrors(value) }
                        },
                        onCorrectChange = { value ->
                            saveSetting { repository.setShowCorrect(value) }
                        },
                        onHapticsChange = { value ->
                            saveSetting { repository.setWinHaptics(value) }
                        }
                    )
                } else if (statsOpen) {
                    StatsScreen(vm = vm, onBack = { statsOpen = false })
                } else if (vm.inGame) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { vm.leaveToHome() }) {
                                Text("‹ Menü")
                            }
                            TextButton(onClick = { settingsOpen = true }) {
                                Text("Einstellungen")
                            }
                        }

                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            GameScreen(vm)
                        }
                    }
                } else {
                    HomeScreen(
                        vm = vm,
                        onOpenSettings = { settingsOpen = true },
                        onOpenStats = { statsOpen = true }
                    )
                }

                if (saveError != null) {
                    AlertDialog(
                        onDismissRequest = { saveError = null },
                        title = { Text("Speichern fehlgeschlagen") },
                        text = { Text(saveError.orEmpty()) },
                        confirmButton = {
                            TextButton(onClick = { saveError = null }) {
                                Text("OK")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: AppSettings,
    enabled: Boolean,
    onBack: () -> Unit,
    onThemeChange: (AppThemeMode) -> Unit,
    onErrorsChange: (Boolean) -> Unit,
    onCorrectChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("Zurück")
        }

        Text(
            text = "Einstellungen",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Darstellung",
            style = MaterialTheme.typography.titleMedium
        )

        AppThemeMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) {
                        onThemeChange(mode)
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = settings.theme == mode,
                    onClick = null,
                    enabled = enabled
                )
                Spacer(Modifier.width(12.dp))
                Text(mode.label)
            }
        }

        HorizontalDivider()

        Text(
            text = "Spielhilfe und Rückmeldung",
            style = MaterialTheme.typography.titleMedium
        )

        SettingsSwitch(
            title = "Fehler markieren",
            description = "Falsche vollständige Gleichungen rot anzeigen.",
            checked = settings.showErrors,
            enabled = enabled,
            onCheckedChange = onErrorsChange
        )

        SettingsSwitch(
            title = "Richtige Gleichungen markieren",
            description = "Vollständig richtige Gleichungen grün anzeigen.",
            checked = settings.showCorrect,
            enabled = enabled,
            onCheckedChange = onCorrectChange
        )

        SettingsSwitch(
            title = "Vibration beim Gewinn",
            description = "Kurzes haptisches Feedback beim Lösen eines Rätsels.",
            checked = settings.winHaptics,
            enabled = enabled,
            onCheckedChange = onHapticsChange
        )

        HorizontalDivider()

        Text(
            text = "Hinweise pro Rätsel",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            "Einfach: 0\nMittel: 1\nSchwer: 2\nExperte: 3"
        )

        Text(
            text = "Diese Grenzen bleiben fest eingestellt.",
            style = MaterialTheme.typography.bodySmall
        )

        HorizontalDivider()

        Text(
            text = "Privat und werbefrei",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            "Die Einstellungen werden lokal gespeichert. " +
                    "Dein laufendes Rätsel und deine Statistik werden " +
                    "durch Änderungen hier nicht zurückgesetzt."
        )

        Text(
            text = "Änderungen werden automatisch gespeichert.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}