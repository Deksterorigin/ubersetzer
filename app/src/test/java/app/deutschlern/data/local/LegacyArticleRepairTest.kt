package app.deutschlern.data.local

import android.util.Log
import app.deutschlern.data.local.dao.WordDao
import app.deutschlern.data.local.entity.WordEntryEntity
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.repository.UserSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LegacyArticleRepairTest {

    private val wordDao: WordDao = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private lateinit var repairManager: LegacyArticleRepairManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<Throwable>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        repairManager = LegacyArticleRepairManager(wordDao, settingsRepository)
    }

    @Test
    fun repairIfNeeded_alreadyRepaired_skipsAndReturnsZero() = runTest {
        coEvery { settingsRepository.getSettings() } returns UserSettings(
            hasRepairedNonNounArticles = true
        )

        val repairedCount = repairManager.repairIfNeeded()
        assertEquals(0, repairedCount)
        coVerify(exactly = 0) { wordDao.getAllWordsSync() }
        coVerify(exactly = 0) { wordDao.update(any()) }
    }

    @Test
    fun repairIfNeeded_nullsArticlesOnlyForExplicitNonNouns_andBacksUpToDataStore() = runTest {
        coEvery { settingsRepository.getSettings() } returns UserSettings(
            hasRepairedNonNounArticles = false
        )

        val word1 = WordEntryEntity(
            id = 1L,
            sessionId = 1L,
            lemma = "Tisch",
            article = "der",
            pos = "noun",
            meaningsJson = "[]",
            firstSeenAt = 1000L,
            lastSeenAt = 1000L
        )
        val word2 = WordEntryEntity(
            id = 2L,
            sessionId = 1L,
            lemma = "Kuchen",
            article = "der",
            pos = "n", // Non-canonical noun, must be preserved!
            meaningsJson = "[]",
            firstSeenAt = 1000L,
            lastSeenAt = 1000L
        )
        val word3 = WordEntryEntity(
            id = 3L,
            sessionId = 1L,
            lemma = "ausüblich",
            article = "die", // Adjective with article, must be nulled!
            pos = "adjective",
            meaningsJson = "[]",
            firstSeenAt = 1000L,
            lastSeenAt = 1000L
        )
        val word4 = WordEntryEntity(
            id = 4L,
            sessionId = 1L,
            lemma = "erwähnen",
            article = "die", // Verb with article, must be nulled!
            pos = "verb",
            meaningsJson = "[]",
            firstSeenAt = 1000L,
            lastSeenAt = 1000L
        )
        val word5 = WordEntryEntity(
            id = 5L,
            sessionId = 1L,
            lemma = "xyz",
            article = "das", // Unknown POS, must be left untouched!
            pos = "unknown_custom_pos",
            meaningsJson = "[]",
            firstSeenAt = 1000L,
            lastSeenAt = 1000L
        )

        coEvery { wordDao.getAllWordsSync() } returns listOf(word1, word2, word3, word4, word5)

        val capturedUpdates = mutableListOf<WordEntryEntity>()
        coEvery { wordDao.update(capture(capturedUpdates)) } returns Unit

        val backupJsonSlot = slot<String>()
        coEvery { settingsRepository.markArticlesRepaired(capture(backupJsonSlot)) } returns Unit

        val repairedCount = repairManager.repairIfNeeded()

        assertEquals(2, repairedCount)
        assertEquals(2, capturedUpdates.size)

        // Verify updated entities have article = null
        assertTrue(capturedUpdates.any { it.id == 3L && it.article == null && it.lemma == "ausüblich" })
        assertTrue(capturedUpdates.any { it.id == 4L && it.article == null && it.lemma == "erwähnen" })

        // Verify backup JSON content
        val records = Json.decodeFromString<List<RepairedArticleBackupRecord>>(backupJsonSlot.captured)
        assertEquals(2, records.size)
        assertEquals(3L, records[0].id)
        assertEquals("ausüblich", records[0].lemma)
        assertEquals("die", records[0].oldArticle)
        assertEquals(4L, records[1].id)
        assertEquals("erwähnen", records[1].lemma)
        assertEquals("die", records[1].oldArticle)

        coVerify(exactly = 1) { settingsRepository.markArticlesRepaired(any()) }
    }

    @Test
    fun normalizePosIfNeeded_alreadyNormalized_skipsAndReturnsZero() = runTest {
        coEvery { settingsRepository.getSettings() } returns UserSettings(
            hasNormalizedPosInDb = true
        )

        val count = repairManager.normalizePosIfNeeded()
        assertEquals(0, count)
        coVerify(exactly = 0) { wordDao.getAllWordsSync() }
    }

    @Test
    fun normalizePosIfNeeded_normalizesLegacyVariants_andBacksUpToDataStore() = runTest {
        coEvery { settingsRepository.getSettings() } returns UserSettings(
            hasNormalizedPosInDb = false
        )

        val word1 = WordEntryEntity(
            id = 5L, sessionId = 1L, lemma = "Laufenden", article = "der", pos = "n",
            meaningsJson = "[]", firstSeenAt = 1000L, lastSeenAt = 1000L, partnerLanguage = "ru"
        )
        val word2 = WordEntryEntity(
            id = 8L, sessionId = 1L, lemma = "auf dem Laufenden", article = null, pos = "adverbial_phrase",
            meaningsJson = "[]", firstSeenAt = 1000L, lastSeenAt = 1000L, partnerLanguage = "ru"
        )
        val word3 = WordEntryEntity(
            id = 32L, sessionId = 1L, lemma = "Bescheid", article = "der", pos = "nomen",
            meaningsJson = "[]", firstSeenAt = 1000L, lastSeenAt = 1000L, partnerLanguage = "ru"
        )
        val word4 = WordEntryEntity(
            id = 42L, sessionId = 1L, lemma = "unaufmerksam", article = null, pos = "adjektiv",
            meaningsJson = "[]", firstSeenAt = 1000L, lastSeenAt = 1000L, partnerLanguage = "ru"
        )
        val word5 = WordEntryEntity(
            id = 50L, sessionId = 1L, lemma = "oft", article = null, pos = "adv",
            meaningsJson = "[]", firstSeenAt = 1000L, lastSeenAt = 1000L, partnerLanguage = "ru"
        )
        val word6 = WordEntryEntity(
            id = 60L, sessionId = 1L, lemma = "Haus", article = "das", pos = "noun", // Already canonical
            meaningsJson = "[]", firstSeenAt = 1000L, lastSeenAt = 1000L, partnerLanguage = "ru"
        )

        coEvery { wordDao.getAllWordsSync() } returns listOf(word1, word2, word3, word4, word5, word6)

        val capturedUpdates = mutableListOf<WordEntryEntity>()
        coEvery { wordDao.update(capture(capturedUpdates)) } returns Unit

        val backupSlot = slot<String>()
        coEvery { settingsRepository.markPosNormalized(capture(backupSlot)) } returns Unit

        val count = repairManager.normalizePosIfNeeded()

        assertEquals(5, count)
        assertEquals(5, capturedUpdates.size)

        assertTrue(capturedUpdates.any { it.id == 5L && it.pos == "noun" })
        assertTrue(capturedUpdates.any { it.id == 8L && it.pos == "phrase" })
        assertTrue(capturedUpdates.any { it.id == 32L && it.pos == "noun" })
        assertTrue(capturedUpdates.any { it.id == 42L && it.pos == "adjective" })
        assertTrue(capturedUpdates.any { it.id == 50L && it.pos == "adverb" })

        val records = Json.decodeFromString<List<RepairedPosBackupRecord>>(backupSlot.captured)
        assertEquals(5, records.size)
        assertEquals(5L, records[0].id)
        assertEquals("n", records[0].oldPos)
        assertEquals("noun", records[0].newPos)
    }

    @Test
    fun normalizePosIfNeeded_whenCollisionExists_skipsRowAndDoesNotMerge() = runTest {
        coEvery { settingsRepository.getSettings() } returns UserSettings(
            hasNormalizedPosInDb = false
        )

        // Word 1 already has canonical pos = "noun"
        val canonicalWord = WordEntryEntity(
            id = 10L, sessionId = 1L, lemma = "Kündigung", article = "die", pos = "noun",
            meaningsJson = "[]", firstSeenAt = 1000L, lastSeenAt = 1000L, partnerLanguage = "ru"
        )
        // Word 2 has legacy pos = "nomen" and same partnerLanguage "ru" -> collision!
        val legacyWord = WordEntryEntity(
            id = 40L, sessionId = 1L, lemma = "Kündigung", article = "die", pos = "nomen",
            meaningsJson = "[]", firstSeenAt = 1000L, lastSeenAt = 1000L, partnerLanguage = "ru"
        )

        coEvery { wordDao.getAllWordsSync() } returns listOf(canonicalWord, legacyWord)

        val capturedUpdates = mutableListOf<WordEntryEntity>()
        coEvery { wordDao.update(capture(capturedUpdates)) } returns Unit

        val count = repairManager.normalizePosIfNeeded()

        // Must skip the collision row, update nothing
        assertEquals(0, count)
        assertEquals(0, capturedUpdates.size)
    }

    @Test
    fun restorePos_restoresOriginalPosFromBackupJson() = runTest {
        val backupRecords = listOf(
            RepairedPosBackupRecord(id = 5L, lemma = "Laufenden", oldPos = "n", newPos = "noun", partnerLanguage = "ru"),
            RepairedPosBackupRecord(id = 8L, lemma = "auf dem Laufenden", oldPos = "adverbial_phrase", newPos = "phrase", partnerLanguage = "ru")
        )
        val backupJson = Json.encodeToString(backupRecords)

        coEvery { settingsRepository.getSettings() } returns UserSettings(
            repairedPosBackupJson = backupJson
        )

        val word5 = WordEntryEntity(id = 5L, sessionId = 1L, lemma = "Laufenden", article = "der", pos = "noun", meaningsJson = "[]", firstSeenAt = 1000L, lastSeenAt = 1000L)
        val word8 = WordEntryEntity(id = 8L, sessionId = 1L, lemma = "auf dem Laufenden", article = null, pos = "phrase", meaningsJson = "[]", firstSeenAt = 1000L, lastSeenAt = 1000L)

        coEvery { wordDao.getById(5L) } returns word5
        coEvery { wordDao.getById(8L) } returns word8

        val capturedUpdates = mutableListOf<WordEntryEntity>()
        coEvery { wordDao.update(capture(capturedUpdates)) } returns Unit

        val restoredCount = repairManager.restorePos()

        assertEquals(2, restoredCount)
        assertTrue(capturedUpdates.any { it.id == 5L && it.pos == "n" })
        assertTrue(capturedUpdates.any { it.id == 8L && it.pos == "adverbial_phrase" })
        coVerify(exactly = 1) { settingsRepository.resetPosNormalizedFlag() }
    }

    @Test
    fun restoreArticles_restoresOriginalArticlesFromBackupJson() = runTest {
        val backupRecords = listOf(
            RepairedArticleBackupRecord(id = 3L, lemma = "ausüblich", pos = "adjective", oldArticle = "die"),
            RepairedArticleBackupRecord(id = 4L, lemma = "erwähnen", pos = "verb", oldArticle = "die")
        )
        val backupJson = Json.encodeToString(backupRecords)

        coEvery { settingsRepository.getSettings() } returns UserSettings(
            repairedArticlesBackupJson = backupJson
        )

        val word3 = WordEntryEntity(id = 3L, sessionId = 1L, lemma = "ausüblich", article = null, pos = "adjective", meaningsJson = "[]", firstSeenAt = 1000L, lastSeenAt = 1000L)
        val word4 = WordEntryEntity(id = 4L, sessionId = 1L, lemma = "erwähnen", article = null, pos = "verb", meaningsJson = "[]", firstSeenAt = 1000L, lastSeenAt = 1000L)

        coEvery { wordDao.getById(3L) } returns word3
        coEvery { wordDao.getById(4L) } returns word4

        val capturedUpdates = mutableListOf<WordEntryEntity>()
        coEvery { wordDao.update(capture(capturedUpdates)) } returns Unit

        val restoredCount = repairManager.restoreArticles()

        assertEquals(2, restoredCount)
        assertTrue(capturedUpdates.any { it.id == 3L && it.article == "die" })
        assertTrue(capturedUpdates.any { it.id == 4L && it.article == "die" })
        coVerify(exactly = 1) { settingsRepository.resetArticlesRepairedFlag() }
    }
}
