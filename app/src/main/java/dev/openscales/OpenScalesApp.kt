package dev.openscales

import android.app.Application
import android.bluetooth.BluetoothManager
import dev.openscales.ble.AndroidBleTransport
import dev.openscales.ble.BleJournal
import dev.openscales.ble.LoggingBleTransport
import dev.openscales.ble.ScaleScanner
import dev.openscales.data.AppSettingsStore
import dev.openscales.data.DataStoreAppSettingsStore
import dev.openscales.data.DataStoreSavedDeviceStore
import dev.openscales.sound.AudioTrackBeeper
import dev.openscales.sound.ButtonSound
import dev.openscales.session.ScaleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OpenScalesApp : Application() {

    /** Главный поток: все изменения состояния сессии и репозитория однопоточны. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val bluetoothManager: BluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }

    val scanner: ScaleScanner by lazy { ScaleScanner(bluetoothManager.adapter) }

    /** Журнал BLE-обмена — только в debug-сборке; в release сырые кадры не хранятся. */
    val bleJournal: BleJournal? = if (BuildConfig.DEBUG) BleJournal() else null

    val repository: ScaleRepository by lazy {
        ScaleRepository(
            scope = appScope,
            store = DataStoreSavedDeviceStore(this),
            transportFactory = { address ->
                val transport = AndroidBleTransport(this, bluetoothManager.adapter, address)
                bleJournal?.let { LoggingBleTransport(transport, it) } ?: transport
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

    private val beeper = AudioTrackBeeper()

    val buttonSound: ButtonSound by lazy {
        ButtonSound(appScope, appSettingsStore, beeper).also { sound ->
            // Трек держим готовым только пока звук включён; при смене ноты — пересоздаём заранее.
            appScope.launch {
                sound.settings.collect { if (it.beepEnabled) beeper.prepare(it.beepNote) else beeper.release() }
            }
        }
    }

    val isBluetoothEnabled: Boolean get() = bluetoothManager.adapter?.isEnabled == true
}
