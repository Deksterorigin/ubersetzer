package app.deutschlern.domain.model.check

enum class CheckCategory {
    SPELLING,
    GRAMMAR,
    PUNCTUATION,
    WORD_CHOICE;

    companion object {
        fun fromString(value: String): CheckCategory = when (value.trim().lowercase()) {
            "spelling" -> SPELLING
            "punctuation" -> PUNCTUATION
            "word_choice", "wordchoice", "lexical" -> WORD_CHOICE
            else -> GRAMMAR
        }
    }
}

enum class CommentIssue {
    CONTRADICTION,
    UNCLEAR_REFERENCE,
    MISSING_INFORMATION;

    companion object {
        fun fromString(value: String): CommentIssue = when (value.trim().lowercase()) {
            "contradiction" -> CONTRADICTION
            "missing_information", "missing" -> MISSING_INFORMATION
            else -> UNCLEAR_REFERENCE
        }
    }
}

data class CheckEditHunk(
    val id: String,
    val original: String,
    val corrected: String,
    val category: CheckCategory,
    val explanation: String,
    val rule: String? = null,
    val isAccepted: Boolean = true,
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val isSingleWord: Boolean = false,
    val cleanCorrectedWord: String? = null,
    val groupId: String? = null
)

data class CheckComment(
    val fragment: String,
    val issue: CommentIssue,
    val explanation: String
)

data class TextCheckResult(
    val originalText: String,
    val correctedText: String,
    val hunks: List<CheckEditHunk>,
    val comments: List<CheckComment> = emptyList(),
    val isOverEditWarning: Boolean = false,
    val providerModel: String = "",
    val partnerLanguage: String = "ru"
)

data class TextCheckEntry(
    val id: Long = 0,
    val originalText: String,
    val correctedText: String,
    val finalText: String,
    val hunks: List<CheckEditHunk>,
    val comments: List<CheckComment>,
    val createdAt: Long,
    val providerModel: String,
    val partnerLanguage: String
)
