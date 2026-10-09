package app.deutschlern.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.deutschlern.data.local.entity.TextTranslationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TextTranslationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(translation: TextTranslationEntity): Long

    @Query("SELECT * FROM text_translations ORDER BY createdAt DESC")
    fun getAll(): Flow<List<TextTranslationEntity>>

    @Query("SELECT * FROM text_translations WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TextTranslationEntity?
}
