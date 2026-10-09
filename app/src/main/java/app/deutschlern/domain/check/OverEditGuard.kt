package app.deutschlern.domain.check

object OverEditGuard {

    data class EvaluationResult(
        val isOverEdit: Boolean,
        val reason: String? = null,
        val identityChangedWordCount: Int = 0,
        val totalWordCount: Int = 0,
        val ratio: Float = 0f,
        val invariantViolations: List<String> = emptyList()
    )

    private val URL_REGEX = Regex("""https?://[^\s/$.?#].[^\s]*""", RegexOption.IGNORE_CASE)
    private val EMAIL_REGEX = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")
    private val DATE_TIME_REGEX = Regex("""\b(?:\d{1,2}\.\d{1,2}\.(?:\d{2,4})?|\d{1,2}:\d{2})\b""")
    private val DIGIT_SEQ_REGEX = Regex("""\b\d+([.,]\d+)?%?\b""")

    /**
     * Evaluates whether a corrected paragraph or text exceeds the minimal-edit thresholds.
     */
    fun evaluate(originalRaw: String, correctedRaw: String): EvaluationResult {
        val original = normalizeText(originalRaw)
        val corrected = normalizeText(correctedRaw)

        // 1. Line Break & Paragraph Structure Check
        val origLines = original.lines().map { it.trimEnd() }
        val corrLines = corrected.lines().map { it.trimEnd() }
        val lineMismatch = origLines.size != corrLines.size

        val origParagraphs = original.split("\n\n").filter { it.isNotBlank() }
        val corrParagraphs = corrected.split("\n\n").filter { it.isNotBlank() }
        val paraMismatch = origParagraphs.size != corrParagraphs.size

        // 2. Length Ratio Bounds
        val origLen = original.length
        val corrLen = corrected.length
        var lengthMismatch = false
        var lengthRatio = 1f
        if (origLen > 0) {
            lengthRatio = corrLen.toFloat() / origLen.toFloat()
            val minRatio = if (origLen < 50) 0.70f else 0.85f
            val maxRatio = if (origLen < 50) 1.35f else 1.15f
            if (lengthRatio < minRatio || lengthRatio > maxRatio) {
                lengthMismatch = true
            }
        }

        // 3. Invariants Check
        val invariantViolations = checkInvariants(original, corrected)

        // 4. Token & Word Identity Rewrite Ratio
        val diffHunks = TokenDiffEngine.computeDiff(original, corrected)
        val origTokens = TokenDiffEngine.tokenize(original).filter { it.type == TokenDiffEngine.TokenType.WORD }
        val totalWordCount = origTokens.size

        var identityChangedWords = 0
        for (hunk in diffHunks) {
            val origWords = TokenDiffEngine.tokenize(hunk.originalText).filter { it.type == TokenDiffEngine.TokenType.WORD }
            val corrWords = TokenDiffEngine.tokenize(hunk.correctedText).filter { it.type == TokenDiffEngine.TokenType.WORD }

            // If it's pure punctuation or case change, exclude from identity change count
            if (TokenDiffEngine.isPunctuationOnly(hunk.originalText, hunk.correctedText)) {
                continue
            }
            if (TokenDiffEngine.isCaseOnly(hunk.originalText, hunk.correctedText)) {
                continue
            }

            // Check if words match case-insensitively without punctuation
            var allWordsMatchCaseInsensitive = true
            if (origWords.size == corrWords.size && origWords.isNotEmpty()) {
                for (idx in origWords.indices) {
                    val ow = cleanWord(origWords[idx].text)
                    val cw = cleanWord(corrWords[idx].text)
                    if (!ow.equals(cw, ignoreCase = true)) {
                        allWordsMatchCaseInsensitive = false
                        break
                    }
                }
            } else {
                allWordsMatchCaseInsensitive = false
            }

            if (!allWordsMatchCaseInsensitive) {
                identityChangedWords += maxOf(origWords.size, corrWords.size)
            }
        }

        val ratio = if (totalWordCount > 0) identityChangedWords.toFloat() / totalWordCount.toFloat() else 0f

        val isOverRatio = if (totalWordCount >= 20) {
            ratio > 0.30f
        } else {
            // For short texts, require ratio > 0.50 AND more than 3 changed words
            ratio > 0.50f && identityChangedWords > 3
        }

        val isOverEdit = lineMismatch || paraMismatch || lengthMismatch || invariantViolations.isNotEmpty() || isOverRatio
        val reason = when {
            lineMismatch -> "Line break count mismatch: expected ${origLines.size}, got ${corrLines.size}"
            paraMismatch -> "Paragraph count mismatch: expected ${origParagraphs.size}, got ${corrParagraphs.size}"
            invariantViolations.isNotEmpty() -> "Invariant violation: ${invariantViolations.first()}"
            isOverRatio -> "Word rewrite ratio exceeded: ${(ratio * 100).toInt()}% ($identityChangedWords of $totalWordCount words)"
            lengthMismatch -> "Length deviation: ratio is $lengthRatio"
            else -> null
        }

        return EvaluationResult(
            isOverEdit = isOverEdit,
            reason = reason,
            identityChangedWordCount = identityChangedWords,
            totalWordCount = totalWordCount,
            ratio = ratio,
            invariantViolations = invariantViolations
        )
    }

