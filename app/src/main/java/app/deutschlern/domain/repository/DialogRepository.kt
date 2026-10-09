package app.deutschlern.domain.repository

import app.deutschlern.domain.model.dialog.Dialog
import app.deutschlern.domain.model.dialog.DialogSegment
import app.deutschlern.domain.model.dialog.DialogTask
import kotlinx.coroutines.flow.Flow

interface DialogRepository {
    fun getAllDialogs(): Flow<List<Dialog>>
    suspend fun getDialogById(id: Long): Dialog?
    suspend fun saveDialog(dialog: Dialog): Long
    suspend fun updateDialog(dialog: Dialog)
    suspend fun deleteDialog(id: Long)

    fun getSegmentsForDialog(dialogId: Long): Flow<List<DialogSegment>>
    suspend fun getSegmentsSync(dialogId: Long): List<DialogSegment>
    suspend fun insertSegment(segment: DialogSegment): Long
    suspend fun insertSegments(segments: List<DialogSegment>)

    fun getTasksForDialog(dialogId: Long): Flow<List<DialogTask>>
    suspend fun getTaskById(taskId: Long): DialogTask?
    suspend fun insertTask(task: DialogTask): Long
    suspend fun updateTask(task: DialogTask)
    suspend fun deleteTask(taskId: Long)
}
