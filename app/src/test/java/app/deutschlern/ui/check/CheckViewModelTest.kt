package app.deutschlern.ui.check

import androidx.lifecycle.SavedStateHandle
import app.deutschlern.domain.model.PartnerLanguage
import app.deutschlern.domain.model.check.CheckCategory
import app.deutschlern.domain.model.check.CheckEditHunk
import app.deutschlern.domain.model.check.TextCheckEntry
import app.deutschlern.domain.model.check.TextCheckResult
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.repository.TextCheckRepository
import app.deutschlern.domain.repository.UserSettings
import app.deutschlern.domain.repository.VocabularyRepository
import app.deutschlern.domain.usecase.CheckTextUseCase
import app.deutschlern.domain.usecase.TranslateWordUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val checkTextUseCase = mockk<CheckTextUseCase>()
    private val textCheckRepository = mockk<TextCheckRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>()
    private val translateWordUseCase = mockk<TranslateWordUseCase>(relaxed = true)
    private val vocabularyRepository = mockk<VocabularyRepository>(relaxed = true)
    private val textRecognitionEngine = mockk<app.deutschlern.domain.ocr.TextRecognitionEngine>(relaxed = true)
    private val context = mockk<android.content.Context>(relaxed = true)

    private lateinit var viewModel: CheckViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { settingsRepository.settingsFlow } returns flowOf(UserSettings())
        coEvery { settingsRepository.getSettings() } returns UserSettings()
        coEvery { settingsRepository.setHasAcceptedCheckConsent(any()) } returns Unit

        viewModel = CheckViewModel(
            checkTextUseCase = checkTextUseCase,
            textCheckRepository = textCheckRepository,
            settingsRepository = settingsRepository,
            translateWordUseCase = translateWordUseCase,
            vocabularyRepository = vocabularyRepository,
            textRecognitionEngine = textRecognitionEngine,
            appContext = context,
            savedStateHandle = SavedStateHandle()
        )
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onInputChanged_updatesInputText() {
        viewModel.onEvent(CheckUiEvent.OnInputChanged("Hallo Welt"))
        assertEquals("Hallo Welt", viewModel.uiState.value.inputText)
    }

    @Test
    fun onClearInput_clearsInputText() {
        viewModel.onEvent(CheckUiEvent.OnInputChanged("Text"))
        viewModel.onEvent(CheckUiEvent.OnClearInput)
        assertTrue(viewModel.uiState.value.inputText.isEmpty())
    }

    @Test
    fun onSelectCategoryFilter_updatesFilter() {
        viewModel.onEvent(CheckUiEvent.OnSelectCategoryFilter(CheckCategory.SPELLING))
        assertEquals(CheckCategory.SPELLING, viewModel.uiState.value.categoryFilter)

        viewModel.onEvent(CheckUiEvent.OnSelectCategoryFilter(null))
        assertNull(viewModel.uiState.value.categoryFilter)
    }

    @Test
    fun onToggleHunkAcceptance_flipsStateAndRecalculatesFinalText() {
        val hunk = CheckEditHunk(
            id = "h1",
            original = "ein",
            corrected = "einen",
            category = CheckCategory.GRAMMAR,
            explanation = "Akkusativ",
            isAccepted = true,
            startOffset = 9,
            endOffset = 12
        )
        val checkResult = TextCheckResult(
            originalText = "Ich habe ein Hund.",
            correctedText = "Ich habe einen Hund.",
            hunks = listOf(hunk),
            partnerLanguage = "ru"
        )

        coEvery { checkTextUseCase(any(), any(), any()) } returns Result.success(checkResult)
        coEvery { textCheckRepository.saveCheck(any()) } returns 1L

        viewModel.onEvent(CheckUiEvent.OnInputChanged("Ich habe ein Hund."))
        viewModel.onEvent(CheckUiEvent.OnAcceptConsent)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.result?.hunks?.first()?.isAccepted == true)
        assertEquals("Ich habe einen Hund.", viewModel.getAssembledFinalText())

        // Toggle acceptance -> should become unaccepted
        viewModel.onEvent(CheckUiEvent.OnToggleHunkAcceptance("h1"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.result?.hunks?.first()?.isAccepted == true)
        assertEquals("Ich habe ein Hund.", viewModel.getAssembledFinalText())
    }

    @Test
    fun onAcceptAllHunks_setsAllHunksToAccepted() {
        val hunk = CheckEditHunk(
            id = "h1",
            original = "ein",
            corrected = "einen",
            category = CheckCategory.GRAMMAR,
            explanation = "Akkusativ",
            isAccepted = false,
            startOffset = 9,
            endOffset = 12
        )
        val checkResult = TextCheckResult(
            originalText = "Ich habe ein Hund.",
            correctedText = "Ich habe einen Hund.",
            hunks = listOf(hunk),
            partnerLanguage = "ru"
        )

        coEvery { checkTextUseCase(any(), any(), any()) } returns Result.success(checkResult)
        coEvery { textCheckRepository.saveCheck(any()) } returns 1L

        viewModel.onEvent(CheckUiEvent.OnInputChanged("Ich habe ein Hund."))
        viewModel.onEvent(CheckUiEvent.OnAcceptConsent)
        testDispatcher.scheduler.advanceUntilIdle()

        // Initially false
        assertFalse(viewModel.uiState.value.result?.hunks?.first()?.isAccepted == true)

        // Accept all
        viewModel.onEvent(CheckUiEvent.OnAcceptAllHunks)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.result?.hunks?.first()?.isAccepted == true)
        assertEquals("Ich habe einen Hund.", viewModel.getAssembledFinalText())
    }

    @Test
    fun onResetCheck_clearsResult() {
        viewModel.onEvent(CheckUiEvent.OnInputChanged("Text"))
        viewModel.onEvent(CheckUiEvent.OnResetCheck)

        assertNull(viewModel.uiState.value.result)
    }

    @Test
    fun photoSourceDialog_openAndDismiss_updatesState() {
        assertFalse(viewModel.uiState.value.showPhotoSourceDialog)

        viewModel.onEvent(CheckUiEvent.OnOpenPhotoSourceDialog)
        assertTrue(viewModel.uiState.value.showPhotoSourceDialog)

        viewModel.onEvent(CheckUiEvent.OnDismissPhotoSourceDialog)
        assertFalse(viewModel.uiState.value.showPhotoSourceDialog)
    }

    @Test
    fun onOcrImagesSelected_success_setsPreviewText() = runTest {
        val mockUri = mockk<android.net.Uri>()
        coEvery { textRecognitionEngine.recognizeTextFromUri(mockUri) } returns Result.success("Guten Tag Welt!")

        viewModel.onEvent(CheckUiEvent.OnOcrImagesSelected(listOf(mockUri)))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isOcrLoading)
        assertEquals("Guten Tag Welt!", viewModel.uiState.value.ocrPreviewText)
        assertNull(viewModel.uiState.value.ocrErrorMessage)
    }

    @Test
    fun onOcrImagesSelected_empty_setsErrorMessage() = runTest {
        val mockUri = mockk<android.net.Uri>()
        coEvery { textRecognitionEngine.recognizeTextFromUri(mockUri) } returns Result.success("")

        viewModel.onEvent(CheckUiEvent.OnOcrImagesSelected(listOf(mockUri)))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isOcrLoading)
        assertNull(viewModel.uiState.value.ocrPreviewText)
        assertEquals("ocr_error_no_text", viewModel.uiState.value.ocrErrorMessage)
    }

    @Test
    fun onInsertOcrText_appendsToInputAndDismisses() = runTest {
        viewModel.onEvent(CheckUiEvent.OnInputChanged("Bereits vorhanden."))
        viewModel.onEvent(CheckUiEvent.OnOcrPreviewTextChanged("Neu erkannt."))

        viewModel.onEvent(CheckUiEvent.OnInsertOcrText)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Bereits vorhanden.\n\nNeu erkannt.", viewModel.uiState.value.inputText)
        assertNull(viewModel.uiState.value.ocrPreviewText)
    }

    @Test
    fun onDismissOcrPreview_clearsPreviewText() {
        viewModel.onEvent(CheckUiEvent.OnOcrPreviewTextChanged("Text"))
        viewModel.onEvent(CheckUiEvent.OnDismissOcrPreview)

        assertNull(viewModel.uiState.value.ocrPreviewText)
    }
}
