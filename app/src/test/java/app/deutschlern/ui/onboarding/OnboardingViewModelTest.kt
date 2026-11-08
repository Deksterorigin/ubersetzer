package app.deutschlern.ui.onboarding

import android.util.Log
import app.deutschlern.domain.model.AppLanguage
import app.deutschlern.domain.model.PartnerLanguage
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.repository.UserSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val settingsFlow = MutableStateFlow(UserSettings())
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        every { settingsRepository.settingsFlow } returns settingsFlow
        viewModel = OnboardingViewModel(settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selection_reflectsCurrentSettings() = runTest {
        settingsFlow.value = UserSettings(appLanguageCode = "ar", selectedPartnerLanguageCode = "fa")
        advanceUntilIdle()

        assertEquals(AppLanguage.ARABIC, viewModel.uiState.value.appLanguage)
        assertEquals(PartnerLanguage.DARI, viewModel.uiState.value.partnerLanguage)
    }

    @Test
    fun selectingLanguages_goesThroughTheRepository() = runTest {
        viewModel.onSelectAppLanguage(AppLanguage.GERMAN)
        viewModel.onSelectPartnerLanguage(PartnerLanguage.UKRAINIAN)
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setAppLanguage("de") }
        coVerify(exactly = 1) { settingsRepository.setSelectedPartnerLanguage("uk") }
    }

    @Test
    fun finish_publishesExit_onlyAfterTheFlagIsWritten() = runTest {
        val written = CompletableDeferred<Unit>()
        coEvery { settingsRepository.setHasCompletedOnboarding(true) } coAnswers { written.await() }

        viewModel.finish(OnboardingExit.SETTINGS)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.exit)

        written.complete(Unit)
        advanceUntilIdle()
        assertEquals(OnboardingExit.SETTINGS, viewModel.uiState.value.exit)
    }

    @Test
    fun finish_later_opensTranslate_andSetsTheFlag() = runTest {
        viewModel.finish(OnboardingExit.TRANSLATE)
        advanceUntilIdle()

        assertEquals(OnboardingExit.TRANSLATE, viewModel.uiState.value.exit)
        coVerify(exactly = 1) { settingsRepository.setHasCompletedOnboarding(true) }
    }

    @Test
    fun finish_secondTapIsIgnored() = runTest {
        viewModel.finish(OnboardingExit.SETTINGS)
        viewModel.finish(OnboardingExit.TRANSLATE)
        advanceUntilIdle()

        assertEquals(OnboardingExit.SETTINGS, viewModel.uiState.value.exit)
        coVerify(exactly = 1) { settingsRepository.setHasCompletedOnboarding(true) }
    }

    @Test
    fun replay_keepsTheFlagTrue() = runTest {
        settingsFlow.value = UserSettings(hasCompletedOnboarding = true)
        advanceUntilIdle()
        coVerify(exactly = 0) { settingsRepository.setHasCompletedOnboarding(any()) }

        viewModel.finish(OnboardingExit.TRANSLATE)
        advanceUntilIdle()

        coVerify(exactly = 0) { settingsRepository.setHasCompletedOnboarding(false) }
        assertEquals(OnboardingExit.TRANSLATE, viewModel.uiState.value.exit)
    }

    @Test
    fun finish_stillLeaves_whenTheFlagCannotBeWritten() = runTest {
        mockkStatic(Log::class)
        try {
            every { Log.e(any(), any(), any()) } returns 0
            coEvery { settingsRepository.setHasCompletedOnboarding(true) } throws IOException("disk full")

            viewModel.finish(OnboardingExit.TRANSLATE)
            advanceUntilIdle()

            assertEquals(OnboardingExit.TRANSLATE, viewModel.uiState.value.exit)
        } finally {
            unmockkStatic(Log::class)
        }
    }
}
