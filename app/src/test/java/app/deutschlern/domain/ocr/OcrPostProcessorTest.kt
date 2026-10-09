package app.deutschlern.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrPostProcessorTest {

    @Test
    fun `empty and blank text returns empty string`() {
        assertEquals("", OcrPostProcessor.process(""))
        assertEquals("", OcrPostProcessor.process("   \n \t \n  "))
    }

    @Test
    fun `dehyphenates single German word broken across line`() {
        val input = "Spra-\nche"
        val expected = "Sprache"
        assertEquals(expected, OcrPostProcessor.process(input))
    }

    @Test
    fun `dehyphenates with soft hyphen character`() {
        val input = "Ver\u00AD\nänderung"
        val expected = "Veränderung"
        assertEquals(expected, OcrPostProcessor.process(input))
    }

    @Test
    fun `dehyphenates inside sentence preserving umlauts and ß`() {
        val input = "Er muss regel-\nmäßig für die Prü-\nfung üben."
        val expected = "Er muss regelmäßig für die Prüfung üben."
        assertEquals(expected, OcrPostProcessor.process(input))
    }

    @Test
    fun `retains hyphen for capitalized compound words`() {
        val input = "Baden-\nWürttemberg"
        val expected = "Baden-Württemberg"
        assertEquals(expected, OcrPostProcessor.process(input))
    }

    @Test
    fun `retains hyphen for abbreviations like U-Bahn`() {
        val input = "U-\nBahn"
        val expected = "U-Bahn"
        assertEquals(expected, OcrPostProcessor.process(input))

        val input2 = "B2-\nNiveau"
        val expected2 = "B2-Niveau"
        assertEquals(expected2, OcrPostProcessor.process(input2))
    }

    @Test
    fun `joins normal wrapped lines within paragraph with single space`() {
        val input = "Dies ist ein deutscher Text,\nder über mehrere Zeilen\ngedruckt ist."
        val expected = "Dies ist ein deutscher Text, der über mehrere Zeilen gedruckt ist."
        assertEquals(expected, OcrPostProcessor.process(input))
    }

    @Test
    fun `preserves paragraph breaks as double newlines`() {
        val input = "Erster Absatz mit Text.\n\nZweiter Absatz mit mehr Text."
        val expected = "Erster Absatz mit Text.\n\nZweiter Absatz mit mehr Text."
        assertEquals(expected, OcrPostProcessor.process(input))
    }

    @Test
    fun `handles multiple empty lines between paragraphs cleanly`() {
        val input = "Erster Absatz.\n\n\n\nZweiter Absatz."
        val expected = "Erster Absatz.\n\nZweiter Absatz."
        assertEquals(expected, OcrPostProcessor.process(input))
    }

    @Test
    fun `handles complex multi-paragraph document with hyphens and spaces`() {
        val input = """
            Die deutsche Spra-
            che ist für viele Ler-
            nende eine Heraus-
            forderung.

            Besonders in Baden-
            Württemberg spricht man
            oft Dialekt.
        """.trimIndent()

        val expected = """
            Die deutsche Sprache ist für viele Lernende eine Herausforderung.

            Besonders in Baden-Württemberg spricht man oft Dialekt.
        """.trimIndent()

        assertEquals(expected, OcrPostProcessor.process(input))
    }

    @Test
    fun `preserves punctuation dash without merging`() {
        val input = "Das alte Haus –\naber es ist schön."
        val expected = "Das alte Haus – aber es ist schön."
        assertEquals(expected, OcrPostProcessor.process(input))
    }
}
