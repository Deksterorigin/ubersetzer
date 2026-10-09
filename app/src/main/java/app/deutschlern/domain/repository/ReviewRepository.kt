package app.deutschlern.domain.repository

import app.deutschlern.domain.model.ReviewCard
import app.deutschlern.domain.sm2.Sm2Grade
import kotlinx.coroutines.flow.Flow

interface ReviewRepository {

    /**
     * Emits all review cards due today (dueAt <= now, or new words without review state),
     * sorted with weak words first (timesTranslated DESC, easeFactor ASC).
     */
    fun getDueReviewCards(now: Long = System.currentTimeMillis()): Flow<List<ReviewCard>>

    /**
     * Emits review cards for all words in the most recent lesson (ignoring due date),
     * sorted with weak words first.
     */
    fun getLastLessonReviewCards(): Flow<List<ReviewCard>>

    /**
     * Records a review evaluation (Sm2Grade), calculates next interval/EF, updates Room,
     * and returns the updated ReviewCard.
     */
    suspend fun reviewCard(
        wordId: Long,
        grade: Sm2Grade,
        now: Long = System.currentTimeMillis()
    ): ReviewCard

    /**
     * Emits the count of cards due today.
     */
    fun getDueCardsCount(now: Long = System.currentTimeMillis()): Flow<Int>
}
