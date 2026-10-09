package app.deutschlern.util

import app.deutschlern.domain.model.Meaning
import app.deutschlern.domain.model.WordEntry
import app.deutschlern.domain.model.WordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordSearchHelperTest {

    private fun createWord(
        lemma: String,
        pos: String = "noun",
        article: String? = null,
        translations: List<String> = emptyList(),
        exampleDe: String = "",
        exampleRu: String = ""
    ): WordEntry {
        return WordEntry(
            id = 1L,
            sessionId = 1L,
            lemma = lemma,
            pos = pos,
            article = article,
            meanings = translations.map { trans ->
                Meaning(
                    translation = trans,
                    exampleDe = exampleDe,
                    exampleRu = exampleRu
                )
            },
            status = WordStatus.NEW
        )
    }

    @Test
    fun normalizeForSearch_removesGermanUmlautsAndSharpS() {
        assertEquals("uber", WordSearchHelper.normalizeForSearch("über"))
        assertEquals("apfel", WordSearchHelper.normalizeForSearch("Äpfel"))
        assertEquals("schon", WordSearchHelper.normalizeForSearch("schön"))
        assertEquals("fuss", WordSearchHelper.normalizeForSearch("Fuß"))
        assertEquals("gross", WordSearchHelper.normalizeForSearch("groß"))
    }

    @Test
    fun normalizeForSearch_expandsDigraphs() {
        assertEquals("uber", WordSearchHelper.normalizeForSearch("ueber"))
        assertEquals("apfel", WordSearchHelper.normalizeForSearch("aepfel"))
        assertEquals("schon", WordSearchHelper.normalizeForSearch("schoen"))
    }

    @Test
    fun matches_exactAndPartialLemma() {
        val word = createWord(lemma = "Wohnung")
        assertTrue(WordSearchHelper.matchesWord(word, "Wohnung"))
        assertTrue(WordSearchHelper.matchesWord(word, "wohn"))
        assertTrue(WordSearchHelper.matchesWord(word, "nung"))
        assertFalse(WordSearchHelper.matchesWord(word, "haus"))
    }

    @Test
    fun matches_umlautTolerance_queryWithoutUmlautMatchesWordWithUmlaut() {
        val word = createWord(lemma = "überall")
        assertTrue(WordSearchHelper.matchesWord(word, "uber"))
        assertTrue(WordSearchHelper.matchesWord(word, "ueber"))
        assertTrue(WordSearchHelper.matchesWord(word, "überall"))
    }

    @Test
    fun matches_umlautTolerance_queryWithUmlautMatchesWordWithDigraphOrVowel() {
        val wordAe = createWord(lemma = "Äpfel")
        assertTrue(WordSearchHelper.matchesWord(wordAe, "apfel"))
        assertTrue(WordSearchHelper.matchesWord(wordAe, "aepfel"))
        assertTrue(WordSearchHelper.matchesWord(wordAe, "Äpfel"))
        assertTrue(WordSearchHelper.matchesWord(wordAe, "äpfel"))

        val wordUe = createWord(lemma = "Mädchen")
        assertTrue(WordSearchHelper.matchesWord(wordUe, "madchen"))
        assertTrue(WordSearchHelper.matchesWord(wordUe, "maedchen"))

        val wordOe = createWord(lemma = "König")
        assertTrue(WordSearchHelper.matchesWord(wordOe, "konig"))
        assertTrue(WordSearchHelper.matchesWord(wordOe, "koenig"))
    }

    @Test
    fun matches_sharpSTolerance() {
        val word = createWord(lemma = "Straße")
        assertTrue(WordSearchHelper.matchesWord(word, "strasse"))
        assertTrue(WordSearchHelper.matchesWord(word, "straße"))
        assertTrue(WordSearchHelper.matchesWord(word, "stras"))
    }

    @Test
    fun matches_caseInsensitive() {
        val word = createWord(lemma = "Hund")
        assertTrue(WordSearchHelper.matchesWord(word, "hund"))
        assertTrue(WordSearchHelper.matchesWord(word, "HUND"))
        assertTrue(WordSearchHelper.matchesWord(word, "hUnD"))
    }

    @Test
    fun matches_russianTranslation() {
        val word = createWord(
            lemma = "Katze",
            translations = listOf("кошка", "кот")
        )
        assertTrue(WordSearchHelper.matchesWord(word, "кошка"))
        assertTrue(WordSearchHelper.matchesWord(word, "кот"))
        assertTrue(WordSearchHelper.matchesWord(word, "кош"))
        assertFalse(WordSearchHelper.matchesWord(word, "собака"))
    }

    @Test
    fun matches_germanExamples() {
        val word = createWord(
            lemma = "fahren",
            translations = listOf("ехать"),
            exampleDe = "Ich fahre nach Berlin mit dem Auto."
        )
        assertTrue(WordSearchHelper.matchesWord(word, "Berlin"))
        assertTrue(WordSearchHelper.matchesWord(word, "berlin"))
    }

    @Test
    fun matches_blankQuery_alwaysMatches() {
        val word = createWord(lemma = "beliebig")
        assertTrue(WordSearchHelper.matchesWord(word, ""))
        assertTrue(WordSearchHelper.matchesWord(word, "   "))
    }
}
