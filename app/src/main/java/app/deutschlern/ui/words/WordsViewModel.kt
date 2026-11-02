package app.deutschlern.ui.words

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deutschlern.domain.model.SessionWithWords
import app.deutschlern.domain.model.WordEntry
import app.deutschlern.domain.model.WordStatus
import app.deutschlern.domain.model.WordTopic
import app.deutschlern.domain.sanitizer.PosNormalizer
import app.deutschlern.domain.usecase.ClassifyWordTopicsUseCase
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
    private val speechToTextEngine: SpeechToTextEngine,
    private val classifyWordTopics: ClassifyWordTopicsUseCase
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
                    current.copy(
                        rawSessionsWithWords = sessions,
                        isLoading = false,
                        // Update bottom sheet word if it's currently open
                        selectedWordForDetail = current.selectedWordForDetail?.let { selected ->
                            sessions.flatMap { it.words }.find { it.id == selected.id } ?: selected
                        }
                    ).withFilteredWords()
                }
                maybeClassifyTopics(sessions.flatMap { it.words })
            }
        }
    }

    // Runs again only when the number of unclassified words changes, so a missing key or
    // network does not trigger a request on every database emission.
    private var lastPendingTopicCount = -1

    private fun maybeClassifyTopics(words: List<WordEntry>) {
        val pending = words.distinctBy { it.id }.count { it.topics == null }
        if (pending == 0 || pending == lastPendingTopicCount) return
        lastPendingTopicCount = pending
        viewModelScope.launch { classifyWordTopics() }
    }

    fun onEvent(event: WordsUiEvent) {
        when (event) {
            is WordsUiEvent.OnFilterChanged -> updateFilters { it.copy(filter = event.filter) }
            is WordsUiEvent.OnDifficultFilterToggled -> updateFilters { it.copy(difficultOnly = !it.difficultOnly) }
            is WordsUiEvent.OnPosFilterChanged -> updateFilters { it.copy(posFilter = event.pos) }
            is WordsUiEvent.OnTopicFilterChanged -> updateFilters { it.copy(topicFilter = event.topic) }
            is WordsUiEvent.OnResetFilters -> updateFilters {
                it.copy(filter = WordFilter.ALL, difficultOnly = false, posFilter = null, topicFilter = null)
            }
            is WordsUiEvent.OnDifficultChanged -> handleDifficultChanged(event.wordId, event.isDifficult)
            is WordsUiEvent.OnSortChanged -> updateFilters { it.copy(sort = event.sort) }
            is WordsUiEvent.OnSearchQueryChanged -> updateFilters { it.copy(searchQuery = event.query) }
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

    private fun updateFilters(change: (WordsUiState) -> WordsUiState) {
        _uiState.update { change(it).withFilteredWords() }
    }

    private fun handleDifficultChanged(wordId: Long, isDifficult: Boolean) {
        viewModelScope.launch {
            vocabularyRepository.updateWordDifficulty(wordId, isDifficult)
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
                    lastDeletedWord = deleted,
                    snackbarMessage = "Слово «${word.lemma}» удалено",
                    showUndoAction = deleted != null
                )
            }
        }
    }

    private fun handleUndoDelete() {
        val wordToRestore = _uiState.value.lastDeletedWord ?: return
        viewModelScope.launch {
            val restored = vocabularyRepository.restoreWord(wordToRestore)
            _uiState.update {
                it.copy(
                    lastDeletedWord = null,
                    snackbarMessage = if (restored) null else "Слово «${wordToRestore.word.lemma}» уже добавлено заново",
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

    private fun WordsUiState.withFilteredWords(): WordsUiState {
        val allWords = rawSessionsWithWords.flatMap { it.words }.distinctBy { it.id }
        val query = searchQuery.trim()

        fun WordEntry.matches(): Boolean {
            val matchesStatus = when (filter) {
                WordFilter.ALL -> true
                WordFilter.NEW -> status == WordStatus.NEW
                WordFilter.LEARNING -> status == WordStatus.LEARNING
                WordFilter.KNOWN -> status == WordStatus.KNOWN
            }
            return matchesStatus &&
                (!difficultOnly || isDifficult) &&
                (posFilter == null || PosNormalizer.normalize(pos) == posFilter) &&
                (topicFilter == null || topics.orEmpty().contains(topicFilter.key)) &&
                (query.isEmpty() || WordSearchHelper.matchesWord(this, query))
        }

        val comparator: Comparator<WordEntry>? = when (sort) {
            WordSort.BY_DATE -> null
            WordSort.ALPHABETICAL -> compareBy { it.lemma.lowercase() }
            WordSort.MOST_TRANSLATED -> compareByDescending<WordEntry> { it.timesTranslated }.thenBy { it.lemma.lowercase() }
        }

        val grouped = rawSessionsWithWords.mapNotNull { group ->
            val words = group.words.filter { it.matches() }.let { list -> comparator?.let(list::sortedWith) ?: list }
            when {
                words.isEmpty() && (group.words.isNotEmpty() || !group.session.isManual) -> null
                else -> SessionWithWords(session = group.session, words = words)
            }
        }

        val flat = allWords.filter { it.matches() }.let { list ->
            when {
                // While searching, best matches come first regardless of the chosen sort
                query.isNotEmpty() -> list.sortedBy { WordSearchHelper.relevance(it, query) }
                comparator != null -> list.sortedWith(comparator)
                else -> list.sortedByDescending { it.lastSeenAt }
            }
        }

        return copy(
            sessionsWithWords = grouped,
            flatWords = flat,
            availablePos = allWords.map { PosNormalizer.normalize(it.pos) }
                .groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key }
                .filter { it != PosNormalizer.POS_OTHER },
            availableTopics = WordTopic.entries.filter { topic -> allWords.any { it.topics.orEmpty().contains(topic.key) } }
        )
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
        ttsManager.stop()
    }
}
