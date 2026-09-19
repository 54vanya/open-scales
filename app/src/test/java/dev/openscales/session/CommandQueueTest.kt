package dev.openscales.session

import dev.openscales.protocol.Cmd
import dev.openscales.protocol.Frame
import dev.openscales.protocol.FrameCodec
import dev.openscales.protocol.MessageDecoder
import dev.openscales.protocol.ScaleMessage
import dev.openscales.protocol.WeightUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CommandQueueTest {

    /** Записанные кадры и «весы», которые отвечают на них через [respond]. */
    private class Harness(scope: CoroutineScope, var respond: (Frame) -> ByteArray? = { null }) {
        val sent = mutableListOf<Frame>()
        lateinit var queue: CommandQueue

        init {
            queue = CommandQueue(scope) { bytes ->
                val frame = FrameCodec.split(bytes).single()
                sent += frame
                respond(frame)?.let { payload ->
                    scope.launch {
                        queue.onMessage(MessageDecoder.decode(Frame(frame.type, frame.cmd, payload), WeightUnit.GRAM))
                    }
                }
            }
        }
    }

    private fun TestScope.harness(respond: (Frame) -> ByteArray? = { null }) = Harness(backgroundScope, respond)

    @Test
    fun `read returns matching response`() = runTest {
        val h = harness { if (it.cmd == Cmd.BATTERY) byteArrayOf(3, 55) else null }
        val msg = h.queue.read(Cmd.BATTERY)
        assertEquals(55, (msg as ScaleMessage.Battery).percent)
    }

    @Test
    fun `read retries once and then times out after two response windows`() = runTest {
        val h = harness()
        try {
            h.queue.read(Cmd.BATTERY)
            fail("expected timeout")
        } catch (e: CommandException) {
            assertEquals(CommandException.Kind.TIMEOUT, e.kind)
        }
        assertEquals(2, h.sent.size)
        assertEquals(2 * CommandQueue.RESPONSE_TIMEOUT_MS, currentTime)
    }

    @Test
    fun `write is not retried`() = runTest {
        val h = harness()
        runCatching { h.queue.write(Cmd.SOUND, byteArrayOf(1)) }
        assertEquals(1, h.sent.size)
    }

    @Test
    fun `rejected write fails with REJECTED`() = runTest {
        val h = harness { byteArrayOf(0) }
        try {
            h.queue.write(Cmd.SOUND, byteArrayOf(1))
            fail("expected rejection")
        } catch (e: CommandException) {
            assertEquals(CommandException.Kind.REJECTED, e.kind)
        }
    }

    @Test
    fun `commands are sent one at a time`() = runTest {
        val h = harness { if (it.type == Frame.TYPE_WRITE) byteArrayOf(1) else null }
        val a = async { h.queue.write(Cmd.SOUND, byteArrayOf(1)) }
        val b = async { h.queue.write(Cmd.PRECISION, byteArrayOf(1)) }
        a.await(); b.await()
        assertEquals(listOf(Cmd.SOUND, Cmd.PRECISION), h.sent.map { it.cmd })
    }

    @Test
    fun `second tare while first is in flight is dropped`() = runTest {
        val h = harness()
        val first = async { runCatching { h.queue.write(Cmd.TARE, byteArrayOf(0, 0), CommandQueue.Coalesce.TARE) } }
        runCurrent()
        val second = runCatching { h.queue.write(Cmd.TARE, byteArrayOf(0, 0), CommandQueue.Coalesce.TARE) }
        assertEquals(CommandException.Kind.CANCELLED, (second.exceptionOrNull() as CommandException).kind)
        first.await()
        assertEquals(1, h.sent.count { it.cmd == Cmd.TARE })
    }

    @Test
    fun `new timer command supersedes queued one`() = runTest {
        val h = harness { if (it.type == Frame.TYPE_WRITE) byteArrayOf(1) else null }
        // Занимаем очередь чтением без ответа.
        val blocker = async { runCatching { h.queue.read(Cmd.MODEL) } }
        runCurrent()
        val start = async { runCatching { h.queue.write(Cmd.TIMER, byteArrayOf(1), CommandQueue.Coalesce.TIMER) } }
        runCurrent()
        val pause = async { runCatching { h.queue.write(Cmd.TIMER, byteArrayOf(2), CommandQueue.Coalesce.TIMER) } }
        advanceUntilIdle()
        blocker.await()
        assertTrue(start.await().exceptionOrNull() is CommandException)
        assertTrue(pause.await().isSuccess)
        assertEquals(listOf(2), h.sent.filter { it.cmd == Cmd.TIMER }.map { it.payload[0].toInt() })
    }

    @Test
    fun `fire and forget completes without ack`() = runTest {
        val h = harness()
        val result = h.queue.write(Cmd.DISCONNECT, awaitAck = false)
        assertEquals(null, result)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `close cancels pending commands`() = runTest {
        val h = harness()
        val pending = async { runCatching { h.queue.read(Cmd.BATTERY) } }
        runCurrent()
        h.queue.close()
        val error = pending.await().exceptionOrNull() as CommandException
        assertEquals(CommandException.Kind.CANCELLED, error.kind)
    }
}
