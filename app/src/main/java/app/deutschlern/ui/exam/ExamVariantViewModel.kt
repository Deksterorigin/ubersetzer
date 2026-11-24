package app.deutschlern.ui.exam

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deutschlern.data.exam.ExamContentRepository
import app.deutschlern.data.exam.ExamProgressStore
import app.deutschlern.domain.exam.ExamResponseKind
import app.deutschlern.domain.exam.ExamResult
import app.deutschlern.domain.exam.ExamScoring
import app.deutschlern.domain.exam.ExamVariant
import app.deutschlern.domain.usecase.EvaluateExamWritingUseCase
import app.deutschlern.domain.usecase.ExamTaskDescription
import app.deutschlern.util.DialoguePlayback
import app.deutschlern.util.DialogueSpeaker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExamVariantUiState(
    val loading: Boolean = true,
    val variant: ExamVariant? = null,
    val answers: Map<Int, String> = emptyMap(),
    /** Set once the user checked the objective part; answers are locked until reset. */
    val result: ExamResult? = null,
    val noteInputs: List<String> = emptyList(),
    /** Per phone-note field: right, wrong, or null when the source has no expected answer. */
    val noteResults: List<Boolean?>? = null,
    val draft: String = "",
    val ai: AiEvalState = AiEvalState(),
    val showSample: Boolean = false,
    val writingDone: Boolean = false,
    val playback: DialoguePlayback = DialoguePlayback(),
    /** Segment whose transcript the speaker reads, -1 when none. */
    val playingSegment: Int = -1,
    val rate: Float = 1f,
    val plays: Map<Int, Int> = emptyMap()
) {
    val checked: Boolean get() = result != null || noteResults != null
    val hasObjectivePart: Boolean
        get() = variant != null && (variant.questions.isNotEmpty() || variant.phoneNote != null)
    val wordCount: Int get() = ExamScoring.wordCount(draft)

    /** Share of graded items answered right, phone-note fields included; null before checking. */
    val percent: Int?
        get() {
            if (!checked) return null
            val gradable = (result?.gradable ?: 0) + (noteResults?.count { it != null } ?: 0)
            val correct = (result?.correct ?: 0) + (noteResults?.count { it == true } ?: 0)
            return if (gradable == 0) null else correct * 100 / gradable
        }
}

