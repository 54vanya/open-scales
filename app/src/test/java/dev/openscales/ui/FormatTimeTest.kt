package dev.openscales.ui

import dev.openscales.ui.components.formatTime
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTimeTest {
    @Test
    fun `M SS without leading zero and capped at 99 59`() {
        mapOf(
            0 to "0:00", 59 to "0:59", 75 to "1:15", 599 to "9:59", 600 to "10:00",
            5999 to "99:59", 7200 to "99:59", -1 to "0:00",
        ).forEach { (seconds, expected) -> assertEquals("$seconds s", expected, formatTime(seconds)) }
    }
}
