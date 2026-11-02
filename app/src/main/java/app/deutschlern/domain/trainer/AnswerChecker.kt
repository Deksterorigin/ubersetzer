package app.deutschlern.domain.trainer

/**
 * Checks typed or spoken German answers with the tolerance a teacher would apply:
 * case and punctuation never matter, ae/oe/ue/ss count as ä/ö/ü/ß, one typo in a longer word
 * is "almost", and a correct noun without (or with a wrong) article is "almost" too, because
 * the article is part of what has to be remembered.
 */
object AnswerChecker {

    private val ARTICLES = setOf("der", "die", "das")

    fun check(
        input: String,
        expected: String,
        requiredArticle: String? = null,
        nearMisses: List<String> = emptyList()
    ): AnswerVerdict {
        val answerTokens = tokens(input)
        if (answerTokens.isEmpty()) return AnswerVerdict(AnswerResult.WRONG)

        val expectedTokens = tokens(expected).let { list ->
            // The expected value may already carry the article ("die Daumen"); compare it separately
            if (list.size > 1 && list.first() in ARTICLES) list.drop(1) else list
        }

        val typedArticle = answerTokens.first().takeIf { it in ARTICLES && answerTokens.size > 1 }
        val answerBody = if (typedArticle != null) answerTokens.drop(1) else answerTokens

        val bodyVerdict = compare(answerBody, expectedTokens)
        if (bodyVerdict.result == AnswerResult.WRONG) {
            val nearMiss = nearMisses.any { compare(answerBody, tokens(it)).result != AnswerResult.WRONG }
            return if (nearMiss) AnswerVerdict(AnswerResult.ALMOST, AnswerNote.BASE_FORM) else bodyVerdict
        }

        val article = requiredArticle?.lowercase()
        return when {
            article == null -> bodyVerdict
            typedArticle == null -> AnswerVerdict(AnswerResult.ALMOST, AnswerNote.MISSING_ARTICLE)
            typedArticle != article -> AnswerVerdict(AnswerResult.ALMOST, AnswerNote.WRONG_ARTICLE)
            else -> bodyVerdict
        }
    }

    /**
     * Perfekt answers: "hat gemacht" is correct, "gemacht" alone is almost (auxiliary missing).
     */
    fun checkPerfekt(input: String, auxiliaryForm: String, participle: String): AnswerVerdict {
        val full = check(input, "$auxiliaryForm $participle")
        if (full.result != AnswerResult.WRONG) return full
        val participleOnly = check(input, participle)
        return if (participleOnly.result != AnswerResult.WRONG) {
            AnswerVerdict(AnswerResult.ALMOST, AnswerNote.MISSING_AUXILIARY)
        } else {
            full
        }
    }

    private fun compare(answer: List<String>, expected: List<String>): AnswerVerdict {
        if (answer.isEmpty() || expected.isEmpty()) return AnswerVerdict(AnswerResult.WRONG)
        val a = answer.joinToString(" ")
        val e = expected.joinToString(" ")
        return when {
            a == e -> AnswerVerdict(AnswerResult.CORRECT)
            fold(a) == fold(e) -> AnswerVerdict(AnswerResult.CORRECT, AnswerNote.SPELLING_VARIANT)
            e.length >= MIN_LENGTH_FOR_TYPO && levenshtein(fold(a), fold(e)) <= 1 ->
                AnswerVerdict(AnswerResult.ALMOST, AnswerNote.TYPO)
            else -> AnswerVerdict(AnswerResult.WRONG)
        }
    }

    fun tokens(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s-]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

    private fun fold(text: String): String =
        text.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            }
            previous = current
        }
        return previous[b.length]
    }

    private const val MIN_LENGTH_FOR_TYPO = 5
}
