package dev.openscales.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameCodecTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `crc16 modbus matches reference check value`() {
        assertEquals(0x4B37, Crc16.modbus("123456789".toByteArray()))
    }

    @Test
    fun `read frame has header, type 2, zero length and crc`() {
        val frame = FrameCodec.encodeRead(Cmd.BATTERY)
        assertArrayEquals(bytes(0xA5, 0x5A, 0x02, 0x05, 0x00, 0x00), frame.copyOfRange(0, 6))
        assertEquals(8, frame.size)
        val crc = Crc16.modbus(frame, 6)
        assertEquals(crc ushr 8, frame[6].toInt() and 0xFF)
        assertEquals(crc and 0xFF, frame[7].toInt() and 0xFF)
        assertTrue(FrameCodec.hasValidCrc(frame))
    }

    @Test
    fun `tare write frame`() {
        val frame = FrameCodec.encodeWrite(Cmd.TARE, bytes(0, 0))
        assertArrayEquals(bytes(0xA5, 0x5A, 0x03, 0x0D, 0x00, 0x02, 0x00, 0x00), frame.copyOfRange(0, 8))
        assertTrue(FrameCodec.hasValidCrc(frame))
    }

    @Test
    fun `split finds two frames in one notification`() {
        val weight = FrameCodec.encode(Frame.TYPE_READ, Cmd.WEIGHT, bytes(0, 0, 4, 0xD2, 0, 0x19, 0, 0x0F))
        val timer = FrameCodec.encode(Frame.TYPE_READ, Cmd.TIMER, bytes(1))
        val frames = FrameCodec.split(weight + timer)
        assertEquals(listOf(Cmd.WEIGHT, Cmd.TIMER), frames.map { it.cmd })
        assertArrayEquals(bytes(1), frames[1].payload)
    }

    @Test
    fun `split skips garbage before header`() {
        val timer = FrameCodec.encode(Frame.TYPE_READ, Cmd.TIMER, bytes(2))
        val frames = FrameCodec.split(bytes(0x00, 0xA5, 0x11) + timer)
        assertEquals(1, frames.size)
        assertEquals(Cmd.TIMER, frames[0].cmd)
    }

    @Test
    fun `truncated frame is dropped but previous frames are kept`() {
        val timer = FrameCodec.encode(Frame.TYPE_READ, Cmd.TIMER, bytes(1))
        val weight = FrameCodec.encode(Frame.TYPE_READ, Cmd.WEIGHT, ByteArray(8))
        val frames = FrameCodec.split(timer + weight.copyOfRange(0, 10))
        assertEquals(listOf(Cmd.TIMER), frames.map { it.cmd })
    }

    @Test
    fun `absurd length is ignored`() {
        val frames = FrameCodec.split(bytes(0xA5, 0x5A, 0x02, 0x01, 0x10, 0x00, 0, 0, 0, 0))
        assertTrue(frames.isEmpty())
    }
}
