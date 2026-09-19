package dev.openscales.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageDecoderTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun read(cmd: Int, vararg payload: Int) = Frame(Frame.TYPE_READ, cmd, bytes(*payload))
    private fun decode(frame: Frame, unit: WeightUnit = WeightUnit.GRAM) = MessageDecoder.decode(frame, unit)

    @Test
    fun `weight in grams`() {
        val msg = decode(read(Cmd.WEIGHT, 0, 0, 0x04, 0xD2, 0x00, 0x19, 0x00, 0x0F)) as ScaleMessage.Weight
        assertEquals(123.4f, msg.grams, 0.001f)
        assertEquals(2.5f, msg.flowRate, 0.001f)
        assertEquals(15, msg.timeSeconds)
        assertFalse(msg.overload)
    }

    @Test
    fun `weight in ounces uses divisor 100`() {
        val msg = decode(read(Cmd.WEIGHT, 0, 0, 0x04, 0xD2, 0, 0, 0, 0), WeightUnit.OUNCE) as ScaleMessage.Weight
        assertEquals(12.34f, msg.grams, 0.0001f)
    }

    @Test
    fun `negative weight and flow`() {
        val msg = decode(read(Cmd.WEIGHT, 0xFF, 0xFF, 0xFF, 0xF6, 0xFF, 0xFB, 0, 0, 1)) as ScaleMessage.Weight
        assertEquals(-1.0f, msg.grams, 0.001f)
        assertEquals(-0.5f, msg.flowRate, 0.001f)
        assertTrue(msg.overload)
    }

    @Test
    fun `short weight payload becomes raw message`() {
        assertTrue(decode(read(Cmd.WEIGHT, 0, 0, 0)) is ScaleMessage.Raw)
    }

    @Test
    fun `timer states`() {
        assertEquals(TimerState.RUNNING, (decode(read(Cmd.TIMER, 1)) as ScaleMessage.Timer).state)
        assertEquals(TimerState.PAUSED, (decode(read(Cmd.TIMER, 2)) as ScaleMessage.Timer).state)
        assertEquals(TimerState.RESET, (decode(read(Cmd.TIMER, 3)) as ScaleMessage.Timer).state)
    }

    @Test
    fun `battery percent is the second byte - captured from real DOT`() {
        // Реальный ответ TIMEMORE_Dot (TES017): A5 5A 02 05 00 02 03 59 00 00 → 89%.
        val frame = FrameCodec.split(bytes(0xA5, 0x5A, 0x02, 0x05, 0x00, 0x02, 0x03, 0x59, 0x00, 0x00)).single()
        val msg = decode(frame) as ScaleMessage.Battery
        assertEquals(89, msg.percent)
        assertEquals(3, msg.status)
    }

    @Test
    fun `real DOT weight report uses frame type 1 and zero crc`() {
        val frame = FrameCodec.split(
            bytes(0xA5, 0x5A, 0x01, 0x01, 0x00, 0x09, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00, 0x00),
        ).single()
        val msg = decode(frame) as ScaleMessage.Weight
        assertEquals(0f, msg.grams, 0f)
        assertEquals(1, msg.frameType)
    }

    @Test
    fun `model string maps to model`() {
        val payload = "TES016".toByteArray()
        val msg = decode(Frame(Frame.TYPE_READ, Cmd.MODEL, payload)) as ScaleMessage.Model
        assertEquals(ScaleModel.BASIC3, msg.model)
        assertEquals(ScaleModel.ESPRO, ScaleModel.fromModelString("TES015"))
        assertEquals(ScaleModel.DOT, ScaleModel.fromModelString("TES017"))
    }

    @Test
    fun `sensitivity level is in second byte, or first when single byte`() {
        assertEquals(Sensitivity.MEDIUM, (decode(read(Cmd.SENSITIVITY, 5, 2)) as ScaleMessage.SensitivityLevel).sensitivity)
        assertEquals(Sensitivity.HIGH, (decode(read(Cmd.SENSITIVITY, 1)) as ScaleMessage.SensitivityLevel).sensitivity)
    }

    @Test
    fun `standby time in seconds`() {
        assertEquals(300, (decode(read(Cmd.STANDBY_TIME, 0x01, 0x2C, 0, 0)) as ScaleMessage.StandbyTime).seconds)
    }

    @Test
    fun `precision and brightness`() {
        assertEquals(Precision.LOW, (decode(read(Cmd.PRECISION, 10)) as ScaleMessage.PrecisionLevel).precision)
        assertEquals(70, (decode(read(Cmd.BRIGHTNESS, 70)) as ScaleMessage.Brightness).percent)
    }

    @Test
    fun `write ack success and rejection`() {
        assertEquals(ScaleMessage.WriteAck(Cmd.SOUND, true), decode(Frame(Frame.TYPE_WRITE, Cmd.SOUND, bytes(1))))
        assertEquals(ScaleMessage.WriteAck(Cmd.SOUND, false), decode(Frame(Frame.TYPE_WRITE, Cmd.SOUND, bytes(0))))
    }

    @Test
    fun `legacy weight is little endian`() {
        val w = LegacyCodec.decodeWeight(bytes(0x10, 0xD2, 0x04, 0x00, 0x00, 0x64, 0x00, 0x00, 0x00))!!
        assertEquals(123.4f, w.totalGrams, 0.001f)
        assertTrue(w.isDouble)
        assertEquals(10.0f, w.lowerGrams, 0.001f)
    }

    @Test
    fun `legacy rename command`() {
        assertEquals(listOf(0x0B, 2, 'H'.code, 'i'.code), LegacyCodec.rename("Hi").map { it.toInt() })
    }

    @Test
    fun `manufacturer data identifies model`() {
        assertEquals(ScaleModel.ESPRO, Advertisement.parseManufacturerData(bytes(1, 3, 7))!!.model)
        assertEquals(ScaleModel.DOT, Advertisement.parseManufacturerData(bytes(2, 1))!!.model)
        assertEquals(null, Advertisement.parseManufacturerData(bytes(5, 1)))
        assertEquals(null, Advertisement.parseManufacturerData(bytes(1, 9)))
        assertTrue(Advertisement.isScale(null, listOf(GattIds.SERVICE_2025)))
        assertFalse(Advertisement.isScale(null, emptyList()))
    }
}
