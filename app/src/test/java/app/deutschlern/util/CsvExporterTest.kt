package app.deutschlern.util

import app.deutschlern.domain.model.Meaning
import app.deutschlern.domain.model.WordEntry
import app.deutschlern.domain.model.WordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {

    @Test
    fun generateAnkiCsv_emptyList_returnsHeadersOnly() {
        val result = CsvExporter.generateAnkiCsv(emptyList())
        assertEquals("#separator:Comma\r\n#html:true\r\n\"Front\",\"Back\"\r\n", result)
    }

    @Test
    fun generateAnkiCsv_singleNoun_formatsFrontWithArticleAndBackWithExample() {
        val word = WordEntry(
            id = 1L,
            sessionId = 1L,
            lemma = "Hund",
            article = "der",
            pos = "noun",
            meanings = listOf(
                Meaning(
                    translation = "собака",
                    note = "животное",
                    exampleDe = "Der Hund bellt.",
                    exampleRu = "Собака лает."
                )
            ),
            timesTranslated = 1,
            status = WordStatus.NEW
        )

        val result = CsvExporter.generateAnkiCsv(listOf(word))
        val lines = result.split("\r\n").filter { it.isNotBlank() }

        assertEquals(4, lines.size)
        assertEquals("#separator:Comma", lines[0])
        assertEquals("#html:true", lines[1])
        assertEquals("\"Front\",\"Back\"", lines[2])
        assertTrue(lines[3].startsWith("\"der Hund\""))
        assertTrue(lines[3].contains("собака (животное)"))
        assertTrue(lines[3].contains("Der Hund bellt."))
    }

    @Test
    fun generateAnkiCsv_escapesQuotesAndCommasAccordingToRfc4180() {
        val word = WordEntry(
            id = 2L,
            sessionId = 1L,
            lemma = "sagen",
            pos = "verb",
            meanings = listOf(
                Meaning(
                    translation = "сказать, говорить",
                    exampleDe = "Er sagte: \"Guten Tag!\"",
                    exampleRu = "Он сказал: \"Добрый день!\""
                )
            ),
            timesTranslated = 2,
            status = WordStatus.LEARNING
        )

        val result = CsvExporter.generateAnkiCsv(listOf(word))
        assertTrue(result.contains("\"sagen\""))
        assertTrue(result.contains("Er sagte: \"\"Guten Tag!\"\""))
        assertTrue(result.contains("сказать, говорить"))
    }

    @Test
    fun generateAnkiCsv_prefersSelectedMeaningWhenAvailable() {
        val word = WordEntry(
            id = 3L,
            sessionId = 1L,
            lemma = "Schloss",
            article = "das",
            pos = "noun",
            meanings = listOf(
                Meaning(translation = "замок", note = "дворец", exampleDe = "Altes Schloss", exampleRu = "Старый замок"),
                Meaning(translation = "замок", note = "дверной", exampleDe = "Neues Schloss", exampleRu = "Новый замок")
            ),
            selectedMeaningIndex = 1,
            timesTranslated = 1,
            status = WordStatus.NEW
        )

        val result = CsvExporter.generateAnkiCsv(listOf(word))
        assertTrue(result.contains("дверной"))
        assertTrue(result.contains("Neues Schloss"))
    }

    @Test
    fun escapeRfc4180_replacesQuotesAndWrapsInQuotes() {
        val input = "Hello \"World\", test"
        val output = CsvExporter.escapeRfc4180(input)
        assertEquals("\"Hello \"\"World\"\", test\"", output)
    }
}
