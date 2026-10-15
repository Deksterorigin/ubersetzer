package app.deutschlern.domain.stt

import java.util.Locale
import kotlin.math.max

/**
 * Word coverage and Word Error Rate (WER) evaluator for German speech recognition.
 * Compares reference speech scripts against transcribed hypothesis text.
 */
object WordCoverageEvaluator {

    data class CoverageResult(
        val referenceWordCount: Int,
        val hypothesisWordCount: Int,
        val correctWords: Int,
        val substitutions: Int,
        val deletions: Int,
        val insertions: Int,
        val wordErrorRate: Float,
        val coveragePercent: Float,
        val accuracyPercent: Float
    )

    private val WORD_REGEX = Regex("""[\p{L}\p{M}]+""")

    /**
     * Extracts normalized word tokens from text.
     * Preserves German umlauts (ä, ö, ü) and eszett (ß) while lowercasing and stripping punctuation.
     */
    fun tokenize(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return WORD_REGEX.findAll(text)
            .map { it.value.lowercase(Locale.GERMAN) }
            .filter { it.isNotBlank() }
            .toList()
    }

    /**
     * Evaluates word coverage and error metrics between reference and hypothesis text.
     */
    fun evaluate(referenceText: String, hypothesisText: String): CoverageResult {
        val refWords = tokenize(referenceText)
        val hypWords = tokenize(hypothesisText)

        val n = refWords.size
        val m = hypWords.size

        if (n == 0) {
            val wer = if (m == 0) 0f else 1f
            return CoverageResult(
                referenceWordCount = 0,
                hypothesisWordCount = m,
                correctWords = 0,
                substitutions = 0,
                deletions = 0,
                insertions = m,
                wordErrorRate = wer,
                coveragePercent = if (m == 0) 100f else 0f,
                accuracyPercent = if (m == 0) 100f else 0f
            )
        }

        // DP table for Levenshtein word alignment
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j

        for (i in 1..n) {
            for (j in 1..m) {
                if (refWords[i - 1] == hypWords[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1]
                } else {
                    val sub = dp[i - 1][j - 1] + 1
                    val del = dp[i - 1][j] + 1
                    val ins = dp[i][j - 1] + 1
                    dp[i][j] = minOf(sub, del, ins)
                }
            }
        }

        // Backtrack to count substitutions, deletions, insertions, correct words
        var i = n
        var j = m
        var substitutions = 0
        var deletions = 0
        var insertions = 0
        var correct = 0

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && refWords[i - 1] == hypWords[j - 1]) {
                correct++
                i--
                j--
            } else if (i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1) {
                substitutions++
                i--
                j--
            } else if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) {
                deletions++
                i--
            } else {
                insertions++
                j--
            }
        }

        val totalErrors = substitutions + deletions + insertions
        val wer = totalErrors.toFloat() / n.toFloat()
        val coverage = (correct.toFloat() / n.toFloat()) * 100f
        val accuracy = max(0f, (n - totalErrors).toFloat() / n.toFloat()) * 100f

        return CoverageResult(
            referenceWordCount = n,
            hypothesisWordCount = m,
            correctWords = correct,
            substitutions = substitutions,
            deletions = deletions,
            insertions = insertions,
            wordErrorRate = wer,
            coveragePercent = coverage,
            accuracyPercent = accuracy
        )
    }
}
