package app.deutschlern.ui.dialog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deutschlern.domain.dialog.GapFillEvaluator
import app.deutschlern.domain.model.PartnerLanguage
import app.deutschlern.domain.model.dialog.DialogTask
import app.deutschlern.domain.model.dialog.DialogTaskType
import app.deutschlern.domain.model.dialog.GapFillTaskPayload
import app.deutschlern.domain.model.dialog.GapFillTaskResult
import app.deutschlern.domain.model.dialog.OpenEvaluation
import app.deutschlern.domain.model.dialog.OpenTaskPayload
import app.deutschlern.domain.model.dialog.OpenTaskResult
import app.deutschlern.domain.model.dialog.QuizTaskPayload
import app.deutschlern.domain.model.dialog.QuizTaskResult
import app.deutschlern.domain.repository.DialogRepository
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.stt.SpeechToTextEngine
import app.deutschlern.domain.stt.SttEvent
import app.deutschlern.domain.usecase.EvaluateOpenResponseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class DialogTaskUiState(
    val task: DialogTask? = null,
    val isLoading: Boolean = true,
    // MCQ
    val mcqPayload: QuizTaskPayload? = null,
    val mcqAnswers: Map<Int, Int> = emptyMap(), // questionIndex -> selectedOptionIndex
    // GAP_FILL
    val gapFillPayload: GapFillTaskPayload? = null,
    val gapFillAnswers: Map<Int, String> = emptyMap(),
    val gapFillChecks: Map<Int, Boolean> = emptyMap(),
    // OPEN_RESPONSE
    val openPayload: OpenTaskPayload? = null,
    val openAnswers: Map<Int, String> = emptyMap(),
    val openEvaluations: Map<Int, OpenEvaluation> = emptyMap(),
    val isEvaluating: Boolean = false,
    val isListeningVoice: Boolean = false,
    val partnerLanguage: PartnerLanguage = PartnerLanguage.RUSSIAN,
    val errorMessage: String? = null
)

