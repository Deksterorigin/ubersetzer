package app.deutschlern.domain.sanitizer

import java.util.Locale

data class SanitizedWord(
    val lemma: String,
    val article: String?,
    val pos: String,
    val hasSuffixConflict: Boolean = false,
    val expectedSuffixArticle: String? = null
)

object ArticleSanitizer {

    private val VALID_ARTICLES = setOf("der", "die", "das")

    // Exceptions ending in "chen" that are masculine, not diminutive neuter
    private val MASCULINE_CHEN_EXCEPTIONS = setOf(
        "kuchen", "drachen", "rachen", "rechen", "knochen"
    )

    // Strong feminine suffixes
    private val FEMININE_SUFFIXES = listOf("ung", "heit", "keit", "schaft", "tion", "ität")

    // Strong neuter suffixes
    private val NEUTER_SUFFIXES = listOf("chen", "lein")

    // Strong masculine suffixes
    private val MASCULINE_SUFFIXES = listOf("ismus")

    /**
     * Sanitizes a word's article, lemma capitalization, and POS.
     * Pure function applied at all boundaries (parsing, caching, display, persistence, export, review).
     */
    fun sanitize(
        rawLemma: String,
        rawPos: String?,
        rawArticle: String?
    ): SanitizedWord {
        val trimmedLemma = rawLemma.trim()
        val normalizedPos = PosNormalizer.normalize(rawPos)
        val normalizedArticle = rawArticle?.trim()?.lowercase(Locale.ROOT)?.takeIf { it in VALID_ARTICLES }

        // 1. If POS is in the explicit non-noun allowlist, article MUST be null
        if (PosNormalizer.isDefiniteNonNoun(normalizedPos)) {
            return SanitizedWord(
                lemma = trimmedLemma,
                article = null,
                pos = normalizedPos
            )
        }

        // 2. If POS is unknown/other (not in non-noun allowlist, but not explicitly noun)
        if (!PosNormalizer.isNoun(normalizedPos)) {
            // If it claims an article, but is not verified as a noun, null the article unless lemma is clearly capitalized noun
            val isCapitalized = trimmedLemma.isNotEmpty() && trimmedLemma.first().isUpperCase()
            val finalArticle = if (isCapitalized && normalizedArticle != null) normalizedArticle else null
            return SanitizedWord(
                lemma = trimmedLemma,
                article = finalArticle,
                pos = normalizedPos
            )
        }

        // 3. For confirmed nouns:
        // Lowercase noun lemma with a valid article (e.g. from voice input "apfel" or "haus"):
        // Capitalize the lemma instead of dropping the article
        val capitalizedLemma = if (trimmedLemma.isNotEmpty() && trimmedLemma.first().isLowerCase()) {
            trimmedLemma.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.GERMANY) else it.toString() }
        } else {
            trimmedLemma
        }

        // Suffix rule validation (for verification retry check, never overrides model)
        val (hasConflict, expectedArticle) = checkSuffixConflict(capitalizedLemma, normalizedArticle)

        return SanitizedWord(
            lemma = capitalizedLemma,
            article = normalizedArticle,
            pos = PosNormalizer.POS_NOUN,
            hasSuffixConflict = hasConflict,
            expectedSuffixArticle = expectedArticle
        )
    }

    /**
     * Checks if a noun's article contradicts strong German suffix rules.
     */
    fun checkSuffixConflict(lemma: String, article: String?): Pair<Boolean, String?> {
        if (article == null || lemma.isBlank()) return Pair(false, null)
        val lowerLemma = lemma.lowercase(Locale.ROOT)

        // Check masculine "chen" exceptions first (e.g. der Kuchen)
        if (MASCULINE_CHEN_EXCEPTIONS.any { lowerLemma.endsWith(it) }) {
            return if (article != "der") Pair(true, "der") else Pair(false, null)
        }

        // Feminine suffixes
        for (suffix in FEMININE_SUFFIXES) {
            if (lowerLemma.endsWith(suffix)) {
                return if (article != "die") Pair(true, "die") else Pair(false, null)
            }
        }

        // Neuter suffixes
        for (suffix in NEUTER_SUFFIXES) {
            if (lowerLemma.endsWith(suffix)) {
                return if (article != "das") Pair(true, "das") else Pair(false, null)
            }
        }

        // Masculine suffixes
        for (suffix in MASCULINE_SUFFIXES) {
            if (lowerLemma.endsWith(suffix)) {
                return if (article != "der") Pair(true, "der") else Pair(false, null)
            }
        }

        return Pair(false, null)
    }

    /**
     * Checks if a lemma matches a strong German suffix rule.
     * Returns the expected article ("der", "die", or "das"), or null if no strong rule applies or if it's an exception.
     */
    fun checkSuffixRule(lemma: String): String? {
        val lowerLemma = lemma.lowercase(Locale.ROOT)
        if (MASCULINE_CHEN_EXCEPTIONS.any { lowerLemma.endsWith(it) }) return null
        for (suffix in FEMININE_SUFFIXES) {
            if (lowerLemma.endsWith(suffix)) return "die"
        }
        for (suffix in NEUTER_SUFFIXES) {
            if (lowerLemma.endsWith(suffix)) return "das"
        }
        for (suffix in MASCULINE_SUFFIXES) {
            if (lowerLemma.endsWith(suffix)) return "der"
        }
        return null
    }

    /**
     * Sanitizes a German equivalent string in RU->DE translations.
     * An article prefix (e.g. "das Schloss", "die Burg") is allowed only for a single noun.
     * For verbs, adjectives, or phrases, any leading article is stripped.
     */
    fun sanitizeGermanEquivalent(equivalentText: String, expectedPos: String?): Pair<String?, String> {
        val trimmed = equivalentText.trim()
        val normalizedPos = PosNormalizer.normalize(expectedPos)

        val match = Regex("^(der|die|das)\\s+(.+)$", RegexOption.IGNORE_CASE).find(trimmed)
        if (match != null) {
            val article = match.groupValues[1].lowercase(Locale.ROOT)
            val baseLemma = match.groupValues[2].trim()

            // If POS is a definite non-noun (verb, adjective, etc.), strip the article
            if (PosNormalizer.isDefiniteNonNoun(normalizedPos)) {
                return Pair(null, baseLemma)
            }

            // Article prefix is allowed only if baseLemma is capitalized (noun)
            val isNoun = baseLemma.isNotEmpty() && baseLemma.first().isUpperCase() && !baseLemma.contains(" ")
            return if (isNoun) {
                Pair(article, baseLemma)
            } else {
                Pair(null, baseLemma)
            }
        }

        return Pair(null, trimmed)
    }

    fun sanitizeGermanEquivalent(equivalentText: String): String {
        val (article, lemma) = sanitizeGermanEquivalent(equivalentText, null)
        return if (article != null) "$article $lemma" else lemma
    }
}
