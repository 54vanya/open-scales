package dev.openscales.session

import dev.openscales.protocol.Frame
import dev.openscales.protocol.FrameCodec
import dev.openscales.protocol.ScaleMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class CommandException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { TIMEOUT, TRANSPORT, REJECTED, CANCELLED }
}

/**
 * Очередь команд: одна команда в полёте, ответ ищется по `(type, cmd)`,
 * таймаут ответа [responseTimeoutMs], повторы — 1 для чтений, 0 для записей.
 *
 * Коалесинг:
 * - [Coalesce.TARE] — если тара уже в очереди или в полёте, новая отклоняется;
 * - [Coalesce.TIMER] — новая команда таймера вытесняет ещё не отправленные команды таймера.
 */
class CommandQueue(
    private val scope: CoroutineScope,
    private val responseTimeoutMs: Long = RESPONSE_TIMEOUT_MS,
    private val transportWrite: suspend (ByteArray) -> Unit,
) {
    enum class Coalesce { NONE, TARE, TIMER }

    private class Entry(
        val bytes: ByteArray,
        val expectType: Int,
        val expectCmd: Int,
        /** false — fire-and-forget: успех сразу после записи в GATT. */
        val awaitResponse: Boolean,
        val retries: Int,
        val coalesce: Coalesce,
    ) {
        val result = CompletableDeferred<ScaleMessage?>()

        @Volatile
        var response: CompletableDeferred<ScaleMessage>? = null
    }

    private val lock = Any()
    private val queue = ArrayDeque<Entry>()
    private var current: Entry? = null
    private var closed = false
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)
    private val worker: Job = scope.launch { runWorker() }

    suspend fun read(cmd: Int): ScaleMessage =
        submit(Entry(FrameCodec.encodeRead(cmd), Frame.TYPE_READ, cmd, true, retries = 1, Coalesce.NONE))!!

    /** Возвращает подтверждение записи или null для [awaitAck] = false. */
    suspend fun write(
        cmd: Int,
        payload: ByteArray = ByteArray(0),
        coalesce: Coalesce = Coalesce.NONE,
        awaitAck: Boolean = true,
    ): ScaleMessage? = submit(
        Entry(FrameCodec.encodeWrite(cmd, payload), Frame.TYPE_WRITE, cmd, awaitAck, retries = 0, coalesce),
    )

    private suspend fun submit(entry: Entry): ScaleMessage? {
        val superseded = mutableListOf<Entry>()
        synchronized(lock) {
            if (closed) throw CommandException(CommandException.Kind.CANCELLED, "queue closed")
            when (entry.coalesce) {
                Coalesce.TARE -> if (current?.coalesce == Coalesce.TARE || queue.any { it.coalesce == Coalesce.TARE }) {
                    throw CommandException(CommandException.Kind.CANCELLED, "duplicate tare is in flight")
                }

                Coalesce.TIMER -> queue.removeAll { (it.coalesce == Coalesce.TIMER).also { r -> if (r) superseded += it } }
                Coalesce.NONE -> Unit
            }
            queue.addLast(entry)
        }
        superseded.forEach {
            it.result.completeExceptionally(CommandException(CommandException.Kind.CANCELLED, "superseded"))
        }
        wakeUp.trySend(Unit)
        return entry.result.await()
    }

    /** Передаёт в очередь каждое декодированное сообщение от весов. */
    fun onMessage(message: ScaleMessage) {
        val entry = synchronized(lock) { current } ?: return
        if (entry.expectType == message.frameType && entry.expectCmd == message.cmd) {
            entry.response?.complete(message)
        }
    }

    fun close(reason: String = "session closed") {
        val dropped = synchronized(lock) {
            closed = true
            val all = queue.toList() + listOfNotNull(current)
            queue.clear()
            all
        }
        dropped.forEach { it.result.completeExceptionally(CommandException(CommandException.Kind.CANCELLED, reason)) }
        worker.cancel()
    }

    private suspend fun runWorker() {
        for (signal in wakeUp) {
            while (true) {
                val entry = synchronized(lock) {
                    queue.removeFirstOrNull().also { current = it }
                } ?: break
                val outcome = runCatching { execute(entry) }
                synchronized(lock) { current = null }
                outcome.fold(
                    onSuccess = { entry.result.complete(it) },
                    onFailure = { entry.result.completeExceptionally(it) },
                )
            }
        }
    }

    private suspend fun execute(entry: Entry): ScaleMessage? {
        var attempt = 0
        while (true) {
            attempt++
            val response = CompletableDeferred<ScaleMessage>()
            entry.response = response
            try {
                transportWrite(entry.bytes)
            } catch (e: Exception) {
                if (attempt > entry.retries) throw CommandException(CommandException.Kind.TRANSPORT, e.message ?: "write failed")
                continue
            }
            if (!entry.awaitResponse) return null
            val msg = withTimeoutOrNull(responseTimeoutMs) { response.await() }
            if (msg == null) {
                if (attempt > entry.retries) throw CommandException(CommandException.Kind.TIMEOUT, "response timeout")
                continue
            }
            if (msg is ScaleMessage.WriteAck && !msg.success) {
                throw CommandException(CommandException.Kind.REJECTED, "device rejected command 0x%02X".format(msg.cmd))
            }
            return msg
        }
    }

    companion object {
        const val RESPONSE_TIMEOUT_MS = 1_500L
    }
}