    fun normalizeText(raw: String): String {
        return raw.replace("\r\n", "\n").replace("\r", "\n")
    }

    private fun cleanWord(w: String): String {
        return w.filter { it.isLetter() || it == '-' }
    }

    private fun checkInvariants(original: String, corrected: String): List<String> {
        val violations = mutableListOf<String>()

        // Check URLs
        URL_REGEX.findAll(original).forEach { match ->
            val url = match.value
            if (!corrected.contains(url)) {
                violations.add("URL modified or removed: '$url'")
            }
        }

        // Check Emails
        EMAIL_REGEX.findAll(original).forEach { match ->
            val email = match.value
            if (!corrected.contains(email)) {
                violations.add("Email address modified or removed: '$email'")
            }
        }

        // Check Dates & Times
        DATE_TIME_REGEX.findAll(original).forEach { match ->
            val date = match.value
            if (!corrected.contains(date)) {
                violations.add("Date or time modified or removed: '$date'")
            }
        }

        // Check Numbers / Digits
        DIGIT_SEQ_REGEX.findAll(original).forEach { match ->
            val num = match.value
            if (!corrected.contains(num)) {
                violations.add("Number or digit modified: '$num'")
            }
        }

        // Check Mid-sentence Uppercase Tokens that appear unchanged elsewhere or are acronyms
        val sentences = original.split(Regex("""[.!?]\s+"""))
        val allWords = original.split(Regex("""\s+""")).map { it.filter { c -> c.isLetter() } }
        for (sentence in sentences) {
            val words = sentence.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
            if (words.size > 1) {
                // Check from index 1 to end (middle of sentence)
                for (idx in 1 until words.size) {
                    val rawWord = words[idx].filter { it.isLetter() }
                    if (rawWord.isNotEmpty() && rawWord[0].isUpperCase()) {
                        val isAcronym = rawWord.length >= 2 && rawWord.all { it.isUpperCase() }
                        val appearsUnchangedElsewhere = allWords.count { it == rawWord } > 1
                        if (isAcronym || appearsUnchangedElsewhere) {
                            val lowerWordRegex = Regex("""\b${Regex.escape(rawWord.lowercase())}\b""")
                            if (!corrected.contains(rawWord) && lowerWordRegex.containsMatchIn(corrected)) {
                                violations.add("Mid-sentence capitalized token lowercased: '$rawWord'")
                            }
                        }
                    }
                }
            }
        }

        return violations
    }
}
