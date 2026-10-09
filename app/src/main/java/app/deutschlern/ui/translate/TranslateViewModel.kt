package app.deutschlern.ui.translate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deutschlern.data.local.security.SecureKeyStorage
import app.deutschlern.data.remote.provider.MlKitOfflineProvider
import app.deutschlern.domain.model.PartnerLanguage
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.TranslationMode
import app.deutschlern.domain.provider.ProviderError
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.repository.VocabularyRepository
import app.deutschlern.domain.usecase.DetectLanguageUseCase
import app.deutschlern.domain.usecase.DetectModeUseCase
import app.deutschlern.domain.usecase.TranslateTextUseCase
import app.deutschlern.domain.usecase.TranslateWordUseCase
import app.deutschlern.util.PendingTranslationHolder
import app.deutschlern.util.TextNormalizer
import app.deutschlern.util.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import app.deutschlern.domain.stt.SpeechToTextEngine
import app.deutschlern.domain.stt.SttEvent
import kotlinx.coroutines.flow.first

@HiltViewModel
class TranslateViewModel @Inject constructor(
    private val translateTextUseCase: TranslateTextUseCase,
    private val translateWordUseCase: TranslateWordUseCase,
    private val detectLanguageUseCase: DetectLanguageUseCase,
    private val detectModeUseCase: DetectModeUseCase,
    private val vocabularyRepository: VocabularyRepository,
    private val settingsRepository: SettingsRepository,
    private val secureKeyStorage: SecureKeyStorage,
    private val mlKitOfflineProvider: MlKitOfflineProvider,
    private val ttsManager: TtsManager,
    private val speechToTextEngine: SpeechToTextEngine,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val pendingTranslationHolder: PendingTranslationHolder = PendingTranslationHolder()
) : ViewModel() {

    companion object {
        private const val KEY_INPUT_TEXT = "saved_input_text"
        private val ALL_ONLINE_PROVIDERS = listOf("gemini", "groq", "mistral", "cerebras", "openrouter", "custom", "deepl")
    }

    private val _uiState = MutableStateFlow(TranslateUiState())
    val uiState: StateFlow<TranslateUiState> = _uiState.asStateFlow()

    private var translationJob: Job? = null
    private var speechJob: Job? = null
    private var currentWordEntryId: Long? = null

    init {
        savedStateHandle.get<String>(KEY_INPUT_TEXT)?.let { saved ->
            if (saved.isNotBlank() && _uiState.value.inputText.isBlank()) {
                handleInputChanged(saved)
            }
        }

        viewModelScope.launch {
            pendingTranslationHolder.pendingText.collect { text ->
                if (text.isNotBlank()) {
                    pendingTranslationHolder.consumePendingText()
                    handleInputChanged(text)
                    handleTranslate()
                }
            }
        }

        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _uiState.update { current ->
                    val partner = settings.selectedPartnerLanguage
                    val newDir = TranslationDirection.getDirection(partner, fromGerman = current.direction.isFromGerman)
                    current.copy(
                        showModelCaption = settings.showModelCaption,
                        selectedPartnerLanguage = partner,
                        direction = if (!current.hasManualDirectionOverride) newDir else current.direction
                    )
                }
            }
        }

        checkConfiguredKeys()
        checkMlKitStatus()
    }

    fun checkConfiguredKeys() {
        viewModelScope.launch {
            var hasAny = false
            for (p in ALL_ONLINE_PROVIDERS) {
                if (secureKeyStorage.getKey(p) != null) {
                    hasAny = true
                    break
                }
            }
            _uiState.update { it.copy(hasAnyKeyConfigured = hasAny) }
        }
    }

    private fun checkMlKitStatus() {
        viewModelScope.launch {
            val downloaded = mlKitOfflineProvider.areModelsDownloaded()
            _uiState.update { it.copy(isMlKitDownloaded = downloaded) }
        }
    }

    fun onEvent(event: TranslateUiEvent) {
        when (event) {
            is TranslateUiEvent.OnInputTextChanged -> handleInputChanged(event.text)
            is TranslateUiEvent.OnContextTextChanged -> handleContextChanged(event.text)
            is TranslateUiEvent.OnToggleContextExpanded -> handleToggleContextExpanded()
            is TranslateUiEvent.OnClearInputClicked -> handleClearInput()
            is TranslateUiEvent.OnPasteText -> handlePasteText(event.text)
            is TranslateUiEvent.OnSwapDirectionClicked -> handleSwapDirection()
            is TranslateUiEvent.OnSelectPartnerLanguage -> handleSelectPartnerLanguage(event.language)
            is TranslateUiEvent.OnTranslateClicked -> handleTranslate(bypassCache = false)
            is TranslateUiEvent.OnRefreshWithDifferentModelClicked -> handleTranslate(bypassCache = true)
            is TranslateUiEvent.OnRetryClicked -> handleTranslate(bypassCache = false)
            is TranslateUiEvent.OnDismissError -> _uiState.update { it.copy(errorMessage = null) }
            is TranslateUiEvent.OnSelectMeaning -> handleSelectMeaning(event.index)
            is TranslateUiEvent.OnSaveEquivalent -> handleSaveEquivalent(event.meaningIndex)
            is TranslateUiEvent.OnSpeakWord -> handleSpeakWord(event.text)
            is TranslateUiEvent.OnDismissTtsSnackbar -> _uiState.update { it.copy(showTtsInstallSnackbar = false) }
            is TranslateUiEvent.OnDismissSaveMessage -> _uiState.update { it.copy(saveSuccessMessage = null) }
            is TranslateUiEvent.OnDismissFallbackNotification -> _uiState.update { it.copy(fallbackNotification = null) }
            is TranslateUiEvent.OnDownloadMlKit -> handleDownloadMlKit()
            is TranslateUiEvent.OnStartListening -> handleStartListening(event.isContext, event.languageCode)
            is TranslateUiEvent.OnStopListening -> handleStopListening()
            is TranslateUiEvent.OnSelectSpeechAlternative -> handleSelectSpeechAlternative(event.text)
            is TranslateUiEvent.OnDismissSpeechError -> _uiState.update { it.copy(speechErrorMessage = null) }
            is TranslateUiEvent.OnDismissSpeechFirstUseNotice -> _uiState.update { it.copy(showSpeechFirstUseNotice = false) }
            is TranslateUiEvent.OnDownloadSpeechModel -> speechToTextEngine.triggerModelDownload(event.languageCode)
            is TranslateUiEvent.OnSelectSpeechLanguage -> _uiState.update { it.copy(activeSpeechLanguageCode = event.languageCode) }
        }
    }

    private fun handleInputChanged(newText: String) {
        val safeText = if (newText.length > 10000) newText.take(10000) else newText
        savedStateHandle[KEY_INPUT_TEXT] = safeText
        val detectedMode = detectModeUseCase(safeText)

        currentWordEntryId = null
        _uiState.update { current ->
            if (safeText.isBlank()) {
                current.copy(
                    inputText = safeText,
                    mode = detectedMode,
                    translatedText = null,
                    wordTranslation = null,
                    selectedMeaningIndex = null,
                    hasManualDirectionOverride = false,
                    errorMessage = null,
                    sourceModelAttribution = null
                )
            } else {
                val newDirection = if (!current.hasManualDirectionOverride) {
                    detectLanguageUseCase(safeText, current.selectedPartnerLanguage)
                } else {
                    current.direction
                }
                val hasInputChanged = safeText != current.inputText
                current.copy(
                    inputText = safeText,
                    mode = detectedMode,
                    direction = newDirection,
                    translatedText = if (hasInputChanged) null else current.translatedText,
                    wordTranslation = if (hasInputChanged) null else current.wordTranslation,
                    selectedMeaningIndex = if (hasInputChanged) null else current.selectedMeaningIndex,
                    errorMessage = null
                )
            }
        }
    }

    private fun handleContextChanged(context: String) {
        val safeContext = if (context.length > 500) context.take(500) else context
        _uiState.update { it.copy(contextText = safeContext) }
    }

    private fun handleToggleContextExpanded() {
        _uiState.update { it.copy(isContextExpanded = !it.isContextExpanded) }
    }

    private fun handleClearInput() {
        translationJob?.cancel()
        currentWordEntryId = null
        savedStateHandle[KEY_INPUT_TEXT] = ""
        _uiState.update {
            it.copy(
                inputText = "",
                contextText = "",
                mode = TranslationMode.WORD,
                translatedText = null,
                wordTranslation = null,
                selectedMeaningIndex = null,
                isLoading = false,
                errorMessage = null,
                hasManualDirectionOverride = false,
                saveSuccessMessage = null,
                sourceModelAttribution = null,
                fallbackNotification = null
            )
        }
    }

    private fun handlePasteText(pasted: String) {
        handleInputChanged(pasted)
    }

    private fun handleSwapDirection() {
        currentWordEntryId = null
        _uiState.update { current ->
            current.copy(
                direction = current.direction.opposite(),
                translatedText = null,
                wordTranslation = null,
                selectedMeaningIndex = null,
                hasManualDirectionOverride = true,
                errorMessage = null,
                sourceModelAttribution = null
            )
        }
    }

    private fun handleSelectPartnerLanguage(language: PartnerLanguage) {
        viewModelScope.launch {
            settingsRepository.setSelectedPartnerLanguage(language.code)
            _uiState.update { current ->
                val newDir = TranslationDirection.getDirection(language, fromGerman = current.direction.isFromGerman)
                current.copy(
                    selectedPartnerLanguage = language,
                    direction = newDir,
                    translatedText = null,
                    wordTranslation = null,
                    selectedMeaningIndex = null
                )
            }
        }
    }

    private fun handleTranslate(bypassCache: Boolean = false) {
        val currentState = _uiState.value
        if (!currentState.canTranslate) return

        translationJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        translationJob = viewModelScope.launch {
            if (currentState.mode == TranslationMode.WORD) {
                val result = translateWordUseCase(
                    word = currentState.inputText,
                    direction = currentState.direction,
                    context = currentState.contextText.ifBlank { null },
                    bypassCache = bypassCache
                )

                result.fold(
                    onSuccess = { translationResult ->
                        val wordTranslation = translationResult.data
                        val hasContext = currentState.contextText.isNotBlank()
                        val initialSelected = if (hasContext) {
                            wordTranslation.meanings
                                .indexOfFirst { it.bestMatch }
                                .takeIf { it >= 0 }
                        } else {
                            null
                        }

                        // Sanitize meanings: only highlight best_match when context was provided
                        val sanitizedMeanings = if (!hasContext) {
                            wordTranslation.meanings.map { it.copy(bestMatch = false) }
                        } else {
                            wordTranslation.meanings
                        }

                        // Normalize lemma (trim, Unicode NFC) and extract any article
                        val (extractedArticle, baseLemma) = TextNormalizer.extractArticleAndLemma(wordTranslation.lemma)
                        val resolvedArticle = wordTranslation.article?.trim()?.ifBlank { null } ?: extractedArticle
                        val normalizedLemma = TextNormalizer.normalizeLemma(baseLemma)

                        val finalWord = wordTranslation.copy(
                            lemma = normalizedLemma,
                            article = resolvedArticle,
                            meanings = sanitizedMeanings,
                            selectedMeaningIndex = initialSelected
                        )

                        // Word mode auto-saves entry to Room upon successful translation whenever translating from German
                        if (currentState.direction.isFromGerman) {
                            val saved = runCatching { vocabularyRepository.saveWordTranslation(finalWord) }.getOrNull()
                            currentWordEntryId = saved?.id
                        } else {
                            currentWordEntryId = null
                        }

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                wordTranslation = finalWord,
                                selectedMeaningIndex = initialSelected,
                                translatedText = null,
                                errorMessage = null,
                                sourceModelAttribution = translationResult.modelAttribution,
                                fallbackNotification = translationResult.fallbackMessage
                            )
                        }
                    },
                    onFailure = { error ->
                        val msg = if (error is ProviderError) error.userMessage else (error.localizedMessage ?: "Произошла ошибка при переводе слова.")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = msg
                            )
                        }
                    }
                )
            } else {
                val result = translateTextUseCase(
                    text = currentState.inputText,
                    direction = currentState.direction,
                    bypassCache = bypassCache
                )

                result.fold(
                    onSuccess = { translationResult ->
                        val translated = translationResult.data
                        // Save long text translation history
                        runCatching {
                            vocabularyRepository.saveTextTranslation(
                                source = currentState.inputText,
                                result = translated,
                                direction = currentState.direction.name
                            )
                        }

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                translatedText = translated,
                                wordTranslation = null,
                                selectedMeaningIndex = null,
                                errorMessage = null,
                                sourceModelAttribution = translationResult.modelAttribution,
                                fallbackNotification = translationResult.fallbackMessage
                            )
                        }
                    },
                    onFailure = { error ->
                        val msg = if (error is ProviderError) error.userMessage else (error.localizedMessage ?: "Произошла ошибка при переводе текста.")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = msg
                            )
                        }
                    }
                )
            }
        }
    }

    private fun handleSelectMeaning(index: Int) {
        _uiState.update { current ->
            val newSelection = if (current.selectedMeaningIndex == index) null else index
            current.copy(
                selectedMeaningIndex = newSelection,
                wordTranslation = current.wordTranslation?.copy(selectedMeaningIndex = newSelection)
            )
        }

        val wordId = currentWordEntryId
        val selection = _uiState.value.selectedMeaningIndex
        if (wordId != null) {
            viewModelScope.launch {
                runCatching { vocabularyRepository.updateSelectedMeaning(wordId, selection) }
            }
        }
    }

    private fun handleSaveEquivalent(meaningIndex: Int) {
        val current = _uiState.value
        val word = current.wordTranslation ?: return
        val meaning = word.meanings.getOrNull(meaningIndex) ?: return

        viewModelScope.launch {
            val saved = runCatching {
                vocabularyRepository.saveGermanEquivalent(
                    germanLemmaWithArticle = meaning.translation.trim(),
                    russianTranslation = current.inputText.trim(),
                    pos = word.pos
                )
            }.getOrNull()

            val displaySaved = if (saved != null) {
                if (saved.article != null) "${saved.article} ${saved.lemma}" else saved.lemma
            } else {
                val (article, baseLemma) = TextNormalizer.extractArticleAndLemma(meaning.translation)
                val normalizedLemma = TextNormalizer.normalizeLemma(baseLemma)
                if (article != null) "$article $normalizedLemma" else normalizedLemma
            }

            _uiState.update {
                it.copy(saveSuccessMessage = displaySaved)
            }
        }
    }

    private fun handleSpeakWord(text: String) {
        ttsManager.speak(text) {
            _uiState.update { it.copy(showTtsInstallSnackbar = true) }
        }
    }

    private fun handleDownloadMlKit() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingMlKit = true) }
            val result = mlKitOfflineProvider.downloadModels(requireWifi = false)
            val downloaded = mlKitOfflineProvider.areModelsDownloaded()
            _uiState.update {
                it.copy(
                    isDownloadingMlKit = false,
                    isMlKitDownloaded = downloaded,
                    errorMessage = if (result.isFailure) result.exceptionOrNull()?.localizedMessage else null
                )
            }
        }
    }

    private fun handleStartListening(isContext: Boolean, languageCode: String?) {
        ttsManager.stop()
        speechJob?.cancel()

        val activeLang = languageCode
            ?: _uiState.value.activeSpeechLanguageCode
            ?: if (_uiState.value.direction.isFromGerman) "de" else _uiState.value.selectedPartnerLanguage.code

        _uiState.update {
            it.copy(
                isListening = true,
                isListeningContext = isContext,
                activeSpeechLanguageCode = activeLang,
                speechPartialText = null,
                speechAlternatives = emptyList(),
                speechErrorMessage = null
            )
        }

        speechJob = viewModelScope.launch {
            val userSettings = settingsRepository.settingsFlow.first()
            val preferOffline = userSettings.sttPreferOnDevice

            speechToTextEngine.startListening(activeLang, preferOffline = preferOffline).collect { event ->
                when (event) {
                    is SttEvent.Partial -> {
                        _uiState.update { it.copy(speechPartialText = event.text) }
                    }
                    is SttEvent.Final -> {
                        handleSpeechFinal(event.text, event.alternatives, isContext)
                    }
                    is SttEvent.SoundLevel -> {
                        _uiState.update { it.copy(speechSoundLevel = event.rmsDb) }
                    }
                    is SttEvent.Error -> {
                        _uiState.update {
                            it.copy(
                                isListening = false,
                                isListeningContext = false,
                                speechPartialText = null,
                                speechErrorMessage = event.message
                            )
                        }
                    }
                    is SttEvent.StateChanged -> {
                        _uiState.update { it.copy(isListening = event.isListening) }
                    }
                }
            }
        }
    }

    private fun handleSpeechFinal(text: String, alternatives: List<String>, isContext: Boolean) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(isListening = false, isListeningContext = false, speechPartialText = null) }
            return
        }

        // Punctuation stripping: if recognized result has <= 3 tokens, strip trailing punctuation to preserve WORD mode
        val tokens = trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val processedText = if (tokens.size <= 3) {
            trimmed.trimEnd('.', '!', '?', ',', ';', ':')
        } else {
            trimmed
        }

        if (isContext) {
            val current = _uiState.value.contextText
            val merged = if (current.isBlank()) processedText else "$current $processedText"
            handleContextChanged(merged)
            _uiState.update {
                it.copy(
                    isListening = false,
                    isListeningContext = false,
                    speechPartialText = null,
                    speechAlternatives = alternatives
                )
            }
        } else {
            val current = _uiState.value.inputText
            val merged = if (current.isBlank()) processedText else "$current $processedText"
            handleInputChanged(merged)
            _uiState.update {
                it.copy(
                    isListening = false,
                    isListeningContext = false,
                    speechPartialText = null,
                    speechAlternatives = alternatives
                )
            }

            viewModelScope.launch {
                val userSettings = settingsRepository.settingsFlow.first()
                if (userSettings.sttTranslateImmediately && merged.isNotBlank()) {
                    handleTranslate()
                }
            }
        }
    }

    private fun handleStopListening() {
        speechToTextEngine.stopListening()
        speechJob?.cancel()
        speechJob = null
        _uiState.update { it.copy(isListening = false, isListeningContext = false, speechPartialText = null) }
    }

    private fun handleSelectSpeechAlternative(text: String) {
        val trimmed = text.trim()
        val tokens = trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val processed = if (tokens.size <= 3) trimmed.trimEnd('.', '!', '?', ',', ';', ':') else trimmed
        handleInputChanged(processed)
        _uiState.update { it.copy(speechAlternatives = emptyList()) }
    }

    override fun onCleared() {
        super.onCleared()
        translationJob?.cancel()
        speechJob?.cancel()
        speechToTextEngine.cancel()
        ttsManager.stop()
    }
}
