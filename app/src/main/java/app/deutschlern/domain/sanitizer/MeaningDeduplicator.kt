package app.deutschlern.domain.sanitizer

import app.deutschlern.domain.model.Meaning
import java.util.Locale

object MeaningDeduplicator {

    private val PARENTHETICAL_REGEX = Regex("\\([^)]*\\)")
    private val PUNCTUATION_REGEX = Regex("[\\p{Punct}&&[^\\-]]")

    private val FREQUENCY_STATEMENTS = setOf(
        "most common meaning",
        "основное значение",
        "самое частое",
        "самое частое значение",
        "главное значение",
        "common meaning"
    )

    /**
     * Cleans a note string:
     * - Strips wrapping parentheses and trims whitespace
     * - Removes frequency statements
     * - Caps note length at 8 words
     */
    fun cleanNote(rawNote: String?): String? {
        if (rawNote.isNullOrBlank()) return null
        var cleaned = rawNote.trim()
        while (cleaned.startsWith("(") && cleaned.endsWith(")")) {
            cleaned = cleaned.substring(1, cleaned.length - 1).trim()
        }
        if (cleaned.lowercase(Locale.ROOT) in FREQUENCY_STATEMENTS) return null

        val words = cleaned.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.size > 8) {
            cleaned = words.take(8).joinToString(" ") + "..."
        }
        return cleaned.ifBlank { null }
    }

    /**
     * Normalizes a translation string for equality comparison:
     * - Strips parenthetical qualifiers e.g. "упоминать (в речи)" -> "упоминать"
     * - Strips trailing/leading punctuation
     * - Lowercases and trims
     */
    fun normalizeTranslation(translation: String): String {
        return PARENTHETICAL_REGEX.replace(translation, " ")
            .replace(PUNCTUATION_REGEX, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase(Locale.ROOT)
    }

    /**
     * Deduplicates meanings whose normalized translations are equal or differ only by
     * a parenthetical qualifier. Retains the first occurrence.
     * If a duplicate had bestMatch = true, preserves bestMatch on the retained item.
     * Cleans notes on all items.
     */
    fun deduplicate(meanings: List<Meaning>): List<Meaning> {
        if (meanings.isEmpty()) return emptyList()

        val result = mutableListOf<Meaning>()
        val seenNormalized = mutableSetOf<String>()

        for (meaning in meanings) {
            val normalized = normalizeTranslation(meaning.translation)
            val cleanedNote = cleanNote(meaning.note)
            val cleanedMeaning = meaning.copy(note = cleanedNote)

            if (normalized.isBlank()) {
                result.add(cleanedMeaning)
                continue
            }

            if (!seenNormalized.contains(normalized)) {
                seenNormalized.add(normalized)
                result.add(cleanedMeaning)
            } else {
                // If the duplicate had bestMatch = true, mark the existing retained item
                if (meaning.bestMatch) {
                    val existingIndex = result.indexOfFirst {
                        normalizeTranslation(it.translation) == normalized
                    }
                    if (existingIndex != -1) {
                        result[existingIndex] = result[existingIndex].copy(bestMatch = true)
                    }
                }
            }
        }

        return result
    }

    fun deduplicateMeanings(meanings: List<Meaning>): List<Meaning> = deduplicate(meanings)

    /**
     * Remaps an old selectedMeaningIndex when meanings are updated / deduplicated.
     * Matches by normalized translation text. If the selected meaning is no longer
     * present or index is out of bounds, returns null instead of pointing to a wrong meaning.
     */
    fun remapSelectedMeaningIndex(
        oldMeanings: List<Meaning>,
        newMeanings: List<Meaning>,
        oldIndex: Int?
    ): Int? {
        if (oldIndex == null || oldIndex !in oldMeanings.indices) return null
        val oldSelectedText = normalizeTranslation(oldMeanings[oldIndex].translation)
        if (oldSelectedText.isBlank()) return null

        val newIndex = newMeanings.indexOfFirst {
            normalizeTranslation(it.translation) == oldSelectedText
        }
        return if (newIndex != -1) newIndex else null
    }
}

