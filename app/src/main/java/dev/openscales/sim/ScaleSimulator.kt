package dev.openscales.sim

import dev.openscales.ble.BleTransport
import dev.openscales.ble.DiscoveredScale
import dev.openscales.protocol.Cmd
import dev.openscales.protocol.ScaleModel
import dev.openscales.protocol.WeightUnit
import kotlinx.coroutines.CoroutineScope
import java.util.Locale

/**
 * Виртуальные весы debug-сборки. Создаются только при `BuildConfig.DEBUG` (как журнал BLE): в release
 * код недостижим. Эмулятор один на процесс — вес, тара и таймер переживают переподключение, как у настоящих весов.
 * Управляются командами из терминала ([execute]); всё — на главном потоке.
 */
class ScaleSimulator(private val scope: CoroutineScope, nowMs: () -> Long) {

    val emulator = ScaleEmulator(nowMs)

    /** Весы «включены»: после `drop` подключения не удаются до `back`. */
    var available = true
        private set

    private var current: SimulatedBleTransport? = null

    val device: DiscoveredScale
        get() = DiscoveredScale(ADDRESS, emulator.name, ScaleModel.DOT, rssi = 0, bonded = true)

    fun isVirtual(address: String?): Boolean = address.equals(ADDRESS, ignoreCase = true)

    fun transport(): BleTransport = SimulatedBleTransport(this, scope).also { current = it }

    internal fun detach(transport: SimulatedBleTransport) {
        if (current === transport) current = null
    }

    /** Весы сами рвут связь после команды: выключение — как `drop`, сброс и прочее — просто разрыв. */
    internal fun onDeviceDisconnect(cmd: Int) {
        if (cmd == Cmd.POWER_OFF) available = false
        current?.drop()
    }

    /** Команда из терминала; ответ — «ok», состояние для `status` или текст ошибки. */
    fun execute(command: String): String {
        val words = command.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val name = words.firstOrNull() ?: return usage("empty command")
        val args = words.drop(1)
        fun number(i: Int): Double? = args.getOrNull(i)?.toDoubleOrNull()?.takeIf { it.isFinite() }
        fun expect(count: Int) = args.size == count
        return when (name) {
            "weight" -> {
                val g = number(0)?.takeIf { expect(1) } ?: return error("weight <grams>")
                emulator.setWeight(g)
                OK
            }
            "pour" -> {
                val g = number(0)
                val s = number(1)?.takeIf { it > 0 }
                if (g == null || s == null || !expect(2)) return error("pour <grams> <seconds>")
                emulator.pour(g, s)
                OK
            }
            "noise" -> {
                val g = number(0)?.takeIf { it >= 0 && expect(1) } ?: return error("noise <grams>")
                emulator.noiseG = g
                OK
            }
            "unit" -> {
                val unit = when (args.singleOrNull()) {
                    "g" -> WeightUnit.GRAM
                    "oz" -> WeightUnit.OUNCE
                    else -> return error("unit g|oz")
                }
                emulator.unit = unit
                current?.send(emulator.unitFrame())
                OK
            }
            "battery" -> {
                val percent = args.singleOrNull()?.toIntOrNull()?.takeIf { it in 0..100 } ?: return error("battery <0-100>")
                emulator.battery = percent
                current?.send(emulator.batteryFrame())
                OK
            }
            "drop" -> {
                if (!expect(0)) return error("drop")
                available = false
                current?.drop()
                OK
            }
            "back" -> {
                if (!expect(0)) return error("back")
                available = true
                OK
            }
            "reject" -> {
                val group = REJECT_GROUPS[args.singleOrNull()]
                if (group == null && args.singleOrNull() != "none") {
                    return error("reject tare|timer|settings|all|none")
                }
                if (group == null) emulator.rejected.clear() else emulator.rejected += group
                OK
            }
            "status" -> if (expect(0)) status() else error("status")
            "reset" -> {
                if (!expect(0)) return error("reset")
                emulator.reset()
                available = true
                OK
            }
            else -> usage("unknown command: $name")
        }
    }

    private fun status(): String {
        val e = emulator
        val link = when {
            !available -> "dropped"
            current?.isLinked == true -> "connected"
            else -> "idle"
        }
        val rejected = when {
            e.rejected.containsAll(REJECT_GROUPS.getValue("all")) -> listOf("all")
            else -> REJECT_GROUPS.filter { (group, cmds) -> group != "all" && e.rejected.containsAll(cmds) }.keys
                .ifEmpty { listOf("none") }
        }
        return String.format(
            Locale.US,
            "weight=%.1fg tare=%.1fg shown=%.1fg unit=%s timer=%s %ds battery=%d%% link=%s reject=%s noise=%.1fg",
            e.grossG(), e.tareG, e.grossG() - e.tareG, e.unit.symbol, e.timer, e.timerSeconds, e.battery, link,
            rejected.joinToString(","), e.noiseG,
        )
    }

    private fun error(usage: String) = "error: usage: $usage"

    private fun usage(problem: String) = "error: $problem. Commands: weight, pour, noise, unit, battery, drop, back, " +
        "reject, status, reset"

    companion object {
        /** Локально администрируемый MAC: с настоящими весами не пересечётся. */
        const val ADDRESS = "02:00:5C:A1:E0:01"
        const val OK = "ok"

        private val SETTINGS = setOf(
            Cmd.WEIGHT_UNIT, Cmd.SOUND, Cmd.DEVICE_NAME, Cmd.STANDBY_TIME, Cmd.SENSITIVITY, Cmd.PRECISION, Cmd.BRIGHTNESS,
        )

        private val REJECT_GROUPS: Map<String, Set<Int>> = mapOf(
            "tare" to setOf(Cmd.TARE),
            "timer" to setOf(Cmd.TIMER),
            "settings" to SETTINGS,
            "all" to setOf(Cmd.TARE, Cmd.TIMER, Cmd.POWER_OFF, Cmd.FACTORY_RESET, Cmd.FORGET_DEVICE) + SETTINGS,
        )
    }
}
