package app.deutschlern.ui.dialog.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString

/**
 * Tokenizes transcript segments into clickable words and caches the result, so that
 * scrolling long dialogues does not re-run the regex on the main thread.
 *
 * The cache is bounded by the total number of cached characters (not entries): a few very
 * long segments must not be able to hold megabytes in this process-wide object.
 */
object TranscriptTokenizer {

    private val WORD_REGEX = Regex("""[\p{L}\p{M}\-]+|[^\p{L}\p{M}\-]+""")

    private const val MAX_CACHED_CHARS = 1_000_000

    private val cache = LinkedHashMap<String, AnnotatedString>(128, 0.75f, true)
    private var cachedChars = 0

    const val TAG_WORD = "WORD"

    /**
     * Tokenizes [text] into an [AnnotatedString] where words have a "WORD" string annotation.
     * Hits memory cache first; caches results to eliminate re-tokenization overhead.
     */
    fun tokenize(text: String): AnnotatedString {
        if (text.isEmpty()) {
            return AnnotatedString("")
        }

        synchronized(cache) { cache[text] }?.let { return it }

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

        synchronized(cache) {
            if (cache.put(text, annotated) == null) cachedChars += text.length
            val iterator = cache.entries.iterator()
            while (cachedChars > MAX_CACHED_CHARS && iterator.hasNext()) {
                val eldest = iterator.next()
                if (eldest.key === text) continue
                cachedChars -= eldest.key.length
                iterator.remove()
            }
        }
        return annotated
    }

    /**
     * Pre-warms the cache off the main thread for a collection of segment texts.
     */
    fun prewarm(texts: Iterable<String>) {
        for (text in texts) {
            if (text.isNotEmpty() && synchronized(cache) { !cache.containsKey(text) }) {
                tokenize(text)
            }
        }
    }

    /**
     * Clears cache (useful in tests or when memory trim is received).
     */
    fun clearCache() {
        synchronized(cache) {
            cache.clear()
            cachedChars = 0
        }
    }

    /**
     * Returns current cache item count.
     */
    fun cacheSize(): Int = synchronized(cache) { cache.size }
}
