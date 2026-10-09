package app.deutschlern.util

import app.deutschlern.domain.model.WordEntry
import java.text.Normalizer

object WordSearchHelper {

    /**
     * Folds German characters:
     * ä/Ä -> a, ö/Ö -> o, ü/Ü -> u, ß -> ss.
     */
    fun foldUmlauts(input: String): String {
        return input.lowercase()
            .replace("ä", "a")
            .replace("ö", "o")
            .replace("ü", "u")
            .replace("ß", "ss")
    }

    /**
     * Normalizes a string for search matching:
     * - lowercases
     * - folds umlauts (ä->a, ö->o, ü->u, ß->ss)
     * - strips any combining diacritical marks
     * - collapses whitespace
     */
    fun normalizeForSearch(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val folded = foldUmlauts(input.trim())
        val decomposed = Normalizer.normalize(folded, Normalizer.Form.NFD)
        val stripped = decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return stripped
            .replace("ae", "a")
            .replace("oe", "o")
            .replace("ue", "u")
    }

    /**
     * Checks if target matches query (case-insensitive, umlaut-tolerant).
     */
    fun matches(target: String?, query: String?): Boolean {
        if (query.isNullOrBlank()) return true
        if (target.isNullOrBlank()) return false

        val targetNorm = normalizeForSearch(target)
        val queryNorm = normalizeForSearch(query)

        if (targetNorm.contains(queryNorm)) return true

        // Also check digraph substitutions (ae -> a, oe -> o, ue -> u)
        val targetDigraphs = targetNorm
            .replace("ae", "a")
            .replace("oe", "o")
            .replace("ue", "u")
        val queryDigraphs = queryNorm
            .replace("ae", "a")
            .replace("oe", "o")
            .replace("ue", "u")

        return targetDigraphs.contains(queryDigraphs) ||
                targetNorm.contains(queryDigraphs) ||
                targetDigraphs.contains(queryNorm)
    }

    /**
     * Checks if a WordEntry matches query:
     * matches lemma OR any meaning translation.
     */
    fun matchesWord(word: WordEntry, query: String?): Boolean {
        if (query.isNullOrBlank()) return true
        if (matches(word.lemma, query)) return true
        if (word.article != null && matches("${word.article} ${word.lemma}", query)) return true

        return word.meanings.any { meaning ->
            matches(meaning.translation, query) ||
                    matches(meaning.exampleDe, query) ||
                    matches(meaning.exampleRu, query)
        }
    }
}
