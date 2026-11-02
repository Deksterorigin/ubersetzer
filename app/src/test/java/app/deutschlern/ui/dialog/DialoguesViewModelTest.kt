package app.deutschlern.ui.dialog

import app.deutschlern.domain.model.dialog.DeletedDialog
import app.deutschlern.domain.model.dialog.Dialog
import app.deutschlern.domain.model.dialog.DialogSegment
import app.deutschlern.domain.model.dialog.DialogTask
import app.deutschlern.domain.model.dialog.DialogTaskType
import app.deutschlern.domain.repository.DialogRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class DialoguesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dialogRepository = mockk<DialogRepository>(relaxed = true)

    private val testDialog = Dialog(id = 1L, title = "Test Dialog", startedAt = 1000L, durationMs = 5000L, segmentCount = 1)
    private val deleted = DeletedDialog(
        dialog = testDialog,
        segments = listOf(
            DialogSegment(id = 10L, dialogId = 1L, orderIndex = 0, text = "Hallo", speakerLabel = "A", startOffsetMs = 0L)
        ),
        tasks = listOf(
            DialogTask(id = 20L, dialogId = 1L, type = DialogTaskType.MCQ, payloadJson = "{}", createdAt = 1L)
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { dialogRepository.getAllDialogs() } returns flowOf(listOf(testDialog))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createSubscribedViewModel(): DialoguesViewModel {
        val viewModel = DialoguesViewModel(dialogRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun init_loadsDialogues() = runTest(testDispatcher) {
        val viewModel = createSubscribedViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.dialogs.size)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun deleteDialog_exposesDeletedDialogForUndo() = runTest(testDispatcher) {
        coEvery { dialogRepository.deleteDialog(1L) } returns deleted
        val viewModel = createSubscribedViewModel()
        advanceUntilIdle()

        viewModel.deleteDialog(testDialog)
        advanceUntilIdle()

        coVerify { dialogRepository.deleteDialog(1L) }
        assertEquals(testDialog, viewModel.uiState.value.deletedDialog)
    }

    @Test
    fun undoDelete_restoresFullSnapshotIncludingTasks() = runTest(testDispatcher) {
        coEvery { dialogRepository.deleteDialog(1L) } returns deleted
        val viewModel = createSubscribedViewModel()
        advanceUntilIdle()

        viewModel.deleteDialog(testDialog)
        advanceUntilIdle()
        viewModel.undoDelete()
        advanceUntilIdle()

        coVerify(exactly = 1) { dialogRepository.restoreDialog(deleted) }
        assertNull(viewModel.uiState.value.deletedDialog)
    }

    @Test
    fun clearUndo_dropsSnapshot() = runTest(testDispatcher) {
        coEvery { dialogRepository.deleteDialog(1L) } returns deleted
        val viewModel = createSubscribedViewModel()
        viewModel.deleteDialog(testDialog)
        advanceUntilIdle()

        viewModel.clearUndo()
        viewModel.undoDelete()
        advanceUntilIdle()

        coVerify(exactly = 0) { dialogRepository.restoreDialog(any()) }
    }
}
