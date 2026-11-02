package app.deutschlern.data.backup

import android.content.Context
import app.deutschlern.data.local.ImmediateTransactionRunner
import app.deutschlern.data.local.dao.ReviewDao
import app.deutschlern.data.local.dao.SessionDao
import app.deutschlern.data.local.dao.SessionWordDao
import app.deutschlern.data.local.dao.WordDao
import app.deutschlern.data.local.entity.SessionEntity
import app.deutschlern.data.local.entity.WordEntryEntity
import app.deutschlern.data.local.security.SecureKeyStorage
import app.deutschlern.di.NetworkModule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupManagerTest {

    private val wordDao = mockk<WordDao>(relaxed = true)
    private val sessionDao = mockk<SessionDao>(relaxed = true)
    private val sessionWordDao = mockk<SessionWordDao>(relaxed = true)
    private val reviewDao = mockk<ReviewDao>(relaxed = true)
    private val secureKeyStorage = mockk<SecureKeyStorage>(relaxed = true)
    private val json = NetworkModule.provideJson()

    private val manager = BackupManager(
        context = mockk<Context>(relaxed = true),
        wordDao = wordDao,
        sessionDao = sessionDao,
        sessionWordDao = sessionWordDao,
        reviewDao = reviewDao,
        secureKeyStorage = secureKeyStorage,
        transactionRunner = ImmediateTransactionRunner,
        json = json,
        ioDispatcher = UnconfinedTestDispatcher()
    )

    private fun backupWord(id: Long, lemma: String, difficult: Boolean = false) = BackupWord(
        id = id, lemma = lemma, pos = "noun", meaningsJson = "[]",
        firstSeenAt = 1L, lastSeenAt = 2L, sessionId = 7L, isDifficult = difficult
    )

    private val backup = BackupFile(
        createdAt = 100L,
        sessions = listOf(BackupSession(id = 7L, title = "Урок, 1 нояб", date = 50L, isManual = false)),
        words = listOf(backupWord(1L, "Apfel"), backupWord(2L, "Haus", difficult = true)),
        sessionWords = listOf(BackupSessionWord(7L, 1L, 3L), BackupSessionWord(7L, 2L, 4L)),
        reviews = listOf(BackupReview(wordId = 1L, easeFactor = 2.1, intervalDays = 6, repetitions = 3, dueAt = 999L)),
        apiKeys = mapOf("gemini" to "key-a", "groq" to "key-b")
    )

    @Test
    fun restore_addsMissingWords_keepsExisting_andRemapsIds() = runTest {
        coEvery { sessionDao.getByDateAndTitle(50L, "Урок, 1 нояб") } returns null
        coEvery { sessionDao.insert(any()) } returns 70L
        // "Haus" already exists locally (id 20, not marked difficult); "Apfel" is new
        coEvery { wordDao.getByLemmaEquivalentPosAndPartner("Haus", any(), any(), any()) } returns
            WordEntryEntity(id = 20L, lemma = "Haus", pos = "noun", meaningsJson = "[]", firstSeenAt = 1L, lastSeenAt = 1L, sessionId = 1L)
        coEvery { wordDao.getByLemmaEquivalentPosAndPartner("Apfel", any(), any(), any()) } returns null
        coEvery { wordDao.insert(any()) } returns 10L
        coEvery { sessionWordDao.get(any(), any()) } returns null
        coEvery { secureKeyStorage.getKey("gemini") } returns "already-set"
        coEvery { secureKeyStorage.getKey("groq") } returns null

        val result = manager.restore(json.encodeToString(BackupFile.serializer(), backup))

        assertEquals(RestoreResult(wordsAdded = 1, wordsAlreadyPresent = 1, keysRestored = 1), result)
        coVerify { wordDao.insert(match { it.id == 0L && it.lemma == "Apfel" && it.sessionId == 70L }) }
        coVerify { reviewDao.insertOrReplace(match { it.wordId == 10L && it.repetitions == 3 }) }
        coVerify { wordDao.updateDifficult(20L, true) }
        coVerify { sessionWordDao.insert(match { it.sessionId == 70L && it.wordId == 10L }) }
        coVerify { sessionWordDao.insert(match { it.sessionId == 70L && it.wordId == 20L }) }
        // A key entered after reinstalling is never overwritten by the backup
        coVerify(exactly = 0) { secureKeyStorage.saveKey("gemini", any()) }
        coVerify { secureKeyStorage.saveKey("groq", "key-b") }
    }

    @Test
    fun restore_reusesExistingLesson() = runTest {
        coEvery { sessionDao.getByDateAndTitle(50L, "Урок, 1 нояб") } returns
            SessionEntity(id = 5L, title = "Урок, 1 нояб", date = 50L)
        coEvery { wordDao.getByLemmaEquivalentPosAndPartner(any(), any(), any(), any()) } returns null
        coEvery { wordDao.insert(any()) } returns 10L

        manager.restore(json.encodeToString(BackupFile.serializer(), backup))

        coVerify(exactly = 0) { sessionDao.insert(any()) }
        coVerify { wordDao.insert(match { it.sessionId == 5L }) }
    }

    @Test
    fun restore_rejectsForeignJson() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { manager.restore("""{"format":"other","createdAt":1}""") }
        }
    }
}
