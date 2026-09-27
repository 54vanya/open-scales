package dev.openscales.sim

import dev.openscales.protocol.Cmd
import dev.openscales.protocol.Frame
import dev.openscales.protocol.FrameCodec
import dev.openscales.protocol.MessageDecoder
import dev.openscales.protocol.ScaleMessage
import dev.openscales.protocol.ScaleModel
import dev.openscales.protocol.TimerState
import dev.openscales.protocol.WeightUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaleEmulatorTest {

    private var now = 0L
    private val scale = ScaleEmulator(nowMs = { now })

    private fun read(cmd: Int): ScaleMessage =
        MessageDecoder.decode(scale.onFrame(Frame(Frame.TYPE_READ, cmd, ByteArray(0))).frames.single(), scale.unit)

    private fun write(cmd: Int, vararg payload: Int): Frame =
        scale.onFrame(Frame(Frame.TYPE_WRITE, cmd, ByteArray(payload.size) { payload[it].toByte() })).frames.single()

    private fun weight(): ScaleMessage.Weight = MessageDecoder.decode(scale.weightFrame(), scale.unit) as ScaleMessage.Weight

    // region 1.1 ответы

    @Test
    fun `handshake reads answer like a DOT`() {
        assertEquals(80, (read(Cmd.BATTERY) as ScaleMessage.Battery).percent)
        assertEquals(ScaleModel.DOT, (read(Cmd.MODEL) as ScaleMessage.Model).model)
        assertEquals(WeightUnit.GRAM, (read(Cmd.WEIGHT_UNIT) as ScaleMessage.Unit).unit)
        assertEquals(ScaleMessage.ModeStage(1, 1), read(Cmd.MODE_STAGE))
        assertEquals(TimerState.RESET, (read(Cmd.TIMER) as ScaleMessage.Timer).state)
        assertEquals("Virtual DOT", (read(Cmd.DEVICE_NAME) as ScaleMessage.Name).name)
    }

    @Test
    fun `serial number read stays silent like a DOT`() {
        assertTrue(scale.onFrame(Frame(Frame.TYPE_READ, Cmd.SERIAL_NUMBER, ByteArray(0))).frames.isEmpty())
    }

    @Test
    fun `writes are acknowledged and rejected on demand`() {
        scale.setWeight(312.0)
        assertArrayEquals(byteArrayOf(1), write(Cmd.TARE, 0, 0).payload)
        assertEquals(0.0, weight().grams.toDouble(), 1e-6)

        scale.setWeight(400.0)
        scale.rejected += Cmd.TARE
        assertArrayEquals(byteArrayOf(0), write(Cmd.TARE, 0, 0).payload)
        assertEquals(88.0, weight().grams.toDouble(), 1e-4)
    }

    @Test
    fun `written settings are read back`() {
        write(Cmd.DEVICE_NAME, *"Kitchen".toByteArray().map { it.toInt() }.toIntArray())
        assertEquals("Kitchen", (read(Cmd.DEVICE_NAME) as ScaleMessage.Name).name)
        write(Cmd.WEIGHT_UNIT, 1)
        assertEquals(WeightUnit.OUNCE, (read(Cmd.WEIGHT_UNIT) as ScaleMessage.Unit).unit)
        write(Cmd.STANDBY_TIME, 0x02, 0x58, 0, 0)
        assertEquals(600, (read(Cmd.STANDBY_TIME) as ScaleMessage.StandbyTime).seconds)
    }

    @Test
    fun `overrides win over emulation`() {
        scale.readOverrides[Cmd.TIMER] = byteArrayOf(1)
        assertEquals(TimerState.RUNNING, (read(Cmd.TIMER) as ScaleMessage.Timer).state)
    }

    @Test
    fun `power off acknowledges and drops the link`() {
        val response = scale.onFrame(Frame(Frame.TYPE_WRITE, Cmd.POWER_OFF, ByteArray(0)))
        assertTrue(response.disconnect)
        assertArrayEquals(byteArrayOf(1), response.frames.single().payload)
    }

    @Test
    fun `frames go out with a zero crc like the real scale`() {
        val bytes = ScaleEmulator.encodeLikeScale(scale.weightFrame())
        assertEquals(0, bytes[bytes.size - 1].toInt())
        assertEquals(0, bytes[bytes.size - 2].toInt())
        assertEquals(Frame.TYPE_REPORT, FrameCodec.split(bytes).single().type)
    }

    // endregion

    // region 1.2 вес по времени

    @Test
    fun `pour grows the weight linearly`() {
        scale.pour(250.0, 30.0)
        now = 15_000
        assertEquals(125.0, weight().grams.toDouble(), 0.05)
        assertEquals(8.3, weight().flowRate.toDouble(), 0.05)
        now = 30_000
        assertEquals(250.0, weight().grams.toDouble(), 0.05)
        now = 32_000
        assertEquals(250.0, weight().grams.toDouble(), 0.05)
        assertEquals(0.0, weight().flowRate.toDouble(), 0.05)
    }

    @Test
    fun `new pour continues from the current weight`() {
        scale.pour(100.0, 10.0)
        now = 5_000
        scale.pour(100.0, 10.0)
        now = 15_000
        assertEquals(150.0, scale.grossG(), 1e-9)
    }

    @Test
    fun `ounces`() {
        scale.setWeight(250.0)
        scale.unit = WeightUnit.OUNCE
        assertEquals(8.82, weight().grams.toDouble(), 1e-4)
        assertEquals(WeightUnit.OUNCE, (MessageDecoder.decode(scale.unitFrame(), WeightUnit.GRAM) as ScaleMessage.Unit).unit)
    }

    @Test
    fun `noise stays within the amplitude and is off by default`() {
        scale.setWeight(10.0)
        assertEquals(10.0, weight().grams.toDouble(), 1e-6)
        scale.noiseG = 0.2
        repeat(100) { assertEquals(10.0, weight().grams.toDouble(), 0.21) }
    }

    // endregion

    // region 1.3 таймер

    @Test
    fun `timer runs pauses and resets`() {
        write(Cmd.TIMER, 1)
        now = 12_500
        assertEquals(12, weight().timeSeconds)
        write(Cmd.TIMER, 2)
        now = 20_000
        assertEquals(12, weight().timeSeconds)
        assertEquals(TimerState.PAUSED, (read(Cmd.TIMER) as ScaleMessage.Timer).state)
        write(Cmd.TIMER, 3)
        assertEquals(0, weight().timeSeconds)
    }

    @Test
    fun `resetting a zero timer tares like a DOT`() {
        scale.setWeight(18.0)
        write(Cmd.TIMER, 3)
        assertEquals(0.0, weight().grams.toDouble(), 1e-6)

        // Таймер с временем сбрасывается без тары.
        scale.setWeight(50.0)
        write(Cmd.TIMER, 1)
        now = 3_000
        write(Cmd.TIMER, 3)
        assertEquals(32.0, weight().grams.toDouble(), 1e-4)
    }

    // endregion
}
