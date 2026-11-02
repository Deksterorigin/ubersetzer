package app.deutschlern.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.deutschlern.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE date = :date ORDER BY id DESC")
    suspend fun getSessionsForDate(date: Long): List<SessionEntity>

    @Query("SELECT * FROM sessions ORDER BY date DESC, id DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

}
