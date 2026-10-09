package app.deutschlern.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.deutschlern.data.local.entity.DialogTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DialogTaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DialogTaskEntity): Long

    @Update
    suspend fun updateTask(task: DialogTaskEntity)

    @Delete
    suspend fun deleteTask(task: DialogTaskEntity)

    @Query("SELECT * FROM dialog_tasks WHERE dialogId = :dialogId ORDER BY createdAt ASC")
    fun getTasksForDialog(dialogId: Long): Flow<List<DialogTaskEntity>>

    @Query("SELECT * FROM dialog_tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): DialogTaskEntity?

    @Query("DELETE FROM dialog_tasks WHERE dialogId = :dialogId")
    suspend fun deleteTasksForDialog(dialogId: Long)
}
