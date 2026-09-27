package dev.openscales

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainThreadWatchdogTest {

    @Test
    fun `one blocking of the main thread gives one stall`() {
        val main = Executors.newSingleThreadExecutor()
        val stalls = mutableListOf<Long>()
        val watchdog = MainThreadWatchdog(
            onStall = { synchronized(stalls) { stalls += it } },
            periodMs = 10,
            post = { main.execute(it) },
            nowMs = { System.nanoTime() / 1_000_000 },
        )
        val checks = Thread { repeat(80) { watchdog.check() } }
        checks.start()
        Thread.sleep(100)
        main.execute { Thread.sleep(500) }
        checks.join(10_000)
        main.shutdown()
        main.awaitTermination(1, TimeUnit.SECONDS)

        assertEquals(stalls.toString(), 1, stalls.size)
        assertTrue(stalls.toString(), stalls.single() in 300..600)
    }
}
