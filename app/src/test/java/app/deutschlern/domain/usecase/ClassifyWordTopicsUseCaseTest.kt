package app.deutschlern.domain.usecase

import app.deutschlern.domain.model.Meaning
import app.deutschlern.domain.model.WordEntry
import app.deutschlern.domain.provider.StructuredGenerationProvider
import app.deutschlern.domain.provider.TranslationProvider
import app.deutschlern.domain.provider.TranslationProviderRegistry
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.repository.UserSettings
import app.deutschlern.domain.repository.VocabularyRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClassifyWordTopicsUseCaseTest {

    private val provider = mockk<TranslationProvider>(moreInterfaces = arrayOf(StructuredGenerationProvider::class))
    private val structured get() = provider as StructuredGenerationProvider
    private val registry = mockk<TranslationProviderRegistry>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val vocabularyRepository = mockk<VocabularyRepository>(relaxed = true)

    private val useCase = ClassifyWordTopicsUseCase(registry, settingsRepository, vocabularyRepository)

    private val kuendigen = WordEntry(id = 1L, sessionId = 1L, lemma = "kündigen", pos = "verb", meanings = listOf(Meaning("увольняться")))
    private val apfel = WordEntry(id = 2L, sessionId = 1L, lemma = "Apfel", article = "der", pos = "noun", meanings = listOf(Meaning("яблоко")))

    @Before
    fun setUp() {
        coEvery { settingsRepository.getSettings() } returns UserSettings()
        every { registry.getProvider(any()) } returns provider
        every { structured.id } returns "gemini"
        coEvery { vocabularyRepository.getWordsWithoutTopics(any()) } returnsMany listOf(listOf(kuendigen, apfel), emptyList())
    }

    @Test
    fun storesOnlyAllowedTopics_andEmptyListForSkippedWords() = runTest {
        coEvery { structured.generateStructured(any(), any(), any(), any()) } returns Result.success(
            """{"items":[{"id":1,"topics":["work","bureaucracy","finance"]},{"id":99,"topics":["food_shopping"]}]}"""
        )

        val result = useCase()

        assertEquals(2, result.getOrNull())
        // At most two topics are kept, in the model's order
        coVerify { vocabularyRepository.updateWordTopics(1L, listOf("work", "bureaucracy")) }
        // A word the model ignored is marked as classified so it is not sent again forever
        coVerify { vocabularyRepository.updateWordTopics(2L, emptyList()) }
    }

    @Test
    fun unknownTopicKeys_areDropped() = runTest {
        coEvery { structured.generateStructured(any(), any(), any(), any()) } returns Result.success(
            """{"items":[{"id":1,"topics":["sports","work"]},{"id":2,"topics":["food_shopping"]}]}"""
        )

        useCase()

        coVerify { vocabularyRepository.updateWordTopics(1L, listOf("work")) }
        coVerify { vocabularyRepository.updateWordTopics(2L, listOf("food_shopping")) }
    }

    @Test
    fun providerFailure_leavesWordsUnclassified() = runTest {
        coEvery { structured.generateStructured(any(), any(), any(), any()) } returns Result.failure(IllegalStateException("no key"))

        val result = useCase()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { vocabularyRepository.updateWordTopics(any(), any()) }
    }
}
