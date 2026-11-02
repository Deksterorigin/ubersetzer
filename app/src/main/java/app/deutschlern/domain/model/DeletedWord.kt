package app.deutschlern.domain.model

/**
 * Snapshot of everything removed together with a word (cascade), so that undo restores
 * the SM-2 progress and lesson membership, not just the word itself.
 */
data class DeletedWord(
    val word: WordEntry,
    val review: ReviewProgress?,
    val sessionLinks: List<SessionLink>
) {
    data class ReviewProgress(
        val easeFactor: Double,
        val intervalDays: Int,
        val repetitions: Int,
        val dueAt: Long
    )

    data class SessionLink(
        val sessionId: Long,
        val translatedAt: Long
    )
}
