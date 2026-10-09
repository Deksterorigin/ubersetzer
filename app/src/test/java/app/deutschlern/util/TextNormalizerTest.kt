package app.deutschlern.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

class TextNormalizerTest {

    @Test
    fun normalize_trimsLeadingAndTrailingWhitespace() {
        val input = "   Guten Morgen   "
        val expected = "Guten Morgen"
        assertEquals(expected, TextNormalizer.normalize(input))
    }

    @Test
    fun normalize_collapsesMultipleWhitespaceCharacters() {
        val input = "Das   ist    ein\t\tTest\nmit\r\nmehreren   Leerzeichen."
        val expected = "Das ist ein Test mit mehreren Leerzeichen."
        assertEquals(expected, TextNormalizer.normalize(input))
    }

    @Test
    fun normalize_preservesCaseStrictly() {
        // In German, "Schloss" (noun = castle) and "schloss" (verb = closed) are different words
        val noun = TextNormalizer.normalize("Schloss")
        val verb = TextNormalizer.normalize("schloss")

        assertEquals("Schloss", noun)
        assertEquals("schloss", verb)
        assertNotEquals(noun, verb)
    }

    @Test
    fun normalize_appliesUnicodeNfcNormalization() {
        // 'ä' composed (U+00E4) vs decomposed 'a' + combining diaeresis (U+0061 + U+0308)
        val decomposed = "a\u0308pfel"
        val expectedNfc = "äpfel"

        val normalized = TextNormalizer.normalize(decomposed)
        assertEquals(expectedNfc, normalized)
        assertEquals(Normalizer.Form.NFC, Normalizer.isNormalized(normalized, Normalizer.Form.NFC).let {
            Normalizer.Form.NFC
        })
    }

    @Test
    fun normalize_emptyAndBlank_returnsEmptyString() {
        assertEquals("", TextNormalizer.normalize(""))
        assertEquals("", TextNormalizer.normalize("     "))
        assertEquals("", TextNormalizer.normalize(null))
    }

    @Test
    fun normalizeLemma_trimsAndNormalizesNfc() {
        val input = "  u\u0308berraschen   "
        val expected = "überraschen"
        assertEquals(expected, TextNormalizer.normalizeLemma(input))
    }

    @Test
    fun normalizeLemma_emptyAndBlank_returnsEmptyString() {
        assertEquals("", TextNormalizer.normalizeLemma(""))
        assertEquals("", TextNormalizer.normalizeLemma("   \t  "))
        assertEquals("", TextNormalizer.normalizeLemma(null))
    }

    @Test
    fun normalizeLemma_preservesNounCapitalization() {
        assertEquals("Schloss", TextNormalizer.normalizeLemma("   Schloss   "))
        assertEquals("schloss", TextNormalizer.normalizeLemma(" schloss "))
    }

    @Test
    fun normalizeLemma_combiningCharacters_normalizesToNfc() {
        val decomposed = "a\u0308pfel"
        val expected = "äpfel"
        assertEquals(expected, TextNormalizer.normalizeLemma(decomposed))
        assertTrue(Normalizer.isNormalized(TextNormalizer.normalizeLemma(decomposed), Normalizer.Form.NFC))
    }

    @Test
    fun normalizeLemma_collapsesInternalWhitespace() {
        assertEquals("sich freuen", TextNormalizer.normalizeLemma("  sich    freuen  "))
        assertEquals("sich ärgern über", TextNormalizer.normalizeLemma("sich \t ärgern \n über"))
    }

    @Test
    fun extractArticleAndLemma_nounsWithArticles_extractsCorrectly() {
        assertEquals(Pair("das", "Schloss"), TextNormalizer.extractArticleAndLemma("das Schloss"))
        assertEquals(Pair("der", "Tisch"), TextNormalizer.extractArticleAndLemma("der   Tisch"))
        assertEquals(Pair("die", "Burg"), TextNormalizer.extractArticleAndLemma("die Burg"))
        assertEquals(Pair("das", "Schloss"), TextNormalizer.extractArticleAndLemma("Das Schloss"))
        assertEquals(Pair("die", "Burg"), TextNormalizer.extractArticleAndLemma("DIE Burg"))
    }

    @Test
    fun extractArticleAndLemma_withoutArticle_returnsNullArticle() {
        assertEquals(Pair(null, "aufstehen"), TextNormalizer.extractArticleAndLemma("aufstehen"))
        assertEquals(Pair(null, "Schloss"), TextNormalizer.extractArticleAndLemma("Schloss"))
        assertEquals(Pair(null, "sich freuen"), TextNormalizer.extractArticleAndLemma("sich freuen"))
        assertEquals(Pair(null, "Dasein"), TextNormalizer.extractArticleAndLemma("Dasein"))
        assertEquals(Pair(null, "Dienstag"), TextNormalizer.extractArticleAndLemma("Dienstag"))
        assertEquals(Pair(null, "Derwisch"), TextNormalizer.extractArticleAndLemma("Derwisch"))
    }

    @Test
    fun extractArticleAndLemma_emptyAndNull_returnsEmpty() {
        assertEquals(Pair(null, ""), TextNormalizer.extractArticleAndLemma(null))
        assertEquals(Pair(null, ""), TextNormalizer.extractArticleAndLemma("   "))
    }
}
