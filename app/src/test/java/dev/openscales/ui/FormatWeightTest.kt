package dev.openscales.ui

import dev.openscales.protocol.WeightUnit
import dev.openscales.ui.components.FLOW_PLACEHOLDER_DIGITS
import dev.openscales.ui.components.formatWeight
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatWeightTest {
    @Test
    fun `decimals follow the unit`() {
        assertEquals("18.3", formatWeight(18.3f, WeightUnit.GRAM))
        assertEquals("1999.9", formatWeight(1999.9f, WeightUnit.GRAM))
        assertEquals("0.65", formatWeight(0.648f, WeightUnit.OUNCE))
    }

    @Test
    fun `no value shows digit-wide dashes, three before the point for weight and two for flow`() {
        val d = "−"
        assertEquals("$d$d$d.$d", formatWeight(null, WeightUnit.GRAM))
        assertEquals("$d$d$d.$d$d", formatWeight(null, WeightUnit.OUNCE))
        assertEquals("$d$d.$d", formatWeight(null, WeightUnit.GRAM, FLOW_PLACEHOLDER_DIGITS))
        assertEquals("$d$d.$d$d", formatWeight(null, WeightUnit.OUNCE, FLOW_PLACEHOLDER_DIGITS))
    }
}
