package app.deutschlern.data.repository

import app.deutschlern.data.local.dao.CacheDao
import app.deutschlern.data.local.entity.CacheEntryEntity
import app.deutschlern.data.local.security.SecureKeyStorage
import app.deutschlern.data.remote.provider.GeminiProvider
import app.deutschlern.domain.model.Meaning
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.WordTranslation
import app.deutschlern.domain.provider.ProviderCapabilities
import app.deutschlern.domain.provider.ProviderError
import app.deutschlern.domain.provider.TranslationProvider
import app.deutschlern.domain.provider.TranslationProviderRegistry
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.repository.UserSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationRepositoryImplTest {

    private val registry = mockk<TranslationProviderRegistry>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val secureKeyStorage = mockk<SecureKeyStorage>()
    private val cacheDao = mockk<CacheDao>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    private val testDispatcher = UnconfinedTestDispatcher()

    private val geminiProvider = mockk<GeminiProvider>()
    private val groqProvider = mockk<TranslationProvider>()

    private lateinit var repository: TranslationRepositoryImpl

    @Before
    fun setUp() {
        coEvery { geminiProvider.id } returns "gemini"
        coEvery { geminiProvider.displayName } returns "Google Gemini"
        coEvery { geminiProvider.capabilities } returns ProviderCapabilities(supportsWordMode = true, supportsTextMode = true)

        coEvery { groqProvider.id } returns "groq"
        coEvery { groqProvider.displayName } returns "Groq"
        coEvery { groqProvider.capabilities } returns ProviderCapabilities(supportsWordMode = true, supportsTextMode = true)

        coEvery { registry.geminiProvider } returns geminiProvider
        coEvery { registry.getProvider("gemini") } returns geminiProvider
        coEvery { registry.getProvider("groq") } returns groqProvider

        coEvery { settingsRepository.getSettings() } returns UserSettings(
            selectedWordProviderId = "gemini",
            selectedTextProviderId = "gemini",
            isAutoFallbackEnabled = true,
            fallbackOrder = listOf("gemini", "groq"),
            useCrossModelCache = true
        )

        coEvery { secureKeyStorage.getKey("gemini") } returns "gemini-key"
        coEvery { secureKeyStorage.getKey("groq") } returns "groq-key"

        repository = TranslationRepositoryImpl(
            registry = registry,
            settingsRepository = settingsRepository,
            secureKeyStorage = secureKeyStorage,
            cacheDao = cacheDao,
            json = json,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun translateWord_exactCacheHit_returnsCachedWithoutCallingProvider() = runTest {
        val word = WordTranslation(
            lemma = "Haus",
            article = "das",
            pos = "noun",
            meanings = listOf(Meaning(translation = "дом", exampleDe = "Haus", exampleRu = "Дом"))
        )
        val cached = CacheEntryEntity(
            cacheKey = "exact_key",
            semanticKey = "sem_key",
            responseJson = json.encodeToString(word),
            providerId = "Google Gemini",
            modelName = "gemini-2.5-flash",
            createdAt = System.currentTimeMillis() - 1000
        )
        coEvery { cacheDao.get(any()) } returns cached

        val result = repository.translateWord("Haus", TranslationDirection.DE_RU)

        assertTrue(result.isSuccess)
        val translationResult = result.getOrThrow()
        assertEquals("Haus", translationResult.data.lemma)
        coVerify(exactly = 0) { geminiProvider.translateWord(any(), any(), any(), any()) }
    }

    @Test
    fun translateWord_crossModelCacheFallback_whenEnabled_returnsCached() = runTest {
        val word = WordTranslation(
            lemma = "Buch",
            article = "das",
            pos = "noun",
            meanings = listOf(Meaning(translation = "книга", exampleDe = "Buch", exampleRu = "Книга"))
        )
        val cached = CacheEntryEntity(
            cacheKey = "other_model_key",
            semanticKey = "sem_key",
            responseJson = json.encodeToString(word),
            providerId = "Groq",
            modelName = "llama-3.3-70b-versatile",
            createdAt = System.currentTimeMillis() - 1000
        )
        // Exact miss
        coEvery { cacheDao.get(any()) } returns null
        // Semantic hit
        coEvery { cacheDao.getBySemanticKey(any()) } returns cached

        val result = repository.translateWord("Buch", TranslationDirection.DE_RU)

        assertTrue(result.isSuccess)
        val translationResult = result.getOrThrow()
        assertEquals("Buch", translationResult.data.lemma)
        assertTrue(translationResult.modelAttribution.contains("Groq"))
        coVerify(exactly = 0) { geminiProvider.translateWord(any(), any(), any(), any()) }
    }

    @Test
    fun translateWord_bypassCacheTrue_ignoresCacheAndCallsProvider() = runTest {
        val cached = CacheEntryEntity(
            cacheKey = "exact_key",
            semanticKey = "sem_key",
            responseJson = "{}",
            createdAt = System.currentTimeMillis() - 1000
        )
        coEvery { cacheDao.get(any()) } returns cached

        val freshWord = WordTranslation(
            lemma = "Auto",
            article = "das",
            pos = "noun",
            meanings = listOf(Meaning(translation = "автомобиль", exampleDe = "Auto", exampleRu = "Автомобиль"))
        )
        coEvery { geminiProvider.translateWord("Auto", TranslationDirection.DE_RU, null, any()) } returns Result.success(freshWord)

        val result = repository.translateWord("Auto", TranslationDirection.DE_RU, bypassCache = true)

        assertTrue(result.isSuccess)
        assertEquals("Auto", result.getOrThrow().data.lemma)
        coVerify(exactly = 1) { geminiProvider.translateWord("Auto", TranslationDirection.DE_RU, null, any()) }
    }

    @Test
    fun translateWord_primaryFails429_fallsBackToNextProvider() = runTest {
        coEvery { cacheDao.get(any()) } returns null
        coEvery { cacheDao.getBySemanticKey(any()) } returns null

        // Primary (Gemini) hits 429
        coEvery { geminiProvider.translateWord("Tisch", TranslationDirection.DE_RU, null, any()) } returns Result.failure(
            ProviderError.RateLimitExceeded("Google Gemini", 30)
        )

        // Fallback (Groq) succeeds
        val fallbackWord = WordTranslation(
            lemma = "Tisch",
            article = "der",
            pos = "noun",
            meanings = listOf(Meaning(translation = "стол", exampleDe = "Tisch", exampleRu = "Стол"))
        )
        coEvery { groqProvider.translateWord("Tisch", TranslationDirection.DE_RU, null, any()) } returns Result.success(fallbackWord)

        val result = repository.translateWord("Tisch", TranslationDirection.DE_RU)

        assertTrue(result.isSuccess)
        val translationResult = result.getOrThrow()
        assertEquals("Tisch", translationResult.data.lemma)
        assertEquals("Переключено на Groq", translationResult.fallbackMessage)
        assertTrue(translationResult.modelAttribution.contains("Groq"))
    }

    @Test
    fun translateWord_primaryFails401_doesNotFallBack() = runTest {
        coEvery { cacheDao.get(any()) } returns null
        coEvery { cacheDao.getBySemanticKey(any()) } returns null

        // Primary (Gemini) fails with invalid key
        coEvery { geminiProvider.translateWord("Stuhl", TranslationDirection.DE_RU, null, any()) } returns Result.failure(
            ProviderError.InvalidKey("Google Gemini")
        )

        val result = repository.translateWord("Stuhl", TranslationDirection.DE_RU)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ProviderError.InvalidKey)
        // Groq must NEVER be called on 401 per requirement
        coVerify(exactly = 0) { groqProvider.translateWord(any(), any(), any(), any()) }
    }

    @Test
    fun translateWord_primaryFails404ModelNotFound_fallsBackToNextProvider() = runTest {
        coEvery { cacheDao.get(any()) } returns null
        coEvery { cacheDao.getBySemanticKey(any()) } returns null

        // Primary (Gemini) hits 404 ModelNotFound
        coEvery { geminiProvider.translateWord("Fenster", TranslationDirection.DE_RU, null, any()) } returns Result.failure(
            ProviderError.ModelNotFound("Google Gemini", "gemini-2.5-flash")
        )

        // Fallback (Groq) succeeds
        val fallbackWord = WordTranslation(
            lemma = "Fenster",
            article = "das",
            pos = "noun",
            meanings = listOf(Meaning(translation = "окно", exampleDe = "Fenster", exampleRu = "Окно"))
        )
        coEvery { groqProvider.translateWord("Fenster", TranslationDirection.DE_RU, null, any()) } returns Result.success(fallbackWord)

        val result = repository.translateWord("Fenster", TranslationDirection.DE_RU)

        assertTrue(result.isSuccess)
        val translationResult = result.getOrThrow()
        assertEquals("Fenster", translationResult.data.lemma)
        assertEquals("Переключено на Groq", translationResult.fallbackMessage)
        coVerify(exactly = 1) { groqProvider.translateWord("Fenster", TranslationDirection.DE_RU, null, any()) }
    }
}
