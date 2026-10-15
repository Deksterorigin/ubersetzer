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
}
