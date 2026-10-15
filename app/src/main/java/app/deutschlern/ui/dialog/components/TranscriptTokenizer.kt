package app.deutschlern.ui.dialog.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Thread-safe cached tokenizer for dialogue transcript segments.
 * Pre-compiles the regex pattern and maintains a memory-bounded LRU cache
 * to avoid expensive tokenization on the Android main/UI thread during LazyColumn scrolling.
 */
object TranscriptTokenizer {

    private val WORD_REGEX = Regex("""[\p{L}\p{M}\-]+|[^\p{L}\p{M}\-]+""")

    private const val MAX_CACHE_SIZE = 5000

    private val cache: MutableMap<String, AnnotatedString> = Collections.synchronizedMap(
        object : LinkedHashMap<String, AnnotatedString>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AnnotatedString>?): Boolean {
                return size > MAX_CACHE_SIZE
            }
        }
    )

    const val TAG_WORD = "WORD"

    /**
     * Tokenizes [text] into an [AnnotatedString] where words have a "WORD" string annotation.
     * Hits memory cache first; caches results to eliminate re-tokenization overhead.
     */
    fun tokenize(text: String): AnnotatedString {
        if (text.isEmpty()) {
            return AnnotatedString("")
        }

        cache[text]?.let { return it }

        val matches = WORD_REGEX.findAll(text).toList()
        val annotated = buildAnnotatedString {
            matches.forEach { matchResult ->
                val token = matchResult.value
                val isWord = token.any { it.isLetter() }

                if (isWord) {
                    val startIndex = length
                    append(token)
                    val endIndex = length
                    addStringAnnotation(
                        tag = TAG_WORD,
                        annotation = token,
                        start = startIndex,
                        end = endIndex
                    )
                } else {
                    append(token)
                }
            }
        }

        cache[text] = annotated
        return annotated
    }

    /**
     * Pre-warms the cache off the main thread for a collection of segment texts.
     */
    fun prewarm(texts: Iterable<String>) {
        for (text in texts) {
            if (text.isNotEmpty() && !cache.containsKey(text)) {
                tokenize(text)
            }
        }
    }

    /**
     * Clears cache (useful in tests or when memory trim is received).
     */
    fun clearCache() {
        cache.clear()
    }

    /**
     * Returns current cache item count.
     */
    fun cacheSize(): Int = cache.size
}
