package app.deutschlern.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.deutschlern.data.local.entity.DialogSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DialogSegmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegment(segment: DialogSegmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<DialogSegmentEntity>)

    @Query("SELECT * FROM dialog_segments WHERE dialogId = :dialogId ORDER BY orderIndex ASC")
    fun getSegmentsForDialog(dialogId: Long): Flow<List<DialogSegmentEntity>>

    @Query("SELECT * FROM dialog_segments WHERE dialogId = :dialogId ORDER BY orderIndex ASC")
    suspend fun getSegmentsSync(dialogId: Long): List<DialogSegmentEntity>

    @Query("DELETE FROM dialog_segments WHERE dialogId = :dialogId")
    suspend fun deleteSegmentsForDialog(dialogId: Long)
}
