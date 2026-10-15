package app.deutschlern.ui.words

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deutschlern.domain.model.SessionWithWords
import app.deutschlern.domain.model.WordEntry
import app.deutschlern.domain.model.WordStatus
import app.deutschlern.domain.repository.VocabularyRepository
import app.deutschlern.util.TtsManager
import app.deutschlern.util.WordSearchHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import app.deutschlern.domain.model.Meaning
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.WordTranslation
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.usecase.TranslateWordUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import app.deutschlern.domain.stt.SpeechToTextEngine
import app.deutschlern.domain.stt.SttEvent

@HiltViewModel
class WordsViewModel @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
    private val ttsManager: TtsManager,
    private val translateWordUseCase: TranslateWordUseCase,
    private val settingsRepository: SettingsRepository,
    private val speechToTextEngine: SpeechToTextEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(WordsUiState())
    val uiState: StateFlow<WordsUiState> = _uiState.asStateFlow()

    private var bulkJob: Job? = null
    private var speechJob: Job? = null

    init {
        observeSessions()
    }

    private fun observeSessions() {
        viewModelScope.launch {
            vocabularyRepository.getSessionsWithWords().collectLatest { sessions ->
                _uiState.update { current ->
                    val filtered = applyFilterAndSort(
                        sessions = sessions,
                        filter = current.filter,
                        sort = current.sort,
                        query = current.searchQuery
                    )
                    current.copy(
                        rawSessionsWithWords = sessions,
                        sessionsWithWords = filtered,
                        isLoading = false,
                        // Update bottom sheet word if it's currently open
                        selectedWordForDetail = current.selectedWordForDetail?.let { selected ->
                            sessions.flatMap { it.words }.find { it.id == selected.id } ?: selected
                        }
                    )
                }
            }
        }
    }

    fun onEvent(event: WordsUiEvent) {
        when (event) {
            is WordsUiEvent.OnFilterChanged -> handleFilterChanged(event.filter)
            is WordsUiEvent.OnSortChanged -> handleSortChanged(event.sort)
            is WordsUiEvent.OnSearchQueryChanged -> handleSearchQueryChanged(event.query)
            is WordsUiEvent.OnWordClicked -> handleWordClicked(event.word)
            is WordsUiEvent.OnDismissBottomSheet -> _uiState.update { it.copy(selectedWordForDetail = null) }
            is WordsUiEvent.OnStatusChanged -> handleStatusChanged(event.wordId, event.newStatus)
            is WordsUiEvent.OnSelectMeaning -> handleSelectMeaning(event.wordId, event.meaningIndex)
            is WordsUiEvent.OnDeleteWord -> handleDeleteWord(event.word)
            is WordsUiEvent.OnUndoDelete -> handleUndoDelete()
            is WordsUiEvent.OnCreateNewSession -> handleCreateNewSession()
            is WordsUiEvent.OnSpeakWord -> ttsManager.speak(event.text) {
                _uiState.update { it.copy(snackbarMessage = "Голосовые данные для немецкого языка не установлены.") }
            }
            is WordsUiEvent.OnDismissSnackbar -> _uiState.update { it.copy(snackbarMessage = null, showUndoAction = false) }
            // Add Word events
            is WordsUiEvent.OnOpenAddWordClicked -> _uiState.update { it.copy(isAddWordSheetVisible = true) }
            is WordsUiEvent.OnDismissAddWordSheet -> _uiState.update {
                it.copy(
                    isAddWordSheetVisible = false,
                    addWordTranslateError = null,
                    manualError = null,
                    addWordTranslatedResult = null,
                    addWordTranslateInput = ""
                )
            }
            is WordsUiEvent.OnSelectAddWordTab -> _uiState.update { it.copy(addWordTab = event.tab) }
            is WordsUiEvent.OnAddWordInputChange -> _uiState.update { it.copy(addWordTranslateInput = event.text, addWordTranslateError = null) }
            is WordsUiEvent.OnAddWordMicClicked -> handleAddWordMic()
            is WordsUiEvent.OnAddWordTranslateClicked -> handleAddWordTranslate()
            is WordsUiEvent.OnAddWordSelectMeaning -> _uiState.update { it.copy(addWordSelectedMeaningIndex = event.index) }
            is WordsUiEvent.OnAddWordSaveClicked -> handleAddWordSave()
            is WordsUiEvent.OnManualWordChange -> _uiState.update {
                it.copy(
                    manualGermanWord = event.word,
                    manualArticle = event.article,
                    manualPos = event.pos,
                    manualTranslation = event.translation,
                    manualExample = event.example,
                    manualError = null
                )
            }
            is WordsUiEvent.OnManualEnhanceWithAiClicked -> handleManualEnhanceWithAi()
            is WordsUiEvent.OnManualSaveClicked -> handleManualSave()
            is WordsUiEvent.OnBulkInputChange -> _uiState.update { it.copy(bulkInputText = event.text) }
            is WordsUiEvent.OnBulkStartClicked -> handleBulkStart()
            is WordsUiEvent.OnBulkStopClicked -> handleBulkStop()
            is WordsUiEvent.OnDismissDuplicateDialog -> _uiState.update { it.copy(duplicateWordDialogData = null) }
            is WordsUiEvent.OnOpenDuplicateWord -> _uiState.update {
                it.copy(
                    duplicateWordDialogData = null,
                    isAddWordSheetVisible = false,
                    selectedWordForDetail = event.word
                )
            }
            is WordsUiEvent.OnAddDuplicateToLesson -> handleAddDuplicateToLesson(event.wordId)
        }
    }

    private fun handleFilterChanged(filter: WordFilter) {
        _uiState.update { current ->
            current.copy(
                filter = filter,
                sessionsWithWords = applyFilterAndSort(
                    sessions = current.rawSessionsWithWords,
                    filter = filter,
                    sort = current.sort,
                    query = current.searchQuery
                )
            )
        }
    }

    private fun handleSortChanged(sort: WordSort) {
        _uiState.update { current ->
            current.copy(
                sort = sort,
                sessionsWithWords = applyFilterAndSort(
                    sessions = current.rawSessionsWithWords,
                    filter = current.filter,
                    sort = sort,
                    query = current.searchQuery
                )
            )
        }
    }

    private fun handleSearchQueryChanged(query: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                sessionsWithWords = applyFilterAndSort(
                    sessions = current.rawSessionsWithWords,
                    filter = current.filter,
                    sort = current.sort,
                    query = query
                )
            )
        }
    }

    private fun handleWordClicked(word: WordEntry) {
        _uiState.update { it.copy(selectedWordForDetail = word) }
    }

    private fun handleStatusChanged(wordId: Long, newStatus: WordStatus) {
        viewModelScope.launch {
            vocabularyRepository.updateWordStatus(wordId, newStatus)
            _uiState.update { current ->
                if (current.selectedWordForDetail?.id == wordId) {
                    current.copy(
                        selectedWordForDetail = current.selectedWordForDetail.copy(status = newStatus)
                    )
                } else current
            }
        }
    }

    private fun handleSelectMeaning(wordId: Long, meaningIndex: Int?) {
        viewModelScope.launch {
            vocabularyRepository.updateSelectedMeaning(wordId, meaningIndex)
            _uiState.update { current ->
                if (current.selectedWordForDetail?.id == wordId) {
                    current.copy(
                        selectedWordForDetail = current.selectedWordForDetail.copy(selectedMeaningIndex = meaningIndex)
                    )
                } else current
            }
        }
    }

    private fun handleDeleteWord(word: WordEntry) {
        viewModelScope.launch {
            val deleted = vocabularyRepository.deleteWord(word.id)
            _uiState.update { current ->
                current.copy(
                    selectedWordForDetail = null,
                    lastDeletedWord = deleted ?: word,
                    snackbarMessage = "Слово «${word.lemma}» удалено",
                    showUndoAction = true
                )
            }
        }
    }

    private fun handleUndoDelete() {
        val wordToRestore = _uiState.value.lastDeletedWord ?: return
        viewModelScope.launch {
            vocabularyRepository.restoreWord(wordToRestore)
            _uiState.update {
                it.copy(
                    lastDeletedWord = null,
                    snackbarMessage = null,
                    showUndoAction = false
                )
            }
        }
    }

    private fun handleCreateNewSession() {
        viewModelScope.launch {
            val session = vocabularyRepository.createManualSession()
            _uiState.update {
                it.copy(
                    snackbarMessage = "Создан новый урок: ${session.title}",
                    showUndoAction = false
                )
            }
        }
    }

    private fun applyFilterAndSort(
        sessions: List<SessionWithWords>,
        filter: WordFilter,
        sort: WordSort,
        query: String
    ): List<SessionWithWords> {
        val trimmedQuery = query.trim()

        return sessions.mapNotNull { sessionGroup ->
            val filteredWords = sessionGroup.words.filter { word ->
                val matchesFilter = when (filter) {
                    WordFilter.ALL -> true
                    WordFilter.NEW -> word.status == WordStatus.NEW
                    WordFilter.LEARNING -> word.status == WordStatus.LEARNING
                    WordFilter.KNOWN -> word.status == WordStatus.KNOWN
                }
                val matchesSearch = if (trimmedQuery.isEmpty()) true else WordSearchHelper.matchesWord(word, trimmedQuery)
                matchesFilter && matchesSearch
            }

            val sortedWords = when (sort) {
                WordSort.BY_DATE -> filteredWords
                WordSort.ALPHABETICAL -> filteredWords.sortedBy { it.lemma.lowercase() }
                WordSort.MOST_TRANSLATED -> filteredWords.sortedWith(
                    compareByDescending<WordEntry> { it.timesTranslated }
                        .thenBy { it.lemma.lowercase() }
                )
            }

            if (sortedWords.isEmpty() && sessionGroup.words.isNotEmpty()) {
                null
            } else if (sortedWords.isEmpty() && !sessionGroup.session.isManual) {
                null
            } else {
                SessionWithWords(
                    session = sessionGroup.session,
                    words = sortedWords
                )
            }
        }
    }

    private fun handleAddWordTranslate() {
        val text = _uiState.value.addWordTranslateInput.trim()
        if (text.isBlank()) return
        _uiState.update { it.copy(isAddWordTranslating = true, addWordTranslateError = null) }
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val direction = TranslationDirection.getDirection(settings.selectedPartnerLanguage, fromGerman = true)
            val result = translateWordUseCase(text, direction = direction)
            if (result.isSuccess) {
                val translation = result.getOrNull()?.data
                _uiState.update {
                    it.copy(
                        isAddWordTranslating = false,
                        addWordTranslatedResult = translation,
                        addWordSelectedMeaningIndex = 0
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isAddWordTranslating = false,
                        addWordTranslateError = result.exceptionOrNull()?.localizedMessage ?: "Ошибка перевода"
                    )
                }
            }
        }
    }

    private fun handleAddWordSave() {
        val translation = _uiState.value.addWordTranslatedResult ?: return
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val existing = vocabularyRepository.findWord(translation.lemma, translation.pos, settings.selectedPartnerLanguage.code)
            if (existing != null) {
                _uiState.update {
                    it.copy(
                        duplicateWordDialogData = DuplicateWordData(
                            existingWord = existing,
                            pendingWordTranslation = translation
                        )
                    )
                }
            } else {
                vocabularyRepository.saveWordTranslation(
                    translation.copy(
                        selectedMeaningIndex = _uiState.value.addWordSelectedMeaningIndex,
                        partnerLanguage = settings.selectedPartnerLanguage.code
                    )
                )
                _uiState.update {
                    it.copy(
                        isAddWordSheetVisible = false,
                        addWordTranslateInput = "",
                        addWordTranslatedResult = null,
                        snackbarMessage = "Слово успешно добавлено",
                        showUndoAction = false
                    )
                }
            }
        }
    }

    private fun handleManualEnhanceWithAi() {
        val word = _uiState.value.manualGermanWord.trim()
        if (word.isBlank()) return
        _uiState.update { it.copy(isEnhancingWithAi = true, manualError = null) }
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val direction = TranslationDirection.getDirection(settings.selectedPartnerLanguage, fromGerman = true)
            val result = translateWordUseCase(word, direction = direction)
            if (result.isSuccess) {
                val res = result.getOrNull()?.data
                if (res != null) {
                    _uiState.update { current ->
                        current.copy(
                            isEnhancingWithAi = false,
                            manualArticle = current.manualArticle ?: res.article,
                            manualPos = if (current.manualPos == "noun" && res.pos.isNotBlank()) res.pos else current.manualPos,
                            manualTranslation = if (current.manualTranslation.isBlank()) res.meanings.firstOrNull()?.translation.orEmpty() else current.manualTranslation,
                            manualExample = if (current.manualExample.isBlank()) res.meanings.firstOrNull()?.exampleDe.orEmpty() else current.manualExample
                        )
                    }
                } else {
                    _uiState.update { it.copy(isEnhancingWithAi = false) }
                }
            } else {
                _uiState.update {
                    it.copy(
                        isEnhancingWithAi = false,
                        manualError = result.exceptionOrNull()?.localizedMessage ?: "Ошибка дополнения"
                    )
                }
            }
        }
    }

    private fun handleManualSave() {
        val word = _uiState.value.manualGermanWord.trim()
        val translation = _uiState.value.manualTranslation.trim()
        if (word.isBlank() || translation.isBlank()) {
            _uiState.update { it.copy(manualError = "Заполните слово и перевод") }
            return
        }
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val pos = _uiState.value.manualPos
            val existing = vocabularyRepository.findWord(word, pos, settings.selectedPartnerLanguage.code)
            if (existing != null) {
                _uiState.update {
                    it.copy(
                        duplicateWordDialogData = DuplicateWordData(existingWord = existing)
                    )
                }
            } else {
                val rawArt = if (_uiState.value.manualArticle == "none") null else _uiState.value.manualArticle
                val sanitized = app.deutschlern.domain.sanitizer.ArticleSanitizer.sanitize(
                    rawLemma = word,
                    rawPos = pos,
                    rawArticle = rawArt
                )
                val wordTrans = WordTranslation(
                    lemma = sanitized.lemma,
                    article = sanitized.article,
                    pos = sanitized.pos,
                    meanings = listOf(
                        Meaning(
                            translation = translation,
                            exampleDe = _uiState.value.manualExample.trim()
                        )
                    ),
                    partnerLanguage = settings.selectedPartnerLanguage.code
                )
                vocabularyRepository.saveWordTranslation(wordTrans)
                _uiState.update {
                    it.copy(
                        isAddWordSheetVisible = false,
                        manualGermanWord = "",
                        manualTranslation = "",
                        manualExample = "",
                        manualArticle = null,
                        snackbarMessage = "Слово успешно добавлено",
                        showUndoAction = false
                    )
                }
            }
        }
    }

    private fun handleBulkStart() {
        val words = _uiState.value.bulkInputText
            .split("[\n,]+".toRegex())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(30)
        if (words.isEmpty()) return

        bulkJob?.cancel()
        bulkJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBulkTranslating = true,
                    bulkProgressCurrent = 0,
                    bulkProgressTotal = words.size,
                    bulkStatusMessage = null
                )
            }
            val settings = settingsRepository.settingsFlow.first()
            var addedCount = 0

            for ((index, item) in words.withIndex()) {
                _uiState.update { it.copy(bulkProgressCurrent = index + 1) }
                val direction = TranslationDirection.getDirection(settings.selectedPartnerLanguage, fromGerman = true)
                val res = translateWordUseCase(item, direction = direction)
                if (res.isSuccess) {
                    res.getOrNull()?.data?.let { trans ->
                        vocabularyRepository.saveWordTranslation(
                            trans.copy(partnerLanguage = settings.selectedPartnerLanguage.code)
                        )
                        addedCount++
                    }
                }
            }
            _uiState.update {
                it.copy(
                    isBulkTranslating = false,
                    isAddWordSheetVisible = false,
                    bulkInputText = "",
                    snackbarMessage = "Добавлено $addedCount из ${words.size} слов",
                    showUndoAction = false
                )
            }
        }
    }

    private fun handleBulkStop() {
        bulkJob?.cancel()
        bulkJob = null
        _uiState.update { it.copy(isBulkTranslating = false) }
    }

    private fun handleAddDuplicateToLesson(wordId: Long) {
        viewModelScope.launch {
            vocabularyRepository.addWordToCurrentSession(wordId)
            _uiState.update {
                it.copy(
                    duplicateWordDialogData = null,
                    isAddWordSheetVisible = false,
                    snackbarMessage = "Слово добавлено в текущий урок",
                    showUndoAction = false
                )
            }
        }
    }

    private fun handleAddWordMic() {
        ttsManager.stop()
        if (speechJob?.isActive == true) {
            speechToTextEngine.stopListening()
            speechJob?.cancel()
            speechJob = null
            return
        }

        speechJob = viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            speechToTextEngine.startListening("de", preferOffline = settings.sttPreferOnDevice).collect { event ->
                when (event) {
                    is SttEvent.Partial -> {
                        _uiState.update { it.copy(addWordTranslateInput = event.text) }
                    }
                    is SttEvent.Final -> {
                        val cleaned = event.text.trim().trimEnd('.', '!', '?', ',', ';')
                        _uiState.update { it.copy(addWordTranslateInput = cleaned) }
                    }
                    is SttEvent.Error -> {
                        _uiState.update { it.copy(addWordTranslateError = event.message) }
                    }
                    else -> Unit
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        bulkJob?.cancel()
        speechJob?.cancel()
        speechToTextEngine.cancel()
        ttsManager.stop()
    }
}
