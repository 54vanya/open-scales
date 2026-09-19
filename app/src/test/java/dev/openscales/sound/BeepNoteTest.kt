package dev.openscales.sound

import dev.openscales.data.AppSettings
import dev.openscales.data.BeepNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class BeepNoteTest {
    @Test
    fun `notes match equal temperament of the sixth octave`() {
        // Номер полутона от A4: C6 = +15 … B6 = +26.
        val semitones = listOf(15, 17, 19, 20, 22, 24, 26)
        BeepNote.entries.zip(semitones).forEach { (note, n) ->
            val exact = 440.0 * 2.0.pow(n / 12.0)
            assertTrue("$note ${note.hz} vs $exact", abs(note.hz - exact) < 1.0)
        }
    }

    @Test
    fun `defaults are enabled A6 and unknown name falls back to A6`() {
        assertEquals(AppSettings(beepEnabled = true, beepNote = BeepNote.A6, triggerOnPress = true), AppSettings())
        assertEquals(BeepNote.A6, BeepNote.fromName("H7"))
        assertEquals(BeepNote.A6, BeepNote.fromName(null))
        assertEquals(BeepNote.G6, BeepNote.fromName("G6"))
    }
}
