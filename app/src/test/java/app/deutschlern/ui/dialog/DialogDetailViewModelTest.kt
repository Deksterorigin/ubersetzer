package app.deutschlern.ui.dialog

import androidx.lifecycle.SavedStateHandle
import app.deutschlern.domain.model.dialog.Dialog
import app.deutschlern.domain.model.dialog.DialogSegment
import app.deutschlern.domain.model.dialog.DialogTask
import app.deutschlern.domain.repository.DialogRepository
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.repository.UserSettings
import app.deutschlern.domain.repository.VocabularyRepository
import app.deutschlern.domain.usecase.GenerateDialogSummaryUseCase
import app.deutschlern.domain.usecase.GenerateDialogTasksUseCase
import app.deutschlern.domain.usecase.TranslateWordUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DialogDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dialogRepository = mockk<DialogRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val generateDialogSummaryUseCase = mockk<GenerateDialogSummaryUseCase>(relaxed = true)
    private val generateDialogTasksUseCase = mockk<GenerateDialogTasksUseCase>(relaxed = true)
    private val translateWordUseCase = mockk<TranslateWordUseCase>(relaxed = true)
    private val vocabularyRepository = mockk<VocabularyRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { settingsRepository.settingsFlow } returns flowOf(UserSettings())
        every { dialogRepository.getSegmentsForDialog(any()) } returns flowOf(emptyList())
        every { dialogRepository.getTasksForDialog(any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle): DialogDetailViewModel {
        return DialogDetailViewModel(
            savedStateHandle = savedStateHandle,
            dialogRepository = dialogRepository,
            settingsRepository = settingsRepository,
            generateDialogSummaryUseCase = generateDialogSummaryUseCase,
            generateDialogTasksUseCase = generateDialogTasksUseCase,
            translateWordUseCase = translateWordUseCase,
            vocabularyRepository = vocabularyRepository
        )
    }

    @Test
    fun init_withLongNavArgument_doesNotThrowClassCastException_andSetsDialogId() = runTest(testDispatcher) {
        val testDialog = Dialog(id = 42L, title = "Test Long Arg Dialog", startedAt = 1000L, durationMs = 5000L)
        coEvery { dialogRepository.getDialogById(42L) } returns testDialog
        coEvery { dialogRepository.getSegmentsForDialog(42L) } returns flowOf(
            listOf(DialogSegment(id = 1L, dialogId = 42L, orderIndex = 0, text = "Hallo", startOffsetMs = 0L))
        )

        val handle = SavedStateHandle(mapOf("dialogId" to 42L))
        val viewModel = createViewModel(handle)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertEquals(42L, viewModel.dialogId)
        val uiState = viewModel.uiState.value
        assertNotNull(uiState.dialog)
        assertEquals(42L, uiState.dialog?.id)
        assertEquals("Test Long Arg Dialog", uiState.dialog?.title)
        assertEquals(1, uiState.segments.size)
    }

    @Test
    fun init_withStringNavArgument_doesNotThrowClassCastException_andParsesDialogId() = runTest(testDispatcher) {
        val testDialog = Dialog(id = 88L, title = "Test String Arg Dialog", startedAt = 2000L, durationMs = 3000L)
        coEvery { dialogRepository.getDialogById(88L) } returns testDialog

        val handle = SavedStateHandle(mapOf("dialogId" to "88"))
        val viewModel = createViewModel(handle)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertEquals(88L, viewModel.dialogId)
        assertEquals(88L, viewModel.uiState.value.dialog?.id)
    }

    @Test
    fun init_withMissingDialogId_defaultsToZero() = runTest(testDispatcher) {
        val handle = SavedStateHandle()
        val viewModel = createViewModel(handle)
        advanceUntilIdle()

        assertEquals(0L, viewModel.dialogId)
    }

    @Test
    fun selectTab_updatesSelectedTabIndex() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("dialogId" to 1L))
        val viewModel = createViewModel(handle)
        backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.selectedTab)
        viewModel.selectTab(2)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.selectedTab)
    }
}
