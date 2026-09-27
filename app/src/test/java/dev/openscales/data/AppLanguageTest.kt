package dev.openscales.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {

    @Test
    fun `language from tag`() {
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.fromTag("ru"))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.fromTag("ru-RU"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en-US"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("de"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
    }

    @Test
    fun `local store keeps the choice`() {
        var saved: String? = null
        val store = LocalAppLanguageStore(read = { saved }, write = { saved = it })
        assertEquals(AppLanguage.SYSTEM, store.current())
        store.set(AppLanguage.ENGLISH)
        assertEquals("en", saved)
        assertEquals(AppLanguage.ENGLISH, store.current())
        store.set(AppLanguage.SYSTEM)
        assertEquals(AppLanguage.SYSTEM, store.current())
    }
}
