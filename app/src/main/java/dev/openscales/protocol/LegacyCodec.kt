package dev.openscales.protocol

/** Протокол старых двойных весов TES08. */
object LegacyCodec {

    data class LegacyWeight(val totalGrams: Float, val isDouble: Boolean, val lowerGrams: Float)

    /** Indicate с характеристики `2A9D`. */
    fun decodeWeight(data: ByteArray): LegacyWeight? {
        if (data.size < 5) return null
        val total = data.s32le(1)
        val isDouble = data.u8(0) == 0x10
        val lower = if (isDouble && data.size >= 9) data.s32le(5) else total
        return LegacyWeight(total / 10f, isDouble, lower / 10f)
    }

    /** `[cmd] + payload` для характеристики `553f4e49-…`. */
    fun command(cmd: Int, payload: ByteArray = ByteArray(0)): ByteArray = byteArrayOf(cmd.toByte()) + payload

    /** Переименование: `[0x0B, len, bytes…]`. */
    fun rename(name: String): ByteArray {
        val bytes = name.toByteArray(Charsets.UTF_8)
        return command(LegacyCmd.DEVICE_NAME, byteArrayOf(bytes.size.toByte()) + bytes)
    }
}
