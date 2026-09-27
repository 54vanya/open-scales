package dev.openscales.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceStoreTest {

    @Test
    fun `own colors are on by default and the choice is kept`() {
        val saved = mutableMapOf<String, Boolean>()
        val store = AppearanceStore(read = { saved[it] }, write = { k, v -> saved[k] = v })
        assertTrue(store.ownColors.value)

        store.setOwnColors(false)
        assertFalse(store.ownColors.value)

        // Новый запуск читает сохранённое значение.
        val reopened = AppearanceStore(read = { saved[it] }, write = { k, v -> saved[k] = v })
        assertFalse(reopened.ownColors.value)
        assertEquals(mapOf("own_colors" to false), saved)
    }
}
