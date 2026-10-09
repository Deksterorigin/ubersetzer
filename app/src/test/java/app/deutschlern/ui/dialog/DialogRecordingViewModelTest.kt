package app.deutschlern.ui.dialog

import app.deutschlern.domain.model.dialog.Dialog
import app.deutschlern.domain.repository.DialogRepository
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.repository.UserSettings
import app.deutschlern.domain.stt.SpeechToTextEngine
import app.deutschlern.domain.stt.SttEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DialogRecordingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val sttEngine = mockk<SpeechToTextEngine>(relaxed = true)
    private val dialogRepository = mockk<DialogRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { settingsRepository.settingsFlow } returns flowOf(UserSettings())
        coEvery { settingsRepository.getSettings() } returns UserSettings()
        every { sttEngine.startListening(any(), any()) } returns emptyFlow()
        coEvery { dialogRepository.saveDialog(any()) } returns 42L
        coEvery { dialogRepository.getDialogById(42L) } returns Dialog(id = 42L, title = "Dialog Test", startedAt = 1000L, durationMs = 0)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_createsDraftDialogAndStartsRecording() = runTest(testDispatcher) {
        val viewModel = DialogRecordingViewModel(sttEngine, dialogRepository, settingsRepository)
        advanceUntilIdle()

        assertEquals(42L, viewModel.uiState.value.draftDialogId)
        assertTrue(viewModel.uiState.value.isRecording)
        assertEquals("A", viewModel.uiState.value.activeSpeaker)
    }

    @Test
    fun toggleSpeaker_switchesBetweenAAndB() = runTest(testDispatcher) {
        val viewModel = DialogRecordingViewModel(sttEngine, dialogRepository, settingsRepository)
        advanceUntilIdle()

        assertEquals("A", viewModel.uiState.value.activeSpeaker)

        viewModel.toggleSpeaker()
        advanceUntilIdle()
        assertEquals("B", viewModel.uiState.value.activeSpeaker)

        viewModel.toggleSpeaker()
        advanceUntilIdle()
        assertEquals("A", viewModel.uiState.value.activeSpeaker)
    }

    @Test
    fun pauseAndResume_updatesRecordingState() = runTest(testDispatcher) {
        val viewModel = DialogRecordingViewModel(sttEngine, dialogRepository, settingsRepository)
        advanceUntilIdle()

        viewModel.pauseRecording()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRecording)

        viewModel.resumeRecording()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isRecording)
    }

    @Test
    fun stopAndSave_finalizesAndSetsSavedDialogId() = runTest(testDispatcher) {
        val viewModel = DialogRecordingViewModel(sttEngine, dialogRepository, settingsRepository)
        advanceUntilIdle()

        viewModel.stopAndSave()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRecording)
        assertEquals(42L, viewModel.uiState.value.savedDialogId)
        coVerify { dialogRepository.updateDialog(match { it.id == 42L }) }
    }

    @Test
    fun cancelRecording_deletesDraftDialog() = runTest(testDispatcher) {
        val viewModel = DialogRecordingViewModel(sttEngine, dialogRepository, settingsRepository)
        advanceUntilIdle()

        viewModel.cancelRecording()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDismissed)
        coVerify { dialogRepository.deleteDialog(42L) }
    }
}
