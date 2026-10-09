package app.deutschlern.domain.ocr

object OcrPostProcessor {

    private val HYPHEN_CHARS = charArrayOf('-', '\u00AD', '–', '—')

    /**
     * Processes raw OCR text from German documents:
     * 1. Normalizes line endings (\r\n -> \n)
     * 2. Reconstructs paragraphs and dehyphenates line breaks
     * 3. Normalizes whitespace and punctuation
     */
    fun process(rawText: String): String {
        if (rawText.isBlank()) return ""

        val normalized = rawText.replace("\r\n", "\n").replace('\r', '\n')
        
        // Split by paragraph breaks: 2 or more newlines, possibly with whitespace in between
        val rawParagraphs = normalized.split(Regex("\\n\\s*\\n+"))

        val processedParagraphs = rawParagraphs.mapNotNull { rawParagraph ->
            val processed = processSingleParagraph(rawParagraph)
            if (processed.isBlank()) null else processed
        }

        return processedParagraphs.joinToString("\n\n")
    }

    private fun processSingleParagraph(paragraphText: String): String {
        val lines = paragraphText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ""
        if (lines.size == 1) return cleanupSpacing(lines[0])

        val result = StringBuilder()

        for (i in lines.indices) {
            val currentLine = lines[i]
            if (i == 0) {
                result.append(currentLine)
                continue
            }

            val prevLineSoFar = result.toString()
            val nextLine = currentLine

            val hyphenIndex = findTrailingHyphen(prevLineSoFar)

            if (hyphenIndex != -1) {
                // Check if this hyphen was a word split
                val prefix = prevLineSoFar.substring(0, hyphenIndex)
                val lastToken = prefix.split(Regex("\\s+")).lastOrNull() ?: ""
                val firstNextToken = nextLine.split(Regex("\\s+")).firstOrNull() ?: ""

                if (isWordBreakHyphen(lastToken, firstNextToken)) {
                    // Check if genuine compound (e.g. Baden-Württemberg, U-Bahn) vs syllable split (Spra-che)
                    if (isCapitalizedCompound(lastToken, firstNextToken)) {
                        // Keep hyphen, join without space: Baden- + Württemberg -> Baden-Württemberg
                        // prefix already contains letters before hyphen. We keep the hyphen character '-'.
                        val keptHyphen = "-"
                        val cleanPrefix = prefix.trimEnd()
                        val combined = cleanPrefix + keptHyphen + nextLine.trimStart()
                        result.setLength(0)
                        result.append(combined)
                    } else {
                        // Syllable break: Spra- + che -> Sprache (remove hyphen and newline, no space)
                        val cleanPrefix = prefix.trimEnd()
                        val combined = cleanPrefix + nextLine.trimStart()
                        result.setLength(0)
                        result.append(combined)
                    }
                } else {
                    // Standalone punctuation hyphen or dash: keep space
                    result.append(" ").append(nextLine)
                }
            } else {
                // Normal line wrap inside paragraph: join with single space
                result.append(" ").append(nextLine)
            }
        }

        return cleanupSpacing(result.toString())
    }

    private fun findTrailingHyphen(text: String): Int {
        val trimmed = text.trimEnd()
        if (trimmed.isEmpty()) return -1
        val lastChar = trimmed.last()
        return if (HYPHEN_CHARS.contains(lastChar)) {
            trimmed.length - 1
        } else {
            -1
        }
    }

    /**
     * Determines whether a hyphen at line end is a split word or punctuation.
     * True if prefix token is a word ending in hyphen and next token starts with a letter.
     */
    private fun isWordBreakHyphen(prefixToken: String, nextToken: String): Boolean {
        if (prefixToken.isEmpty() || nextToken.isEmpty()) return false

        // Prefix should end with a letter (before the hyphen)
        val cleanPrefix = prefixToken.trimEnd { HYPHEN_CHARS.contains(it) }
        if (cleanPrefix.isEmpty() || !cleanPrefix.last().isLetterOrDigit()) return false

        // Next token should start with a letter
        val firstCharNext = nextToken.first()
        return firstCharNext.isLetter()
    }

    /**
     * Checks if it's a compound word that should retain its hyphen:
     * e.g., Baden-Württemberg, Karl-Marx-Straße, U-Bahn, S-Bahn, B2-Niveau.
     */
    private fun isCapitalizedCompound(prefixToken: String, nextToken: String): Boolean {
        val cleanPrefix = prefixToken.trimEnd { HYPHEN_CHARS.contains(it) }
        val firstPrefix = cleanPrefix.firstOrNull() ?: return false
        val firstNext = nextToken.firstOrNull() ?: return false

        // If next token starts with uppercase:
        // Case 1: Prefix is single uppercase letter or digit+letter (U-Bahn, S-Bahn, E-Mail, B2-Niveau)
        if (firstNext.isUpperCase()) {
            if (firstPrefix.isUpperCase() || cleanPrefix.any { it.isDigit() }) {
                return true
            }
        }

        return false
    }

    private fun cleanupSpacing(text: String): String {
        return text
            .replace(Regex("[ \\t]+"), " ")
            .trim()
    }
}
