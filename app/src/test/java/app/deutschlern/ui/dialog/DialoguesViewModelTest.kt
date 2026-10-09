package app.deutschlern.ui.dialog

import app.deutschlern.domain.model.dialog.Dialog
import app.deutschlern.domain.model.dialog.DialogSegment
import app.deutschlern.domain.repository.DialogRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DialoguesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dialogRepository = mockk<DialogRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_loadsDialogues() = runTest(testDispatcher) {
        val testDialogs = listOf(
            Dialog(id = 1L, title = "Test Dialog", startedAt = 1000L, durationMs = 5000L, segmentCount = 2)
        )
        coEvery { dialogRepository.getAllDialogs() } returns flowOf(testDialogs)

        val viewModel = DialoguesViewModel(dialogRepository)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.dialogs.size)
        assertEquals("Test Dialog", viewModel.uiState.value.dialogs[0].title)
    }

    @Test
    fun deleteDialog_callsRepositoryAndSetsBackup() = runTest(testDispatcher) {
        val testDialog = Dialog(id = 1L, title = "To Delete", startedAt = 1000L, durationMs = 5000L)
        val testSegments = listOf(
            DialogSegment(id = 10L, dialogId = 1L, orderIndex = 0, text = "Hallo", startOffsetMs = 0)
        )
        coEvery { dialogRepository.getAllDialogs() } returns flowOf(emptyList())
        coEvery { dialogRepository.getSegmentsSync(1L) } returns testSegments

        val viewModel = DialoguesViewModel(dialogRepository)
        advanceUntilIdle()

        viewModel.deleteDialog(testDialog)
        advanceUntilIdle()

        coVerify { dialogRepository.deleteDialog(1L) }
        assertNotNull(viewModel.uiState.value.deletedDialog)
        assertEquals("To Delete", viewModel.uiState.value.deletedDialog?.title)
    }

    @Test
    fun undoDelete_restoresDialogAndSegments() = runTest(testDispatcher) {
        val testDialog = Dialog(id = 1L, title = "Restored Dialog", startedAt = 1000L, durationMs = 5000L)
        val testSegments = listOf(
            DialogSegment(id = 10L, dialogId = 1L, orderIndex = 0, text = "Hallo", startOffsetMs = 0)
        )
        coEvery { dialogRepository.getAllDialogs() } returns flowOf(emptyList())
        coEvery { dialogRepository.getSegmentsSync(1L) } returns testSegments
        coEvery { dialogRepository.saveDialog(any()) } returns 2L

        val viewModel = DialoguesViewModel(dialogRepository)
        advanceUntilIdle()

        viewModel.deleteDialog(testDialog)
        advanceUntilIdle()

        viewModel.undoDelete()
        advanceUntilIdle()

        coVerify { dialogRepository.saveDialog(match { it.title == "Restored Dialog" }) }
        coVerify { dialogRepository.insertSegments(match { it.size == 1 && it[0].dialogId == 2L }) }
        assertNull(viewModel.uiState.value.deletedDialog)
    }
}
