package app.deutschlern.data.repository

import app.deutschlern.data.local.dao.SessionDao
import app.deutschlern.data.local.dao.SessionWordDao
import app.deutschlern.data.local.dao.TextTranslationDao
import app.deutschlern.data.local.dao.WordDao
import app.deutschlern.data.local.entity.SessionEntity
import app.deutschlern.data.local.entity.SessionWordEntity
import app.deutschlern.data.local.entity.TextTranslationEntity
import app.deutschlern.data.local.entity.WordEntryEntity
import app.deutschlern.di.NetworkModule
import app.deutschlern.domain.model.Meaning
import app.deutschlern.domain.model.WordStatus
import app.deutschlern.domain.model.WordTranslation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VocabularyRepositoryImplTest {

    private val wordDao = mockk<WordDao>(relaxed = true)
    private val sessionDao = mockk<SessionDao>(relaxed = true)
    private val sessionWordDao = mockk<SessionWordDao>(relaxed = true)
    private val textTranslationDao = mockk<TextTranslationDao>(relaxed = true)
    private val json = NetworkModule.provideJson()
    private val testDispatcher = UnconfinedTestDispatcher()

    private val repository = VocabularyRepositoryImpl(
        wordDao = wordDao,
        sessionDao = sessionDao,
        sessionWordDao = sessionWordDao,
        textTranslationDao = textTranslationDao,
        json = json,
        ioDispatcher = testDispatcher
    )

    @Test
    fun getOrCreateActiveSession_whenSessionExistsToday_reusesExisting() = runTest {
        val existingSession = SessionEntity(
            id = 10L,
            title = "Урок, 4 окт",
            date = VocabularyRepositoryImpl.getStartOfDay(),
            isManual = false
        )
        coEvery { sessionDao.getSessionsForDate(any()) } returns listOf(existingSession)

        val session = repository.getOrCreateActiveSession()

        assertEquals(10L, session.id)
        assertEquals("Урок, 4 окт", session.title)
        coVerify(exactly = 0) { sessionDao.insert(any()) }
    }

    @Test
    fun getOrCreateActiveSession_whenNoSessionToday_createsAutomaticSession() = runTest {
        coEvery { sessionDao.getSessionsForDate(any()) } returns emptyList()
        coEvery { sessionDao.insert(any()) } returns 25L

        val session = repository.getOrCreateActiveSession()

        assertEquals(25L, session.id)
        assertTrue(session.title.startsWith("Урок,"))
        coVerify(exactly = 1) { sessionDao.insert(match { !it.isManual }) }
    }

    @Test
    fun createManualSession_createsNewManualSession() = runTest {
        val today = VocabularyRepositoryImpl.getStartOfDay()
        val firstSession = SessionEntity(id = 1L, title = "Урок, 4 окт", date = today, isManual = false)
        coEvery { sessionDao.getSessionsForDate(any()) } returns listOf(firstSession)
        coEvery { sessionDao.insert(any()) } returns 2L

        val manualSession = repository.createManualSession()

        assertEquals(2L, manualSession.id)
        assertTrue(manualSession.title.contains("(2)"))
        coVerify(exactly = 1) { sessionDao.insert(match { it.isManual }) }
    }

    @Test
    fun saveWordTranslation_newWord_insertsWordAndLinksToActiveSession() = runTest {
        val today = VocabularyRepositoryImpl.getStartOfDay()
        val sessionEntity = SessionEntity(id = 5L, title = "Урок, 4 окт", date = today, isManual = false)
        coEvery { sessionDao.getSessionsForDate(any()) } returns listOf(sessionEntity)

        coEvery { wordDao.getByLemmaPosAndPartner("Haus", "noun", "ru") } returns null
        coEvery { wordDao.insert(any()) } returns 101L

        val wordTranslation = WordTranslation(
            lemma = "das Haus",
            pos = "noun",
            meanings = listOf(Meaning(translation = "дом", exampleDe = "Das Haus ist schön", exampleRu = "Дом красивый"))
        )

        val inserted = repository.saveWordTranslation(wordTranslation)

        assertEquals(101L, inserted.id)
        assertEquals("Haus", inserted.lemma)
        assertEquals("das", inserted.article)
        assertEquals("ru", inserted.partnerLanguage)
        assertEquals(5L, inserted.sessionId)
        assertEquals(1, inserted.timesTranslated)
        assertEquals(WordStatus.NEW, inserted.status)

        // Verifies wordDao insert called with proper session and values
        val wordSlot = slot<WordEntryEntity>()
        coVerify(exactly = 1) { wordDao.insert(capture(wordSlot)) }
        assertEquals("Haus", wordSlot.captured.lemma)
        assertEquals("das", wordSlot.captured.article)
        assertEquals("ru", wordSlot.captured.partnerLanguage)
        assertEquals(5L, wordSlot.captured.sessionId)
        assertEquals(1, wordSlot.captured.timesTranslated)

        // Verifies session_words link
        coVerify(exactly = 1) {
            sessionWordDao.insert(match { it.sessionId == 5L && it.wordId == 101L })
        }
    }

    @Test
    fun saveWordTranslation_existingWord_preservesSessionIdAndIncrementsTimesTranslated() = runTest {
        val today = VocabularyRepositoryImpl.getStartOfDay()
        val currentSession = SessionEntity(id = 20L, title = "Урок, 4 окт", date = today, isManual = false)
        coEvery { sessionDao.getSessionsForDate(any()) } returns listOf(currentSession)

        val existingWord = WordEntryEntity(
            id = 50L,
            sessionId = 1L, // Originally added in session 1!
            lemma = "Apfel",
            article = "der",
            pos = "noun",
            meaningsJson = "[]",
            grammarJson = null,
            synonymsJson = "[]",
            collocationsJson = "[]",
            timesTranslated = 3,
            firstSeenAt = 1000L,
            lastSeenAt = 2000L,
            status = WordStatus.LEARNING.name,
            partnerLanguage = "ru"
        )
        coEvery { wordDao.getByLemmaPosAndPartner("Apfel", "noun", "ru") } returns existingWord

        val retranslatedWord = WordTranslation(
            lemma = "Apfel",
            article = "der",
            pos = "noun",
            meanings = listOf(Meaning(translation = "яблоко", exampleDe = "Ein Apfel", exampleRu = "Яблоко")),
            partnerLanguage = "ru"
        )

        val result = repository.saveWordTranslation(retranslatedWord)

        assertEquals(50L, result.id)
        assertEquals(4, result.timesTranslated) // 3 + 1
        assertEquals(WordStatus.LEARNING, result.status) // Kept existing status
        assertEquals("ru", result.partnerLanguage)

        // Verify update on wordDao preserves original sessionId = 1 and increments timesTranslated = 4
        val updateSlot = slot<WordEntryEntity>()
        coVerify(exactly = 1) { wordDao.update(capture(updateSlot)) }
        assertEquals(50L, updateSlot.captured.id)
        assertEquals(1L, updateSlot.captured.sessionId) // Preserved original session id!
        assertEquals(4, updateSlot.captured.timesTranslated)
        assertEquals("ru", updateSlot.captured.partnerLanguage)

        // Verify word is linked to current active session 20 in session_words
        coVerify(exactly = 1) {
            sessionWordDao.insert(match { it.sessionId == 20L && it.wordId == 50L })
        }
    }

    @Test
    fun saveWordTranslation_withPartnerLanguage_differentiatesSameLemma() = runTest {
        val today = VocabularyRepositoryImpl.getStartOfDay()
        val currentSession = SessionEntity(id = 12L, title = "Урок, 4 окт", date = today, isManual = false)
        coEvery { sessionDao.getSessionsForDate(any()) } returns listOf(currentSession)

        // Look up for "uk" returns null even if "ru" might exist
        coEvery { wordDao.getByLemmaPosAndPartner("Buch", "noun", "uk") } returns null
        coEvery { wordDao.insert(any()) } returns 200L

        val ukrainianTranslation = WordTranslation(
            lemma = "das Buch",
            pos = "noun",
            meanings = listOf(Meaning(translation = "книга")),
            partnerLanguage = "uk"
        )

        val result = repository.saveWordTranslation(ukrainianTranslation)

        assertEquals(200L, result.id)
        assertEquals("Buch", result.lemma)
        assertEquals("uk", result.partnerLanguage)

        val insertSlot = slot<WordEntryEntity>()
        coVerify(exactly = 1) { wordDao.insert(capture(insertSlot)) }
        assertEquals("Buch", insertSlot.captured.lemma)
        assertEquals("uk", insertSlot.captured.partnerLanguage)
    }

    @Test
    fun findWord_whenExists_returnsWordEntry() = runTest {
        val existingEntity = WordEntryEntity(
            id = 70L,
            sessionId = 1L,
            lemma = "Hund",
            article = "der",
            pos = "noun",
            meaningsJson = "[{\"translation\":\"собака\"}]",
            firstSeenAt = 1000L,
            lastSeenAt = 2000L,
            partnerLanguage = "ru"
        )
        coEvery { wordDao.getByLemmaPosAndPartner("Hund", "noun", "ru") } returns existingEntity

        val found = repository.findWord("der Hund", "noun", "ru")

        assertNotNull(found)
        assertEquals(70L, found?.id)
        assertEquals("Hund", found?.lemma)
        assertEquals("der", found?.article)
        assertEquals("ru", found?.partnerLanguage)
        assertEquals("собака", found?.meanings?.firstOrNull()?.translation)
    }

    @Test
    fun findWord_whenDoesNotExist_returnsNull() = runTest {
        coEvery { wordDao.getByLemmaPosAndPartner("Katze", "noun", "fa") } returns null

        val found = repository.findWord("die Katze", "noun", "fa")

        assertEquals(null, found)
    }

    @Test
    fun saveGermanEquivalent_extractsArticleAndLemma_doesNotStoreRussianAsLemma() = runTest {
        val today = VocabularyRepositoryImpl.getStartOfDay()
        val currentSession = SessionEntity(id = 8L, title = "Урок, 4 окт", date = today, isManual = false)
        coEvery { sessionDao.getSessionsForDate(any()) } returns listOf(currentSession)

        coEvery { wordDao.getByLemmaPosAndPartner("Schloss", "noun", "ru") } returns null
        coEvery { wordDao.insert(any()) } returns 77L

        val result = repository.saveGermanEquivalent(
            germanLemmaWithArticle = "das Schloss",
            russianTranslation = "замок",
            pos = "noun"
        )

        assertEquals("Schloss", result.lemma)
        assertEquals("das", result.article)
        assertEquals("замок", result.meanings.firstOrNull()?.translation)
        assertEquals("ru", result.partnerLanguage)

        val slot = slot<WordEntryEntity>()
        coVerify(exactly = 1) { wordDao.insert(capture(slot)) }
        assertEquals("Schloss", slot.captured.lemma)
        assertEquals("das", slot.captured.article)
        assertEquals("ru", slot.captured.partnerLanguage)
    }

    @Test
    fun updateSelectedMeaning_delegatesToWordDao() = runTest {
        repository.updateSelectedMeaning(15L, 2)
        coVerify(exactly = 1) { wordDao.updateSelectedMeaningIndex(15L, 2) }
    }

    @Test
    fun updateWordStatus_delegatesToWordDao() = runTest {
        repository.updateWordStatus(15L, WordStatus.KNOWN)
        coVerify(exactly = 1) { wordDao.updateStatus(15L, WordStatus.KNOWN.name) }
    }

    @Test
    fun deleteWord_and_restoreWord() = runTest {
        val existingEntity = WordEntryEntity(
            id = 88L,
            sessionId = 3L,
            lemma = "Buch",
            article = "das",
            pos = "noun",
            meaningsJson = "[]",
            grammarJson = null,
            synonymsJson = "[]",
            collocationsJson = "[]",
            timesTranslated = 2,
            firstSeenAt = 100L,
            lastSeenAt = 200L,
            status = WordStatus.NEW.name,
            partnerLanguage = "ru"
        )
        coEvery { wordDao.getById(88L) } returns existingEntity

        val deleted = repository.deleteWord(88L)
        assertNotNull(deleted)
        assertEquals(88L, deleted?.id)
        assertEquals("ru", deleted?.partnerLanguage)
        coVerify(exactly = 1) { wordDao.deleteById(88L) }

        // Restore
        repository.restoreWord(deleted!!)
        coVerify(exactly = 1) { wordDao.insertOrReplace(match { it.id == 88L && it.lemma == "Buch" && it.partnerLanguage == "ru" }) }
        coVerify(exactly = 1) { sessionWordDao.insert(match { it.sessionId == 3L && it.wordId == 88L }) }
    }

    @Test
    fun saveTextTranslation_insertsEntity() = runTest {
        repository.saveTextTranslation(
            source = "Wie geht es dir?",
            result = "Как твои дела?",
            direction = "DE_RU"
        )

        val slot = slot<TextTranslationEntity>()
        coVerify(exactly = 1) { textTranslationDao.insert(capture(slot)) }
        assertEquals("Wie geht es dir?", slot.captured.source)
        assertEquals("Как твои дела?", slot.captured.result)
        assertEquals("DE_RU", slot.captured.direction)
        assertTrue(slot.captured.createdAt > 0)
    }
}
