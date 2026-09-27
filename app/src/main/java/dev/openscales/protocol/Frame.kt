package dev.openscales.protocol

/**
 * Кадр протокола 2025: `A5 5A | type | cmd | len(BE16) | payload | crc(BE16)`.
 *
 * `type`: [TYPE_REPORT] — периодический отчёт весов (кадры веса), [TYPE_READ] — запрос чтения и ответ на него,
 * [TYPE_WRITE] — запись и подтверждение записи (payload[0] == 1 — успех).
 */
class Frame(val type: Int, val cmd: Int, val payload: ByteArray) {

    val isWriteAck: Boolean get() = type == TYPE_WRITE

    override fun toString(): String =
        "Frame(type=$type, cmd=0x%02X, payload=%s)".format(cmd, payload.toHex())

    companion object {
        const val HEADER_0 = 0xA5
        const val HEADER_1 = 0x5A
        const val TYPE_REPORT = 0x01
        const val TYPE_READ = 0x02
        const val TYPE_WRITE = 0x03
        const val OVERHEAD = 8
        const val MAX_PAYLOAD = 1024
    }
}

object FrameCodec {

    /** Запрос чтения — порт `Ble2025DeviceUtils.b()`. */
    fun encodeRead(cmd: Int): ByteArray = encode(Frame.TYPE_READ, cmd, ByteArray(0))

    /** Команда записи — порт `Ble2025DeviceUtils.c()`. */
    fun encodeWrite(cmd: Int, payload: ByteArray = ByteArray(0)): ByteArray =
        encode(Frame.TYPE_WRITE, cmd, payload)

    fun encode(type: Int, cmd: Int, payload: ByteArray): ByteArray {
        require(payload.size <= Frame.MAX_PAYLOAD) { "payload too large: ${payload.size}" }
        val out = ByteArray(payload.size + Frame.OVERHEAD)
        out[0] = Frame.HEADER_0.toByte()
        out[1] = Frame.HEADER_1.toByte()
        out[2] = type.toByte()
        out[3] = cmd.toByte()
        out[4] = (payload.size ushr 8).toByte()
        out[5] = payload.size.toByte()
        payload.copyInto(out, 6)
        val crc = Crc16.modbus(out, out.size - 2)
        out[out.size - 2] = (crc ushr 8).toByte()
        out[out.size - 1] = crc.toByte()
        return out
    }

    /**
     * Выделяет все кадры из одного уведомления. Порт цикла из `BleService$15.onCharacteristicChanged`:
     * ищем `A5 5A`, читаем длину, отбрасываем кадры с невалидной длиной, на обрезанном кадре — стоп.
     * CRC не проверяется строго: оригинал только логирует несовпадение.
     */
    fun split(data: ByteArray): List<Frame> {
        val frames = mutableListOf<Frame>()
        var i = 0
        while (i < data.size - 1) {
            if (data.u8(i) == Frame.HEADER_0 && data.u8(i + 1) == Frame.HEADER_1) {
                if (i + 6 > data.size) break
                val len = (data.u8(i + 4) shl 8) or data.u8(i + 5)
                if (len <= Frame.MAX_PAYLOAD) {
                    val end = i + len + Frame.OVERHEAD
                    if (end > data.size) break
                    frames += Frame(
                        type = data.u8(i + 2),
                        cmd = data.u8(i + 3),
                        payload = data.copyOfRange(i + 6, i + 6 + len),
                    )
                    i = end
                    continue
                }
            }
            i++
        }
        return frames
    }

    fun hasValidCrc(rawFrame: ByteArray): Boolean {
        if (rawFrame.size < Frame.OVERHEAD) return false
        val expected = (rawFrame.u8(rawFrame.size - 2) shl 8) or rawFrame.u8(rawFrame.size - 1)
        return Crc16.modbus(rawFrame, rawFrame.size - 2) == expected
    }
}

internal fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

internal fun ByteArray.u16be(index: Int): Int = (u8(index) shl 8) or u8(index + 1)

internal fun ByteArray.s16be(index: Int): Int = u16be(index).toShort().toInt()

internal fun ByteArray.s32be(index: Int): Int =
    (u8(index) shl 24) or (u8(index + 1) shl 16) or (u8(index + 2) shl 8) or u8(index + 3)

internal fun ByteArray.s32le(index: Int): Int =
    (u8(index + 3) shl 24) or (u8(index + 2) shl 16) or (u8(index + 1) shl 8) or u8(index)

fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
