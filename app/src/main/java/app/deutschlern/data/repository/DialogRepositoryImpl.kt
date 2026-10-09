package app.deutschlern.data.repository

import app.deutschlern.data.local.dao.DialogDao
import app.deutschlern.data.local.dao.DialogSegmentDao
import app.deutschlern.data.local.dao.DialogTaskDao
import app.deutschlern.data.local.entity.DialogEntity
import app.deutschlern.data.local.entity.DialogSegmentEntity
import app.deutschlern.data.local.entity.DialogTaskEntity
import app.deutschlern.domain.model.dialog.Dialog
import app.deutschlern.domain.model.dialog.DialogSegment
import app.deutschlern.domain.model.dialog.DialogSummary
import app.deutschlern.domain.model.dialog.DialogTask
import app.deutschlern.domain.model.dialog.DialogTaskType
import app.deutschlern.domain.repository.DialogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DialogRepositoryImpl @Inject constructor(
    private val dialogDao: DialogDao,
    private val dialogSegmentDao: DialogSegmentDao,
    private val dialogTaskDao: DialogTaskDao,
    private val json: Json
) : DialogRepository {

    override fun getAllDialogs(): Flow<List<Dialog>> {
        return dialogDao.getAllDialogs().map { list ->
            list.map { entityToDialog(it) }
        }
    }

    override suspend fun getDialogById(id: Long): Dialog? {
        val entity = dialogDao.getDialogById(id) ?: return null
        return entityToDialog(entity)
    }

    override suspend fun saveDialog(dialog: Dialog): Long {
        val entity = dialogToEntity(dialog)
        return dialogDao.insertDialog(entity)
    }

    override suspend fun updateDialog(dialog: Dialog) {
        val entity = dialogToEntity(dialog)
        dialogDao.updateDialog(entity)
    }

    override suspend fun deleteDialog(id: Long) {
        dialogDao.deleteDialogById(id)
    }

    override fun getSegmentsForDialog(dialogId: Long): Flow<List<DialogSegment>> {
        return dialogSegmentDao.getSegmentsForDialog(dialogId).map { list ->
            list.map { segmentEntityToDomain(it) }
        }
    }

    override suspend fun getSegmentsSync(dialogId: Long): List<DialogSegment> {
        return dialogSegmentDao.getSegmentsSync(dialogId).map { segmentEntityToDomain(it) }
    }

    override suspend fun insertSegment(segment: DialogSegment): Long {
        return dialogSegmentDao.insertSegment(segmentDomainToEntity(segment))
    }

    override suspend fun insertSegments(segments: List<DialogSegment>) {
        dialogSegmentDao.insertSegments(segments.map { segmentDomainToEntity(it) })
    }

    override fun getTasksForDialog(dialogId: Long): Flow<List<DialogTask>> {
        return dialogTaskDao.getTasksForDialog(dialogId).map { list ->
            list.map { taskEntityToDomain(it) }
        }
    }

    override suspend fun getTaskById(taskId: Long): DialogTask? {
        val entity = dialogTaskDao.getTaskById(taskId) ?: return null
        return taskEntityToDomain(entity)
    }

    override suspend fun insertTask(task: DialogTask): Long {
        return dialogTaskDao.insertTask(taskDomainToEntity(task))
    }

    override suspend fun updateTask(task: DialogTask) {
        dialogTaskDao.updateTask(taskDomainToEntity(task))
    }

    override suspend fun deleteTask(taskId: Long) {
        val entity = dialogTaskDao.getTaskById(taskId) ?: return
        dialogTaskDao.deleteTask(entity)
    }

    private fun entityToDialog(entity: DialogEntity): Dialog {
        val summary = entity.summaryJson?.let { raw ->
            runCatching {
                json.decodeFromString(DialogSummary.serializer(), raw)
            }.getOrNull()
        }
        return Dialog(
            id = entity.id,
            title = entity.title,
            startedAt = entity.startedAt,
            durationMs = entity.durationMs,
            localeTag = entity.localeTag,
            segmentCount = entity.segmentCount,
            summary = summary,
            summaryProviderModel = entity.summaryProviderModel,
            summaryUpdatedAt = entity.summaryUpdatedAt
        )
    }

    private fun dialogToEntity(dialog: Dialog): DialogEntity {
        val summaryJson = dialog.summary?.let {
            runCatching {
                json.encodeToString(DialogSummary.serializer(), it)
            }.getOrNull()
        }
        return DialogEntity(
            id = dialog.id,
            title = dialog.title,
            startedAt = dialog.startedAt,
            durationMs = dialog.durationMs,
            localeTag = dialog.localeTag,
            segmentCount = dialog.segmentCount,
            summaryJson = summaryJson,
            summaryProviderModel = dialog.summaryProviderModel,
            summaryUpdatedAt = dialog.summaryUpdatedAt
        )
    }

    private fun segmentEntityToDomain(entity: DialogSegmentEntity): DialogSegment {
        return DialogSegment(
            id = entity.id,
            dialogId = entity.dialogId,
            orderIndex = entity.orderIndex,
            text = entity.text,
            speakerLabel = entity.speakerLabel,
            startOffsetMs = entity.startOffsetMs
        )
    }

    private fun segmentDomainToEntity(segment: DialogSegment): DialogSegmentEntity {
        return DialogSegmentEntity(
            id = segment.id,
            dialogId = segment.dialogId,
            orderIndex = segment.orderIndex,
            text = segment.text,
            speakerLabel = segment.speakerLabel,
            startOffsetMs = segment.startOffsetMs
        )
    }

    private fun taskEntityToDomain(entity: DialogTaskEntity): DialogTask {
        return DialogTask(
            id = entity.id,
            dialogId = entity.dialogId,
            type = DialogTaskType.fromString(entity.type),
            payloadJson = entity.payloadJson,
            resultJson = entity.resultJson,
            scorePercent = entity.scorePercent,
            createdAt = entity.createdAt,
            completedAt = entity.completedAt
        )
    }

    private fun taskDomainToEntity(task: DialogTask): DialogTaskEntity {
        return DialogTaskEntity(
            id = task.id,
            dialogId = task.dialogId,
            type = task.type.name,
            payloadJson = task.payloadJson,
            resultJson = task.resultJson,
            scorePercent = task.scorePercent,
            createdAt = task.createdAt,
            completedAt = task.completedAt
        )
    }
}
