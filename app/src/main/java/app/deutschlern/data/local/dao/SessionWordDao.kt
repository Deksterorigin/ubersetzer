package app.deutschlern.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.deutschlern.data.local.entity.SessionWordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionWordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sessionWord: SessionWordEntity): Long

    @Query("SELECT * FROM session_words WHERE sessionId = :sessionId AND wordId = :wordId LIMIT 1")
    suspend fun get(sessionId: Long, wordId: Long): SessionWordEntity?

    @Query("SELECT * FROM session_words WHERE wordId = :wordId")
    suspend fun getByWordId(wordId: Long): List<SessionWordEntity>

    @Query("SELECT * FROM session_words")
    fun getAll(): Flow<List<SessionWordEntity>>
}
