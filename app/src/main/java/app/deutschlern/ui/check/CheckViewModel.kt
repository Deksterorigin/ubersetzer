package app.deutschlern.ui.check

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deutschlern.R
import app.deutschlern.data.local.security.SecureKeyStorage
import app.deutschlern.domain.check.TokenDiffEngine
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.check.CheckEditHunk
import app.deutschlern.domain.model.check.TextCheckEntry
import app.deutschlern.domain.model.check.TextCheckResult
import app.deutschlern.domain.ocr.TextRecognitionEngine
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.repository.TextCheckRepository
import app.deutschlern.domain.repository.VocabularyRepository
import app.deutschlern.domain.usecase.CheckTextUseCase
import app.deutschlern.domain.usecase.TranslateWordUseCase
import app.deutschlern.util.AppStrings
import app.deutschlern.util.TempImageCleaner
import app.deutschlern.util.runSuspendCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CheckViewModel @Inject constructor(
    private val checkTextUseCase: CheckTextUseCase,
    private val textCheckRepository: TextCheckRepository,
    private val settingsRepository: SettingsRepository,
    private val translateWordUseCase: TranslateWordUseCase,
    private val vocabularyRepository: VocabularyRepository,
    private val textRecognitionEngine: TextRecognitionEngine,
    private val secureKeyStorage: SecureKeyStorage,
    @ApplicationContext private val appContext: Context,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    companion object {
        private const val KEY_CHECK_INPUT = "saved_check_input"
        private const val MAX_OCR_PAGES = 3
    }

    private val _uiState = MutableStateFlow(CheckUiState())
    val uiState: StateFlow<CheckUiState> = _uiState.asStateFlow()

    private var checkJob: Job? = null
    private var ocrJob: Job? = null
    private var historySaveJob: Job? = null
    private var savedEntry: TextCheckEntry? = null
    private val ocrImageUris = mutableListOf<Uri>()

    init {
        savedStateHandle.get<String>(KEY_CHECK_INPUT)?.let { saved ->
            if (saved.isNotBlank() && _uiState.value.inputText.isBlank()) {
                handleInputChanged(saved)
            }
        }

        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _uiState.update { current ->
                    current.copy(
                        partnerLanguage = settings.selectedPartnerLanguage
                    )
                }
                refreshProviderKey()
            }
        }
    }

    fun onEvent(event: CheckUiEvent) {
        when (event) {
            is CheckUiEvent.OnInputChanged -> handleInputChanged(event.text)
            is CheckUiEvent.OnClearInput -> handleClearInput()
            is CheckUiEvent.OnPasteText -> handleInsertedText(event.text)
            is CheckUiEvent.OnCheckClicked -> handleCheckClicked()
            is CheckUiEvent.OnRetryClicked -> handleCheckClicked()
            is CheckUiEvent.OnDismissError -> _uiState.update { it.copy(errorMessage = null) }
            is CheckUiEvent.OnRefreshProviderKey -> viewModelScope.launch { refreshProviderKey() }
            is CheckUiEvent.OnSelectViewMode -> _uiState.update { it.copy(viewMode = event.mode) }
            is CheckUiEvent.OnSelectCategoryFilter -> _uiState.update { it.copy(categoryFilter = event.category) }
            is CheckUiEvent.OnOpenHunkDetail -> _uiState.update { it.copy(activeHunkId = event.hunk.id) }
            is CheckUiEvent.OnDismissHunkDetail -> _uiState.update { it.copy(activeHunkId = null) }
            is CheckUiEvent.OnToggleHunkAcceptance -> handleToggleHunkAcceptance(event.hunkId)
            is CheckUiEvent.OnAcceptAllHunks -> setAllHunksAccepted(true)
            is CheckUiEvent.OnRejectAllHunks -> setAllHunksAccepted(false)
            is CheckUiEvent.OnAddHunkToVocabulary -> handleAddHunkToVocabulary(event.hunk)
            is CheckUiEvent.OnShowMessage -> _uiState.update { it.copy(snackbarMessage = event.message) }
            is CheckUiEvent.OnDismissMessage -> _uiState.update { it.copy(snackbarMessage = null) }
            is CheckUiEvent.OnResetCheck -> handleResetCheck()
            is CheckUiEvent.OnAcceptConsent -> handleAcceptConsent()
            is CheckUiEvent.OnDeclineConsent -> _uiState.update { it.copy(showConsentDialog = false) }
            is CheckUiEvent.OnLoadSavedCheck -> handleLoadSavedCheck(event.checkId)
            is CheckUiEvent.OnOpenPhotoSourceDialog -> _uiState.update { it.copy(showPhotoSourceDialog = true) }
            is CheckUiEvent.OnDismissPhotoSourceDialog -> _uiState.update { it.copy(showPhotoSourceDialog = false) }
            is CheckUiEvent.OnOcrImagesSelected -> handleOcrImages(event.uris)
            is CheckUiEvent.OnOcrSourceFailed -> _uiState.update { it.copy(ocrError = OcrError.SOURCE_UNAVAILABLE) }
            is CheckUiEvent.OnOcrSelectPage -> handleOcrSelectPage(event.pageIndex)
            is CheckUiEvent.OnOcrSelectionChanged -> updateCurrentOcrPage { it.copy(selectedIds = event.wordIds) }
            is CheckUiEvent.OnOcrSelectAll -> updateCurrentOcrPage { page ->
                page.copy(selectedIds = page.layout.words.map { it.id }.toSet())
            }
            is CheckUiEvent.OnOcrClearSelection -> updateCurrentOcrPage { it.copy(selectedIds = emptySet()) }
            is CheckUiEvent.OnOcrConfirmSelection -> handleOcrConfirmSelection()
            is CheckUiEvent.OnOcrDiscardSelection -> finishOcrSelection()
            is CheckUiEvent.OnOcrPreviewTextChanged -> _uiState.update { it.copy(ocrPreviewText = event.text) }
            is CheckUiEvent.OnInsertOcrText -> handleInsertOcrText()
            is CheckUiEvent.OnDismissOcrPreview -> _uiState.update { it.copy(ocrPreviewText = null) }
            is CheckUiEvent.OnDismissOcrError -> _uiState.update { it.copy(ocrError = null) }
        }
    }

    private suspend fun refreshProviderKey() {
        val hasKey = runSuspendCatching {
            !secureKeyStorage.getKey(checkTextUseCase.activeProviderId()).isNullOrBlank()
        }.getOrDefault(true)
        _uiState.update { it.copy(hasProviderKey = hasKey) }
    }

    private fun handleInputChanged(text: String) {
        val safeText = text.take(MAX_CHECK_INPUT_CHARS)
        savedStateHandle[KEY_CHECK_INPUT] = safeText
        _uiState.update {
            it.copy(
                inputText = safeText,
                errorMessage = null
            )
        }
    }

    /** Pasted or scanned text can exceed the limit unnoticed, unlike typing next to the counter. */
    private fun handleInsertedText(text: String) {
        handleInputChanged(text)
        if (text.length > MAX_CHECK_INPUT_CHARS) {
            val message = AppStrings.get(R.string.check_text_truncated, MAX_CHECK_INPUT_CHARS)
            _uiState.update { it.copy(snackbarMessage = message) }
        }
    }

    private fun handleClearInput() {
        savedStateHandle.remove<String>(KEY_CHECK_INPUT)
        savedEntry = null
        _uiState.update {
            it.copy(
                inputText = "",
                result = null,
                errorMessage = null,
                activeHunkId = null
            )
        }
    }

    private fun handleCheckClicked() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            if (!settings.hasAcceptedCheckConsent) {
                _uiState.update { it.copy(showConsentDialog = true) }
                return@launch
            }
            executeCheck(text)
        }
    }

    private fun handleAcceptConsent() {
        viewModelScope.launch {
            settingsRepository.setHasAcceptedCheckConsent(true)
            _uiState.update { it.copy(showConsentDialog = false) }
            val text = _uiState.value.inputText.trim()
            if (text.isNotBlank()) executeCheck(text)
        }
    }

    private fun executeCheck(text: String) {
        checkJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null
            )
        }

        checkJob = viewModelScope.launch {
            val partnerLang = _uiState.value.partnerLanguage
            val checkResult = checkTextUseCase(text, partnerLang)

            checkResult.fold(
                onSuccess = { res ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            result = res,
                            viewMode = CheckViewMode.CORRECTED,
                            categoryFilter = null,
                            providerModelAttribution = res.providerModel,
                            errorMessage = null
                        )
                    }
                    saveNewCheckToHistory(res)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: AppStrings.get(R.string.check_error_generic)
                        )
                    }
                }
            )
        }
    }

    private fun saveNewCheckToHistory(res: TextCheckResult) {
        val entry = TextCheckEntry(
            originalText = res.originalText,
            correctedText = res.correctedText,
            finalText = TokenDiffEngine.assembleFinalText(res.originalText, res.hunks),
            hunks = res.hunks,
            comments = res.comments,
            createdAt = System.currentTimeMillis(),
            providerModel = res.providerModel,
            partnerLanguage = res.partnerLanguage
        )
        savedEntry = null
        historySaveJob = viewModelScope.launch {
            runSuspendCatching { textCheckRepository.saveCheck(entry) }
                .onSuccess { id -> savedEntry = entry.copy(id = id) }
        }
    }

    /** Writes are chained so that a quick series of toggles ends with the latest state in history. */
    private fun persistAcceptance() {
        val previousSave = historySaveJob
        historySaveJob = viewModelScope.launch {
            previousSave?.join()
            val saved = savedEntry ?: return@launch
            val res = _uiState.value.result ?: return@launch
            val updated = saved.copy(
                hunks = res.hunks,
                finalText = TokenDiffEngine.assembleFinalText(res.originalText, res.hunks)
            )
            runSuspendCatching {
                // The save replaces by id, which would resurrect an entry deleted in history meanwhile
                if (textCheckRepository.getCheckById(saved.id) != null) {
                    textCheckRepository.saveCheck(updated)
                    savedEntry = updated
                }
            }
        }
    }

    private fun handleToggleHunkAcceptance(hunkId: String) {
        val currentResult = _uiState.value.result ?: return
        val targetHunk = currentResult.hunks.firstOrNull { it.id == hunkId } ?: return
        val newAccepted = !targetHunk.isAccepted

        val updatedHunks = currentResult.hunks.map { hunk ->
            if (hunk.id == hunkId || (targetHunk.groupId != null && hunk.groupId == targetHunk.groupId)) {
                hunk.copy(isAccepted = newAccepted)
            } else {
                hunk
            }
        }

        _uiState.update { it.copy(result = currentResult.copy(hunks = updatedHunks)) }
        persistAcceptance()
    }

    private fun setAllHunksAccepted(accepted: Boolean) {
        val currentResult = _uiState.value.result ?: return
        val updatedHunks = currentResult.hunks.map { it.copy(isAccepted = accepted) }
        _uiState.update {
            it.copy(result = currentResult.copy(hunks = updatedHunks))
        }
        persistAcceptance()
    }

    private fun handleAddHunkToVocabulary(hunk: CheckEditHunk) {
        val word = hunk.cleanCorrectedWord ?: return
        if (_uiState.value.isAddingWordToVocab) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAddingWordToVocab = true) }
            val direction = TranslationDirection.getDirection(
                partner = _uiState.value.partnerLanguage,
                fromGerman = true
            )

            val outcome = runSuspendCatching {
                val translation = translateWordUseCase(
                    word = word,
                    direction = direction,
                    context = hunk.original
                ).getOrThrow()
                vocabularyRepository.saveWordTranslation(translation.data)
            }

            val message = outcome.fold(
                onSuccess = { AppStrings.get(R.string.check_word_added, word) },
                onFailure = { it.localizedMessage ?: AppStrings.get(R.string.check_word_add_failed) }
            )
            // The sheet closes because it would cover the snackbar
            _uiState.update {
                it.copy(
                    isAddingWordToVocab = false,
                    activeHunkId = null,
                    snackbarMessage = message
                )
            }
        }
    }

    private fun handleResetCheck() {
        savedEntry = null
        _uiState.update {
            it.copy(
                result = null,
                activeHunkId = null,
                errorMessage = null
            )
        }
    }

    private fun handleLoadSavedCheck(checkId: Long) {
        viewModelScope.launch {
            val entry = textCheckRepository.getCheckById(checkId) ?: return@launch
            handleInputChanged(entry.originalText)
            val hunks = TokenDiffEngine.restoreSavedHunks(
                originalText = entry.originalText,
                correctedText = entry.correctedText,
                saved = entry.hunks
            )
            savedEntry = entry
            _uiState.update {
                it.copy(
                    result = TextCheckResult(
                        originalText = entry.originalText,
                        correctedText = entry.correctedText,
                        hunks = hunks,
                        comments = entry.comments,
                        providerModel = entry.providerModel,
                        partnerLanguage = entry.partnerLanguage
                    ),
                    viewMode = CheckViewMode.CORRECTED,
                    categoryFilter = null,
                    activeHunkId = null,
                    providerModelAttribution = entry.providerModel
                )
            }
        }
    }

    fun getAssembledFinalText(): String {
        val res = _uiState.value.result ?: return _uiState.value.inputText
        return TokenDiffEngine.assembleFinalText(res.originalText, res.hunks)
    }

    private fun handleOcrImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        // The picker's limit is not enforced by its fallback on devices without the photo picker
        val (accepted, surplus) = uris.withIndex().partition { it.index < MAX_OCR_PAGES }
        surplus.forEach { TempImageCleaner.deleteTempUri(appContext, it.value) }

        releaseOcrImages()
        ocrImageUris += accepted.map { it.value }
        ocrJob?.cancel()
        ocrJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isOcrLoading = true,
                    ocrError = null,
                    ocrSelection = null,
                    showPhotoSourceDialog = false
                )
            }
            val pages = mutableListOf<OcrSelectionPage>()
            var failedCount = 0
            for ((_, uri) in accepted) {
                runSuspendCatching { textRecognitionEngine.scanPage(uri).getOrThrow() }
                    .onSuccess { pages += OcrSelectionPage(it.bitmap, it.layout) }
                    .onFailure { failedCount++ }
            }

            val firstPageWithText = pages.indexOfFirst { it.layout.words.isNotEmpty() }
            if (firstPageWithText == -1) {
                releaseOcrImages()
                val error = if (pages.isEmpty()) OcrError.RECOGNITION_FAILED else OcrError.NO_TEXT
                _uiState.update { it.copy(isOcrLoading = false, ocrError = error) }
            } else {
                _uiState.update {
                    it.copy(
                        isOcrLoading = false,
                        ocrSelection = OcrSelectionState(
                            pages = pages,
                            currentPageIndex = firstPageWithText,
                            failedPageCount = failedCount
                        )
                    )
                }
            }
        }
    }

    private fun handleOcrSelectPage(pageIndex: Int) {
        _uiState.update { state ->
            val selection = state.ocrSelection
            if (selection == null || pageIndex !in selection.pages.indices) {
                state
            } else {
                state.copy(ocrSelection = selection.copy(currentPageIndex = pageIndex))
            }
        }
    }

    private fun updateCurrentOcrPage(transform: (OcrSelectionPage) -> OcrSelectionPage) {
        _uiState.update { state ->
            val selection = state.ocrSelection ?: return@update state
            val pages = selection.pages.toMutableList().apply {
                this[selection.currentPageIndex] = transform(this[selection.currentPageIndex])
            }
            state.copy(ocrSelection = selection.copy(pages = pages))
        }
    }

    private fun handleOcrConfirmSelection() {
        val text = _uiState.value.ocrSelection?.selectedText.orEmpty()
        if (text.isBlank()) return
        _uiState.update { it.copy(ocrPreviewText = text) }
    }

    private fun handleInsertOcrText() {
        val preview = _uiState.value.ocrPreviewText
        if (!preview.isNullOrBlank()) {
            val current = _uiState.value.inputText
            handleInsertedText(if (current.isBlank()) preview else "$current\n\n$preview")
        }
        finishOcrSelection()
    }

    private fun finishOcrSelection() {
        ocrJob?.cancel()
        releaseOcrImages()
        _uiState.update {
            it.copy(ocrSelection = null, ocrPreviewText = null, isOcrLoading = false)
        }
    }

    private fun releaseOcrImages() {
        ocrImageUris.forEach { TempImageCleaner.deleteTempUri(appContext, it) }
        ocrImageUris.clear()
    }

    override fun onCleared() {
        super.onCleared()
        checkJob?.cancel()
        releaseOcrImages()
    }
}
