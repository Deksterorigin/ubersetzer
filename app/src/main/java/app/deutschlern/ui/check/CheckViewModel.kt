package app.deutschlern.ui.check

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deutschlern.domain.check.TokenDiffEngine
import app.deutschlern.domain.model.PartnerLanguage
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.check.CheckCategory
import app.deutschlern.domain.model.check.CheckEditHunk
import app.deutschlern.domain.model.check.TextCheckEntry
import app.deutschlern.domain.model.check.TextCheckResult
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.repository.TextCheckRepository
import app.deutschlern.domain.repository.VocabularyRepository
import app.deutschlern.domain.usecase.CheckTextUseCase
import app.deutschlern.domain.usecase.TranslateWordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import app.deutschlern.domain.ocr.TextRecognitionEngine
import app.deutschlern.util.TempImageCleaner
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val appContext: Context,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    companion object {
        private const val KEY_CHECK_INPUT = "saved_check_input"
        private const val MAX_INPUT_CHARS = 5000
    }

    private val _uiState = MutableStateFlow(CheckUiState())
    val uiState: StateFlow<CheckUiState> = _uiState.asStateFlow()

    private var checkJob: Job? = null

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
            }
        }
    }

    fun onEvent(event: CheckUiEvent) {
        when (event) {
            is CheckUiEvent.OnInputChanged -> handleInputChanged(event.text)
            is CheckUiEvent.OnClearInput -> handleClearInput()
            is CheckUiEvent.OnPasteText -> handleInputChanged(event.text)
            is CheckUiEvent.OnCheckClicked -> handleCheckClicked()
            is CheckUiEvent.OnRetryClicked -> handleCheckClicked()
            is CheckUiEvent.OnDismissError -> _uiState.update { it.copy(errorMessage = null) }
            is CheckUiEvent.OnSelectViewMode -> _uiState.update { it.copy(viewMode = event.mode) }
            is CheckUiEvent.OnSelectCategoryFilter -> _uiState.update { it.copy(categoryFilter = event.category) }
            is CheckUiEvent.OnOpenHunkDetail -> _uiState.update { it.copy(activeHunkDetail = event.hunk) }
            is CheckUiEvent.OnDismissHunkDetail -> _uiState.update { it.copy(activeHunkDetail = null) }
            is CheckUiEvent.OnToggleHunkAcceptance -> handleToggleHunkAcceptance(event.hunkId)
            is CheckUiEvent.OnAcceptAllHunks -> handleAcceptAllHunks()
            is CheckUiEvent.OnAddHunkToVocabulary -> handleAddHunkToVocabulary(event.hunk)
            is CheckUiEvent.OnDismissAddWordMessage -> _uiState.update { it.copy(addWordSuccessMessage = null) }
            is CheckUiEvent.OnResetCheck -> handleResetCheck()
            is CheckUiEvent.OnAcceptConsent -> handleAcceptConsent()
            is CheckUiEvent.OnDeclineConsent -> _uiState.update { it.copy(showConsentDialog = false) }
            is CheckUiEvent.OnLoadSavedCheck -> handleLoadSavedCheck(event.checkId)
            is CheckUiEvent.OnOpenPhotoSourceDialog -> _uiState.update { it.copy(showPhotoSourceDialog = true) }
            is CheckUiEvent.OnDismissPhotoSourceDialog -> _uiState.update { it.copy(showPhotoSourceDialog = false) }
            is CheckUiEvent.OnOcrImagesSelected -> handleOcrImages(event.uris)
            is CheckUiEvent.OnOcrPreviewTextChanged -> _uiState.update { it.copy(ocrPreviewText = event.text) }
            is CheckUiEvent.OnInsertOcrText -> handleInsertOcrText()
            is CheckUiEvent.OnDismissOcrPreview -> _uiState.update { it.copy(ocrPreviewText = null) }
            is CheckUiEvent.OnDismissOcrError -> _uiState.update { it.copy(ocrErrorMessage = null) }
        }
    }

    private fun handleInputChanged(text: String) {
        val safeText = if (text.length > MAX_INPUT_CHARS) text.take(MAX_INPUT_CHARS) else text
        savedStateHandle[KEY_CHECK_INPUT] = safeText
        _uiState.update {
            it.copy(
                inputText = safeText,
                errorMessage = null
            )
        }
    }

    private fun handleClearInput() {
        savedStateHandle.remove<String>(KEY_CHECK_INPUT)
        _uiState.update {
            it.copy(
                inputText = "",
                result = null,
                errorMessage = null,
                activeHunkDetail = null
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
            executeCheck(_uiState.value.inputText.trim())
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

                    // Save to history in background
                    viewModelScope.launch {
                        val finalText = TokenDiffEngine.assembleFinalText(res.originalText, res.hunks)
                        val entry = TextCheckEntry(
                            originalText = res.originalText,
                            correctedText = res.correctedText,
                            finalText = finalText,
                            hunks = res.hunks,
                            comments = res.comments,
                            createdAt = System.currentTimeMillis(),
                            providerModel = res.providerModel,
                            partnerLanguage = res.partnerLanguage
                        )
                        textCheckRepository.saveCheck(entry)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Ошибка при проверке текста"
                        )
                    }
                }
            )
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

        val updatedActiveHunk = if (_uiState.value.activeHunkDetail?.id == hunkId) {
            _uiState.value.activeHunkDetail?.copy(isAccepted = newAccepted)
        } else {
            _uiState.value.activeHunkDetail
        }

        _uiState.update {
            it.copy(
                result = currentResult.copy(hunks = updatedHunks),
                activeHunkDetail = updatedActiveHunk
            )
        }
    }

    private fun handleAcceptAllHunks() {
        val currentResult = _uiState.value.result ?: return
        val updatedHunks = currentResult.hunks.map { it.copy(isAccepted = true) }
        _uiState.update {
            it.copy(result = currentResult.copy(hunks = updatedHunks))
        }
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

            val translationResult = translateWordUseCase(
                word = word,
                direction = direction,
                context = hunk.original
            )

            translationResult.fold(
                onSuccess = { res ->
                    vocabularyRepository.saveWordTranslation(res.data)
                    _uiState.update {
                        it.copy(
                            isAddingWordToVocab = false,
                            addWordSuccessMessage = "«$word» добавлено в словарь"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isAddingWordToVocab = false,
                            errorMessage = err.localizedMessage ?: "Не удалось добавить слово"
                        )
                    }
                }
            )
        }
    }

    private fun handleResetCheck() {
        _uiState.update {
            it.copy(
                result = null,
                activeHunkDetail = null,
                errorMessage = null
            )
        }
    }

    private fun handleLoadSavedCheck(checkId: Long) {
        viewModelScope.launch {
            val entry = textCheckRepository.getCheckById(checkId) ?: return@launch
            handleInputChanged(entry.originalText)
            _uiState.update {
                it.copy(
                    result = TextCheckResult(
                        originalText = entry.originalText,
                        correctedText = entry.correctedText,
                        hunks = entry.hunks,
                        comments = entry.comments,
                        providerModel = entry.providerModel,
                        partnerLanguage = entry.partnerLanguage
                    ),
                    viewMode = CheckViewMode.CORRECTED,
                    categoryFilter = null,
                    providerModelAttribution = entry.providerModel
                )
            }
        }
    }

    fun getAssembledFinalText(): String {
        val res = _uiState.value.result ?: return _uiState.value.inputText
        return TokenDiffEngine.assembleFinalText(res.originalText, res.hunks)
    }

    private fun handleOcrImages(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOcrLoading = true, ocrErrorMessage = null, showPhotoSourceDialog = false) }
            val recognizedBlocks = mutableListOf<String>()
            try {
                for (uri in uris) {
                    try {
                        val ocrResult = textRecognitionEngine.recognizeTextFromUri(uri)
                        ocrResult.onSuccess { text ->
                            if (text.isNotBlank()) {
                                recognizedBlocks.add(text)
                            }
                        }
                    } finally {
                        TempImageCleaner.deleteTempUri(appContext, uri)
                    }
                }
                val combined = recognizedBlocks.joinToString("\n\n")
                if (combined.isNotBlank()) {
                    _uiState.update { it.copy(isOcrLoading = false, ocrPreviewText = combined) }
                } else {
                    _uiState.update { it.copy(isOcrLoading = false, ocrErrorMessage = "ocr_error_no_text") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isOcrLoading = false, ocrErrorMessage = "ocr_error_failed") }
            }
        }
    }

    private fun handleInsertOcrText() {
        val preview = _uiState.value.ocrPreviewText
        if (!preview.isNullOrBlank()) {
            val current = _uiState.value.inputText
            val combined = if (current.isBlank()) preview else "$current\n\n$preview"
            handleInputChanged(combined)
        }
        _uiState.update { it.copy(ocrPreviewText = null) }
    }

    override fun onCleared() {
        super.onCleared()
        checkJob?.cancel()
    }
}