@HiltViewModel
class ExamVariantViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: ExamContentRepository,
    private val progressStore: ExamProgressStore,
    private val evaluateWriting: EvaluateExamWritingUseCase,
    private val speaker: DialogueSpeaker
) : ViewModel() {

    private val variantId: String = savedStateHandle.get<String>(ARG_VARIANT_ID).orEmpty()

    private val _state = MutableStateFlow(
        ExamVariantUiState(
            answers = savedStateHandle.get<HashMap<Int, String>>(KEY_ANSWERS).orEmpty(),
            noteInputs = savedStateHandle.get<ArrayList<String>>(KEY_NOTE).orEmpty(),
            draft = savedStateHandle.get<String>(KEY_DRAFT).orEmpty()
        )
    )
    val state: StateFlow<ExamVariantUiState> = _state.asStateFlow()

    val timer = ExamTimer(viewModelScope, DEFAULT_WRITING_MINUTES * 60)

    init {
        viewModelScope.launch {
            val variant = runCatching { repository.variant(variantId) }.getOrNull()
            variant?.writing?.let { writing ->
                timer.reset((writing.timeMinutes ?: DEFAULT_WRITING_MINUTES) * 60)
            }
            _state.update { state ->
                val fields = variant?.phoneNote?.fields?.size ?: 0
                state.copy(
                    loading = false,
                    variant = variant,
                    noteInputs = List(fields) { state.noteInputs.getOrNull(it).orEmpty() }
                )
            }
        }
        viewModelScope.launch {
            speaker.state.collect { playback -> _state.update { it.copy(playback = playback) } }
        }
    }

    fun onAnswer(questionNumber: Int, key: String) {
        if (_state.value.checked) return
        _state.update { it.copy(answers = it.answers + (questionNumber to key)) }
        savedStateHandle[KEY_ANSWERS] = HashMap(_state.value.answers)
    }

    fun onNoteInput(index: Int, value: String) {
        if (_state.value.checked) return
        _state.update { state ->
            state.copy(noteInputs = state.noteInputs.toMutableList().also { if (index in it.indices) it[index] = value })
        }
        savedStateHandle[KEY_NOTE] = ArrayList(_state.value.noteInputs)
    }

    fun onCheck() {
        val current = _state.value
        val variant = current.variant ?: return
        val result = variant.questions.takeIf { it.isNotEmpty() }?.let { ExamScoring.score(variant, current.answers) }
        val noteResults = variant.phoneNote?.fields?.mapIndexed { i, field ->
            if (field.answer.isBlank()) null
            else ExamScoring.noteFieldMatches(field.answer, current.noteInputs.getOrNull(i).orEmpty())
        }
        _state.update { it.copy(result = result, noteResults = noteResults) }
        _state.value.percent?.let { percent ->
            viewModelScope.launch { progressStore.recordResult(variant.id, percent) }
        }
    }

    fun onReset() {
        speaker.stop()
        _state.update { state ->
            state.copy(
                answers = emptyMap(),
                result = null,
                noteResults = null,
                noteInputs = List(state.noteInputs.size) { "" }
            )
        }
        savedStateHandle[KEY_ANSWERS] = HashMap<Int, String>()
        savedStateHandle[KEY_NOTE] = ArrayList(_state.value.noteInputs)
    }

    fun onPlayToggle(segmentIndex: Int) {
        val current = _state.value
        if (current.playback.playing && current.playingSegment == segmentIndex) {
            speaker.stop()
            return
        }
        val transcript = current.variant?.segments?.getOrNull(segmentIndex)?.transcript.orEmpty()
        if (transcript.isEmpty()) return
        speaker.stop()
        speaker.setRate(current.rate)
        _state.update {
            it.copy(playingSegment = segmentIndex, plays = it.plays + (segmentIndex to (it.plays[segmentIndex] ?: 0) + 1))
        }
        speaker.play(transcript)
    }

    fun onRateSelected(rate: Float) {
        speaker.setRate(rate)
        _state.update { it.copy(rate = rate) }
    }

    fun onDraftChange(text: String) {
        _state.update { it.copy(draft = text) }
        savedStateHandle[KEY_DRAFT] = text
    }

    fun onToggleSample() = _state.update { it.copy(showSample = !it.showSample) }

    fun onEvaluateWriting() {
        val current = _state.value
        val variant = current.variant ?: return
        val writing = variant.writing ?: return
        if (current.ai.running) return
        _state.update { it.copy(ai = AiEvalState(running = true)) }
        viewModelScope.launch {
            val task = ExamTaskDescription(
                title = variant.title,
                situation = writing.situation,
                points = writing.points,
                writingKind = writing.kind,
                minWords = writing.minWords
            )
            evaluateWriting(task, current.draft, ExamResponseKind.WRITING)
                .onSuccess { evaluation ->
                    _state.update { it.copy(ai = AiEvalState(result = evaluation), writingDone = true) }
                    progressStore.recordResult(variant.id, 100)
                }
                .onFailure { error -> _state.update { it.copy(ai = error.toAiEvalState()) } }
        }
    }

    fun onWritingDone() {
        val variant = _state.value.variant ?: return
        _state.update { it.copy(writingDone = true) }
        timer.pause()
        viewModelScope.launch { progressStore.recordResult(variant.id, 100) }
    }

    override fun onCleared() {
        speaker.release()
        super.onCleared()
    }

    companion object {
        const val ARG_VARIANT_ID = "variantId"
        private const val KEY_ANSWERS = "answers"
        private const val KEY_NOTE = "note_inputs"
        private const val KEY_DRAFT = "draft"
        private const val DEFAULT_WRITING_MINUTES = 30
    }
}