@HiltViewModel
class DialogTaskViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dialogRepository: DialogRepository,
    private val settingsRepository: SettingsRepository,
    private val evaluateOpenResponseUseCase: EvaluateOpenResponseUseCase,
    private val sttEngine: SpeechToTextEngine,
    private val json: Json
) : ViewModel() {

    val dialogId: Long = savedStateHandle.get<String>("dialogId")?.toLongOrNull()
        ?: savedStateHandle.get<Long>("dialogId")
        ?: 0L

    val taskId: Long = savedStateHandle.get<String>("taskId")?.toLongOrNull()
        ?: savedStateHandle.get<Long>("taskId")
        ?: 0L

    private val _uiState = MutableStateFlow(DialogTaskUiState())
    val uiState: StateFlow<DialogTaskUiState> = _uiState.asStateFlow()

    private var sttJob: Job? = null

    init {
        loadTask()
    }

    private fun loadTask() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val partnerLang = settingsRepository.getSettings().selectedPartnerLanguage
            val task = dialogRepository.getTaskById(taskId)

            if (task == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Task not found") }
                return@launch
            }

            when (task.type) {
                DialogTaskType.MCQ -> {
                    val payload = runCatching {
                        json.decodeFromString<QuizTaskPayload>(task.payloadJson)
                    }.getOrNull()
                    val answersMap = mutableMapOf<Int, Int>()
                    task.resultJson?.let { res ->
                        val result = runCatching { json.decodeFromString<QuizTaskResult>(res) }.getOrNull()
                        result?.answers?.forEachIndexed { index, selectedIdx ->
                            if (selectedIdx >= 0) answersMap[index] = selectedIdx
                        }
                    }
                    _uiState.update {
                        it.copy(
                            task = task,
                            isLoading = false,
                            mcqPayload = payload,
                            mcqAnswers = answersMap,
                            partnerLanguage = partnerLang
                        )
                    }
                }
                DialogTaskType.GAP_FILL -> {
                    val payload = runCatching {
                        json.decodeFromString<GapFillTaskPayload>(task.payloadJson)
                    }.getOrNull()
                    val answersMap = mutableMapOf<Int, String>()
                    val checksMap = mutableMapOf<Int, Boolean>()
                    task.resultJson?.let { res ->
                        val result = runCatching { json.decodeFromString<GapFillTaskResult>(res) }.getOrNull()
                        result?.userAnswers?.forEachIndexed { index, answer ->
                            answersMap[index] = answer
                            payload?.exercises?.getOrNull(index)?.let { ex ->
                                checksMap[index] = GapFillEvaluator.isAnswerCorrect(answer, ex.expectedAnswer, ex.alternatives)
                            }
                        }
                    }
                    _uiState.update {
                        it.copy(
                            task = task,
                            isLoading = false,
                            gapFillPayload = payload,
                            gapFillAnswers = answersMap,
                            gapFillChecks = checksMap,
                            partnerLanguage = partnerLang
                        )
                    }
                }
                DialogTaskType.OPEN_RESPONSE -> {
                    val payload = runCatching {
                        json.decodeFromString<OpenTaskPayload>(task.payloadJson)
                    }.getOrNull()
                    val answersMap = mutableMapOf<Int, String>()
                    val evalsMap = mutableMapOf<Int, OpenEvaluation>()
                    task.resultJson?.let { res ->
                        val result = runCatching { json.decodeFromString<OpenTaskResult>(res) }.getOrNull()
                        result?.answers?.forEachIndexed { index, ans ->
                            answersMap[index] = ans
                        }
                        result?.evaluations?.forEachIndexed { index, ev ->
                            evalsMap[index] = ev
                        }
                    }
                    _uiState.update {
                        it.copy(
                            task = task,
                            isLoading = false,
                            openPayload = payload,
                            openAnswers = answersMap,
                            openEvaluations = evalsMap,
                            partnerLanguage = partnerLang
                        )
                    }
                }
            }
        }
    }

    fun selectMcqOption(questionIndex: Int, optionIndex: Int) {
        val currentAnswers = _uiState.value.mcqAnswers.toMutableMap()
        currentAnswers[questionIndex] = optionIndex
        _uiState.update { it.copy(mcqAnswers = currentAnswers) }

        val payload = _uiState.value.mcqPayload ?: return
        val totalQuestions = payload.questions.size
        var correctCount = 0

        payload.questions.forEachIndexed { idx, q ->
            val chosen = currentAnswers[idx]
            if (chosen != null && chosen == q.correctIndex) {
                correctCount++
            }
        }

        val allAnswered = currentAnswers.size >= totalQuestions
        val scorePercent = if (totalQuestions > 0) (correctCount * 100) / totalQuestions else 0

        val result = QuizTaskResult(
            answers = (0 until totalQuestions).map { currentAnswers[it] ?: -1 }
        )
        val resultJson = json.encodeToString(QuizTaskResult.serializer(), result)

        viewModelScope.launch {
            val t = _uiState.value.task ?: return@launch
            val updated = t.copy(
                resultJson = resultJson,
                scorePercent = if (allAnswered) scorePercent else t.scorePercent,
                completedAt = if (allAnswered) System.currentTimeMillis() else t.completedAt
            )
            dialogRepository.updateTask(updated)
            _uiState.update { it.copy(task = updated) }
        }
    }

    fun updateGapFillAnswer(index: Int, text: String) {
        val answers = _uiState.value.gapFillAnswers.toMutableMap()
        answers[index] = text
        _uiState.update { it.copy(gapFillAnswers = answers) }
    }

    fun checkGapFillAnswer(index: Int) {
        val payload = _uiState.value.gapFillPayload ?: return
        val exercise = payload.exercises.getOrNull(index) ?: return
        val userAns = _uiState.value.gapFillAnswers[index] ?: ""

        val isCorrect = GapFillEvaluator.isAnswerCorrect(
            userAnswer = userAns,
            expectedAnswer = exercise.expectedAnswer,
            alternatives = exercise.alternatives
        )

        val checks = _uiState.value.gapFillChecks.toMutableMap()
        checks[index] = isCorrect
        _uiState.update { it.copy(gapFillChecks = checks) }

        val totalExercises = payload.exercises.size
        val allChecked = checks.size >= totalExercises
        val correctCount = checks.values.count { it }
        val scorePercent = if (totalExercises > 0) (correctCount * 100) / totalExercises else 0

        val result = GapFillTaskResult(
            userAnswers = (0 until totalExercises).map { _uiState.value.gapFillAnswers[it] ?: "" }
        )
        val resultJson = json.encodeToString(GapFillTaskResult.serializer(), result)

        viewModelScope.launch {
            val t = _uiState.value.task ?: return@launch
            val updated = t.copy(
                resultJson = resultJson,
                scorePercent = if (allChecked) scorePercent else t.scorePercent,
                completedAt = if (allChecked) System.currentTimeMillis() else t.completedAt
            )
            dialogRepository.updateTask(updated)
            _uiState.update { it.copy(task = updated) }
        }
    }

    fun updateOpenAnswer(index: Int, text: String) {
        val answers = _uiState.value.openAnswers.toMutableMap()
        answers[index] = text
        _uiState.update { it.copy(openAnswers = answers) }
    }

    fun evaluateOpenAnswer(index: Int) {
        val payload = _uiState.value.openPayload ?: return
        val question = payload.questions.getOrNull(index) ?: return
        val studentAnswer = _uiState.value.openAnswers[index] ?: ""
        if (studentAnswer.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isEvaluating = true, errorMessage = null) }
            val partnerLang = settingsRepository.getSettings().selectedPartnerLanguage

            val evalResult = evaluateOpenResponseUseCase(
                questionDe = question.promptDe,
                studentAnswer = studentAnswer,
                partnerLanguage = partnerLang
            )

            _uiState.update { it.copy(isEvaluating = false) }

            evalResult.onSuccess { evaluation ->
                val evals = _uiState.value.openEvaluations.toMutableMap()
                evals[index] = evaluation
                _uiState.update { it.copy(openEvaluations = evals) }

                val total = payload.questions.size
                val avgScore = if (evals.isNotEmpty()) evals.values.map { it.scorePercent }.average().toInt() else 0
                val allAnswered = evals.size >= total

                val result = OpenTaskResult(
                    answers = (0 until total).map { _uiState.value.openAnswers[it] ?: "" },
                    evaluations = (0 until total).mapNotNull { evals[it] }
                )
                val resultJson = json.encodeToString(OpenTaskResult.serializer(), result)

                val t = _uiState.value.task ?: return@launch
                val updated = t.copy(
                    resultJson = resultJson,
                    scorePercent = if (allAnswered) avgScore else t.scorePercent,
                    completedAt = if (allAnswered) System.currentTimeMillis() else t.completedAt
                )
                dialogRepository.updateTask(updated)
                _uiState.update { it.copy(task = updated) }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Failed to evaluate answer") }
            }
        }
    }

    fun toggleVoiceInput(index: Int) {
        if (_uiState.value.isListeningVoice) {
            stopVoiceInput()
        } else {
            startVoiceInput(index)
        }
    }

    private fun startVoiceInput(index: Int) {
        sttJob?.cancel()
        _uiState.update { it.copy(isListeningVoice = true) }
        val currentPrefix = _uiState.value.openAnswers[index] ?: ""

        sttJob = viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            sttEngine.startListening("de-DE", preferOffline = settings.sttPreferOnDevice).collect { event ->
                when (event) {
                    is SttEvent.Final -> {
                        val appendText = if (currentPrefix.isBlank()) event.text.trim() else "$currentPrefix ${event.text.trim()}"
                        updateOpenAnswer(index, appendText)
                        stopVoiceInput()
                    }
                    is SttEvent.Error -> {
                        if (!event.isRecoverable) {
                            stopVoiceInput()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun stopVoiceInput() {
        _uiState.update { it.copy(isListeningVoice = false) }
        sttEngine.stopListening()
        sttJob?.cancel()
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        sttEngine.cancel()
        sttJob?.cancel()
    }
}
