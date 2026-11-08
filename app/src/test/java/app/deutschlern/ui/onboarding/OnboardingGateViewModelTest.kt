package app.deutschlern.ui.onboarding

import android.util.Log
import app.deutschlern.data.local.dao.WordDao
import app.deutschlern.data.local.security.SecureKeyStorage
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

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingGateViewModelTest {

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val wordDao = mockk<WordDao>(relaxed = true)
    private val secureKeyStorage = mockk<SecureKeyStorage>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        coEvery { settingsRepository.getSettings() } returns UserSettings()
        coEvery { wordDao.hasAnyWord() } returns false
        coEvery { secureKeyStorage.hasAnyKey() } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = OnboardingGateViewModel(settingsRepository, wordDao, secureKeyStorage)

    @Test
    fun freshInstall_showsOnboarding_withoutMarkingItDone() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(true, viewModel.showOnboarding.value)
        coVerify(exactly = 0) { settingsRepository.setHasCompletedOnboarding(any()) }
    }

    @Test
    fun existingWords_silentlyCompleteOnboarding() = runTest {
        coEvery { wordDao.hasAnyWord() } returns true

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(false, viewModel.showOnboarding.value)
        coVerify(exactly = 1) { settingsRepository.setHasCompletedOnboarding(true) }
    }

    @Test
    fun existingApiKey_silentlyCompletesOnboarding() = runTest {
        coEvery { secureKeyStorage.hasAnyKey() } returns true

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(false, viewModel.showOnboarding.value)
        coVerify(exactly = 1) { settingsRepository.setHasCompletedOnboarding(true) }
    }

    @Test
    fun completedOnboarding_neverShows_evenWithNoDataLeft() = runTest {
        coEvery { settingsRepository.getSettings() } returns UserSettings(hasCompletedOnboarding = true)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(false, viewModel.showOnboarding.value)
        coVerify(exactly = 0) { wordDao.hasAnyWord() }
        coVerify(exactly = 0) { settingsRepository.setHasCompletedOnboarding(any()) }
    }

    @Test
    fun decisionIsUnknown_untilTheDataCheckAnswers() = runTest {
        val answer = CompletableDeferred<Boolean>()
        coEvery { wordDao.hasAnyWord() } coAnswers { answer.await() }

        val viewModel = createViewModel()
        advanceUntilIdle()
        assertNull(viewModel.showOnboarding.value)

        answer.complete(true)
        advanceUntilIdle()
        assertEquals(false, viewModel.showOnboarding.value)
    }

    @Test
    fun failedDataCheck_opensTheApp_andDecidesAgainNextLaunch() = runTest {
        mockkStatic(Log::class)
        try {
            every { Log.e(any(), any(), any()) } returns 0
            coEvery { wordDao.hasAnyWord() } throws IllegalStateException("database unavailable")

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(false, viewModel.showOnboarding.value)
            coVerify(exactly = 0) { settingsRepository.setHasCompletedOnboarding(any()) }
        } finally {
            unmockkStatic(Log::class)
        }
    }
}
