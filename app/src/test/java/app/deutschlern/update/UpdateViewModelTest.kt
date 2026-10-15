package app.deutschlern.update

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import app.deutschlern.domain.repository.VocabularyRepository
import app.deutschlern.update.data.UpdateRepository
import app.deutschlern.update.model.AppVersion
import app.deutschlern.update.model.ReleaseInfo
import app.deutschlern.update.model.UpdateError
import app.deutschlern.update.model.UpdateState
import app.deutschlern.update.receiver.InstallEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val updateRepository = mockk<UpdateRepository>(relaxed = true)
    private val vocabularyRepository = mockk<VocabularyRepository>(relaxed = true)
    private val savedStateHandle = SavedStateHandle()
    private val installEventsFlow = MutableSharedFlow<InstallEvent>(extraBufferCapacity = 10)

    private lateinit var viewModel: UpdateViewModel

    private val sampleRelease = ReleaseInfo(
        tagName = "v2.0.0",
        version = AppVersion.parse("2.0.0"),
        releaseNotes = "New features",
        apkFileName = "app-v2.0.0.apk",
        downloadUrl = "https://github.com/Deksterorigin/ubersetzer/download/v2.0.0/app.apk",
        apkSizeBytes = 125000000L,
        expectedDigest = "sha256:abcdef"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { updateRepository.getCurrentVersionName() } returns "1.0"
        every { updateRepository.getCurrentVersionCode() } returns 1L
        every { updateRepository.installEvents } returns installEventsFlow
        every { updateRepository.canRequestPackageInstalls() } returns true

        viewModel = UpdateViewModel(
            updateRepository = updateRepository,
            vocabularyRepository = vocabularyRepository,
            savedStateHandle = savedStateHandle,
            isFeatureEnabled = true
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_loadsVersionAndDefaults() {
        val state = viewModel.uiState.value
        assertEquals("1.0", state.currentVersionName)
        assertEquals(1L, state.currentVersionCode)
        assertTrue(state.isWifiOnly)
        assertTrue(state.isFeatureEnabled)
        assertTrue(state.updateState is UpdateState.Idle)
    }

    @Test
    fun onCheckForUpdates_whenFeatureDisabled_doesNothing() = runTest {
        val disabledViewModel = UpdateViewModel(
            updateRepository = updateRepository,
            vocabularyRepository = vocabularyRepository,
            savedStateHandle = savedStateHandle,
            isFeatureEnabled = false
        )

        disabledViewModel.onCheckForUpdates()
        advanceUntilIdle()

        coVerify(exactly = 0) { updateRepository.checkForUpdates() }
        assertTrue(disabledViewModel.uiState.value.updateState is UpdateState.Idle)
    }

    @Test
    fun onCheckForUpdates_whenNewerAvailable_transitionsToUpdateAvailable() = runTest {
        coEvery { updateRepository.checkForUpdates() } returns Result.success(sampleRelease)

        viewModel.onCheckForUpdates()
        advanceUntilIdle()

        val state = viewModel.uiState.value.updateState
        assertTrue(state is UpdateState.UpdateAvailable)
        assertEquals(sampleRelease, (state as UpdateState.UpdateAvailable).release)
    }

    @Test
    fun onCheckForUpdates_whenUpToDate_transitionsToUpToDate() = runTest {
        coEvery { updateRepository.checkForUpdates() } returns Result.success(null)

        viewModel.onCheckForUpdates()
        advanceUntilIdle()

        val state = viewModel.uiState.value.updateState
        assertTrue(state is UpdateState.UpToDate)
        assertEquals("1.0", (state as UpdateState.UpToDate).currentVersion)
    }

    @Test
    fun onCheckForUpdates_onRateLimit_transitionsToError() = runTest {
        coEvery { updateRepository.checkForUpdates() } returns Result.failure(UpdateError.RateLimitExceeded)

        viewModel.onCheckForUpdates()
        advanceUntilIdle()

        val state = viewModel.uiState.value.updateState
        assertTrue(state is UpdateState.Error)
        assertEquals(UpdateError.RateLimitExceeded, (state as UpdateState.Error).error)
    }

    @Test
    fun onCheckForUpdates_onNetworkError_transitionsToError() = runTest {
        coEvery { updateRepository.checkForUpdates() } returns Result.failure(UpdateError.NetworkError)

        viewModel.onCheckForUpdates()
        advanceUntilIdle()

        val state = viewModel.uiState.value.updateState
        assertTrue(state is UpdateState.Error)
        assertEquals(UpdateError.NetworkError, (state as UpdateState.Error).error)
    }

    @Test
    fun onDownloadAndInstall_whenPermissionGranted_proceedsToInstalling() = runTest {
        val fakeApkFile = mockk<File>(relaxed = true)
        every { fakeApkFile.exists() } returns true

        coEvery {
            updateRepository.downloadApk(sampleRelease, any(), any())
        } returns Result.success(fakeApkFile)

        every { updateRepository.verifyApk(fakeApkFile, sampleRelease.expectedDigest) } returns Result.success(Unit)
        every { updateRepository.canRequestPackageInstalls() } returns true
        every { updateRepository.installApk(fakeApkFile) } returns Result.success(Unit)

        viewModel.onDownloadAndInstall(sampleRelease)
        advanceUntilIdle()

        coVerify(exactly = 1) { updateRepository.installApk(fakeApkFile) }
        assertTrue(viewModel.uiState.value.updateState is UpdateState.Installing)
    }

    @Test
    fun onDownloadAndInstall_whenPermissionNotGranted_transitionsToPermissionRequired() = runTest {
        val fakeApkFile = mockk<File>(relaxed = true)
        every { fakeApkFile.exists() } returns true

        coEvery {
            updateRepository.downloadApk(sampleRelease, any(), any())
        } returns Result.success(fakeApkFile)

        every { updateRepository.verifyApk(fakeApkFile, sampleRelease.expectedDigest) } returns Result.success(Unit)
        every { updateRepository.canRequestPackageInstalls() } returns false

        viewModel.onDownloadAndInstall(sampleRelease)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.updateState is UpdateState.PermissionRequired)
        coVerify(exactly = 0) { updateRepository.installApk(any()) }
    }

    @Test
    fun onCancelDownload_resetsToIdle() = runTest {
        viewModel.onCancelDownload()

        coVerify(exactly = 1) { updateRepository.cancelDownload() }
        assertTrue(viewModel.uiState.value.updateState is UpdateState.Idle)
    }

    @Test
    fun onToggleWifiOnly_updatesState() {
        viewModel.onToggleWifiOnly(false)
        assertEquals(false, viewModel.uiState.value.isWifiOnly)

        viewModel.onToggleWifiOnly(true)
        assertEquals(true, viewModel.uiState.value.isWifiOnly)
    }

    @Test
    fun observeInstallEvents_pendingUserAction_setsConfirmationIntent() = runTest {
        val mockIntent = mockk<Intent>()
        installEventsFlow.emit(InstallEvent.PendingUserAction(mockIntent))
        advanceUntilIdle()

        assertEquals(mockIntent, viewModel.uiState.value.confirmationIntentToLaunch)

        viewModel.onConfirmationIntentLaunched()
        assertEquals(null, viewModel.uiState.value.confirmationIntentToLaunch)
    }

    @Test
    fun observeInstallEvents_success_cleansUpAndResetsToIdle() = runTest {
        installEventsFlow.emit(InstallEvent.Success)
        advanceUntilIdle()

        coVerify(exactly = 1) { updateRepository.cleanupTempFiles() }
        assertTrue(viewModel.uiState.value.updateState is UpdateState.Idle)
    }

    @Test
    fun observeInstallEvents_failure_cleansUpAndSetsError() = runTest {
        installEventsFlow.emit(InstallEvent.Failure(statusCode = -1, message = "INSTALL_FAILED_INVALID_APK"))
        advanceUntilIdle()

        coVerify(exactly = 1) { updateRepository.cleanupTempFiles() }
        val state = viewModel.uiState.value.updateState
        assertTrue(state is UpdateState.Error)
        val error = (state as UpdateState.Error).error
        assertTrue(error is UpdateError.InstallFailed)
        assertEquals(-1, (error as UpdateError.InstallFailed).statusCode)
        assertEquals("INSTALL_FAILED_INVALID_APK", error.statusMessage)
    }
}
