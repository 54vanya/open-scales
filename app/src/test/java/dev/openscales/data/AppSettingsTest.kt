package dev.openscales.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
}
