package app.deutschlern.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.deutschlern.data.local.entity.WordEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(word: WordEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(word: WordEntryEntity): Long

    @Update
    suspend fun update(word: WordEntryEntity)

    @Delete
    suspend fun delete(word: WordEntryEntity)

    @Query("DELETE FROM words WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM words WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WordEntryEntity?

    @Query("SELECT * FROM words WHERE lemma = :lemma AND pos = :pos AND partnerLanguage = :partnerLanguage LIMIT 1")
    suspend fun getByLemmaPosAndPartner(lemma: String, pos: String, partnerLanguage: String): WordEntryEntity?

    @Query("SELECT * FROM words WHERE lemma = :lemma AND pos = :pos AND partnerLanguage = 'ru' LIMIT 1")
    suspend fun getByLemmaAndPos(lemma: String, pos: String): WordEntryEntity?

    @Query("UPDATE words SET selectedMeaningIndex = :index WHERE id = :id")
    suspend fun updateSelectedMeaningIndex(id: Long, index: Int?)

    @Query("UPDATE words SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("SELECT * FROM words ORDER BY lastSeenAt DESC")
    fun getAllWords(): Flow<List<WordEntryEntity>>

    @Query("SELECT * FROM words ORDER BY lastSeenAt DESC")
    suspend fun getAllWordsSync(): List<WordEntryEntity>

    @Query("""
        SELECT w.* FROM words w
        INNER JOIN session_words sw ON w.id = sw.wordId
        WHERE sw.sessionId = :sessionId
        ORDER BY sw.translatedAt DESC
    """)
    fun getWordsForSession(sessionId: Long): Flow<List<WordEntryEntity>>

    @Query("""
        SELECT w.* FROM words w
        INNER JOIN session_words sw ON w.id = sw.wordId
        WHERE sw.sessionId = :sessionId
        ORDER BY sw.translatedAt DESC
    """)
    suspend fun getWordsForSessionSync(sessionId: Long): List<WordEntryEntity>
}
