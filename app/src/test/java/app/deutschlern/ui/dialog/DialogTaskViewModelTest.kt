package app.deutschlern.ui.dialog

import androidx.lifecycle.SavedStateHandle
import app.deutschlern.domain.model.PartnerLanguage
import app.deutschlern.domain.model.dialog.DialogTask
import app.deutschlern.domain.model.dialog.DialogTaskType
import app.deutschlern.domain.repository.DialogRepository
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.repository.UserSettings
import app.deutschlern.domain.stt.SpeechToTextEngine
import app.deutschlern.domain.usecase.EvaluateOpenResponseUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DialogTaskViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dialogRepository = mockk<DialogRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val evaluateOpenResponseUseCase = mockk<EvaluateOpenResponseUseCase>(relaxed = true)
    private val sttEngine = mockk<SpeechToTextEngine>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { settingsRepository.getSettings() } returns UserSettings()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle): DialogTaskViewModel {
        return DialogTaskViewModel(
            savedStateHandle = savedStateHandle,
            dialogRepository = dialogRepository,
            settingsRepository = settingsRepository,
            evaluateOpenResponseUseCase = evaluateOpenResponseUseCase,
            sttEngine = sttEngine,
            json = json
        )
    }

    @Test
    fun init_withLongNavArguments_doesNotThrowClassCastException_andSetsIds() = runTest(testDispatcher) {
        val testTask = DialogTask(
            id = 20L,
            dialogId = 10L,
            type = DialogTaskType.MCQ,
            payloadJson = "{\"questions\":[]}",
            createdAt = 1000L
        )
        coEvery { dialogRepository.getTaskById(20L) } returns testTask

        val handle = SavedStateHandle(mapOf("dialogId" to 10L, "taskId" to 20L))
        val viewModel = createViewModel(handle)
        advanceUntilIdle()

        assertEquals(10L, viewModel.dialogId)
        assertEquals(20L, viewModel.taskId)
        assertEquals(20L, viewModel.uiState.value.task?.id)
    }

    @Test
    fun init_withStringNavArguments_doesNotThrowClassCastException_andParsesIds() = runTest(testDispatcher) {
        val testTask = DialogTask(
            id = 30L,
            dialogId = 15L,
            type = DialogTaskType.MCQ,
            payloadJson = "{\"questions\":[]}",
            createdAt = 1000L
        )
        coEvery { dialogRepository.getTaskById(30L) } returns testTask

        val handle = SavedStateHandle(mapOf("dialogId" to "15", "taskId" to "30"))
        val viewModel = createViewModel(handle)
        advanceUntilIdle()

        assertEquals(15L, viewModel.dialogId)
        assertEquals(30L, viewModel.taskId)
        assertEquals(30L, viewModel.uiState.value.task?.id)
    }

    @Test
    fun init_withMissingNavArguments_defaultsToZero() = runTest(testDispatcher) {
        val handle = SavedStateHandle()
        val viewModel = createViewModel(handle)
        advanceUntilIdle()

        assertEquals(0L, viewModel.dialogId)
        assertEquals(0L, viewModel.taskId)
    }
}
