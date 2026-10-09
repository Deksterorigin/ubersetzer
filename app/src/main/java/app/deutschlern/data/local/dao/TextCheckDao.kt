package app.deutschlern.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.deutschlern.data.local.entity.TextCheckEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TextCheckDao {

    @Query("SELECT * FROM text_checks ORDER BY createdAt DESC")
    fun getAll(): Flow<List<TextCheckEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TextCheckEntity): Long

    @Query("SELECT * FROM text_checks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TextCheckEntity?

    @Query("DELETE FROM text_checks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM text_checks")
    suspend fun deleteAll()
}
