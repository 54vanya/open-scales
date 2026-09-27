package dev.openscales.session

import android.util.Log
import dev.openscales.ble.BleException
import dev.openscales.ble.BleTransport
import dev.openscales.ble.BondState
import dev.openscales.ble.TransportEvent
import dev.openscales.protocol.Cmd
import dev.openscales.protocol.FrameCodec
import dev.openscales.protocol.GattIds
import dev.openscales.protocol.LegacyCmd
import dev.openscales.protocol.LegacyCodec
import dev.openscales.protocol.MessageDecoder
import dev.openscales.protocol.Precision
import dev.openscales.protocol.ScaleMessage
import dev.openscales.protocol.ScaleModel
import dev.openscales.protocol.Sensitivity
import dev.openscales.protocol.TimerState
import dev.openscales.protocol.WeightUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext

/**
 * Одна попытка подключения к весам и работа с ними до разрыва.
 */
class ScaleSession(
    private val transport: BleTransport,
    parentContext: CoroutineContext,
    knownModel: ScaleModel = ScaleModel.UNKNOWN,
    knownName: String? = null,
    private val log: (String) -> Unit = { Log.i(TAG, it) },
    /** Сессия завершилась; вызывается синхронно, до публикации конечного состояния. */
    private val onEnded: (EndReason) -> Unit = {},
) {
    private val scope = CoroutineScope(parentContext + SupervisorJob(parentContext[Job]))

    private val _state = MutableStateFlow(
        ScaleState(address = transport.address, model = knownModel, name = knownName),
    )
    val state: StateFlow<ScaleState> = _state.asStateFlow()

    private val _timerReports = MutableSharedFlow<TimerReport>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * Таймер весов на каждый кадр веса: состояние из последнего `0x02` и секунды из кадра. Идёт только после
     * того, как рукопожатие прочитало `0x02`: до этого состояние по умолчанию неотличимо от сброшенного таймера.
     */
    val timerReports: Flow<TimerReport> = _timerReports.asSharedFlow()

    private val ended = CompletableDeferred<EndReason>()

    private val queue = CommandQueue(scope) { bytes ->
        transport.write(GattIds.SERVICE_2025, GattIds.WRITE_2025, bytes)
    }

    @Volatile private var legacy = false
    @Volatile private var protocolDataSeen = false
    @Volatile private var pendingEndReason: EndReason? = null
    @Volatile private var timerStateKnown = false

    val isLegacy: Boolean get() = legacy

    /** Запускает подключение; результат — [awaitEnd]. */
    fun start() {
        scope.launch { collectEvents() }
        scope.launch {
            try {
                connect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = (e as? ConnectionException)?.reason ?: ConnectionError.Failed(e.message ?: e.toString())
                fail(reason, e.message ?: e.toString())
            }
        }
    }

    suspend fun awaitEnd(): EndReason = ended.await()

    // region connection

    private suspend fun connect() {
        var rebuilds = 0
        while (true) {
            try {
                connectOnce()
                return
            } catch (e: RebuildGatt) {
                if (rebuilds++ >= MAX_GATT_REBUILDS) throw BleException(e.message ?: "GATT rebuild failed")
                log("rebuild GATT: ${e.message}")
                transport.close()
                delay(REBUILD_DELAY_MS)
            }
        }
    }

    private suspend fun connectOnce() {
        setPhase(ConnectionPhase.CONNECTING)
        protocolDataSeen = false
        transport.connect()
        val services = transport.discoverServices()
        if (GattIds.SERVICE_LEGACY in services) {
            connectLegacy()
            return
        }
        if (GattIds.SERVICE_2025 !in services) throw ConnectionException(ConnectionError.NotTimemore, "device is not a Timemore scale")

        setPhase(ConnectionPhase.BONDING)
        val newlyBonded = ensureBonded()

        setPhase(ConnectionPhase.SUBSCRIBING)
        delay(MTU_DELAY_MS)
        runCatching { transport.requestMtu(REQUESTED_MTU) }
            .onSuccess { log("MTU $it") }
            .onFailure { log("MTU failed, continue: ${it.message}") }
        delay(if (newlyBonded) NOTIFY_DELAY_AFTER_BOND_MS else NOTIFY_DELAY_MS)

        try {
            transport.enableNotifications(GattIds.SERVICE_2025, GattIds.NOTIFY_2025, indication = false)
        } catch (e: BleException) {
            // Оригинал принимает канал, если данные пришли раньше колбэка CCCD, иначе пересоздаёт GATT.
            if (!protocolDataSeen) throw RebuildGatt("CCCD failed without data: ${e.message}")
            log("CCCD callback failed but protocol data seen: ${e.message}")
        }

        delay(HANDSHAKE_DELAY_MS)
        setPhase(ConnectionPhase.HANDSHAKING)
        handshake()
        // Короткий интервал соединения (~7.5–15 мс) — команды с кнопок проходят заметно быстрее.
        transport.requestHighPriority()
        setPhase(ConnectionPhase.READY)
    }

    /** @return true, если bond создан в этой сессии. */
    private suspend fun ensureBonded(): Boolean {
        when (transport.bondState()) {
            BondState.BONDED -> return false
            BondState.BONDING -> awaitBond(BOND_COMPLETE_TIMEOUT_MS, sawBonding = true)
            BondState.NONE -> {
                if (!transport.createBond()) throw ConnectionException(ConnectionError.PairingNotStarted, "createBond failed")
                awaitBond(BOND_START_TIMEOUT_MS, sawBonding = false)
            }
        }
        return true
    }

    /** Опрашивает bondState: broadcast'ы о сопряжении на части прошивок теряются (так делает и оригинал). */
    private suspend fun awaitBond(startTimeoutMs: Long, sawBonding: Boolean) {
        var bonding = sawBonding
        var waited = 0L
        while (true) {
            when (transport.bondState()) {
                BondState.BONDED -> return
                BondState.BONDING -> bonding = true
                BondState.NONE -> if (bonding) throw ConnectionException(ConnectionError.PairingRejected, "pairing rejected")
            }
            val limit = if (bonding) BOND_COMPLETE_TIMEOUT_MS else startTimeoutMs
            if (waited >= limit) {
                throw if (bonding) {
                    ConnectionException(ConnectionError.PairingTimeout, "pairing did not finish")
                } else {
                    ConnectionException(ConnectionError.PairingNotStarted, "pairing did not start")
                }
            }
            delay(BOND_POLL_MS)
            waited += BOND_POLL_MS
        }
    }

    private suspend fun handshake() {
        // Обязательны батарея и (если модель неизвестна) модель.
        try {
            queue.read(Cmd.BATTERY)
        } catch (e: CommandException) {
            throw RebuildGatt("battery read did not confirm protocol readiness: ${e.message}")
        }
        if (!_state.value.model.isKnown) {
            val model = (queue.read(Cmd.MODEL) as? ScaleMessage.Model)?.model ?: ScaleModel.UNKNOWN
            if (!model.isKnown) throw ConnectionException(ConnectionError.NoModel, "scale did not report its model")
        } else {
            optionalRead(Cmd.MODEL)
        }
        optionalRead(Cmd.WEIGHT_UNIT)
        optionalRead(Cmd.MODE_STAGE)
        timerStateKnown = optionalRead(Cmd.TIMER)
        optionalRead(Cmd.DEVICE_NAME)
    }

    /** @return true, если весы ответили. */
    private suspend fun optionalRead(cmd: Int): Boolean =
        try {
            queue.read(cmd)
            true
        } catch (e: CommandException) {
            log("optional read 0x%02X failed: %s".format(cmd, e.message))
            false
        }

    private suspend fun connectLegacy() {
        legacy = true
        _state.update { it.copy(model = ScaleModel.OLD_DOUBLE) }
        setPhase(ConnectionPhase.SUBSCRIBING)
        delay(LEGACY_INDICATE_DELAY_MS)
        transport.enableNotifications(GattIds.SERVICE_LEGACY, GattIds.WEIGHT_LEGACY, indication = true)
        setPhase(ConnectionPhase.READY)
    }

    // endregion

    // region incoming data

    private suspend fun collectEvents() {
        transport.events.collect { event ->
            when (event) {
                is TransportEvent.Notification -> onNotification(event)
                is TransportEvent.Disconnected -> onDisconnected(event.status)
                is TransportEvent.BondChanged -> log("bond ${event.previous} -> ${event.state}")
            }
        }
    }

    private fun onNotification(event: TransportEvent.Notification) {
        if (event.characteristic == GattIds.WEIGHT_LEGACY) {
            LegacyCodec.decodeWeight(event.value)?.let { w -> _state.update { it.copy(weight = w.totalGrams) } }
            return
        }
        if (event.characteristic != GattIds.NOTIFY_2025) return
        val frames = FrameCodec.split(event.value)
        if (frames.isNotEmpty()) protocolDataSeen = true
        for (frame in frames) {
            val message = MessageDecoder.decode(frame, _state.value.unit)
            apply(message)
            if (message is ScaleMessage.Weight && timerStateKnown) {
                _timerReports.tryEmit(TimerReport(_state.value.timerState, message.timeSeconds))
            }
            queue.onMessage(message)
        }
    }

    private fun apply(message: ScaleMessage) {
        _state.update { s ->
            when (message) {
                is ScaleMessage.Weight -> s.copy(
                    weight = message.grams,
                    flowRate = message.flowRate,
                    timeSeconds = message.timeSeconds,
                    overload = message.overload,
                )

                is ScaleMessage.Timer -> s.copy(
                    timerState = message.state,
                    timeSeconds = if (message.state == TimerState.RESET) 0 else s.timeSeconds,
                )

                is ScaleMessage.Battery -> s.copy(batteryPercent = message.percent)
                is ScaleMessage.Unit -> s.copy(unit = message.unit)
                is ScaleMessage.Model -> if (message.model.isKnown) s.copy(model = message.model) else s
                is ScaleMessage.Name -> if (message.name.isNotBlank()) s.copy(name = message.name) else s
                is ScaleMessage.Sound -> s.copy(settings = s.settings.copy(sound = message.enabled))
                is ScaleMessage.StandbyTime -> s.copy(settings = s.settings.copy(standbyMinutes = message.seconds / 60))
                is ScaleMessage.SensitivityLevel -> s.copy(settings = s.settings.copy(sensitivity = message.sensitivity))
                is ScaleMessage.PrecisionLevel -> s.copy(settings = s.settings.copy(precision = message.precision))
                is ScaleMessage.Brightness -> s.copy(settings = s.settings.copy(brightness = message.percent))
                is ScaleMessage.Firmware -> s.copy(settings = s.settings.copy(firmware = message.version))
                is ScaleMessage.SerialNumber -> s.copy(settings = s.settings.copy(serial = message.serial))
                is ScaleMessage.ModeStage, is ScaleMessage.WriteAck, is ScaleMessage.Raw -> s
            }
        }
    }

    private fun onDisconnected(status: Int) {
        if (ended.isCompleted) return
        val wasReady = _state.value.phase == ConnectionPhase.READY
        val reason = pendingEndReason ?: if (wasReady) EndReason.LOST else EndReason.FAILED
        log("disconnected status=$status reason=$reason")
        if (reason == EndReason.FAILED) {
            fail(ConnectionError.DroppedWhileConnecting, "disconnected while connecting (status=$status)")
            return
        }
        finish(
            reason,
            ConnectionPhase.DISCONNECTED,
            if (reason == EndReason.LOST) ConnectionError.Lost else null,
        )
    }

    // endregion

    // region commands

    private fun requireReady() {
        if (!_state.value.isReady) throw CommandException(CommandException.Kind.CANCELLED, "scale not connected")
    }

    suspend fun tare() {
        requireReady()
        if (legacy) return legacyWrite(LegacyCodec.command(LegacyCmd.TARE))
        queue.write(Cmd.TARE, byteArrayOf(0, 0), CommandQueue.Coalesce.TARE)
    }

    suspend fun toggleTimer() {
        if (_state.value.timerState == TimerState.RUNNING) pauseTimer() else startTimer()
    }

    suspend fun startTimer() = timer(TimerState.RUNNING)

    suspend fun pauseTimer() = timer(TimerState.PAUSED)

    suspend fun resetTimer() = timer(TimerState.RESET)

    /**
     * Команда таймера весам. Секундомер ведёт приложение, поэтому по умолчанию ([awaitAck] = false)
     * команда уходит без подтверждения: её результат на показания не влияет, а лишний раунд-трип
     * задерживает следующую команду. С [awaitAck] = true состояние сессии переключается оптимистично
     * и откатывается при отказе — этого ждёт режим синхронизации с весами.
     */
    suspend fun timer(target: TimerState, awaitAck: Boolean = true) {
        requireReady()
        if (legacy) {
            val cmd = when (target) {
                TimerState.RUNNING -> LegacyCmd.START_TIMER
                TimerState.PAUSED -> LegacyCmd.PAUSE_TIMER
                TimerState.RESET -> LegacyCmd.RESET_TIMER
            }
            return legacyWrite(LegacyCodec.command(cmd))
        }
        // Сброс при уже нулевом таймере весы понимают как тару и обнуляют вес (проверено на DOT):
        // сбрасывать им нечего, а тару делает только кнопка «Тара».
        if (target == TimerState.RESET && !scaleTimerHasSomethingToReset()) return
        if (!awaitAck) {
            queue.write(Cmd.TIMER, byteArrayOf(target.code.toByte()), CommandQueue.Coalesce.TIMER, awaitAck = false)
            return
        }
        // Оптимистично: интерфейс меняется в момент нажатия, не дожидаясь BLE-обмена (~0.3–0.6 с).
        val previous = _state.value
        applyTimerState(target)
        try {
            queue.write(Cmd.TIMER, byteArrayOf(target.code.toByte()), CommandQueue.Coalesce.TIMER)
        } catch (e: CommandException) {
            // Вытеснена более новой командой таймера — её состояние уже применено.
            if (e.kind == CommandException.Kind.CANCELLED && _state.value.isReady) return
            _state.update { it.copy(timerState = previous.timerState, timeSeconds = previous.timeSeconds) }
            throw e
        }
        // Как оригинал, сверяем состояние с весами, но не задерживаем вызывающего.
        scope.launch { optionalRead(Cmd.TIMER) }
    }

    private fun scaleTimerHasSomethingToReset(): Boolean {
        val state = _state.value
        return state.timerState == TimerState.RUNNING || state.timeSeconds > 0
    }

    private fun applyTimerState(target: TimerState) {
        _state.update {
            it.copy(timerState = target, timeSeconds = if (target == TimerState.RESET) 0 else it.timeSeconds)
        }
    }

    private suspend fun legacyWrite(bytes: ByteArray) {
        transport.write(GattIds.SERVICE_LEGACY, GattIds.COMMAND_LEGACY, bytes)
    }

    /** Чтение параметров для экрана настроек. */
    suspend fun loadSettings() {
        requireReady()
        if (legacy) return
        val model = _state.value.model
        if (model.hasSoundSwitch) optionalRead(Cmd.SOUND)
        optionalRead(Cmd.WEIGHT_UNIT)
        optionalRead(Cmd.SENSITIVITY)
        optionalRead(Cmd.STANDBY_TIME)
        optionalRead(Cmd.PRECISION)
        optionalRead(Cmd.FIRMWARE_VERSION)
        optionalRead(Cmd.SERIAL_NUMBER)
        if (model.hasBrightness) optionalRead(Cmd.BRIGHTNESS)
    }

    private suspend fun writeAndReread(cmd: Int, payload: ByteArray, rereadCmd: Int = cmd) {
        requireReady()
        queue.write(cmd, payload)
        optionalRead(rereadCmd)
    }

    suspend fun setUnit(unit: WeightUnit) = writeAndReread(Cmd.WEIGHT_UNIT, byteArrayOf(unit.code.toByte()))

    suspend fun setSound(enabled: Boolean) {
        requireReady()
        val v: Byte = if (enabled) 1 else 0
        if (legacy) {
            legacyWrite(LegacyCodec.command(LegacyCmd.KEY_SOUND, byteArrayOf(v)))
            _state.update { it.copy(settings = it.settings.copy(sound = enabled)) }
            return
        }
        writeAndReread(Cmd.SOUND, byteArrayOf(v))
    }

    suspend fun setSensitivity(value: Sensitivity) =
        writeAndReread(Cmd.SENSITIVITY, byteArrayOf(5, value.level.toByte()))

    suspend fun setPrecision(value: Precision) = writeAndReread(Cmd.PRECISION, byteArrayOf(value.code.toByte()))

    suspend fun setStandbyMinutes(minutes: Int) {
        require(minutes in STANDBY_RANGE) { "standby must be in $STANDBY_RANGE" }
        val seconds = minutes * 60
        writeAndReread(Cmd.STANDBY_TIME, byteArrayOf((seconds ushr 8).toByte(), seconds.toByte(), 0, 0))
    }

    suspend fun setBrightness(percent: Int) =
        writeAndReread(Cmd.BRIGHTNESS, byteArrayOf(percent.coerceIn(0, 100).toByte()))

    suspend fun rename(name: String) {
        requireReady()
        val bytes = name.trim().toByteArray(Charsets.UTF_8)
        val limit = if (legacy) LEGACY_NAME_MAX_BYTES else NAME_MAX_BYTES
        require(bytes.isNotEmpty() && bytes.size <= limit) { "name must be 1..$limit bytes" }
        if (legacy) {
            legacyWrite(LegacyCodec.rename(name.trim()))
            _state.update { it.copy(name = name.trim()) }
            return
        }
        writeAndReread(Cmd.DEVICE_NAME, bytes)
    }

    suspend fun powerOff() = deviceCommand(Cmd.POWER_OFF)

    suspend fun factoryReset() = deviceCommand(Cmd.FACTORY_RESET)

    /** Команда 0x1A перед удалением bond. */
    suspend fun forgetOnDevice() {
        if (!_state.value.isReady || legacy) return
        deviceCommand(Cmd.FORGET_DEVICE)
    }

    /** Команды, после которых весы сами рвут связь: ждать подтверждения бессмысленно. */
    private suspend fun deviceCommand(cmd: Int) {
        requireReady()
        pendingEndReason = EndReason.DEVICE_COMMAND
        runCatching { queue.write(cmd, awaitAck = cmd == Cmd.FACTORY_RESET) }
        scope.launch {
            delay(DISCONNECT_GRACE_MS)
            finish(EndReason.DEVICE_COMMAND, ConnectionPhase.DISCONNECTED, null)
        }
    }

    /** Ручное отключение: 0x1C, затем закрытие GATT через 1.5 с. */
    suspend fun disconnect() {
        pendingEndReason = EndReason.USER
        if (_state.value.isReady && !legacy) {
            setPhase(ConnectionPhase.DISCONNECTING)
            runCatching { queue.write(Cmd.DISCONNECT, awaitAck = false) }
            withTimeoutOrNull(DISCONNECT_GRACE_MS) { ended.await() }
        }
        finish(EndReason.USER, ConnectionPhase.DISCONNECTED, null)
    }

    // endregion

    private fun setPhase(phase: ConnectionPhase) {
        if (ended.isCompleted) return
        log("phase=$phase")
        _state.update { it.copy(phase = phase, error = null) }
    }

    private fun fail(error: ConnectionError, message: String) {
        log("failed: $message")
        finish(pendingEndReason ?: EndReason.FAILED, ConnectionPhase.FAILED, error)
    }

    private fun finish(reason: EndReason, phase: ConnectionPhase, error: ConnectionError?) {
        if (!ended.complete(reason)) return
        onEnded(reason)
        val finalPhase = if (reason == EndReason.FAILED) ConnectionPhase.FAILED else phase
        _state.update { it.copy(phase = finalPhase, error = error, flowRate = 0f) }
        queue.close()
        transport.close()
        scope.cancel()
    }

    /** Закрыть сессию без отправки команд (смена устройства, выход). */
    fun close() {
        finish(EndReason.USER, ConnectionPhase.DISCONNECTED, null)
    }

    private class RebuildGatt(message: String) : Exception(message)

    companion object {
        private const val TAG = "ScaleSession"
        const val REQUESTED_MTU = 247
        const val MTU_DELAY_MS = 500L
        const val NOTIFY_DELAY_MS = 700L
        const val NOTIFY_DELAY_AFTER_BOND_MS = 1_000L
        const val HANDSHAKE_DELAY_MS = 500L
        const val BOND_START_TIMEOUT_MS = 10_000L
        const val BOND_COMPLETE_TIMEOUT_MS = 35_000L
        const val BOND_POLL_MS = 250L
        const val REBUILD_DELAY_MS = 800L
        const val MAX_GATT_REBUILDS = 2
        const val LEGACY_INDICATE_DELAY_MS = 1_000L
        const val DISCONNECT_GRACE_MS = 1_500L
        const val NAME_MAX_BYTES = 20
        const val LEGACY_NAME_MAX_BYTES = 14
        val STANDBY_RANGE = 1..99
    }
}
