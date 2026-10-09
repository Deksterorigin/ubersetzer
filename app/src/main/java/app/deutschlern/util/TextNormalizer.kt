package app.deutschlern.util

import java.text.Normalizer

object TextNormalizer {

    /**
     * Normalizes text for caching and comparisons:
     * 1. Trims leading and trailing whitespace.
     * 2. Collapses consecutive whitespace sequences into a single space.
     * 3. Applies Unicode Normalization Form C (NFC).
     *
     * Case is preserved because capitalization in German distinguishes parts of speech
     * (e.g. "Schloss" noun vs "schloss" verb).
     */
    fun normalize(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val trimmed = input.trim()
        val collapsed = trimmed.replace(Regex("\\s+"), " ")
        return Normalizer.normalize(collapsed, Normalizer.Form.NFC)
    }

    /**
     * Normalizes a lemma (infinitives/singular forms) with trim, whitespace collapse, and NFC normalization.
     */
    fun normalizeLemma(lemma: String?): String {
        if (lemma.isNullOrBlank()) return ""
        val trimmed = lemma.trim()
        val collapsed = trimmed.replace(Regex("\\s+"), " ")
        return Normalizer.normalize(collapsed, Normalizer.Form.NFC)
    }

    /**
     * Extracts German article (der, die, das) and base lemma from a phrase.
     * E.g. "das Schloss" -> Pair("das", "Schloss")
     *      "die Burg" -> Pair("die", "Burg")
     *      "Schloss" -> Pair(null, "Schloss")
     *      "aufstehen" -> Pair(null, "aufstehen")
     */
    fun extractArticleAndLemma(input: String?): Pair<String?, String> {
        if (input.isNullOrBlank()) return Pair(null, "")
        val normalized = normalizeLemma(input)
        val match = Regex("^(der|die|das)\\s+(.+)$", RegexOption.IGNORE_CASE).find(normalized)
        return if (match != null) {
            val article = match.groupValues[1].lowercase()
            val lemma = match.groupValues[2].trim()
            Pair(article, lemma)
        } else {
            Pair(null, normalized)
        }
    }
}
