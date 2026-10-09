package app.deutschlern.util

import app.deutschlern.domain.model.TranslationMode

object ModeDetector {
    private val SENTENCE_DELIMITERS = Regex("[.!?;\\n\\r]")

    /**
     * Detects translation mode:
     * - [TranslationMode.WORD]: 1–3 tokens and no sentence punctuation (., !, ?, ;, newline).
     * - [TranslationMode.TEXT]: 4+ tokens OR contains sentence punctuation or newlines.
     */
    fun detectMode(input: String?): TranslationMode {
        if (input.isNullOrBlank()) {
            return TranslationMode.WORD
        }

        // Check sentence delimiters on untrimmed input so trailing/leading newlines or punctuation are not stripped
        if (SENTENCE_DELIMITERS.containsMatchIn(input)) {
            return TranslationMode.TEXT
        }

        val trimmed = input.trim()

        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (tokens.size in 1..3) {
            TranslationMode.WORD
        } else {
            TranslationMode.TEXT
        }
    }
}
