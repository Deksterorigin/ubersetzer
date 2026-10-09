package app.deutschlern.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.deutschlern.data.local.entity.ReviewStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(reviewState: ReviewStateEntity): Long

    @Update
    suspend fun update(reviewState: ReviewStateEntity)

    @Query("SELECT * FROM review_states WHERE wordId = :wordId LIMIT 1")
    suspend fun getByWordId(wordId: Long): ReviewStateEntity?

    @Query("SELECT * FROM review_states WHERE dueAt <= :now ORDER BY dueAt ASC")
    suspend fun getDueReviewStates(now: Long): List<ReviewStateEntity>

    @Query("SELECT * FROM review_states")
    fun getAllReviewStates(): Flow<List<ReviewStateEntity>>

    @Query("SELECT * FROM review_states")
    suspend fun getAllReviewStatesSync(): List<ReviewStateEntity>

    @Query("DELETE FROM review_states WHERE wordId = :wordId")
    suspend fun deleteByWordId(wordId: Long)
}
