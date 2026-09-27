package dev.openscales

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecondsProbeTest {

    @Test
    fun `steady ticks are quiet`() {
        val probe = SecondsProbe(maxGapMs = 1200)
        for (s in 0..30) assertNull(probe.onValue(s, s * 1000L + 7))
        assertNull(probe.onValue(30, 30_500)) // повторная отрисовка того же значения
    }

    @Test
    fun `freeze then catch up is reported`() {
        val probe = SecondsProbe(maxGapMs = 1200)
        probe.onValue(19, 19_000)
        probe.onValue(20, 20_000)
        assertEquals("20→22 after 2100ms", probe.onValue(22, 22_100))
    }

    @Test
    fun `late but single step is reported only with the gap check`() {
        val draws = SecondsProbe(maxGapMs = 1200)
        draws.onValue(5, 5_000)
        assertEquals("5→6 after 1500ms", draws.onValue(6, 6_500))
        val ticks = SecondsProbe()
        ticks.onValue(5, 5_000)
        assertNull(ticks.onValue(6, 6_500))
        assertEquals("6→8 after 1000ms", ticks.onValue(8, 7_500))
    }

    @Test
    fun `reset and pause are not skips`() {
        val probe = SecondsProbe(maxGapMs = 1200)
        probe.onValue(250, 1_000)
        assertNull(probe.onValue(0, 2_000)) // сброс
        assertNull(probe.onValue(1, 3_000))
        probe.pause()
        assertNull(probe.onValue(2, 60_000)) // продолжили после паузы
        assertNull(probe.onValue(3, 61_000))
    }
}
