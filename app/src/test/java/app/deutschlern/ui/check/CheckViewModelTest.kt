package app.deutschlern.ui.check

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.deutschlern.R
import app.deutschlern.data.local.security.SecureKeyStorage
import app.deutschlern.domain.check.TokenDiffEngine
import app.deutschlern.domain.model.TranslationResult
import app.deutschlern.domain.model.WordTranslation
import app.deutschlern.domain.model.check.CheckCategory
import app.deutschlern.domain.model.check.CheckEditHunk
import app.deutschlern.domain.model.check.TextCheckEntry
import app.deutschlern.domain.model.check.TextCheckResult
import app.deutschlern.domain.ocr.OcrPageLayout
import app.deutschlern.domain.ocr.OcrScannedPage
import app.deutschlern.domain.ocr.OcrWord
import app.deutschlern.domain.ocr.TextRecognitionEngine
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.repository.TextCheckRepository
import app.deutschlern.domain.repository.UserSettings
import app.deutschlern.domain.repository.VocabularyRepository
import app.deutschlern.domain.usecase.CheckTextUseCase
import app.deutschlern.domain.usecase.TranslateWordUseCase
import app.deutschlern.util.AppStrings
import app.deutschlern.util.TempImageCleaner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
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
    private val textRecognitionEngine = mockk<TextRecognitionEngine>()
    private val secureKeyStorage = mockk<SecureKeyStorage>()
    private val context = mockk<android.content.Context>(relaxed = true)

    private lateinit var viewModel: CheckViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(TempImageCleaner)
        every { TempImageCleaner.deleteTempUri(any(), any()) } just runs
        coEvery { settingsRepository.settingsFlow } returns flowOf(UserSettings())
        coEvery { settingsRepository.getSettings() } returns UserSettings()
        coEvery { settingsRepository.setHasAcceptedCheckConsent(any()) } returns Unit
        coEvery { checkTextUseCase.activeProviderId() } returns "gemini"
        coEvery { secureKeyStorage.getKey(any()) } returns "key"

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        unmockkObject(TempImageCleaner)
        Dispatchers.resetMain()
    }

    private fun createViewModel() = CheckViewModel(
        checkTextUseCase = checkTextUseCase,
        textCheckRepository = textCheckRepository,
        settingsRepository = settingsRepository,
        translateWordUseCase = translateWordUseCase,
        vocabularyRepository = vocabularyRepository,
        textRecognitionEngine = textRecognitionEngine,
        secureKeyStorage = secureKeyStorage,
        appContext = context,
        savedStateHandle = SavedStateHandle()
    )

    private fun hunk(
        id: String,
        category: CheckCategory = CheckCategory.GRAMMAR,
        groupId: String? = null,
        isAccepted: Boolean = true,
        cleanWord: String? = null
    ) = CheckEditHunk(
        id = id,
        original = "ein",
        corrected = "einen",
        category = category,
        explanation = "Akkusativ",
        isAccepted = isAccepted,
        startOffset = 9,
        endOffset = 12,
        isSingleWord = cleanWord != null,
        cleanCorrectedWord = cleanWord,
        groupId = groupId
    )

    private fun loadResult(vararg hunks: CheckEditHunk) {
        val checkResult = TextCheckResult(
            originalText = "Ich habe ein Hund.",
            correctedText = "Ich habe einen Hund.",
            hunks = hunks.toList(),
            partnerLanguage = "ru"
        )
        coEvery { checkTextUseCase(any(), any(), any()) } returns Result.success(checkResult)
        coEvery { textCheckRepository.saveCheck(any()) } returns 1L
        viewModel.onEvent(CheckUiEvent.OnInputChanged("Ich habe ein Hund."))
        viewModel.onEvent(CheckUiEvent.OnAcceptConsent)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun scanned(vararg words: String): OcrScannedPage = OcrScannedPage(
        bitmap = mockk<Bitmap>(),
        layout = OcrPageLayout(
            width = 100,
            height = 100,
            words = words.mapIndexed { index, text -> OcrWord(index, text, 0, 0, emptyList()) }
        )
    )

    private fun uriScanning(result: Result<OcrScannedPage>): Uri = mockk<Uri>().also { uri ->
        coEvery { textRecognitionEngine.scanPage(uri) } returns result
    }

    private fun startSelection(vararg uris: Uri) {
        viewModel.onEvent(CheckUiEvent.OnOcrImagesSelected(uris.toList()))
        testDispatcher.scheduler.advanceUntilIdle()
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
    fun categoryFilter_narrowsHunksAndCountsCoverAllCategories() {
        loadResult(
            hunk("a", CheckCategory.SPELLING),
            hunk("b", CheckCategory.GRAMMAR),
            hunk("c", CheckCategory.GRAMMAR)
        )

        viewModel.onEvent(CheckUiEvent.OnSelectCategoryFilter(CheckCategory.GRAMMAR))

        val state = viewModel.uiState.value
        assertEquals(listOf("b", "c"), state.filteredHunks.map { it.id })
        assertEquals(mapOf(CheckCategory.SPELLING to 1, CheckCategory.GRAMMAR to 2), state.categoryCounts)
    }

    @Test
    fun onToggleHunkAcceptance_flipsStateAndRecalculatesFinalText() {
        loadResult(hunk("h1"))

        assertTrue(viewModel.uiState.value.result?.hunks?.first()?.isAccepted == true)
        assertEquals("Ich habe einen Hund.", viewModel.getAssembledFinalText())

        viewModel.onEvent(CheckUiEvent.OnToggleHunkAcceptance("h1"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.result?.hunks?.first()?.isAccepted == true)
        assertEquals("Ich habe ein Hund.", viewModel.getAssembledFinalText())
    }

    @Test
    fun acceptAndRejectAll_setEveryHunk() {
        loadResult(hunk("a", isAccepted = false), hunk("b"))

        viewModel.onEvent(CheckUiEvent.OnAcceptAllHunks)
        assertTrue(viewModel.uiState.value.allEditsAccepted)
        assertEquals(2, viewModel.uiState.value.acceptedEditsCount)

        viewModel.onEvent(CheckUiEvent.OnRejectAllHunks)
        assertEquals(0, viewModel.uiState.value.acceptedEditsCount)
    }

    @Test
    fun activeHunkDetail_followsAcceptAllAndGroupToggles() {
        loadResult(hunk("a", groupId = "g"), hunk("b", groupId = "g"))
        viewModel.onEvent(CheckUiEvent.OnOpenHunkDetail(hunk("b", groupId = "g")))

        viewModel.onEvent(CheckUiEvent.OnToggleHunkAcceptance("a"))
        assertEquals(false, viewModel.uiState.value.activeHunkDetail?.isAccepted)

        viewModel.onEvent(CheckUiEvent.OnAcceptAllHunks)
        assertEquals(true, viewModel.uiState.value.activeHunkDetail?.isAccepted)
    }

    @Test
    fun toggle_persistsFinalTextAndAcceptanceToHistory() {
        loadResult(hunk("h1"))
        val first = TextCheckEntry(
            id = 1,
            originalText = "",
            correctedText = "",
            finalText = "",
            hunks = emptyList(),
            comments = emptyList(),
            createdAt = 0,
            providerModel = "",
            partnerLanguage = "ru"
        )
        coEvery { textCheckRepository.getCheckById(1L) } returns first
        val saved = slot<TextCheckEntry>()
        coEvery { textCheckRepository.saveCheck(capture(saved)) } returns 1L

        viewModel.onEvent(CheckUiEvent.OnToggleHunkAcceptance("h1"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1L, saved.captured.id)
        assertEquals("Ich habe ein Hund.", saved.captured.finalText)
        assertFalse(saved.captured.hunks.first().isAccepted)
    }

    @Test
    fun toggle_doesNotResurrectAnEntryDeletedFromHistory() {
        loadResult(hunk("h1"))
        coEvery { textCheckRepository.getCheckById(1L) } returns null

        viewModel.onEvent(CheckUiEvent.OnToggleHunkAcceptance("h1"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { textCheckRepository.saveCheck(any()) }
    }

    @Test
    fun loadSavedCheck_restoresOffsetsSoTheDiffAndFinalTextWork() {
        val original = "Ich habe ein Hund."
        val corrected = "Ich habe einen Hund."
        coEvery { textCheckRepository.getCheckById(7L) } returns TextCheckEntry(
            id = 7,
            originalText = original,
            correctedText = corrected,
            finalText = original,
            hunks = listOf(
                CheckEditHunk(
                    id = "saved_7_0",
                    original = "ein",
                    corrected = "einen",
                    category = CheckCategory.SPELLING,
                    explanation = "Akkusativ",
                    isAccepted = false
                )
            ),
            comments = emptyList(),
            createdAt = 0,
            providerModel = "",
            partnerLanguage = "ru"
        )

        viewModel.onEvent(CheckUiEvent.OnLoadSavedCheck(7L))
        testDispatcher.scheduler.advanceUntilIdle()

        val restored = viewModel.uiState.value.result!!.hunks.single()
        assertEquals(9, restored.startOffset)
        assertEquals(12, restored.endOffset)
        assertEquals(CheckCategory.SPELLING, restored.category)
        assertFalse(restored.isAccepted)
        assertEquals(original, viewModel.getAssembledFinalText())

        viewModel.onEvent(CheckUiEvent.OnToggleHunkAcceptance("saved_7_0"))
        assertEquals(corrected, viewModel.getAssembledFinalText())
    }

    @Test
    fun acceptConsent_withBlankText_doesNotRunCheck() {
        viewModel.onEvent(CheckUiEvent.OnAcceptConsent)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showConsentDialog)
        assertFalse(viewModel.uiState.value.isLoading)
        coVerify(exactly = 0) { checkTextUseCase(any(), any(), any()) }
    }

    @Test
    fun missingProviderKey_blocksCheckUntilRefreshFindsOne() {
        coEvery { secureKeyStorage.getKey(any()) } returns null
        viewModel.onEvent(CheckUiEvent.OnInputChanged("Text"))
        viewModel.onEvent(CheckUiEvent.OnRefreshProviderKey)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.hasProviderKey)
        assertFalse(viewModel.uiState.value.canCheck)

        coEvery { secureKeyStorage.getKey(any()) } returns "key"
        viewModel.onEvent(CheckUiEvent.OnRefreshProviderKey)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canCheck)
    }

    @Test
    fun addToVocabulary_saveThrows_clearsBusyFlagAndReports() {
        loadResult(hunk("h1", cleanWord = "einen"))
        val translation = mockk<TranslationResult<WordTranslation>>()
        every { translation.data } returns mockk()
        coEvery { translateWordUseCase(any(), any(), any()) } returns Result.success(translation)
        coEvery { vocabularyRepository.saveWordTranslation(any()) } throws IllegalStateException("db down")
        viewModel.onEvent(CheckUiEvent.OnOpenHunkDetail(hunk("h1")))

        viewModel.onEvent(CheckUiEvent.OnAddHunkToVocabulary(hunk("h1", cleanWord = "einen")))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAddingWordToVocab)
        assertEquals("db down", state.snackbarMessage)
        assertNull(state.activeHunkDetail)
    }

    @Test
    fun addToVocabulary_translationFails_clearsBusyFlagAndFallsBackToGenericMessage() {
        loadResult(hunk("h1", cleanWord = "einen"))
        coEvery { translateWordUseCase(any(), any(), any()) } returns Result.failure(RuntimeException())

        viewModel.onEvent(CheckUiEvent.OnAddHunkToVocabulary(hunk("h1", cleanWord = "einen")))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAddingWordToVocab)
        assertEquals(AppStrings.get(R.string.check_word_add_failed), viewModel.uiState.value.snackbarMessage)
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
    fun ocrImages_startSelectionWithNothingSelectedAndKeepTheTempFile() = runTest {
        val uri = uriScanning(Result.success(scanned("Guten", "Tag")))

        startSelection(uri)

        val state = viewModel.uiState.value
        assertFalse(state.isOcrLoading)
        assertEquals(0, state.ocrSelection?.selectedWordCount)
        assertNull(state.ocrPreviewText)
        verify(exactly = 0) { TempImageCleaner.deleteTempUri(any(), any()) }
    }

    @Test
    fun ocrImages_noPageHasText_reportsNoTextAndCleansUp() = runTest {
        val uri = uriScanning(Result.success(scanned()))

        startSelection(uri)

        val state = viewModel.uiState.value
        assertNull(state.ocrSelection)
        assertEquals(OcrError.NO_TEXT, state.ocrError)
        verify { TempImageCleaner.deleteTempUri(context, uri) }
    }

    @Test
    fun ocrImages_allPagesFail_reportsFailureAndCleansUp() = runTest {
        val uri = uriScanning(Result.failure(RuntimeException("boom")))

        startSelection(uri)

        assertEquals(OcrError.RECOGNITION_FAILED, viewModel.uiState.value.ocrError)
        verify { TempImageCleaner.deleteTempUri(context, uri) }
    }

    @Test
    fun ocrImages_onePageFails_otherPagesSurviveAndFailureIsCounted() = runTest {
        val bad = uriScanning(Result.failure(RuntimeException("boom")))
        val good = uriScanning(Result.success(scanned("Hallo")))

        startSelection(bad, good)

        val selection = viewModel.uiState.value.ocrSelection
        assertNotNull(selection)
        assertEquals(1, selection!!.pages.size)
        assertEquals(1, selection.failedPageCount)
    }

    @Test
    fun ocrImages_engineThrows_otherPagesSurvive() = runTest {
        val bad = mockk<Uri>()
        coEvery { textRecognitionEngine.scanPage(bad) } throws IllegalStateException("boom")
        val good = uriScanning(Result.success(scanned("Hallo")))

        startSelection(bad, good)

        assertEquals(1, viewModel.uiState.value.ocrSelection?.failedPageCount)
    }

    @Test
    fun ocrImages_emptyPageDoesNotBlockAndSelectionOpensOnFirstPageWithText() = runTest {
        val empty = uriScanning(Result.success(scanned()))
        val good = uriScanning(Result.success(scanned("Hallo")))

        startSelection(empty, good)

        val selection = viewModel.uiState.value.ocrSelection!!
        assertEquals(2, selection.pages.size)
        assertEquals(1, selection.currentPageIndex)
    }

    @Test
    fun ocrImages_moreThanThree_scansThreeAndCleansTheRest() = runTest {
        val uris = List(3) { uriScanning(Result.success(scanned("Hallo"))) }
        val surplus = mockk<Uri>()

        startSelection(*uris.toTypedArray(), surplus)

        assertEquals(3, viewModel.uiState.value.ocrSelection?.pages?.size)
        coVerify(exactly = 0) { textRecognitionEngine.scanPage(surplus) }
        verify { TempImageCleaner.deleteTempUri(context, surplus) }
    }

    @Test
    fun selection_isKeptPerPageAndAssembledAcrossPages() = runTest {
        startSelection(
            uriScanning(Result.success(scanned("Erste", "Seite"))),
            uriScanning(Result.success(scanned("Zweite", "Seite")))
        )

        viewModel.onEvent(CheckUiEvent.OnOcrSelectionChanged(setOf(0)))
        viewModel.onEvent(CheckUiEvent.OnOcrSelectPage(1))
        assertTrue(viewModel.uiState.value.ocrSelection!!.currentPage.selectedIds.isEmpty())
        viewModel.onEvent(CheckUiEvent.OnOcrSelectionChanged(setOf(1)))
        viewModel.onEvent(CheckUiEvent.OnOcrSelectPage(0))

        val selection = viewModel.uiState.value.ocrSelection!!
        assertEquals(setOf(0), selection.currentPage.selectedIds)
        assertEquals(2, selection.selectedWordCount)
        assertEquals("Erste\n\nSeite", selection.selectedText)
    }

    @Test
    fun selectAllAndClear_affectOnlyTheCurrentPage() = runTest {
        startSelection(
            uriScanning(Result.success(scanned("a", "b"))),
            uriScanning(Result.success(scanned("c")))
        )

        viewModel.onEvent(CheckUiEvent.OnOcrSelectAll)
        viewModel.onEvent(CheckUiEvent.OnOcrSelectPage(1))
        viewModel.onEvent(CheckUiEvent.OnOcrSelectAll)
        viewModel.onEvent(CheckUiEvent.OnOcrClearSelection)

        val pages = viewModel.uiState.value.ocrSelection!!.pages
        assertEquals(setOf(0, 1), pages[0].selectedIds)
        assertTrue(pages[1].selectedIds.isEmpty())
    }

    @Test
    fun confirmWithNothingSelected_opensNoPreview() = runTest {
        startSelection(uriScanning(Result.success(scanned("Hallo"))))

        viewModel.onEvent(CheckUiEvent.OnOcrConfirmSelection)

        assertNull(viewModel.uiState.value.ocrPreviewText)
    }

    @Test
    fun cancellingPreview_returnsToTheSelectionWithImagesIntact() = runTest {
        startSelection(uriScanning(Result.success(scanned("Hallo"))))
        viewModel.onEvent(CheckUiEvent.OnOcrSelectAll)
        viewModel.onEvent(CheckUiEvent.OnOcrConfirmSelection)
        assertEquals("Hallo", viewModel.uiState.value.ocrPreviewText)

        viewModel.onEvent(CheckUiEvent.OnDismissOcrPreview)

        assertNull(viewModel.uiState.value.ocrPreviewText)
        assertEquals(setOf(0), viewModel.uiState.value.ocrSelection?.currentPage?.selectedIds)
        verify(exactly = 0) { TempImageCleaner.deleteTempUri(any(), any()) }
    }

    @Test
    fun insert_appendsToExistingInputEndsSelectionAndCleansUp() = runTest {
        val uri = uriScanning(Result.success(scanned("Neu", "erkannt.")))
        viewModel.onEvent(CheckUiEvent.OnInputChanged("Bereits vorhanden."))
        startSelection(uri)
        viewModel.onEvent(CheckUiEvent.OnOcrSelectAll)
        viewModel.onEvent(CheckUiEvent.OnOcrConfirmSelection)

        viewModel.onEvent(CheckUiEvent.OnInsertOcrText)

        val state = viewModel.uiState.value
        assertEquals("Bereits vorhanden.\n\nNeu erkannt.", state.inputText)
        assertNull(state.ocrPreviewText)
        assertNull(state.ocrSelection)
        verify(exactly = 1) { TempImageCleaner.deleteTempUri(context, uri) }
    }

    @Test
    fun insert_beyondTheLimit_truncatesAndTellsTheUser() = runTest {
        viewModel.onEvent(CheckUiEvent.OnOcrPreviewTextChanged("x".repeat(MAX_CHECK_INPUT_CHARS + 1)))

        viewModel.onEvent(CheckUiEvent.OnInsertOcrText)

        val state = viewModel.uiState.value
        assertEquals(MAX_CHECK_INPUT_CHARS, state.inputText.length)
        assertEquals(
            AppStrings.get(R.string.check_text_truncated, MAX_CHECK_INPUT_CHARS),
            state.snackbarMessage
        )
    }

    @Test
    fun discard_endsSelectionCleansUpAndLeavesInputAlone() = runTest {
        val uri = uriScanning(Result.success(scanned("Hallo")))
        viewModel.onEvent(CheckUiEvent.OnInputChanged("Bleibt."))
        startSelection(uri)

        viewModel.onEvent(CheckUiEvent.OnOcrDiscardSelection)

        val state = viewModel.uiState.value
        assertNull(state.ocrSelection)
        assertEquals("Bleibt.", state.inputText)
        verify(exactly = 1) { TempImageCleaner.deleteTempUri(context, uri) }
    }

    @Test
    fun clearingTheViewModel_cleansUpOpenSelection() = runTest {
        val uri = uriScanning(Result.success(scanned("Hallo")))
        startSelection(uri)

        CheckViewModel::class.java.getDeclaredMethod("onCleared").apply {
            isAccessible = true
            invoke(viewModel)
        }

        verify(exactly = 1) { TempImageCleaner.deleteTempUri(context, uri) }
    }

    @Test
    fun onInsertOcrText_withoutSelectionStillAppends() = runTest {
        viewModel.onEvent(CheckUiEvent.OnInputChanged("Bereits vorhanden."))
        viewModel.onEvent(CheckUiEvent.OnOcrPreviewTextChanged("Neu erkannt."))

        viewModel.onEvent(CheckUiEvent.OnInsertOcrText)

        assertEquals("Bereits vorhanden.\n\nNeu erkannt.", viewModel.uiState.value.inputText)
        assertNull(viewModel.uiState.value.ocrPreviewText)
    }

    @Test
    fun finalTextMatchesEngineAssembly() {
        loadResult(hunk("h1"))
        val hunks = viewModel.uiState.value.result!!.hunks
        assertEquals(
            TokenDiffEngine.assembleFinalText("Ich habe ein Hund.", hunks),
            viewModel.getAssembledFinalText()
        )
    }
}
