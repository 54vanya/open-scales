package dev.openscales.data

import dev.openscales.recipe.StepWeightMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `timer sync is off by default`() = runTest {
        assertFalse(AppSettings().syncTimerWithScale)
        assertFalse(InMemoryAppSettingsStore().settings.first().syncTimerWithScale)
    }

    @Test
    fun `timer sync is stored`() = runTest {
        val store = InMemoryAppSettingsStore()
        store.setSyncTimerWithScale(true)
        assertTrue(store.settings.first().syncTimerWithScale)
    }

    @Test
    fun `slashed zero is on by default`() = runTest {
        assertTrue(AppSettings().slashedZero)
        assertTrue(InMemoryAppSettingsStore().settings.first().slashedZero)
    }

    @Test
    fun `slashed zero is stored`() = runTest {
        val store = InMemoryAppSettingsStore()
        store.setSlashedZero(false)
        assertFalse(store.settings.first().slashedZero)
    }

    @Test
    fun `step weight mode is left to pour by default`() = runTest {
        assertEquals(StepWeightMode.REMAINING, AppSettings().stepWeightMode)
        assertEquals(StepWeightMode.REMAINING, InMemoryAppSettingsStore().settings.first().stepWeightMode)
        assertEquals(StepWeightMode.REMAINING, StepWeightMode.fromName(null))
        assertEquals(StepWeightMode.REMAINING, StepWeightMode.fromName("junk"))
    }

    @Test
    fun `step weight mode is stored`() = runTest {
        val store = InMemoryAppSettingsStore()
        store.setStepWeightMode(StepWeightMode.POURED)
        assertEquals(StepWeightMode.POURED, store.settings.first().stepWeightMode)
    }

    @Test
    fun `step signals are on by default and stored`() = runTest {
        assertTrue(AppSettings().stepSignals)
        val store = InMemoryAppSettingsStore()
        assertTrue(store.settings.first().stepSignals)
        store.setStepSignals(false)
        assertFalse(store.settings.first().stepSignals)
    }
}
