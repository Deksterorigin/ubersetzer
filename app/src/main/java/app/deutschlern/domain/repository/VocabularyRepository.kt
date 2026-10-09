package app.deutschlern.domain.repository

import app.deutschlern.domain.model.Session
import app.deutschlern.domain.model.SessionWithWords
import app.deutschlern.domain.model.WordEntry
import app.deutschlern.domain.model.WordStatus
import app.deutschlern.domain.model.WordTranslation
import kotlinx.coroutines.flow.Flow

interface VocabularyRepository {
    suspend fun getOrCreateActiveSession(): Session

    suspend fun createManualSession(): Session

    fun getSessionsWithWords(): Flow<List<SessionWithWords>>

    fun getAllWords(): Flow<List<WordEntry>>

    suspend fun saveWordTranslation(wordTranslation: WordTranslation): WordEntry

    suspend fun findWord(lemma: String, pos: String, partnerLanguage: String = "ru"): WordEntry?

    suspend fun addWordToCurrentSession(wordId: Long)

    suspend fun saveGermanEquivalent(
        germanLemmaWithArticle: String,
        russianTranslation: String,
        pos: String = "other"
    ): WordEntry

    suspend fun updateSelectedMeaning(wordId: Long, meaningIndex: Int?)

    suspend fun updateWordStatus(wordId: Long, status: WordStatus)

    suspend fun deleteWord(wordId: Long): WordEntry?

    suspend fun restoreWord(word: WordEntry)

    suspend fun saveTextTranslation(source: String, result: String, direction: String)
}
