package app.deutschlern.ui.dialog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deutschlern.domain.model.PartnerLanguage
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.WordTranslation
import app.deutschlern.domain.model.dialog.Dialog
import app.deutschlern.domain.model.dialog.DialogSegment
import app.deutschlern.domain.model.dialog.DialogTask
import app.deutschlern.domain.model.dialog.DialogTaskType
import app.deutschlern.domain.repository.DialogRepository
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.repository.VocabularyRepository
import app.deutschlern.domain.usecase.GenerateDialogSummaryUseCase
import app.deutschlern.domain.usecase.GenerateDialogTasksUseCase
import app.deutschlern.domain.usecase.TranslateWordUseCase
import app.deutschlern.ui.navigation.getNavigationLong
import app.deutschlern.util.runSuspendCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import app.deutschlern.ui.dialog.components.TranscriptTokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DialogDetailUiState(
    val dialog: Dialog? = null,
    val segments: List<DialogSegment> = emptyList(),
    val tasks: List<DialogTask> = emptyList(),
    val selectedTab: Int = 0, // 0 = Transcript, 1 = Summary, 2 = Tasks
    val isGeneratingSummary: Boolean = false,
    val isGeneratingTask: Boolean = false,
    val generatingTaskType: DialogTaskType? = null,
    val selectedWordCard: WordTranslation? = null,
    val isWordLoading: Boolean = false,
    val wordMessage: String? = null,
    val errorMessage: String? = null,
    val partnerLanguage: PartnerLanguage = PartnerLanguage.RUSSIAN
)

@HiltViewModel
class DialogDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dialogRepository: DialogRepository,
    private val settingsRepository: SettingsRepository,
    private val generateDialogSummaryUseCase: GenerateDialogSummaryUseCase,
    private val generateDialogTasksUseCase: GenerateDialogTasksUseCase,
    private val translateWordUseCase: TranslateWordUseCase,
    private val vocabularyRepository: VocabularyRepository
) : ViewModel() {

    val dialogId: Long = savedStateHandle.getNavigationLong("dialogId")

    private val _selectedTab = MutableStateFlow(0)
    private val _isGeneratingSummary = MutableStateFlow(false)
    private val _isGeneratingTask = MutableStateFlow(false)
    private val _generatingTaskType = MutableStateFlow<DialogTaskType?>(null)
    private val _selectedWordCard = MutableStateFlow<WordTranslation?>(null)
    private val _isWordLoading = MutableStateFlow(false)
    private val _wordMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _dialogFlow = MutableStateFlow<Dialog?>(null)

    private val contentFlow = combine(
        _dialogFlow,
        dialogRepository.getSegmentsForDialog(dialogId),
        dialogRepository.getTasksForDialog(dialogId),
        ::Triple
    )
    private val generationFlow = combine(_isGeneratingSummary, _isGeneratingTask, _generatingTaskType, ::Triple)
    private val wordFlow = combine(_selectedWordCard, _isWordLoading, _wordMessage, ::Triple)
    private val errorAndSettingsFlow = combine(_errorMessage, settingsRepository.settingsFlow, ::Pair)

    val uiState: StateFlow<DialogDetailUiState> = combine(
        contentFlow,
        generationFlow,
        wordFlow,
        _selectedTab,
        errorAndSettingsFlow
    ) { (dialog, segments, tasks), (genSummary, genTask, genTaskType), (wordCard, wordLoading, wordMessage), tab, (error, settings) ->
        DialogDetailUiState(
            dialog = dialog,
            segments = segments,
            tasks = tasks,
            selectedTab = tab,
            isGeneratingSummary = genSummary,
            isGeneratingTask = genTask,
            generatingTaskType = genTaskType,
            selectedWordCard = wordCard,
            isWordLoading = wordLoading,
            wordMessage = wordMessage,
            errorMessage = error,
            partnerLanguage = settings.selectedPartnerLanguage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DialogDetailUiState()
    )

    init {
        loadDialog()
        // Tokenize transcript off the main thread once per content change, not on every UI state emission
        dialogRepository.getSegmentsForDialog(dialogId)
            .map { segments -> segments.map(DialogSegment::text) }
            .distinctUntilChanged()
            .onEach(TranscriptTokenizer::prewarm)
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    private fun loadDialog() {
        viewModelScope.launch {
            val d = dialogRepository.getDialogById(dialogId)
            _dialogFlow.value = d
        }
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun onWordClick(word: String, segmentContext: String) {
        viewModelScope.launch {
            _isWordLoading.value = true
            _errorMessage.value = null
            val partnerLang = settingsRepository.getSettings().selectedPartnerLanguage
            val direction = TranslationDirection.getDirection(
                partner = partnerLang,
                fromGerman = true
            )

            val result = translateWordUseCase(
                word = word,
                direction = direction,
                context = segmentContext
            )

            _isWordLoading.value = false
            result.onSuccess { translationResult ->
                _selectedWordCard.value = translationResult.data
            }.onFailure { err ->
                _errorMessage.value = err.message
            }
        }
    }

    fun dismissWordCard() {
        _selectedWordCard.value = null
    }

    fun saveWordToVocab(word: WordTranslation) {
        viewModelScope.launch {
            runSuspendCatching {
                vocabularyRepository.saveWordTranslation(word)
                _wordMessage.value = word.lemma
            }.onFailure { err ->
                _errorMessage.value = err.message
            }
        }
    }

    fun clearWordMessage() {
        _wordMessage.value = null
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun generateSummary() {
        val currentSegments = uiState.value.segments
        if (currentSegments.isEmpty() || _isGeneratingSummary.value) return

        viewModelScope.launch {
            _isGeneratingSummary.value = true
            _errorMessage.value = null
            val transcript = buildTranscript(currentSegments)
            val partnerLang = settingsRepository.getSettings().selectedPartnerLanguage

            val result = generateDialogSummaryUseCase(
                dialogId = dialogId,
                transcript = transcript,
                partnerLanguage = partnerLang
            )

            _isGeneratingSummary.value = false
            result.onSuccess {
                loadDialog()
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Failed to generate summary"
            }
        }
    }

    fun generateTask(type: DialogTaskType) {
        val currentSegments = uiState.value.segments
        if (currentSegments.isEmpty() || _isGeneratingTask.value) return

        viewModelScope.launch {
            _isGeneratingTask.value = true
            _generatingTaskType.value = type
            _errorMessage.value = null
            val transcript = buildTranscript(currentSegments)
            val partnerLang = settingsRepository.getSettings().selectedPartnerLanguage

            val result = generateDialogTasksUseCase(
                dialogId = dialogId,
                transcript = transcript,
                taskType = type,
                partnerLanguage = partnerLang
            )

            _isGeneratingTask.value = false
            _generatingTaskType.value = null
            result.onFailure { err ->
                _errorMessage.value = err.message ?: "Failed to generate exercise"
            }
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            dialogRepository.deleteTask(taskId)
        }
    }

    fun deleteDialog(onDeleted: () -> Unit) {
        viewModelScope.launch {
            dialogRepository.deleteDialog(dialogId)
            onDeleted()
        }
    }

    private fun buildTranscript(segments: List<DialogSegment>): String {
        return segments.joinToString("\n") { seg ->
            val speaker = seg.speakerLabel?.let { "Sprecher $it" } ?: "Sprecher"
            "$speaker: ${seg.text}"
        }
    }
}
