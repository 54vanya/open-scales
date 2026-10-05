package dev.openscales.recipe

import dev.openscales.protocol.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ручной ввод дозы: точность весов, точка и запятая, 1–100 г. */
class ManualDoseTest {

    @Test
    fun `grams take one decimal`() {
        assertTrue(ManualDose.accepts("18.2", WeightUnit.GRAM))
        assertTrue(ManualDose.accepts("18,2", WeightUnit.GRAM))
        assertTrue(ManualDose.accepts("18.", WeightUnit.GRAM))
        assertTrue(ManualDose.accepts("", WeightUnit.GRAM))
        assertFalse(ManualDose.accepts("18.25", WeightUnit.GRAM))
        assertFalse(ManualDose.accepts("1000", WeightUnit.GRAM))
        assertFalse(ManualDose.accepts("1-2", WeightUnit.GRAM))
    }

    @Test
    fun `ounces take two decimals`() {
        assertTrue(ManualDose.accepts("0,64", WeightUnit.OUNCE))
        assertFalse(ManualDose.accepts("0.641", WeightUnit.OUNCE))
    }

    @Test
    fun `parses into grams`() {
        assertEquals(18.2, ManualDose.parseGrams("18,2", WeightUnit.GRAM)!!, 1e-9)
        assertEquals(0.64 * GRAMS_PER_OUNCE, ManualDose.parseGrams("0.64", WeightUnit.OUNCE)!!, 1e-9)
    }

    @Test
    fun `refuses empty and out of range`() {
        assertNull(ManualDose.parseGrams("", WeightUnit.GRAM))
        assertNull(ManualDose.parseGrams(".", WeightUnit.GRAM))
        assertNull(ManualDose.parseGrams("0.9", WeightUnit.GRAM))
        assertNull(ManualDose.parseGrams("150", WeightUnit.GRAM))
        assertEquals(100.0, ManualDose.parseGrams("100", WeightUnit.GRAM)!!, 1e-9)
        assertNull(ManualDose.parseGrams("3.53", WeightUnit.OUNCE))
    }

    @Test
    fun `bounds are rounded inwards`() {
        assertEquals("1" to "100", ManualDose.bounds(WeightUnit.GRAM))
        assertEquals("0.04" to "3.52", ManualDose.bounds(WeightUnit.OUNCE))
        val (min, max) = ManualDose.bounds(WeightUnit.OUNCE)
        assertTrue(ManualDose.parseGrams(min, WeightUnit.OUNCE) != null)
        assertTrue(ManualDose.parseGrams(max, WeightUnit.OUNCE) != null)
    }

    @Test
    fun `prefill has the scale precision`() {
        assertEquals("18.2", ManualDose.text(18.200000762939453, WeightUnit.GRAM))
        assertEquals("15.0", ManualDose.text(15.0, WeightUnit.GRAM))
        assertEquals("0.53", ManualDose.text(15.0, WeightUnit.OUNCE))
    }
}
