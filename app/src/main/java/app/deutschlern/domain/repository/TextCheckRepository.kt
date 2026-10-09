package app.deutschlern.domain.repository

import app.deutschlern.domain.model.check.TextCheckEntry
import kotlinx.coroutines.flow.Flow

interface TextCheckRepository {
    fun getAllChecks(): Flow<List<TextCheckEntry>>
    suspend fun saveCheck(entry: TextCheckEntry): Long
    suspend fun deleteCheck(id: Long)
    suspend fun clearAllChecks()
    suspend fun getCheckById(id: Long): TextCheckEntry?
}
