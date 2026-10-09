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

    @Query("SELECT * FROM session_words WHERE sessionId = :sessionId ORDER BY translatedAt DESC")
    fun getWordsForSession(sessionId: Long): Flow<List<SessionWordEntity>>

    @Query("SELECT * FROM session_words WHERE sessionId = :sessionId ORDER BY translatedAt DESC")
    suspend fun getWordsForSessionSync(sessionId: Long): List<SessionWordEntity>

    @Query("SELECT * FROM session_words WHERE sessionId = :sessionId AND wordId = :wordId LIMIT 1")
    suspend fun get(sessionId: Long, wordId: Long): SessionWordEntity?

    @Query("DELETE FROM session_words WHERE sessionId = :sessionId AND wordId = :wordId")
    suspend fun delete(sessionId: Long, wordId: Long)

    @Query("DELETE FROM session_words WHERE wordId = :wordId")
    suspend fun deleteByWordId(wordId: Long)

    @Query("SELECT * FROM session_words")
    fun getAll(): Flow<List<SessionWordEntity>>
}
