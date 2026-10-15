package app.deutschlern.ui.dialog.components

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TranscriptTokenizerTest {

    @Before
    fun setUp() {
        TranscriptTokenizer.clearCache()
    }

    @After
    fun tearDown() {
        TranscriptTokenizer.clearCache()
    }

    @Test
    fun tokenize_emptyString_returnsEmptyAnnotatedString() {
        val result = TranscriptTokenizer.tokenize("")
        assertEquals("", result.text)
        assertTrue(result.getStringAnnotations(TranscriptTokenizer.TAG_WORD, 0, 0).isEmpty())
    }

    @Test
    fun tokenize_germanSentence_createsWordAnnotationsCorrectly() {
        val input = "Hallo, wie geht's? Ich möchte über Köln sprechen!"
        val result = TranscriptTokenizer.tokenize(input)

        assertEquals(input, result.text)
        val wordAnnotations = result.getStringAnnotations(TranscriptTokenizer.TAG_WORD, 0, input.length)

        val words = wordAnnotations.map { it.item }
        assertTrue(words.contains("Hallo"))
        assertTrue(words.contains("wie"))
        assertTrue(words.contains("geht"))
        assertTrue(words.contains("Ich"))
        assertTrue(words.contains("möchte"))
        assertTrue(words.contains("über"))
        assertTrue(words.contains("Köln"))
        assertTrue(words.contains("sprechen"))
    }

    @Test
    fun tokenize_caching_returnsCachedAnnotatedString() {
        val input = "Guten Tag, Herr Müller!"
        val first = TranscriptTokenizer.tokenize(input)
        val second = TranscriptTokenizer.tokenize(input)

        assertSame("Second call should return cached instance", first, second)
        assertEquals(1, TranscriptTokenizer.cacheSize())
    }

    @Test
    fun tokenize_surrogatePairsAndEmojis_doesNotCrash() {
        val input = "Super gemacht! 👍 🎉 Danke schön!"
        val result = TranscriptTokenizer.tokenize(input)

        assertEquals(input, result.text)
        val wordAnnotations = result.getStringAnnotations(TranscriptTokenizer.TAG_WORD, 0, input.length)
        assertTrue(wordAnnotations.any { it.item == "Super" })
        assertTrue(wordAnnotations.any { it.item == "gemacht" })
        assertTrue(wordAnnotations.any { it.item == "Danke" })
        assertTrue(wordAnnotations.any { it.item == "schön" })
    }

    @Test
    fun tokenize_veryLongToken_linearScanWithoutBacktracking() {
        val longWord = "Donaudampfschifffahrtselektrizitätenhauptbetriebswerkbauunterbeamtengesellschaft".repeat(100)
        val result = TranscriptTokenizer.tokenize(longWord)

        assertEquals(longWord, result.text)
        val annotations = result.getStringAnnotations(TranscriptTokenizer.TAG_WORD, 0, longWord.length)
        assertEquals(1, annotations.size)
        assertEquals(longWord, annotations[0].item)
    }

    @Test
    fun prewarm_populatesCacheForGivenTexts() {
        val texts = listOf("Satz eins", "Satz zwei", "Satz drei")
        TranscriptTokenizer.prewarm(texts)

        assertEquals(3, TranscriptTokenizer.cacheSize())
        val res = TranscriptTokenizer.tokenize("Satz zwei")
        assertEquals("Satz zwei", res.text)
    }
}
