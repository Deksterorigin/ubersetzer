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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    val dialogId: Long = savedStateHandle.get<String>("dialogId")?.toLongOrNull()
        ?: savedStateHandle.get<Long>("dialogId")
        ?: 0L

    private val _selectedTab = MutableStateFlow(0)
    private val _isGeneratingSummary = MutableStateFlow(false)
    private val _isGeneratingTask = MutableStateFlow(false)
    private val _generatingTaskType = MutableStateFlow<DialogTaskType?>(null)
    private val _selectedWordCard = MutableStateFlow<WordTranslation?>(null)
    private val _isWordLoading = MutableStateFlow(false)
    private val _wordMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _dialogFlow = MutableStateFlow<Dialog?>(null)

    val uiState: StateFlow<DialogDetailUiState> = combine(
        _dialogFlow,
        dialogRepository.getSegmentsForDialog(dialogId),
        dialogRepository.getTasksForDialog(dialogId),
        _selectedTab,
        _isGeneratingSummary,
        _isGeneratingTask,
        _selectedWordCard,
        _isWordLoading,
        settingsRepository.settingsFlow
    ) { args ->
        val dialog = args[0] as? Dialog
        val segments = (args[1] as? List<*>)?.filterIsInstance<DialogSegment>() ?: emptyList()
        val tasks = (args[2] as? List<*>)?.filterIsInstance<DialogTask>() ?: emptyList()
        val tab = args[3] as Int
        val genSummary = args[4] as Boolean
        val genTask = args[5] as Boolean
        val wordCard = args[6] as? WordTranslation
        val wordLoading = args[7] as Boolean
        val settings = args[8] as app.deutschlern.domain.repository.UserSettings

        DialogDetailUiState(
            dialog = dialog,
            segments = segments,
            tasks = tasks,
            selectedTab = tab,
            isGeneratingSummary = genSummary,
            isGeneratingTask = genTask,
            generatingTaskType = _generatingTaskType.value,
            selectedWordCard = wordCard,
            isWordLoading = wordLoading,
            wordMessage = _wordMessage.value,
            errorMessage = _errorMessage.value,
            partnerLanguage = settings.selectedPartnerLanguage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DialogDetailUiState()
    )

    init {
        loadDialog()
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
            runCatching {
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
        if (currentSegments.isEmpty()) return

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
        if (currentSegments.isEmpty()) return

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

    private fun buildTranscript(segments: List<DialogSegment>): String {
        return segments.joinToString("\n") { seg ->
            val speaker = seg.speakerLabel?.let { "Sprecher $it" } ?: "Sprecher"
            "$speaker: ${seg.text}"
        }
    }
}
