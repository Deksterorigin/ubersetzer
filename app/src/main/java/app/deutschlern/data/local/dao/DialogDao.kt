package app.deutschlern.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.deutschlern.data.local.entity.DialogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DialogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDialog(dialog: DialogEntity): Long

    @Update
    suspend fun updateDialog(dialog: DialogEntity)

    @Delete
    suspend fun deleteDialog(dialog: DialogEntity)

    @Query("DELETE FROM dialogs WHERE id = :id")
    suspend fun deleteDialogById(id: Long)

    @Query("SELECT * FROM dialogs WHERE id = :id")
    suspend fun getDialogById(id: Long): DialogEntity?

    @Query("SELECT * FROM dialogs ORDER BY startedAt DESC")
    fun getAllDialogs(): Flow<List<DialogEntity>>

    @Query("SELECT COUNT(*) FROM dialogs")
    suspend fun getDialogCount(): Int

    @Query("UPDATE dialogs SET status = 'INTERRUPTED' WHERE status = 'RECORDING'")
    suspend fun markInterruptedRecordings()
}
