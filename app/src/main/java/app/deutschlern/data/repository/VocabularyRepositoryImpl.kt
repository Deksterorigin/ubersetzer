package app.deutschlern.data.repository

import app.deutschlern.data.local.TransactionRunner
import app.deutschlern.data.local.dao.ReviewDao
import app.deutschlern.data.local.dao.SessionDao
import app.deutschlern.data.local.dao.SessionWordDao
import app.deutschlern.data.local.dao.TextTranslationDao
import app.deutschlern.data.local.dao.WordDao
import app.deutschlern.data.local.entity.ReviewStateEntity
import app.deutschlern.data.local.entity.SessionEntity
import app.deutschlern.data.local.entity.SessionWordEntity
import app.deutschlern.data.local.entity.TextTranslationEntity
import app.deutschlern.data.local.mapper.WordEntryMapper
import app.deutschlern.data.local.mapper.WordEntryMapper.toDomain
import app.deutschlern.di.IoDispatcher
import app.deutschlern.domain.model.DeletedWord
import app.deutschlern.domain.model.Meaning
import app.deutschlern.domain.model.Session
import app.deutschlern.domain.model.SessionWithWords
import app.deutschlern.domain.model.WordEntry
import app.deutschlern.domain.model.WordStatus
import app.deutschlern.domain.model.WordTranslation
import app.deutschlern.domain.repository.VocabularyRepository
import app.deutschlern.domain.sanitizer.ArticleSanitizer
import app.deutschlern.domain.sanitizer.MeaningDeduplicator
import app.deutschlern.domain.sanitizer.PosNormalizer
import app.deutschlern.util.TextNormalizer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
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
    private val reviewDao: ReviewDao,
    private val textTranslationDao: TextTranslationDao,
    private val transactionRunner: TransactionRunner,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : VocabularyRepository {

    // Runs inside a transaction so that two concurrent callers cannot both create today's session.
    override suspend fun getOrCreateActiveSession(): Session = withContext(ioDispatcher) {
        transactionRunner.run {
            val now = System.currentTimeMillis()
            val startOfDay = getStartOfDay(now)
            sessionDao.getSessionsForDate(startOfDay).firstOrNull()?.toDomain()
                ?: run {
                    val newSession = SessionEntity(
                        title = "Урок, ${formatSessionDate(now)}",
                        date = startOfDay,
                        isManual = false
                    )
                    newSession.copy(id = sessionDao.insert(newSession)).toDomain()
                }
        }
    }

    override suspend fun createManualSession(): Session = withContext(ioDispatcher) {
        transactionRunner.run {
            val now = System.currentTimeMillis()
            val startOfDay = getStartOfDay(now)
            val todaySessions = sessionDao.getSessionsForDate(startOfDay)

            val formattedDate = formatSessionDate(now)
            val title = if (todaySessions.isEmpty()) {
                "Урок, $formattedDate"
            } else {
                "Урок, $formattedDate (${todaySessions.size + 1})"
            }

            val newSession = SessionEntity(title = title, date = startOfDay, isManual = true)
            newSession.copy(id = sessionDao.insert(newSession)).toDomain()
        }
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
                SessionWithWords(
                    session = sessionEntity.toDomain(),
                    words = links.mapNotNull { link ->
                        wordMap[link.wordId]?.let { WordEntryMapper.entityToDomain(it, json) }
                    }
                )
            }
        }.flowOn(ioDispatcher)
    }

    override fun getAllWords(): Flow<List<WordEntry>> {
        return wordDao.getAllWords()
            .map { entities -> entities.map { WordEntryMapper.entityToDomain(it, json) } }
            .flowOn(ioDispatcher)
    }

    // Lookup and insert run in one transaction: without it two concurrent saves of the same
    // word both miss the lookup and the second insert fails on the unique (lemma, pos, partner) index.
    override suspend fun saveWordTranslation(wordTranslation: WordTranslation): WordEntry = withContext(ioDispatcher) {
        val (extractedArticle, baseLemma) = TextNormalizer.extractArticleAndLemma(wordTranslation.lemma)
        val candidateArticle = wordTranslation.article?.trim()?.ifBlank { null } ?: extractedArticle
        val rawLemma = if (extractedArticle != null) baseLemma else wordTranslation.lemma

        val sanitized = ArticleSanitizer.sanitize(
            rawLemma = rawLemma,
            rawPos = wordTranslation.pos,
            rawArticle = candidateArticle
        )

        val normalizedLemma = TextNormalizer.normalizeLemma(sanitized.lemma)
        val cleanPos = sanitized.pos
        val partnerLang = wordTranslation.partnerLanguage.trim().lowercase().ifBlank { "ru" }
        val deduplicatedMeanings = MeaningDeduplicator.deduplicate(wordTranslation.meanings)

        transactionRunner.run {
            val activeSession = getOrCreateActiveSession()
            val now = System.currentTimeMillis()

            val existing = wordDao.getByLemmaEquivalentPosAndPartner(
                lemma = normalizedLemma,
                posList = PosNormalizer.getEquivalentPosList(cleanPos),
                canonicalPos = cleanPos,
                partnerLanguage = partnerLang
            )

            val normalizedWord = wordTranslation.copy(
                lemma = normalizedLemma,
                article = sanitized.article,
                pos = cleanPos,
                meanings = deduplicatedMeanings,
                partnerLanguage = partnerLang
            )

            val savedEntity = if (existing != null) {
                // Re-translation: keep sessionId, first-seen date, status and SM-2 progress
                val selectedIndex = wordTranslation.selectedMeaningIndex
                    ?: MeaningDeduplicator.remapSelectedMeaningIndex(
                        oldMeanings = WordEntryMapper.parseMeanings(existing.meaningsJson, json),
                        oldIndex = existing.selectedMeaningIndex,
                        newMeanings = deduplicatedMeanings
                    )

                WordEntryMapper.wordTranslationToEntity(
                    word = normalizedWord.copy(selectedMeaningIndex = selectedIndex),
                    sessionId = existing.sessionId,
                    timesTranslated = existing.timesTranslated + 1,
                    firstSeenAt = existing.firstSeenAt,
                    lastSeenAt = now,
                    status = existing.status,
                    json = json
                ).copy(id = existing.id).also { wordDao.update(it) }
            } else {
                val newEntity = WordEntryMapper.wordTranslationToEntity(
                    word = normalizedWord,
                    sessionId = activeSession.id,
                    timesTranslated = 1,
                    firstSeenAt = now,
                    lastSeenAt = now,
                    status = WordStatus.NEW.name,
                    json = json
                )
                newEntity.copy(id = wordDao.insert(newEntity))
            }

            // Link to the active session so the word appears in the current lesson
            sessionWordDao.insert(
                SessionWordEntity(sessionId = activeSession.id, wordId = savedEntity.id, translatedAt = now)
            )

            WordEntryMapper.entityToDomain(savedEntity, json)
        }
    }

    override suspend fun findWord(lemma: String, pos: String, partnerLanguage: String): WordEntry? = withContext(ioDispatcher) {
        val (_, baseLemma) = TextNormalizer.extractArticleAndLemma(lemma)
        val cleanPos = PosNormalizer.normalize(pos)
        wordDao.getByLemmaEquivalentPosAndPartner(
            lemma = TextNormalizer.normalizeLemma(baseLemma),
            posList = PosNormalizer.getEquivalentPosList(cleanPos),
            canonicalPos = cleanPos,
            partnerLanguage = partnerLanguage.trim().lowercase().ifBlank { "ru" }
        )?.let { WordEntryMapper.entityToDomain(it, json) }
    }

    override suspend fun addWordToCurrentSession(wordId: Long): Unit = withContext(ioDispatcher) {
        transactionRunner.run {
            val activeSession = getOrCreateActiveSession()
            sessionWordDao.insert(
                SessionWordEntity(
                    sessionId = activeSession.id,
                    wordId = wordId,
                    translatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun saveGermanEquivalent(
        germanLemmaWithArticle: String,
        russianTranslation: String,
        pos: String
    ): WordEntry {
        val (sanitizedArticle, baseLemma) = ArticleSanitizer.sanitizeGermanEquivalent(
            equivalentText = germanLemmaWithArticle,
            expectedPos = pos
        )
        val cleanPos = if (pos.isBlank() || pos == "other") {
            if (sanitizedArticle != null) "noun" else "other"
        } else {
            PosNormalizer.normalize(pos)
        }

        return saveWordTranslation(
            WordTranslation(
                lemma = TextNormalizer.normalizeLemma(baseLemma),
                article = sanitizedArticle,
                pos = cleanPos,
                meanings = listOf(
                    Meaning(translation = russianTranslation.trim(), exampleDe = "", exampleRu = "")
                )
            )
        )
    }

    override suspend fun updateSelectedMeaning(wordId: Long, meaningIndex: Int?) = withContext(ioDispatcher) {
        wordDao.updateSelectedMeaningIndex(wordId, meaningIndex)
    }

    override suspend fun updateWordStatus(wordId: Long, status: WordStatus) = withContext(ioDispatcher) {
        wordDao.updateStatus(wordId, status.name)
    }

    override suspend fun deleteWord(wordId: Long): DeletedWord? = withContext(ioDispatcher) {
        transactionRunner.run {
            val existing = wordDao.getById(wordId) ?: return@run null
            val review = reviewDao.getByWordId(wordId)
            val links = sessionWordDao.getByWordId(wordId)
            wordDao.deleteById(wordId)

            DeletedWord(
                word = WordEntryMapper.entityToDomain(existing, json),
                review = review?.let {
                    DeletedWord.ReviewProgress(
                        easeFactor = it.easeFactor,
                        intervalDays = it.intervalDays,
                        repetitions = it.repetitions,
                        dueAt = it.dueAt
                    )
                },
                sessionLinks = links.map { DeletedWord.SessionLink(it.sessionId, it.translatedAt) }
            )
        }
    }

    override suspend fun restoreWord(deleted: DeletedWord): Boolean = withContext(ioDispatcher) {
        val word = deleted.word
        transactionRunner.run {
            // The same word may have been translated again after deletion; never overwrite it.
            val conflicting = wordDao.getByLemmaEquivalentPosAndPartner(
                lemma = word.lemma,
                posList = PosNormalizer.getEquivalentPosList(word.pos),
                canonicalPos = word.pos,
                partnerLanguage = word.partnerLanguage
            )
            if (conflicting != null || wordDao.getById(word.id) != null) return@run false

            val entity = WordEntryMapper.wordTranslationToEntity(
                word = word.toWordTranslation(),
                sessionId = word.sessionId,
                timesTranslated = word.timesTranslated,
                firstSeenAt = word.firstSeenAt,
                lastSeenAt = word.lastSeenAt,
                status = word.status.name,
                json = json
            ).copy(id = word.id)
            wordDao.insert(entity)

            deleted.review?.let {
                reviewDao.insertOrReplace(
                    ReviewStateEntity(
                        wordId = word.id,
                        easeFactor = it.easeFactor,
                        intervalDays = it.intervalDays,
                        repetitions = it.repetitions,
                        dueAt = it.dueAt
                    )
                )
            }

            deleted.sessionLinks
                .filter { sessionDao.getById(it.sessionId) != null }
                .forEach {
                    sessionWordDao.insert(
                        SessionWordEntity(sessionId = it.sessionId, wordId = word.id, translatedAt = it.translatedAt)
                    )
                }
            true
        }
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
    }

    companion object {
        fun formatSessionDate(timestamp: Long): String {
            val formatter = SimpleDateFormat("d MMM", Locale("ru"))
            return formatter.format(Date(timestamp)).trimEnd('.')
        }

        fun getStartOfDay(timestamp: Long = System.currentTimeMillis()): Long {
            return Calendar.getInstance().apply {
                timeInMillis = timestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }
}
