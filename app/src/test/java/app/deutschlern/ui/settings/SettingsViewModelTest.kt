package app.deutschlern.ui.settings

import app.deutschlern.data.local.dao.CacheDao
import app.deutschlern.data.local.security.SecureKeyStorage
import app.deutschlern.data.remote.provider.DeepLProvider
import app.deutschlern.data.remote.provider.MlKitOfflineProvider
import app.deutschlern.domain.provider.ProviderCapabilities
import app.deutschlern.domain.provider.TranslationProvider
import app.deutschlern.domain.provider.TranslationProviderRegistry
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.repository.UserSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val secureKeyStorage = mockk<SecureKeyStorage>(relaxed = true)
    private val registry = mockk<TranslationProviderRegistry>()
    private val cacheDao = mockk<CacheDao>(relaxed = true)
    private val mlKitOfflineProvider = mockk<MlKitOfflineProvider>(relaxed = true)
    private val deepLProvider = mockk<DeepLProvider>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    private val settingsFlow = MutableStateFlow(
        UserSettings(
            selectedWordProviderId = "gemini",
            selectedTextProviderId = "gemini",
            fallbackOrder = listOf("gemini", "groq")
        )
    )

    private val geminiProvider = mockk<TranslationProvider>()
    private val groqProvider = mockk<TranslationProvider>()

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        coEvery { geminiProvider.id } returns "gemini"
        coEvery { geminiProvider.displayName } returns "Google Gemini"
        coEvery { geminiProvider.capabilities } returns ProviderCapabilities(supportsWordMode = true, supportsTextMode = true)

        coEvery { groqProvider.id } returns "groq"
        coEvery { groqProvider.displayName } returns "Groq"
        coEvery { groqProvider.capabilities } returns ProviderCapabilities(supportsWordMode = true, supportsTextMode = true)

        coEvery { registry.getAllProviders() } returns listOf(geminiProvider, groqProvider)
        coEvery { registry.getProvider("gemini") } returns geminiProvider
        coEvery { registry.getProvider("groq") } returns groqProvider

        coEvery { settingsRepository.settingsFlow } returns settingsFlow
        coEvery { secureKeyStorage.isKeyRecoveryNeeded } returns MutableStateFlow(false)
        coEvery { secureKeyStorage.getKey(any()) } returns null

        viewModel = SettingsViewModel(
            settingsRepository = settingsRepository,
            secureKeyStorage = secureKeyStorage,
            registry = registry,
            cacheDao = cacheDao,
            mlKitOfflineProvider = mlKitOfflineProvider,
            deepLProvider = deepLProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_loadsSelectedProviders() = runTest {
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("gemini", state.selectedWordProviderId)
        assertEquals("gemini", state.selectedTextProviderId)
        assertEquals(2, state.providers.size)
    }

    @Test
    fun onApiKeyChanged_savesKeyToSecureStorage() = runTest {
        viewModel.onApiKeyChanged("groq", "gsk_test12345")
        advanceUntilIdle()

        coVerify(exactly = 1) { secureKeyStorage.saveKey("groq", "gsk_test12345") }
    }

    @Test
    fun onSelectWordProvider_updatesRepository() = runTest {
        viewModel.onSelectWordProvider("groq")
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setSelectedWordProvider("groq") }
    }

    @Test
    fun onTestConnectionClicked_callsProviderAndUpdatesStatus() = runTest {
        coEvery { groqProvider.testConnection(any()) } returns Result.success("Работает")

        viewModel.onTestConnectionClicked("groq")
        advanceUntilIdle()

        val groqModel = viewModel.uiState.value.providers.find { it.id == "groq" }
        assertEquals("Работает", groqModel?.testStatus)
    }

    @Test
    fun onClearCacheClicked_clearsDatabaseCache() = runTest {
        viewModel.onClearCacheClicked()
        advanceUntilIdle()

        coVerify(exactly = 1) { cacheDao.clearAll() }
        assertEquals("Кэш переводов очищен", viewModel.uiState.value.infoMessage)
    }

    @Test
    fun keystoreRecoveryNeeded_updatesUiState() = runTest {
        val recoveryFlow = MutableStateFlow(true)
        coEvery { secureKeyStorage.isKeyRecoveryNeeded } returns recoveryFlow

        val testVm = SettingsViewModel(
            settingsRepository = settingsRepository,
            secureKeyStorage = secureKeyStorage,
            registry = registry,
            cacheDao = cacheDao,
            mlKitOfflineProvider = mlKitOfflineProvider,
            deepLProvider = deepLProvider
        )
        advanceUntilIdle()

        assertTrue(testVm.uiState.value.isKeystoreRecoveryNeeded)
    }

    @Test
    fun initialState_loadsSelectedAppLanguageCode() = runTest {
        settingsFlow.value = settingsFlow.value.copy(appLanguageCode = "de")
        advanceUntilIdle()

        assertEquals("de", viewModel.uiState.value.selectedAppLanguageCode)
    }

    @Test
    fun onSelectAppLanguage_updatesRepository() = runTest {
        viewModel.onSelectAppLanguage("ar")
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setAppLanguage("ar") }
    }

    @Test
    fun onSelectAppLanguage_systemDefault_passesEmptyString() = runTest {
        viewModel.onSelectAppLanguage("")
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setAppLanguage("") }
    }

    @Test
    fun onSelectAppLanguage_dari_passesFa() = runTest {
        viewModel.onSelectAppLanguage("fa")
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setAppLanguage("fa") }
    }

    @Test
    fun onSelectAppLanguage_romanian_passesRo() = runTest {
        viewModel.onSelectAppLanguage("ro")
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setAppLanguage("ro") }
    }

    @Test
    fun initialState_loadsSttSettings() = runTest {
        settingsFlow.value = settingsFlow.value.copy(
            sttPreferOnDevice = true,
            sttAutoDetect = false,
            sttTranslateImmediately = true
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(true, state.sttPreferOnDevice)
        assertEquals(false, state.sttAutoDetect)
        assertEquals(true, state.sttTranslateImmediately)
    }

    @Test
    fun onToggleSttPreferOnDevice_updatesRepository() = runTest {
        viewModel.onToggleSttPreferOnDevice(true)
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setSttPreferOnDevice(true) }
    }

    @Test
    fun onToggleSttAutoDetect_updatesRepository() = runTest {
        viewModel.onToggleSttAutoDetect(false)
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setSttAutoDetect(false) }
    }

    @Test
    fun onToggleSttTranslateImmediately_updatesRepository() = runTest {
        viewModel.onToggleSttTranslateImmediately(true)
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setSttTranslateImmediately(true) }
    }
}

