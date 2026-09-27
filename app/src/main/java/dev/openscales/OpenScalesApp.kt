package dev.openscales

import android.app.Activity
import android.app.Application
import android.os.Build
import androidx.core.content.edit
import android.os.Bundle
import android.os.SystemClock
import android.bluetooth.BluetoothManager
import android.util.Log
import java.io.File
import dev.openscales.ble.AndroidBleTransport
import dev.openscales.ble.BleJournal
import dev.openscales.ble.LoggingBleTransport
import dev.openscales.ble.ScaleScanner
import dev.openscales.data.AppLanguage
import dev.openscales.data.AppLanguageStore
import dev.openscales.data.AppSettingsStore
import dev.openscales.data.AppearanceStore
import dev.openscales.data.LocalAppLanguageStore
import dev.openscales.data.SystemAppLanguageStore
import dev.openscales.data.LocalThemeModeStore
import dev.openscales.data.SystemThemeModeStore
import dev.openscales.data.ThemeMode
import dev.openscales.data.ThemeModeStore
import dev.openscales.data.DataStoreAppSettingsStore
import dev.openscales.data.DataStoreSavedDeviceStore
import dev.openscales.data.RecipeStore
import dev.openscales.recipe.BuiltInRecipes
import dev.openscales.recipe.Recipe
import dev.openscales.sim.ScaleSimulator
import dev.openscales.sound.AudioTrackBeeper
import dev.openscales.sound.ButtonSound
import dev.openscales.session.ScaleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OpenScalesApp : Application() {

    /** Главный поток: все изменения состояния сессии и репозитория однопоточны. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val bluetoothManager: BluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }

    val scanner: ScaleScanner by lazy { ScaleScanner(bluetoothManager.adapter) }

    /** Журнал BLE-обмена — только в debug-сборке; в release сырые кадры не хранятся. */
    val bleJournal: BleJournal? = if (BuildConfig.DEBUG) BleJournal() else null

    /** Виртуальные весы — только в debug-сборке; управляются из терминала (`tools/dev.sh sim`). */
    val simulator: ScaleSimulator? =
        if (BuildConfig.DEBUG) ScaleSimulator(appScope, nowMs = SystemClock::elapsedRealtime) else null

    /** Адрес виртуальных весов: им не нужны ни Bluetooth, ни разрешения. В release всегда `false`. */
    fun isVirtual(address: String?): Boolean = simulator?.isVirtual(address) == true

    val repository: ScaleRepository by lazy {
        ScaleRepository(
            scope = appScope,
            store = DataStoreSavedDeviceStore(this),
            syncTimer = appSettingsStore.settings
                .map { it.syncTimerWithScale }
                .stateIn(appScope, SharingStarted.Eagerly, false),
            // Не `nanoTime`: секундомер должен идти и пока телефон спит.
            nowMs = SystemClock::elapsedRealtime,
            transportFactory = { address ->
                val transport = simulator?.takeIf { it.isVirtual(address) }?.transport()
                    ?: AndroidBleTransport(this, bluetoothManager.adapter, address)
                bleJournal?.let { LoggingBleTransport(transport, it) } ?: transport
            },
            tickLog = bleJournal?.let { journal ->
                val skips = SecondsProbe()
                val log: (Int, Long, Boolean) -> Unit = { seconds, lateMs, fallback ->
                    val skip = skips.onValue(seconds, SystemClock.elapsedRealtime())
                    journal.add(
                        BleJournal.Kind.TIMER,
                        "value=$seconds late=${lateMs}ms at=${SystemClock.elapsedRealtime()}" +
                            (if (fallback) " WAKE fallback" else "") + (skip?.let { " SKIP $it" } ?: ""),
                        // Страховочное пробуждение — значит, основное на границе секунды не пришло: это и ищем.
                        notable = lateMs > LATE_TICK_MS || skip != null || fallback,
                    )
                }
                log
            },
            sessionLog = bleJournal?.let { journal ->
                { message ->
                    val kind = if (message.startsWith("phase=")) BleJournal.Kind.PHASE else BleJournal.Kind.INFO
                    journal.add(kind, message)
                }
            },
        )
    }

    val appSettingsStore: AppSettingsStore by lazy { DataStoreAppSettingsStore(this) }

    /** Свои рецепты пользователя; встроенные — в [BuiltInRecipes]. */
    val recipeStore: RecipeStore by lazy {
        RecipeStore(File(filesDir, "recipes"), appScope, log = { Log.w("OpenScales", it) })
    }

    /** Рецепт по id: встроенный или свой. */
    fun recipeById(id: String?): Recipe? = id?.let { BuiltInRecipes.byId(it) ?: recipeStore.get(it) }

    /** Язык интерфейса: на Android 13+ — системный язык приложения, раньше — своё хранилище. */
    val languageStore: AppLanguageStore by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SystemAppLanguageStore(this)
        } else {
            val prefs = getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
            LocalAppLanguageStore(
                read = { prefs.getString(KEY_LANGUAGE, null) },
                write = { prefs.edit { putString(KEY_LANGUAGE, it) } },
            )
        }
    }

    /** Тема: на Android 12+ — ночной режим приложения в системе, раньше — своё хранилище. */
    val themeStore: ThemeModeStore by lazy {
        val prefs = getSharedPreferences(APPEARANCE_PREFS, MODE_PRIVATE)
        val local = LocalThemeModeStore(
            read = { prefs.getString(KEY_THEME, null) },
            write = { prefs.edit { putString(KEY_THEME, it) } },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) SystemThemeModeStore(this, local) else local
    }

    fun setThemeMode(mode: ThemeMode) {
        if (themeStore.current() == mode) return
        themeStore.set(mode)
        // На Android 12+ экраны пересоздаёт система.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) openActivities.toList().forEach { it.recreate() }
    }

    /** Оформление, нужное к первому кадру (собственные цвета приложения). */
    val appearance: AppearanceStore by lazy {
        val prefs = getSharedPreferences(APPEARANCE_PREFS, MODE_PRIVATE)
        AppearanceStore(
            read = { key -> if (prefs.contains(key)) prefs.getBoolean(key, true) else null },
            write = { key, value -> prefs.edit { putBoolean(key, value) } },
        )
    }

    /** Открытые экраны — чтобы на Android < 13 пересоздать их на новом языке. */
    private val openActivities = mutableSetOf<Activity>()

    fun setLanguage(language: AppLanguage) {
        if (languageStore.current() == language) return
        languageStore.set(language)
        // На Android 13+ экраны пересоздаёт система.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) openActivities.toList().forEach { it.recreate() }
    }

    private val beeper = AudioTrackBeeper()

    val buttonSound: ButtonSound by lazy {
        ButtonSound(appScope, appSettingsStore, beeper).also { sound ->
            // Трек держим готовым только пока звук нужен (кнопки или сигналы шагов); при смене ноты — пересоздаём заранее.
            appScope.launch {
                sound.settings.collect {
                    if (it.beepEnabled || it.stepSignals) beeper.prepare(it.beepNote) else beeper.release()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        recipeStore // свои рецепты грузятся в фоне заранее, к открытию вкладки «Рецепты» они уже в памяти
        bleJournal?.let { journal ->
            MainThreadWatchdog(onStall = { ms -> journal.add(BleJournal.Kind.STALL, "main thread busy ${ms}ms") }).start()
        }
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                private var started = 0

                override fun onActivityStarted(activity: Activity) {
                    started++
                    repository.setAppVisible(true)
                }

                override fun onActivityStopped(activity: Activity) {
                    if (--started <= 0) repository.setAppVisible(false)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    openActivities += activity
                }
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) {
                    openActivities -= activity
                }
            },
        )
    }

    private companion object {
        const val LOCALE_PREFS = "locale"
        const val KEY_LANGUAGE = "language"
        const val APPEARANCE_PREFS = "appearance"
        const val KEY_THEME = "theme"

        /** Тик опоздал заметно глазу — в важные события журнала. */
        const val LATE_TICK_MS = 100L
    }

    val isBluetoothEnabled: Boolean get() = bluetoothManager.adapter?.isEnabled == true
}
