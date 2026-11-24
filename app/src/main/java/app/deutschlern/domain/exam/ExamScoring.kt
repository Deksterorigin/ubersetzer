package app.deutschlern.domain.exam

import java.text.Normalizer

data class QuestionResult(
    val question: Question,
    val given: String?,
    /** Null when the source has no answer key, so the question cannot be graded. */
    val correct: Boolean?
)

data class ExamResult(val results: List<QuestionResult>) {
    val gradable: Int get() = results.count { it.correct != null }
    val correct: Int get() = results.count { it.correct == true }
    val percent: Int get() = if (gradable == 0) 0 else correct * 100 / gradable
}

object ExamScoring {

    fun score(variant: ExamVariant, answers: Map<Int, String>): ExamResult = ExamResult(
        variant.questions.map { q ->
            val given = answers[q.number]
            QuestionResult(q, given, q.answer?.let { it == given })
        }
    )

    /** Options a question is answered from: its own, or the shared ones of its segment. */
    fun optionsFor(segment: ExamSegment, question: Question): List<Option> =
        question.options.ifEmpty { segment.sharedOptions }

    fun wordCount(text: String): Int = text.split(Regex("\\s+")).count { word -> word.any { it.isLetterOrDigit() } }

    /**
     * A phone-note entry counts when it carries the expected content: digits must match exactly,
     * words loosely (case, punctuation, umlaut spelling and word order are ignored).
     */
    fun noteFieldMatches(expected: String, given: String): Boolean {
        if (given.isBlank()) return false
        val expectedDigits = expected.filter { it.isDigit() }
        if (expectedDigits.length >= 3) return given.filter { it.isDigit() } == expectedDigits
        val expectedWords = normalizeWords(expected)
        if (expectedWords.isEmpty()) return true
        val givenWords = normalizeWords(given).toSet()
        val hits = expectedWords.count { it in givenWords }
        return hits * 3 >= expectedWords.size * 2
    }

    private val STOP_WORDS = setOf("der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "und", "oder", "zu", "mit", "für", "fuer", "von", "an", "am", "im", "in", "ob", "wir", "sie", "uns")

    private fun normalizeWords(text: String): List<String> =
        Normalizer.normalize(
            text.lowercase()
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss"),
            Normalizer.Form.NFKD
        )
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 1 && it !in STOP_WORDS }
}
