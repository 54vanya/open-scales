package dev.openscales

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/**
 * Сторож главного потока (только debug-сборка): раз в [periodMs] ставит задачу в главный поток и ждёт её.
 * Если задача выполнилась позже [thresholdMs], сообщает длительность в [onStall]. Следующую задачу ставит
 * только после предыдущей, поэтому одно зависание даёт одну строку, а не очередь опоздавших задач.
 */
class MainThreadWatchdog(
    private val onStall: (Long) -> Unit,
    private val periodMs: Long = 100,
    private val thresholdMs: Long = 200,
    private val post: (Runnable) -> Unit = Handler(Looper.getMainLooper())::post,
    private val nowMs: () -> Long = SystemClock::uptimeMillis,
    private val sleep: (Long) -> Unit = Thread::sleep,
) {
    fun start() {
        thread(isDaemon = true, name = "main-watchdog") {
            while (true) check()
        }
    }

    /** Один замер: поставить задачу, дождаться, сравнить с порогом, выждать период. */
    internal fun check() {
        val posted = nowMs()
        val done = CountDownLatch(1)
        post(Runnable { done.countDown() })
        done.await()
        val lag = nowMs() - posted
        if (lag > thresholdMs) onStall(lag)
        sleep(periodMs)
    }
}
