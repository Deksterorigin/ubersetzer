package app.deutschlern.domain.sanitizer

import app.deutschlern.domain.model.Meaning
import app.deutschlern.domain.model.ReviewCard
import app.deutschlern.domain.model.WordEntry
import app.deutschlern.domain.model.WordStatus
import app.deutschlern.util.CsvExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleSanitizerTest {

    @Test
    fun sanitize_verb_dropsArticle() {
        val result = ArticleSanitizer.sanitize(
            rawLemma = "erwähnen",
            rawPos = "verb",
            rawArticle = "die"
        )
        assertEquals("erwähnen", result.lemma)
        assertEquals("verb", result.pos)
        assertNull(result.article)
    }

    @Test
    fun sanitize_adjective_dropsArticle() {
        val result = ArticleSanitizer.sanitize(
            rawLemma = "ausüblich",
            rawPos = "adjective",
            rawArticle = "die"
        )
        assertEquals("ausüblich", result.lemma)
        assertEquals("adjective", result.pos)
        assertNull(result.article)
    }

    @Test
    fun sanitize_adverb_dropsArticle() {
        val result = ArticleSanitizer.sanitize(
            rawLemma = "kaum",
            rawPos = "adverb",
            rawArticle = "der"
        )
        assertEquals("kaum", result.lemma)
        assertEquals("adverb", result.pos)
        assertNull(result.article)
    }

    @Test
    fun sanitize_preposition_dropsArticle() {
        val result = ArticleSanitizer.sanitize(
            rawLemma = "mit",
            rawPos = "preposition",
            rawArticle = "das"
        )
        assertEquals("mit", result.lemma)
        assertEquals("preposition", result.pos)
        assertNull(result.article)
    }

    @Test
    fun sanitize_phrase_dropsArticle() {
        val result = ArticleSanitizer.sanitize(
            rawLemma = "im Grunde",
            rawPos = "phrase",
            rawArticle = "das"
        )
        assertEquals("im Grunde", result.lemma)
        assertEquals("phrase", result.pos)
        assertNull(result.article)
    }

    @Test
    fun sanitize_conjunction_dropsArticle() {
        val result = ArticleSanitizer.sanitize(
            rawLemma = "weil",
            rawPos = "conjunction",
            rawArticle = "der"
        )
        assertEquals("weil", result.lemma)
        assertNull(result.article)
    }

    @Test
    fun sanitize_pronoun_dropsArticle() {
        val result = ArticleSanitizer.sanitize(
            rawLemma = "jeder",
            rawPos = "pronoun",
            rawArticle = "der"
        )
        assertEquals("jeder", result.lemma)
        assertNull(result.article)
    }

    @Test
    fun sanitize_particle_dropsArticle() {
        val result = ArticleSanitizer.sanitize(
            rawLemma = "doch",
            rawPos = "particle",
            rawArticle = "das"
        )
        assertEquals("doch", result.lemma)
        assertNull(result.article)
    }

    @Test
    fun sanitize_masculineNoun_keepsArticle() {
        val result = ArticleSanitizer.sanitize(
            rawLemma = "Tisch",
            rawPos = "noun",
            rawArticle = "der"
        )
        assertEquals("Tisch", result.lemma)
        assertEquals("noun", result.pos)
        assertEquals("der", result.article)
    }

    @Test
    fun sanitize_feminineNoun_keepsArticle() {
        val result = ArticleSanitizer.sanitize(
            rawLemma = "Tür",
            rawPos = "noun",
            rawArticle = "die"
        )
        assertEquals("Tür", result.lemma)
        assertEquals("noun", result.pos)
        assertEquals("die", result.article)
    }

    @Test
    fun sanitize_neuterNoun_keepsArticle() {
        val result = ArticleSanitizer.sanitize(
            rawLemma = "Buch",
            rawPos = "noun",
            rawArticle = "das"
        )
        assertEquals("Buch", result.lemma)
        assertEquals("noun", result.pos)
        assertEquals("das", result.article)
    }

    @Test
    fun sanitize_pluralOnlyNoun_keepsDie() {
        val result = ArticleSanitizer.sanitize(
            rawLemma = "Eltern",
            rawPos = "noun",
            rawArticle = "die"
        )
        assertEquals("Eltern", result.lemma)
        assertEquals("die", result.article)
    }

    @Test
    fun sanitize_lowercaseNounLemma_capitalizesLemma() {
        val result = ArticleSanitizer.sanitize(
            rawLemma = "apfel",
            rawPos = "noun",
            rawArticle = "der"
        )
        assertEquals("Apfel", result.lemma)
        assertEquals("der", result.article)
    }

    @Test
    fun sanitize_nonCanonicalPos_n_normalizedToNoun_andPreservesArticle() {
        val result = ArticleSanitizer.sanitize(
            rawLemma = "Kuchen",
            rawPos = "n",
            rawArticle = "der"
        )
        assertEquals("Kuchen", result.lemma)
        assertEquals("noun", result.pos)
        assertEquals("der", result.article)
    }

    @Test
    fun sanitize_nonCanonicalPos_nomen_normalizedToNoun() {
        val result = ArticleSanitizer.sanitize(
            rawLemma = "Sonne",
            rawPos = "nomen",
            rawArticle = "die"
        )
        assertEquals("Sonne", result.lemma)
        assertEquals("noun", result.pos)
        assertEquals("die", result.article)
    }

    @Test
    fun sanitize_nonCanonicalPos_adj_normalizedToAdjective_andDropsArticle() {
        val result = ArticleSanitizer.sanitize(
            rawLemma = "schnell",
            rawPos = "adj",
            rawArticle = "die"
        )
        assertEquals("schnell", result.lemma)
        assertEquals("adjective", result.pos)
        assertNull(result.article)
    }

    @Test
    fun checkSuffixRule_expectedGenders() {
        assertEquals("die", ArticleSanitizer.checkSuffixRule("Prüfung"))
        assertEquals("die", ArticleSanitizer.checkSuffixRule("Freiheit"))
        assertEquals("die", ArticleSanitizer.checkSuffixRule("Möglichkeit"))
        assertEquals("die", ArticleSanitizer.checkSuffixRule("Freundschaft"))
        assertEquals("die", ArticleSanitizer.checkSuffixRule("Station"))
        assertEquals("die", ArticleSanitizer.checkSuffixRule("Universität"))
        assertEquals("das", ArticleSanitizer.checkSuffixRule("Mädchen"))
        assertEquals("das", ArticleSanitizer.checkSuffixRule("Fräulein"))
        assertEquals("der", ArticleSanitizer.checkSuffixRule("Tourismus"))
    }

    @Test
    fun checkSuffixRule_exceptionsList_returnsNullConflict() {
        assertNull(ArticleSanitizer.checkSuffixRule("Kuchen"))
        assertNull(ArticleSanitizer.checkSuffixRule("Drachen"))
        assertNull(ArticleSanitizer.checkSuffixRule("Rachen"))
        assertNull(ArticleSanitizer.checkSuffixRule("Rechen"))
        assertNull(ArticleSanitizer.checkSuffixRule("Knochen"))
    }

    @Test
    fun sanitizeGermanEquivalent_singleNounKeepsArticle() {
        val result = ArticleSanitizer.sanitizeGermanEquivalent("der Apfel")
        assertEquals("der Apfel", result)
    }

    @Test
    fun sanitizeGermanEquivalent_verbWithArticle_stripsArticle() {
        val result = ArticleSanitizer.sanitizeGermanEquivalent("die erwähnen")
        assertEquals("erwähnen", result)
    }

    @Test
    fun sanitizeGermanEquivalent_adjectiveWithArticle_stripsArticle() {
        val result = ArticleSanitizer.sanitizeGermanEquivalent("das schön")
        assertEquals("schön", result)
    }

    @Test
    fun sanitizeGermanEquivalent_phraseWithoutArticle_unmodified() {
        val result = ArticleSanitizer.sanitizeGermanEquivalent("im Grunde genommen")
        assertEquals("im Grunde genommen", result)
    }

    @Test
    fun goldenTest_csvExport_nounWithArticle_verbWithoutArticle() {
        val noun = WordEntry(
            id = 1L,
            sessionId = 1L,
            lemma = "Tisch",
            article = "der",
            pos = "noun",
            meanings = listOf(Meaning(translation = "стол"))
        )
        val verbWithCorruptedArticle = WordEntry(
            id = 2L,
            sessionId = 1L,
            lemma = "erwähnen",
            article = "die", // corrupted!
            pos = "verb",
            meanings = listOf(Meaning(translation = "упоминать"))
        )
        val adjectiveWithCorruptedArticle = WordEntry(
            id = 3L,
            sessionId = 1L,
            lemma = "ausüblich",
            article = "die", // corrupted!
            pos = "adjective",
            meanings = listOf(Meaning(translation = "осуществимый"))
        )

        val csv = CsvExporter.generateAnkiCsv(listOf(noun, verbWithCorruptedArticle, adjectiveWithCorruptedArticle))
        val lines = csv.split("\r\n").filter { it.isNotBlank() }

        // Line 0: #separator:Comma, Line 1: #html:true, Line 2: "Front","Back"
        assertTrue(lines[3].startsWith("\"der Tisch\""))
        assertTrue(lines[4].startsWith("\"erwähnen\""))
        assertFalse(lines[4].contains("die erwähnen"))
        assertTrue(lines[5].startsWith("\"ausüblich\""))
        assertFalse(lines[5].contains("die ausüblich"))
    }

    @Test
    fun goldenTest_reviewCard_frontDeText_sanitizesArticle() {
        val verb = WordEntry(
            id = 10L,
            sessionId = 1L,
            lemma = "erwähnen",
            article = "die", // invalid!
            pos = "verb",
            meanings = listOf(Meaning(translation = "упоминать"))
        )
        val card = ReviewCard(word = verb)
        assertEquals("erwähnen", card.frontDeText)
        assertEquals("erwähnen", card.backDeText)

        val noun = WordEntry(
            id = 11L,
            sessionId = 1L,
            lemma = "Haus",
            article = "das",
            pos = "noun",
            meanings = listOf(Meaning(translation = "дом"))
        )
        val nounCard = ReviewCard(word = noun)
        assertEquals("das Haus", nounCard.frontDeText)
        assertEquals("das Haus", nounCard.backDeText)
    }
}
