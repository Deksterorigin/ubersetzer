package app.deutschlern.data.local.dao

import androidx.room.Dao
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

    @Update
    suspend fun update(word: WordEntryEntity)

    @Query("DELETE FROM words WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM words WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WordEntryEntity?

    @Query("""
        SELECT * FROM words 
        WHERE lemma = :lemma 
          AND LOWER(pos) IN (:posList) 
          AND partnerLanguage = :partnerLanguage 
        ORDER BY CASE WHEN LOWER(pos) = LOWER(:canonicalPos) THEN 0 ELSE 1 END, id ASC 
        LIMIT 1
    """)
    suspend fun getByLemmaEquivalentPosAndPartner(
        lemma: String,
        posList: List<String>,
        canonicalPos: String,
        partnerLanguage: String
    ): WordEntryEntity?

    @Query("UPDATE words SET selectedMeaningIndex = :index WHERE id = :id")
    suspend fun updateSelectedMeaningIndex(id: Long, index: Int?)

    @Query("UPDATE words SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE words SET isDifficult = :isDifficult WHERE id = :id")
    suspend fun updateDifficult(id: Long, isDifficult: Boolean)

    @Query("UPDATE words SET topicsJson = :topicsJson WHERE id = :id")
    suspend fun updateTopics(id: Long, topicsJson: String)

    @Query("SELECT * FROM words WHERE topicsJson IS NULL ORDER BY lastSeenAt DESC LIMIT :limit")
    suspend fun getWithoutTopics(limit: Int): List<WordEntryEntity>

    @Query("SELECT * FROM words ORDER BY lastSeenAt DESC")
    fun getAllWords(): Flow<List<WordEntryEntity>>

    @Query("SELECT * FROM words ORDER BY lastSeenAt DESC")
    suspend fun getAllWordsSync(): List<WordEntryEntity>

}
