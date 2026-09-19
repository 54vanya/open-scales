package dev.openscales.session

import dev.openscales.ble.BleTransport
import dev.openscales.data.SavedDevice
import dev.openscales.data.SavedDeviceStore
import dev.openscales.protocol.ScaleModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Единственная точка работы с весами в приложении: держит текущую [ScaleSession],
 * запоминает последние весы и переподключается к ним (порт `BleService.V()/ReconnectTask`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScaleRepository(
    private val scope: CoroutineScope,
    private val store: SavedDeviceStore,
    private val transportFactory: (address: String) -> BleTransport,
    private val reconnectIntervalMs: Long = RECONNECT_INTERVAL_MS,
    private val maxReconnectAttempts: Int = MAX_RECONNECT_ATTEMPTS,
    /** Дополнительный получатель лога сессий (журнал BLE в debug-сборке). */
    private val sessionLog: ((String) -> Unit)? = null,
) {
    @Volatile
    private var appVisible = true

    private val session = MutableStateFlow<ScaleSession?>(null)
    private val idle = MutableStateFlow(ScaleState())
    private var connectionJob: Job? = null

    val state: StateFlow<ScaleState> = session
        .flatMapLatest { it?.state ?: idle }
        .stateIn(scope, SharingStarted.Eagerly, ScaleState())

    val savedDevice: StateFlow<SavedDevice?> = store.device.stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Приложение на экране или свёрнуто. В фоне не начинаем новых попыток переподключения:
     * иначе пользователь открывает приложение и видит результат давно истёкших попыток.
     * Установленное соединение при сворачивании сохраняется.
     */
    fun setAppVisible(visible: Boolean) {
        appVisible = visible
    }

    /** Ручное подключение из экрана поиска. */
    fun connect(address: String, name: String, model: ScaleModel) {
        start(SavedDevice(address, name, model), manual = true)
    }

    /** Подключиться к запомненным весам, если сейчас нет активного подключения. */
    fun autoConnect() {
        if (connectionJob?.isActive == true) return
        scope.launch {
            val saved = savedDevice.value ?: store.device.first() ?: return@launch
            if (connectionJob?.isActive != true) start(saved, manual = false)
        }
    }

    private fun start(target: SavedDevice, manual: Boolean) {
        connectionJob?.cancel()
        session.value?.close()
        session.value = null
        idle.value = ScaleState(address = target.address, name = target.name, model = target.model)
        connectionJob = scope.launch { runConnection(target, manual) }
    }

    private suspend fun runConnection(initial: SavedDevice, manual: Boolean) {
        var target = initial
        var attempts = 0
        var everReady = false
        while (true) {
            val s = ScaleSession(
                transportFactory(target.address), scope.coroutineContext, target.model, target.name,
                log = { message ->
                    android.util.Log.i("ScaleSession", message)
                    sessionLog?.invoke(message)
                },
            )
            session.value = s
            s.start()
            val readyWatcher = scope.launch {
                val ready = s.state.first { it.isReady }
                everReady = true
                attempts = 0
                target = SavedDevice(target.address, ready.name ?: target.name, ready.model)
                store.save(target)
            }
            val reason = s.awaitEnd()
            readyWatcher.cancel()
            val last = s.state.value

            val retry = when (reason) {
                EndReason.USER, EndReason.DEVICE_COMMAND -> false
                EndReason.LOST -> true
                EndReason.FAILED -> !manual || everReady
            }
            if (!retry || !appVisible || ++attempts > maxReconnectAttempts) {
                idle.value = last.copy(reconnecting = false, flowRate = 0f)
                session.value = null
                return
            }
            idle.value = last.copy(phase = ConnectionPhase.DISCONNECTED, reconnecting = true, flowRate = 0f)
            session.value = null
            delay(reconnectIntervalMs)
        }
    }

    suspend fun disconnect() {
        val s = session.value
        if (s != null) {
            s.disconnect()
        } else {
            connectionJob?.cancel()
            idle.value = idle.value.copy(phase = ConnectionPhase.DISCONNECTED, reconnecting = false, error = null)
        }
    }

    /** Порт `BleService.u()/G()`: 0x1A, отключение, удаление bond и запомненного устройства. */
    suspend fun forget() {
        val address = state.value.address ?: savedDevice.value?.address
        session.value?.let { s ->
            s.forgetOnDevice()
            s.close()
        }
        connectionJob?.cancel()
        session.value = null
        if (address != null) runCatching { transportFactory(address).removeBond() }
        store.clear()
        idle.value = ScaleState()
    }

    /** Выполнить команду на текущей сессии; бросает [CommandException], если весов нет. */
    suspend fun <T> withSession(block: suspend ScaleSession.() -> T): T {
        val s = session.value ?: throw CommandException(CommandException.Kind.CANCELLED, "весы не подключены")
        return s.block()
    }

    companion object {
        const val RECONNECT_INTERVAL_MS = 5_000L
        const val MAX_RECONNECT_ATTEMPTS = 120
    }
}
