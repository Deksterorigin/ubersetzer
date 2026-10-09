package app.deutschlern.domain.check

import app.deutschlern.data.remote.dto.check.CommentItemDto
import app.deutschlern.data.remote.dto.check.EditItemDto
import app.deutschlern.domain.model.check.CheckCategory
import app.deutschlern.domain.model.check.CheckComment
import app.deutschlern.domain.model.check.CheckEditHunk
import app.deutschlern.domain.model.check.CommentIssue
import java.util.UUID

object TokenDiffEngine {

    enum class TokenType {
        WORD,
        PUNCTUATION,
        WHITESPACE
    }

    data class Token(
        val text: String,
        val type: TokenType,
        val startOffset: Int,
        val endOffset: Int
    )

    private val PUNCTUATION_CHARS = setOf(
        '.', ',', '!', '?', ';', ':', '-', '—', '–', '(', ')', '[', ']',
        '{', '}', '"', '\'', '«', '»', '„', '“', '/', '\\', '…'
    )

    fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        val len = input.length

        while (i < len) {
            val c = input[i]
            when {
                c.isWhitespace() -> {
                    val start = i
                    while (i < len && input[i].isWhitespace()) {
                        i++
                    }
                    tokens.add(Token(input.substring(start, i), TokenType.WHITESPACE, start, i))
                }
                c in PUNCTUATION_CHARS -> {
                    val start = i
                    i++
                    tokens.add(Token(input.substring(start, i), TokenType.PUNCTUATION, start, i))
                }
                else -> {
                    val start = i
                    while (i < len && !input[i].isWhitespace() && input[i] !in PUNCTUATION_CHARS) {
                        i++
                    }
                    tokens.add(Token(input.substring(start, i), TokenType.WORD, start, i))
                }
            }
        }
        return tokens
    }

    private sealed class DiffOp {
        data class Equal(val origToken: Token, val corrToken: Token) : DiffOp()
        data class Delete(val origToken: Token) : DiffOp()
        data class Insert(val corrToken: Token) : DiffOp()
        data class Replace(val origToken: Token, val corrToken: Token) : DiffOp()
    }

    data class RawHunk(
        val originalText: String,
        val correctedText: String,
        val startOffset: Int,
        val endOffset: Int
    )

    fun computeDiff(original: String, corrected: String): List<RawHunk> {
        val origTokens = tokenize(original)
        val corrTokens = tokenize(corrected)

        val n = origTokens.size
        val m = corrTokens.size

        // Standard LCS table
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0 until n) {
            for (j in 0 until m) {
                if (origTokens[i].text == corrTokens[j].text) {
                    dp[i + 1][j + 1] = dp[i][j] + 1
                } else {
                    dp[i + 1][j + 1] = maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }

        // Backtrack to find diff operations
        var i = n
        var j = m
        val ops = mutableListOf<DiffOp>()

        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && origTokens[i - 1].text == corrTokens[j - 1].text -> {
                    ops.add(DiffOp.Equal(origTokens[i - 1], corrTokens[j - 1]))
                    i--
                    j--
                }
                i > 0 && (j == 0 || dp[i - 1][j] >= dp[i][j - 1]) -> {
                    ops.add(DiffOp.Delete(origTokens[i - 1]))
                    i--
                }
                j > 0 -> {
                    ops.add(DiffOp.Insert(corrTokens[j - 1]))
                    j--
                }
            }
        }
        ops.reverse()

        // Group into raw hunks, coalescing adjacent changed tokens separated only by whitespace
        val hunks = mutableListOf<RawHunk>()
        var k = 0
        while (k < ops.size) {
            val op = ops[k]
            if (op is DiffOp.Equal) {
                k++
                continue
            }

            // Beginning of a hunk
            var hunkStartOffset = when (op) {
                is DiffOp.Delete -> op.origToken.startOffset
                is DiffOp.Replace -> op.origToken.startOffset
                is DiffOp.Insert -> {
                    // Try to anchor to preceding token's end or following token's start
                    if (k > 0) {
                        when (val prev = ops[k - 1]) {
                            is DiffOp.Equal -> prev.origToken.endOffset
                            is DiffOp.Delete -> prev.origToken.endOffset
                            is DiffOp.Replace -> prev.origToken.endOffset
                            is DiffOp.Insert -> 0
                        }
                    } else 0
                }
                is DiffOp.Equal -> 0
            }

            val origBuilder = StringBuilder()
            val corrBuilder = StringBuilder()
            var lastOrigEnd = hunkStartOffset

            var endK = k
            while (endK < ops.size) {
                val currentOp = ops[endK]
                if (currentOp is DiffOp.Equal) {
                    // Check if this Equal is just whitespace AND followed immediately by another change
                    if (currentOp.origToken.type == TokenType.WHITESPACE && endK + 1 < ops.size && ops[endK + 1] !is DiffOp.Equal) {
                        origBuilder.append(currentOp.origToken.text)
                        corrBuilder.append(currentOp.corrToken.text)
                        lastOrigEnd = currentOp.origToken.endOffset
                        endK++
                        continue
                    } else {
                        break
                    }
                }

                when (currentOp) {
                    is DiffOp.Delete -> {
                        origBuilder.append(currentOp.origToken.text)
                        lastOrigEnd = currentOp.origToken.endOffset
                    }
                    is DiffOp.Insert -> {
                        corrBuilder.append(currentOp.corrToken.text)
                    }
                    is DiffOp.Replace -> {
                        origBuilder.append(currentOp.origToken.text)
                        corrBuilder.append(currentOp.corrToken.text)
                        lastOrigEnd = currentOp.origToken.endOffset
                    }
                    is DiffOp.Equal -> Unit
                }
                endK++
            }

            val hunkOrig = origBuilder.toString()
            val hunkCorr = corrBuilder.toString()
            if (hunkOrig != hunkCorr) {
                hunks.add(
                    RawHunk(
                        originalText = hunkOrig,
                        correctedText = hunkCorr,
                        startOffset = hunkStartOffset,
                        endOffset = lastOrigEnd
                    )
                )
            }
            k = endK
        }

        return hunks
    }

    /**
     * Matches raw computed diff hunks to model edits.
     * Drops model edits that do not correspond to real hunks.
     * Assigns generic category & explanation to orphaned hunks.
     * Groups multi-token model edits together under the same groupId.
     */
    fun matchHunksToModelEdits(
        rawHunks: List<RawHunk>,
        modelEdits: List<EditItemDto>,
        defaultCategory: CheckCategory = CheckCategory.GRAMMAR,
        defaultExplanation: String = "Исправление"
    ): List<CheckEditHunk> {
        val result = mutableListOf<CheckEditHunk>()

        for (hunk in rawHunks) {
            val matchingModelEdit = modelEdits.firstOrNull { edit ->
                val oMatches = edit.original.isNotBlank() && (
                    hunk.originalText.contains(edit.original, ignoreCase = true) ||
                    edit.original.contains(hunk.originalText, ignoreCase = true)
                )
                val cMatches = edit.corrected.isNotBlank() && (
                    hunk.correctedText.contains(edit.corrected, ignoreCase = true) ||
                    edit.corrected.contains(hunk.correctedText, ignoreCase = true)
                )
                oMatches || cMatches
            }

            val category = when {
                matchingModelEdit != null -> CheckCategory.fromString(matchingModelEdit.category)
                isPunctuationOnly(hunk.originalText, hunk.correctedText) -> CheckCategory.PUNCTUATION
                isCaseOnly(hunk.originalText, hunk.correctedText) -> CheckCategory.SPELLING
                else -> defaultCategory
            }

            val explanation = matchingModelEdit?.explanation?.takeIf { it.isNotBlank() } ?: defaultExplanation
            val rule = matchingModelEdit?.rule

            val isSingle = isSingleGermanWord(hunk.correctedText)
            val cleanWord = if (isSingle) hunk.correctedText.trim() else null

            // Generate groupId from matching model edit if present
            val groupId = if (matchingModelEdit != null) {
                "group_${matchingModelEdit.original.hashCode()}_${matchingModelEdit.corrected.hashCode()}"
            } else null

            result.add(
                CheckEditHunk(
                    id = UUID.randomUUID().toString(),
                    original = hunk.originalText,
                    corrected = hunk.correctedText,
                    category = category,
                    explanation = explanation,
                    rule = rule,
                    isAccepted = true,
                    startOffset = hunk.startOffset,
                    endOffset = hunk.endOffset,
                    isSingleWord = isSingle,
                    cleanCorrectedWord = cleanWord,
                    groupId = groupId
                )
            )
        }

        return result
    }

    fun isPunctuationOnly(orig: String, corr: String): Boolean {
        val strippedOrig = orig.filter { !it.isWhitespace() && it !in PUNCTUATION_CHARS }
        val strippedCorr = corr.filter { !it.isWhitespace() && it !in PUNCTUATION_CHARS }
        return strippedOrig.isEmpty() && strippedCorr.isEmpty()
    }

    fun isCaseOnly(orig: String, corr: String): Boolean {
        return orig.trim().equals(corr.trim(), ignoreCase = true) && orig.trim() != corr.trim()
    }

    fun isSingleGermanWord(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank() || trimmed.contains(' ')) return false
        return trimmed.all { it.isLetter() || it == '-' }
    }

    /**
     * Assembles the final text based on accepted/rejected hunks.
     */
    fun assembleFinalText(originalText: String, hunks: List<CheckEditHunk>): String {
        if (hunks.isEmpty()) return originalText

        // Sort hunks by startOffset
        val sorted = hunks.sortedBy { it.startOffset }
        val sb = StringBuilder()
        var cursor = 0

        for (hunk in sorted) {
            if (hunk.startOffset > cursor && hunk.startOffset <= originalText.length) {
                sb.append(originalText.substring(cursor, hunk.startOffset))
            }

            if (hunk.isAccepted) {
                sb.append(hunk.corrected)
            } else {
                sb.append(hunk.original)
            }
            cursor = maxOf(cursor, hunk.endOffset)
        }

        if (cursor < originalText.length) {
            sb.append(originalText.substring(cursor))
        }

        return sb.toString()
    }
}
