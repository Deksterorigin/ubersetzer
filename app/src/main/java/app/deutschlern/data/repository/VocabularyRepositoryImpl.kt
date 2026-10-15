package app.deutschlern.data.repository

import app.deutschlern.data.local.dao.SessionDao
import app.deutschlern.data.local.dao.SessionWordDao
import app.deutschlern.data.local.dao.TextTranslationDao
import app.deutschlern.data.local.dao.WordDao
import app.deutschlern.data.local.entity.SessionEntity
import app.deutschlern.data.local.entity.SessionWordEntity
import app.deutschlern.data.local.entity.TextTranslationEntity
import app.deutschlern.data.local.mapper.WordEntryMapper
import app.deutschlern.data.local.mapper.WordEntryMapper.toDomain
import app.deutschlern.di.IoDispatcher
import app.deutschlern.domain.model.Meaning
import app.deutschlern.domain.model.Session
import app.deutschlern.domain.model.SessionWithWords
import app.deutschlern.domain.model.WordEntry
import app.deutschlern.domain.model.WordStatus
import app.deutschlern.domain.model.WordTranslation
import app.deutschlern.domain.repository.VocabularyRepository
import app.deutschlern.util.TextNormalizer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class VocabularyRepositoryImpl @Inject constructor(
    private val wordDao: WordDao,
    private val sessionDao: SessionDao,
    private val sessionWordDao: SessionWordDao,
    private val textTranslationDao: TextTranslationDao,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : VocabularyRepository {

    override suspend fun getOrCreateActiveSession(): Session = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val startOfDay = getStartOfDay(now)
        val todaySessions = sessionDao.getSessionsForDate(startOfDay)

        if (todaySessions.isNotEmpty()) {
            return@withContext todaySessions.first().toDomain()
        }

        // Automatic session creation per day: "Урок, 12 окт"
        val title = "Урок, ${formatSessionDate(now)}"
        val newSession = SessionEntity(
            title = title,
            date = startOfDay,
            isManual = false
        )
        val id = sessionDao.insert(newSession)
        newSession.copy(id = id).toDomain()
    }

    override suspend fun createManualSession(): Session = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val startOfDay = getStartOfDay(now)
        val todaySessions = sessionDao.getSessionsForDate(startOfDay)

        val formattedDate = formatSessionDate(now)
        val title = if (todaySessions.isEmpty()) {
            "Урок, $formattedDate"
        } else {
            "Урок, $formattedDate (${todaySessions.size + 1})"
        }

        val newSession = SessionEntity(
            title = title,
            date = startOfDay,
            isManual = true
        )
        val id = sessionDao.insert(newSession)
        newSession.copy(id = id).toDomain()
    }

    override fun getSessionsWithWords(): Flow<List<SessionWithWords>> {
        return combine(
            sessionDao.getAllSessions(),
            wordDao.getAllWords(),
            sessionWordDao.getAll()
        ) { sessions, words, sessionWords ->
            val wordMap = words.associateBy { it.id }
            val sessionWordsMap = sessionWords.groupBy { it.sessionId }

            sessions.map { sessionEntity ->
                val links = sessionWordsMap[sessionEntity.id].orEmpty().sortedByDescending { it.translatedAt }
                val sessionWordsList = links.mapNotNull { link ->
                    wordMap[link.wordId]?.let { WordEntryMapper.entityToDomain(it, json) }
                }
                SessionWithWords(
                    session = sessionEntity.toDomain(),
                    words = sessionWordsList
                )
            }
        }
    }

    override fun getAllWords(): Flow<List<WordEntry>> {
        return wordDao.getAllWords().map { entities ->
            entities.map { WordEntryMapper.entityToDomain(it, json) }
        }
    }

    override suspend fun saveWordTranslation(wordTranslation: WordTranslation): WordEntry = withContext(ioDispatcher) {
        // Extract article and sanitize lemma, pos, and article
        val (extractedArticle, baseLemma) = TextNormalizer.extractArticleAndLemma(wordTranslation.lemma)
        val candidateArticle = wordTranslation.article?.trim()?.ifBlank { null } ?: extractedArticle
        val rawLemma = if (extractedArticle != null) baseLemma else wordTranslation.lemma

        val sanitized = app.deutschlern.domain.sanitizer.ArticleSanitizer.sanitize(
            rawLemma = rawLemma,
            rawPos = wordTranslation.pos,
            rawArticle = candidateArticle
        )

        val normalizedLemma = TextNormalizer.normalizeLemma(sanitized.lemma)
        val cleanPos = sanitized.pos
        val resolvedArticle = sanitized.article
        val partnerLang = wordTranslation.partnerLanguage.trim().lowercase().ifBlank { "ru" }

        // Deduplicate meanings and clean notes
        val deduplicatedMeanings = app.deutschlern.domain.sanitizer.MeaningDeduplicator.deduplicate(wordTranslation.meanings)

        val activeSession = getOrCreateActiveSession()
        val now = System.currentTimeMillis()

        val existing = wordDao.getByLemmaPosAndPartner(normalizedLemma, cleanPos, partnerLang)

        if (existing != null) {
            // Re-translation: increment timesTranslated, update lastSeenAt, KEEP sessionId and SM-2 progress
            val newTimesTranslated = existing.timesTranslated + 1

            // Remap selectedMeaningIndex safely: match selected meaning text, or reset to null
            val oldMeanings = WordEntryMapper.parseMeanings(existing.meaningsJson, json)
            val selectedIndex = if (wordTranslation.selectedMeaningIndex != null) {
                wordTranslation.selectedMeaningIndex
            } else {
                app.deutschlern.domain.sanitizer.MeaningDeduplicator.remapSelectedMeaningIndex(
                    oldMeanings = oldMeanings,
                    oldIndex = existing.selectedMeaningIndex,
                    newMeanings = deduplicatedMeanings
                )
            }

            val updatedEntity = WordEntryMapper.wordTranslationToEntity(
                word = wordTranslation.copy(
                    lemma = normalizedLemma,
                    article = resolvedArticle,
                    pos = cleanPos,
                    meanings = deduplicatedMeanings,
                    selectedMeaningIndex = selectedIndex,
                    partnerLanguage = partnerLang
                ),
                sessionId = existing.sessionId, // Do NOT overwrite words.sessionId
                timesTranslated = newTimesTranslated,
                firstSeenAt = existing.firstSeenAt,
                lastSeenAt = now,
                status = existing.status,
                json = json
            ).copy(id = existing.id)

            wordDao.update(updatedEntity)

            // Insert into session_words for active session so it appears in current lesson
            sessionWordDao.insert(
                SessionWordEntity(
                    sessionId = activeSession.id,
                    wordId = existing.id,
                    translatedAt = now
                )
            )

            WordEntryMapper.entityToDomain(updatedEntity, json)
        } else {
            // New word entry
            val newEntity = WordEntryMapper.wordTranslationToEntity(
                word = wordTranslation.copy(
                    lemma = normalizedLemma,
                    article = resolvedArticle,
                    pos = cleanPos,
                    meanings = deduplicatedMeanings,
                    partnerLanguage = partnerLang
                ),
                sessionId = activeSession.id,
                timesTranslated = 1,
                firstSeenAt = now,
                lastSeenAt = now,
                status = WordStatus.NEW.name,
                json = json
            )

            val newId = wordDao.insert(newEntity)

            sessionWordDao.insert(
                SessionWordEntity(
                    sessionId = activeSession.id,
                    wordId = newId,
                    translatedAt = now
                )
            )

            WordEntryMapper.entityToDomain(newEntity.copy(id = newId), json)
        }
    }

    override suspend fun findWord(lemma: String, pos: String, partnerLanguage: String): WordEntry? = withContext(ioDispatcher) {
        val (_, baseLemma) = TextNormalizer.extractArticleAndLemma(lemma)
        val normalizedLemma = TextNormalizer.normalizeLemma(baseLemma)
        val cleanPos = app.deutschlern.domain.sanitizer.PosNormalizer.normalize(pos)
        val partner = partnerLanguage.trim().lowercase().ifBlank { "ru" }
        wordDao.getByLemmaPosAndPartner(normalizedLemma, cleanPos, partner)?.let {
            WordEntryMapper.entityToDomain(it, json)
        }
    }

    override suspend fun addWordToCurrentSession(wordId: Long): Unit = withContext(ioDispatcher) {
        val activeSession = getOrCreateActiveSession()
        sessionWordDao.insert(
            SessionWordEntity(
                sessionId = activeSession.id,
                wordId = wordId,
                translatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun saveGermanEquivalent(
        germanLemmaWithArticle: String,
        russianTranslation: String,
        pos: String
    ): WordEntry = withContext(ioDispatcher) {
        val (sanitizedArticle, baseLemma) = app.deutschlern.domain.sanitizer.ArticleSanitizer.sanitizeGermanEquivalent(
            equivalentText = germanLemmaWithArticle,
            expectedPos = pos
        )
        val normalizedLemma = TextNormalizer.normalizeLemma(baseLemma)
        val cleanPos = if (pos.isBlank() || pos == "other") {
            if (sanitizedArticle != null) "noun" else "other"
        } else {
            app.deutschlern.domain.sanitizer.PosNormalizer.normalize(pos)
        }

        val wordTranslation = WordTranslation(
            lemma = normalizedLemma,
            article = sanitizedArticle,
            pos = cleanPos,
            meanings = listOf(
                Meaning(
                    translation = russianTranslation.trim(),
                    exampleDe = "",
                    exampleRu = ""
                )
            )
        )

        saveWordTranslation(wordTranslation)
    }

    override suspend fun updateSelectedMeaning(wordId: Long, meaningIndex: Int?) = withContext(ioDispatcher) {
        wordDao.updateSelectedMeaningIndex(wordId, meaningIndex)
    }

    override suspend fun updateWordStatus(wordId: Long, status: WordStatus) = withContext(ioDispatcher) {
        wordDao.updateStatus(wordId, status.name)
    }

    override suspend fun deleteWord(wordId: Long): WordEntry? = withContext(ioDispatcher) {
        val existing = wordDao.getById(wordId) ?: return@withContext null
        wordDao.deleteById(wordId)
        WordEntryMapper.entityToDomain(existing, json)
    }

    override suspend fun restoreWord(word: WordEntry) = withContext(ioDispatcher) {
        val entity = WordEntryMapper.wordTranslationToEntity(
            word = word.toWordTranslation(),
            sessionId = word.sessionId,
            timesTranslated = word.timesTranslated,
            firstSeenAt = word.firstSeenAt,
            lastSeenAt = word.lastSeenAt,
            status = word.status.name,
            json = json
        ).copy(id = word.id)

        wordDao.insertOrReplace(entity)

        // Restore link in session_words
        sessionWordDao.insert(
            SessionWordEntity(
                sessionId = word.sessionId,
                wordId = word.id,
                translatedAt = word.lastSeenAt
            )
        )
        Unit
    }

    override suspend fun saveTextTranslation(source: String, result: String, direction: String): Unit = withContext(ioDispatcher) {
        textTranslationDao.insert(
            TextTranslationEntity(
                source = source,
                result = result,
                direction = direction,
                createdAt = System.currentTimeMillis()
            )
        )
        Unit
    }

    companion object {
        fun formatSessionDate(timestamp: Long): String {
            val date = Date(timestamp)
            val formatter = SimpleDateFormat("d MMM", Locale("ru"))
            return formatter.format(date).trimEnd('.')
        }

        fun getStartOfDay(timestamp: Long = System.currentTimeMillis()): Long {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = timestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return calendar.timeInMillis
        }
    }
}
