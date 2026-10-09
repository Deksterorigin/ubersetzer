package app.deutschlern.data.repository

import app.deutschlern.data.local.dao.TextCheckDao
import app.deutschlern.data.local.entity.TextCheckEntity
import app.deutschlern.data.remote.dto.check.CommentItemDto
import app.deutschlern.data.remote.dto.check.EditItemDto
import app.deutschlern.domain.model.check.CheckCategory
import app.deutschlern.domain.model.check.CheckComment
import app.deutschlern.domain.model.check.CheckEditHunk
import app.deutschlern.domain.model.check.CommentIssue
import app.deutschlern.domain.model.check.TextCheckEntry
import app.deutschlern.domain.repository.TextCheckRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextCheckRepositoryImpl @Inject constructor(
    private val textCheckDao: TextCheckDao,
    private val json: Json
) : TextCheckRepository {

    override fun getAllChecks(): Flow<List<TextCheckEntry>> {
        return textCheckDao.getAll().map { list ->
            list.map { entityToDomain(it) }
        }
    }

    override suspend fun saveCheck(entry: TextCheckEntry): Long {
        val editsDtoList = entry.hunks.map { hunk ->
            EditItemDto(
                original = hunk.original,
                corrected = hunk.corrected,
                category = hunk.category.name.lowercase(),
                explanation = hunk.explanation,
                rule = hunk.rule
            )
        }
        val commentsDtoList = entry.comments.map { comment ->
            CommentItemDto(
                fragment = comment.fragment,
                issue = comment.issue.name.lowercase(),
                explanation = comment.explanation
            )
        }

        val entity = TextCheckEntity(
            id = entry.id,
            originalText = entry.originalText,
            correctedText = entry.correctedText,
            editsJson = json.encodeToString(editsDtoList),
            commentsJson = json.encodeToString(commentsDtoList),
            finalText = entry.finalText,
            createdAt = entry.createdAt,
            providerModel = entry.providerModel,
            partnerLanguage = entry.partnerLanguage
        )
        return textCheckDao.insert(entity)
    }

    override suspend fun deleteCheck(id: Long) {
        textCheckDao.deleteById(id)
    }

    override suspend fun clearAllChecks() {
        textCheckDao.deleteAll()
    }

    override suspend fun getCheckById(id: Long): TextCheckEntry? {
        val entity = textCheckDao.getById(id) ?: return null
        return entityToDomain(entity)
    }

    private fun entityToDomain(entity: TextCheckEntity): TextCheckEntry {
        val editsDtoList = runCatching {
            json.decodeFromString<List<EditItemDto>>(entity.editsJson)
        }.getOrDefault(emptyList())

        val commentsDtoList = runCatching {
            json.decodeFromString<List<CommentItemDto>>(entity.commentsJson)
        }.getOrDefault(emptyList())

        val hunks = editsDtoList.mapIndexed { index, dto ->
            CheckEditHunk(
                id = "saved_${entity.id}_$index",
                original = dto.original,
                corrected = dto.corrected,
                category = CheckCategory.fromString(dto.category),
                explanation = dto.explanation,
                rule = dto.rule,
                isAccepted = true
            )
        }

        val comments = commentsDtoList.map { dto ->
            CheckComment(
                fragment = dto.fragment,
                issue = CommentIssue.fromString(dto.issue),
                explanation = dto.explanation
            )
        }

        return TextCheckEntry(
            id = entity.id,
            originalText = entity.originalText,
            correctedText = entity.correctedText,
            finalText = entity.finalText,
            hunks = hunks,
            comments = comments,
            createdAt = entity.createdAt,
            providerModel = entity.providerModel,
            partnerLanguage = entity.partnerLanguage
        )
    }
}
