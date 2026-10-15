package app.deutschlern.domain.sanitizer

object PosNormalizer {

    const val POS_NOUN = "noun"
    const val POS_VERB = "verb"
    const val POS_ADJECTIVE = "adjective"
    const val POS_ADVERB = "adverb"
    const val POS_PREPOSITION = "preposition"
    const val POS_CONJUNCTION = "conjunction"
    const val POS_PRONOUN = "pronoun"
    const val POS_PARTICLE = "particle"
    const val POS_PHRASE = "phrase"
    const val POS_OTHER = "other"

    /**
     * Explicit non-noun allowlist.
     * Articles MUST be nulled for POS values in this list.
     */
    val NON_NOUN_ALLOWLIST = setOf(
        POS_VERB,
        POS_ADJECTIVE,
        POS_ADVERB,
        POS_PREPOSITION,
        POS_CONJUNCTION,
        POS_PRONOUN,
        POS_PARTICLE,
        POS_PHRASE
    )

    /**
     * Normalizes raw POS strings (e.g. from LLMs, German terminology, or legacy DB entries)
     * into canonical lowercase POS codes.
     */
    fun normalize(rawPos: String?): String {
        if (rawPos.isNullOrBlank()) return POS_OTHER
        val trimmed = rawPos.trim().lowercase()

        return when {
            // Noun variants
            trimmed == "noun" || trimmed == "n" || trimmed == "nomen" || trimmed == "substantiv" -> POS_NOUN

            // Verb variants
            trimmed == "verb" || trimmed == "v" || trimmed == "verbum" -> POS_VERB

            // Adjective variants
            trimmed == "adjective" || trimmed == "adj" || trimmed == "adjektiv" -> POS_ADJECTIVE

            // Adverb variants
            trimmed == "adverb" || trimmed == "adv" -> POS_ADVERB

            // Preposition variants
            trimmed == "preposition" || trimmed == "prep" || trimmed == "präposition" || trimmed == "praeposition" -> POS_PREPOSITION

            // Conjunction variants
            trimmed == "conjunction" || trimmed == "conj" || trimmed == "konjunktion" -> POS_CONJUNCTION

            // Pronoun variants
            trimmed == "pronoun" || trimmed == "pron" || trimmed == "pronomen" -> POS_PRONOUN

            // Particle variants
            trimmed == "particle" || trimmed == "part" || trimmed == "partikel" -> POS_PARTICLE

            // Phrase / Expression variants
            trimmed == "phrase" || trimmed == "adverbial_phrase" || trimmed == "idiom" || trimmed == "redewendung" || trimmed == "ausdruck" -> POS_PHRASE

            // Other / unknown
            trimmed == "other" -> POS_OTHER
            else -> trimmed // Preserves unrecognized POS for safety and audit reporting
        }
    }

    /**
     * Returns true if the normalized POS definitely belongs to a non-noun part of speech.
     */
    fun isDefiniteNonNoun(rawPos: String?): Boolean {
        val normalized = normalize(rawPos)
        return NON_NOUN_ALLOWLIST.contains(normalized)
    }

    /**
     * Returns true if the normalized POS is a noun.
     */
    fun isNoun(rawPos: String?): Boolean {
        return normalize(rawPos) == POS_NOUN
    }

    /**
     * Returns all known legacy and variant strings that normalize to the same POS.
     * Used for database queries to match existing words regardless of whether they
     * were stored as "n", "nomen", "noun", etc.
     */
    fun getEquivalentPosList(rawOrNormalizedPos: String?): List<String> {
        val norm = normalize(rawOrNormalizedPos)
        return when (norm) {
            POS_NOUN -> listOf("noun", "n", "nomen", "substantiv")
            POS_VERB -> listOf("verb", "v", "verbum")
            POS_ADJECTIVE -> listOf("adjective", "adj", "adjektiv")
            POS_ADVERB -> listOf("adverb", "adv")
            POS_PREPOSITION -> listOf("preposition", "prep", "präposition", "praeposition")
            POS_CONJUNCTION -> listOf("conjunction", "conj", "konjunktion")
            POS_PRONOUN -> listOf("pronoun", "pron", "pronomen")
            POS_PARTICLE -> listOf("particle", "part", "partikel")
            POS_PHRASE -> listOf("phrase", "adverbial_phrase", "idiom", "redewendung", "ausdruck")
            POS_OTHER -> {
                val raw = rawOrNormalizedPos?.trim()?.lowercase().orEmpty()
                if (raw.isBlank() || raw == "other") listOf("other") else listOf("other", raw)
            }
            else -> {
                val raw = rawOrNormalizedPos?.trim()?.lowercase().orEmpty()
                if (raw.isNotBlank() && raw != norm) listOf(norm, raw) else listOf(norm)
            }
        }
    }
}
