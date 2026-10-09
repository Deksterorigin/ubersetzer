package app.deutschlern.data.repository

import app.deutschlern.data.local.dao.ReviewDao
import app.deutschlern.data.local.dao.SessionDao
import app.deutschlern.data.local.dao.SessionWordDao
import app.deutschlern.data.local.dao.WordDao
import app.deutschlern.data.local.entity.ReviewStateEntity
import app.deutschlern.data.local.entity.SessionEntity
import app.deutschlern.data.local.entity.SessionWordEntity
import app.deutschlern.data.local.entity.WordEntryEntity
import app.deutschlern.domain.model.WordStatus
import app.deutschlern.domain.sm2.Sm2Grade
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReviewRepositoryImplTest {

    private val wordDao = mockk<WordDao>(relaxed = true)
    private val reviewDao = mockk<ReviewDao>(relaxed = true)
    private val sessionDao = mockk<SessionDao>(relaxed = true)
    private val sessionWordDao = mockk<SessionWordDao>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var repository: ReviewRepositoryImpl

    @Before
    fun setUp() {
        repository = ReviewRepositoryImpl(
            wordDao = wordDao,
            reviewDao = reviewDao,
            sessionDao = sessionDao,
            sessionWordDao = sessionWordDao,
            json = json,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun getDueReviewCards_filtersDueCards_andPrioritizesWeakWords() = runTest(testDispatcher) {
        val now = 100000L

        // Word 1: translated 1 time, due at 50000L (due)
        val word1 = createWordEntity(id = 1L, lemma = "Haus", timesTranslated = 1)
        val review1 = ReviewStateEntity(wordId = 1L, easeFactor = 2.5, intervalDays = 1, repetitions = 1, dueAt = 50000L)

        // Word 2: translated 5 times (WEAK word!), due at 80000L (due)
        val word2 = createWordEntity(id = 2L, lemma = "Schloss", timesTranslated = 5)
        val review2 = ReviewStateEntity(wordId = 2L, easeFactor = 2.3, intervalDays = 1, repetitions = 1, dueAt = 80000L)

        // Word 3: translated 3 times, due at 200000L (NOT due yet)
        val word3 = createWordEntity(id = 3L, lemma = "Katze", timesTranslated = 3)
        val review3 = ReviewStateEntity(wordId = 3L, easeFactor = 2.5, intervalDays = 10, repetitions = 2, dueAt = 200000L)

        coEvery { wordDao.getAllWords() } returns flowOf(listOf(word1, word2, word3))
        coEvery { reviewDao.getAllReviewStates() } returns flowOf(listOf(review1, review2, review3))

        val dueCards = repository.getDueReviewCards(now).first()

        // Only Word 1 and Word 2 are due
        assertEquals(2, dueCards.size)
        // Word 2 has timesTranslated = 5, so it must come FIRST (prioritized weak word)
        assertEquals("Schloss", dueCards[0].word.lemma)
        assertEquals("Haus", dueCards[1].word.lemma)
    }

    @Test
    fun getLastLessonReviewCards_pullsWordsFromLatestSession_ignoringDueDate() = runTest(testDispatcher) {
        val session1 = SessionEntity(id = 1L, title = "Урок 1", date = 1000L, isManual = false)
        val session2 = SessionEntity(id = 2L, title = "Урок 2", date = 2000L, isManual = false) // Latest!

        val wordA = createWordEntity(id = 10L, lemma = "Apfel", timesTranslated = 1)
        val wordB = createWordEntity(id = 20L, lemma = "Birne", timesTranslated = 4) // In session 2, weak
        val wordC = createWordEntity(id = 30L, lemma = "Kirsche", timesTranslated = 2) // In session 2

        coEvery { sessionDao.getAllSessions() } returns flowOf(listOf(session1, session2))
        coEvery { wordDao.getAllWords() } returns flowOf(listOf(wordA, wordB, wordC))
        coEvery { sessionWordDao.getAll() } returns flowOf(
            listOf(
                SessionWordEntity(sessionId = 1L, wordId = 10L, translatedAt = 1000L),
                SessionWordEntity(sessionId = 2L, wordId = 20L, translatedAt = 2010L),
                SessionWordEntity(sessionId = 2L, wordId = 30L, translatedAt = 2020L)
            )
        )
        coEvery { reviewDao.getAllReviewStates() } returns flowOf(emptyList())

        val lastLessonCards = repository.getLastLessonReviewCards().first()

        assertEquals(2, lastLessonCards.size)
        // Word B (timesTranslated = 4) before Word C (timesTranslated = 2)
        assertEquals("Birne", lastLessonCards[0].word.lemma)
        assertEquals("Kirsche", lastLessonCards[1].word.lemma)
    }

    @Test
    fun reviewCard_updatesReviewState_andUpdatesWordStatus() = runTest(testDispatcher) {
        val now = 1000L
        val wordEntity = createWordEntity(id = 15L, lemma = "Tisch", timesTranslated = 1, status = "NEW")
        coEvery { wordDao.getById(15L) } returns wordEntity
        coEvery { reviewDao.getByWordId(15L) } returns null // Fresh card

        val updatedCard = repository.reviewCard(15L, Sm2Grade.GOOD, now)

        assertEquals(1, updatedCard.sm2State.repetitions)
        assertEquals(1, updatedCard.sm2State.intervalDays)
        assertEquals(WordStatus.LEARNING, updatedCard.word.status)

        val slot = slot<ReviewStateEntity>()
        coVerify(exactly = 1) { reviewDao.insertOrReplace(capture(slot)) }
        assertEquals(15L, slot.captured.wordId)
        assertEquals(1, slot.captured.repetitions)
        assertEquals(1, slot.captured.intervalDays)

        coVerify(exactly = 1) { wordDao.updateStatus(15L, WordStatus.LEARNING.name) }
    }

    private fun createWordEntity(
        id: Long,
        lemma: String,
        timesTranslated: Int = 1,
        status: String = "NEW"
    ): WordEntryEntity {
        return WordEntryEntity(
            id = id,
            lemma = lemma,
            article = "der",
            pos = "noun",
            plural = null,
            genitive = null,
            grammarJson = null,
            meaningsJson = "[{\"translation\":\"тест\",\"example_de\":\"bsp\",\"example_ru\":\"пример\"}]",
            synonymsJson = "[]",
            collocationsJson = "[]",
            selectedMeaningIndex = 0,
            timesTranslated = timesTranslated,
            firstSeenAt = 100L,
            lastSeenAt = 200L,
            sessionId = 1L,
            status = status
        )
    }
}
