package app.deutschlern.domain.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordCoverageEvaluatorTest {

    @Test
    fun tokenize_normalizesGermanWordsAndUmlauts() {
        val input = "Guten Tag! Können Sie mir bitte helfen? Das kostet 15€, nicht wahr?"
        val tokens = WordCoverageEvaluator.tokenize(input)

        val expected = listOf(
            "guten", "tag", "können", "sie", "mir", "bitte", "helfen",
            "das", "kostet", "nicht", "wahr"
        )
        assertEquals(expected, tokens)
    }

    @Test
    fun evaluate_identicalTexts_returnsHundredPercentCoverageAndZeroWer() {
        val text = "Ich möchte bitte einen Kaffee mit Milch bestellen."
        val result = WordCoverageEvaluator.evaluate(text, text)

        assertEquals(8, result.referenceWordCount)
        assertEquals(8, result.hypothesisWordCount)
        assertEquals(8, result.correctWords)
        assertEquals(0, result.substitutions)
        assertEquals(0, result.deletions)
        assertEquals(0, result.insertions)
        assertEquals(0f, result.wordErrorRate, 0.001f)
        assertEquals(100f, result.coveragePercent, 0.001f)
        assertEquals(100f, result.accuracyPercent, 0.001f)
    }

    @Test
    fun evaluate_withSubstitutionsAndDeletions_computesCorrectMetrics() {
        val reference = "Ich möchte einen heißen Kaffee" // 5 words
        val hypothesis = "Ich will einen Kaffee"         // 4 words: "möchte"->"will" (sub), "heißen" deleted

        val result = WordCoverageEvaluator.evaluate(reference, hypothesis)

        assertEquals(5, result.referenceWordCount)
        assertEquals(4, result.hypothesisWordCount)
        assertEquals(3, result.correctWords) // "ich", "einen", "kaffee"
        assertEquals(1, result.substitutions) // möchte -> will
        assertEquals(1, result.deletions)     // heißen deleted
        assertEquals(0, result.insertions)

        // 2 errors / 5 words = 0.40 WER, 60% coverage, 60% accuracy
        assertEquals(0.40f, result.wordErrorRate, 0.001f)
        assertEquals(60.0f, result.coveragePercent, 0.001f)
        assertEquals(60.0f, result.accuracyPercent, 0.001f)
    }

    @Test
    fun evaluate_emptyHypothesis_returnsZeroCoverageAndFullDeletions() {
        val reference = "Hallo wie geht es dir"
        val result = WordCoverageEvaluator.evaluate(reference, "")

        assertEquals(5, result.referenceWordCount)
        assertEquals(0, result.hypothesisWordCount)
        assertEquals(0, result.correctWords)
        assertEquals(5, result.deletions)
        assertEquals(1.0f, result.wordErrorRate, 0.001f)
        assertEquals(0.0f, result.coveragePercent, 0.001f)
    }
}
