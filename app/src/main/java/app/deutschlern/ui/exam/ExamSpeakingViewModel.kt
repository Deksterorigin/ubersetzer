package app.deutschlern.ui.exam

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deutschlern.data.exam.ExamContentRepository
import app.deutschlern.data.exam.ExamProgressStore
import app.deutschlern.domain.exam.ExamResponseKind
import app.deutschlern.domain.exam.SpeakingPrompt
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.stt.SpeechToTextEngine
import app.deutschlern.domain.stt.SttEvent
import app.deutschlern.domain.usecase.EvaluateExamWritingUseCase
import app.deutschlern.domain.usecase.ExamTaskDescription
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

data class ExamSpeakingUiState(
    val teil: String = "",
    val loading: Boolean = true,
    val prompts: List<SpeakingPrompt> = emptyList(),
    val index: Int = 0,
    val showRu: Boolean = false,
    val listMode: Boolean = false,
    val showSample: Boolean = false,
    val timerMinutes: Int = 2,
    val recording: Boolean = false,
    val transcript: String = "",
    val partial: String = "",
    val sttError: String? = null,
    val hasMicPermission: Boolean = true,
    val ai: AiEvalState = AiEvalState(),
    val done: Set<String> = emptySet()
) {
    val current: SpeakingPrompt? get() = prompts.getOrNull(index)
}

@HiltViewModel
class ExamSpeakingViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: ExamContentRepository,
    private val progressStore: ExamProgressStore,
    private val evaluate: EvaluateExamWritingUseCase,
    private val sttEngine: SpeechToTextEngine,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val teil: String = savedStateHandle.get<String>(ARG_TEIL).orEmpty()
    private val _state = MutableStateFlow(ExamSpeakingUiState(teil = teil))
    val state: StateFlow<ExamSpeakingUiState> = _state.asStateFlow()

    val timer = ExamTimer(viewModelScope, 2 * 60)
    private var sttJob: Job? = null

    init {
        viewModelScope.launch {
            val prompts = runCatching { repository.speakingPrompts() }.getOrDefault(emptyList()).filter { it.teil == teil }
            val savedIndex = savedStateHandle.get<Int>(KEY_INDEX)
            val index = savedIndex?.takeIf { it in prompts.indices }
                ?: if (prompts.isEmpty()) 0 else Random.nextInt(prompts.size)
            savedStateHandle[KEY_INDEX] = index
            _state.update { it.copy(loading = false, prompts = prompts, index = index) }
        }
        viewModelScope.launch {
            progressStore.bestScores.collect { scores -> _state.update { it.copy(done = scores.keys) } }
        }
    }

    fun onNext() = select(_state.value.index + 1)

    fun onPrevious() = select(_state.value.index - 1)

    fun onRandom() {
        val size = _state.value.prompts.size
        if (size > 1) select(Random.nextInt(size))
    }

    fun select(index: Int) {
        val size = _state.value.prompts.size
        if (size == 0) return
        val wrapped = ((index % size) + size) % size
        stopRecording()
        timer.reset()
        savedStateHandle[KEY_INDEX] = wrapped
        _state.update {
            it.copy(index = wrapped, listMode = false, showSample = false, transcript = "", partial = "", ai = AiEvalState(), sttError = null)
        }
    }

    fun onToggleRu() = _state.update { it.copy(showRu = !it.showRu) }

    fun onToggleListMode() = _state.update { it.copy(listMode = !it.listMode) }

    fun onToggleSample() = _state.update { it.copy(showSample = !it.showSample) }

    fun onTimerMinutes(minutes: Int) {
        timer.reset(minutes * 60)
        _state.update { it.copy(timerMinutes = minutes) }
    }

    fun onTranscriptEdit(text: String) = _state.update { it.copy(transcript = text) }

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(hasMicPermission = granted) }
        if (granted) startRecording()
    }

    fun onRecordToggle() {
        if (_state.value.recording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        sttJob?.cancel()
        _state.update { it.copy(recording = true, sttError = null, partial = "") }
        sttJob = viewModelScope.launch {
            val preferOffline = runCatching { settingsRepository.settingsFlow.first().sttPreferOnDevice }.getOrDefault(false)
            sttEngine.startListening(languageCode = "de-DE", preferOffline = preferOffline).collect { event ->
                when (event) {
                    is SttEvent.Partial -> _state.update { it.copy(partial = event.text) }
                    is SttEvent.Final -> _state.update {
                        val text = event.text.trim()
                        it.copy(
                            transcript = if (text.isEmpty()) it.transcript else listOf(it.transcript.trim(), text).filter(String::isNotEmpty).joinToString(" "),
                            partial = ""
                        )
                    }
                    is SttEvent.Error -> if (!event.isRecoverable) {
                        commitPartial()
                        _state.update { it.copy(recording = false, sttError = event.message) }
                        sttJob?.cancel()
                    }
                    is SttEvent.StateChanged, is SttEvent.SoundLevel -> Unit
                }
            }
        }
    }

    private fun stopRecording() {
        sttJob?.cancel()
        sttJob = null
        commitPartial()
        _state.update { it.copy(recording = false) }
    }

    private fun commitPartial() {
        _state.update {
            if (it.partial.isBlank()) it
            else it.copy(transcript = listOf(it.transcript.trim(), it.partial.trim()).filter(String::isNotEmpty).joinToString(" "), partial = "")
        }
    }

    fun onEvaluate() {
        stopRecording()
        val current = _state.value
        val prompt = current.current ?: return
        if (current.ai.running) return
        _state.update { it.copy(ai = AiEvalState(running = true)) }
        viewModelScope.launch {
            val task = ExamTaskDescription(
                title = listOfNotNull("Sprechen Teil ${prompt.teil}", prompt.title).joinToString(": "),
                situation = prompt.de,
                points = prompt.tasks
            )
            evaluate(task, current.transcript, ExamResponseKind.SPEAKING)
                .onSuccess { evaluation ->
                    _state.update { it.copy(ai = AiEvalState(result = evaluation)) }
                    progressStore.recordResult(prompt.id, 100)
                }
                .onFailure { error -> _state.update { it.copy(ai = error.toAiEvalState()) } }
        }
    }

    fun onMarkDone() {
        val prompt = _state.value.current ?: return
        viewModelScope.launch { progressStore.recordResult(prompt.id, 100) }
    }

    override fun onCleared() {
        sttJob?.cancel()
        super.onCleared()
    }

    companion object {
        const val ARG_TEIL = "teil"
        private const val KEY_INDEX = "index"
    }
}
