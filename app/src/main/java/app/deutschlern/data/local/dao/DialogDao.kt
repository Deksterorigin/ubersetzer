package app.deutschlern.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.deutschlern.data.local.entity.DialogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DialogDao {

    // ABORT, not REPLACE: REPLACE is DELETE+INSERT and would cascade-delete segments and tasks
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDialog(dialog: DialogEntity): Long

    @Update
    suspend fun updateDialog(dialog: DialogEntity)

    @Query("DELETE FROM dialogs WHERE id = :id")
    suspend fun deleteDialogById(id: Long)

    @Query("SELECT * FROM dialogs WHERE id = :id")
    suspend fun getDialogById(id: Long): DialogEntity?

    @Query("SELECT * FROM dialogs ORDER BY startedAt DESC")
    fun getAllDialogs(): Flow<List<DialogEntity>>

    @Query("UPDATE dialogs SET status = 'INTERRUPTED' WHERE status = 'RECORDING'")
    suspend fun markInterruptedRecordings()
}
