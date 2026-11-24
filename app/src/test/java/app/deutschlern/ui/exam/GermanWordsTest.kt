package app.deutschlern.ui.exam

import org.junit.Assert.assertEquals
import org.junit.Test

class GermanWordsTest {

    @Test
    fun `each word carries its own sentence`() {
        val words = findGermanWords("Ich bitte um Rückruf. Der Termin ist am Montag!")
        assertEquals(listOf("Ich", "bitte", "um", "Rückruf", "Der", "Termin", "ist", "am", "Montag"), words.map { it.word })
        assertEquals("Ich bitte um Rückruf.", words.first { it.word == "Rückruf" }.sentence)
        assertEquals("Der Termin ist am Montag!", words.first { it.word == "Termin" }.sentence)
    }

    @Test
    fun `hyphenated compounds and umlauts stay one word, numbers are skipped`() {
        val words = findGermanWords("Die Deutsch-japanische Zusammenarbeit: 20 % Rabatt für Mitarbeiter.")
        assertEquals(listOf("Die", "Deutsch-japanische", "Zusammenarbeit", "Rabatt", "für", "Mitarbeiter"), words.map { it.word })
    }

    @Test
    fun `line breaks end a sentence and ranges point into the text`() {
        val text = "Sehr geehrte Frau Woller,\nleider muss ich mich beschweren"
        val words = findGermanWords(text)
        assertEquals("Sehr geehrte Frau Woller,", words.first { it.word == "Woller" }.sentence)
        assertEquals("leider muss ich mich beschweren", words.last().sentence)
        words.forEach { assertEquals(it.word, text.substring(it.range)) }
    }
}
