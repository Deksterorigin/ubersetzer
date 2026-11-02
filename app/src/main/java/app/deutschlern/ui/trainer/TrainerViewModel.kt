package app.deutschlern.ui.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deutschlern.data.trainer.TrainerStatsStore
import app.deutschlern.domain.model.ReviewCard
import app.deutschlern.domain.model.WordEntry
import app.deutschlern.domain.repository.ReviewRepository
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.repository.VocabularyRepository
import app.deutschlern.domain.sm2.Sm2Grade
import app.deutschlern.domain.stt.SpeechToTextEngine
import app.deutschlern.domain.stt.SttEvent
import app.deutschlern.domain.trainer.AnswerChecker
import app.deutschlern.domain.trainer.AnswerResult
import app.deutschlern.domain.trainer.AnswerVerdict
import app.deutschlern.domain.trainer.Exercise
import app.deutschlern.domain.trainer.ExerciseFactory
import app.deutschlern.domain.trainer.TrainerMode
import app.deutschlern.domain.trainer.TrainerSessionPlanner
import app.deutschlern.domain.trainer.TypingKind
import app.deutschlern.domain.trainer.WordPool
import app.deutschlern.util.TtsManager
import app.deutschlern.util.runSuspendCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrainerUiState(
    val mode: TrainerMode = TrainerMode.MIXED,
    val isLoading: Boolean = true,
    val queue: List<Exercise> = emptyList(),
    val index: Int = 0,
    val input: String = "",
    val hintLevel: Int = 0,
    val verdict: AnswerVerdict? = null,
    val selectedOption: Int? = null,
    val isListening: Boolean = false,
    val speechError: String? = null,
    val totalWords: Int = 0,
    val firstTryCorrect: Int = 0,
    val mistakes: List<WordEntry> = emptyList(),
    val autoMarkedDifficult: List<WordEntry> = emptyList()
) {
    val current: Exercise? get() = queue.getOrNull(index)
    val isFinished: Boolean get() = !isLoading && index >= queue.size
    val isEmpty: Boolean get() = !isLoading && queue.isEmpty()
    val progress: Float get() = if (queue.isEmpty()) 0f else index.toFloat() / queue.size
}

@HiltViewModel
class TrainerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reviewRepository: ReviewRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val settingsRepository: SettingsRepository,
    private val sttEngine: SpeechToTextEngine,
    private val ttsManager: TtsManager,
    private val statsStore: TrainerStatsStore
) : ViewModel() {

    private val mode = savedStateHandle.get<String>(ARG_MODE)
        ?.let { runCatching { TrainerMode.valueOf(it) }.getOrNull() } ?: TrainerMode.MIXED
    private val pool = savedStateHandle.get<String>(ARG_POOL)
        ?.let { runCatching { WordPool.valueOf(it) }.getOrNull() } ?: WordPool.SMART

    private val factory = ExerciseFactory()

    private val _uiState = MutableStateFlow(TrainerUiState(mode = mode))
    val uiState: StateFlow<TrainerUiState> = _uiState.asStateFlow()

    private var cardsById: Map<Long, ReviewCard> = emptyMap()
    private var allWords: List<WordEntry> = emptyList()
    // Only the first answer per word counts for scheduling and statistics
    private val gradedWordIds = mutableSetOf<Long>()
    private val wrongCounts = mutableMapOf<Long, Int>()
    private var sttJob: Job? = null

    init {
        startSession(restrictTo = null)
    }

    fun restartWithMistakes() {
        val ids = _uiState.value.mistakes.map { it.id }.toSet()
        startSession(restrictTo = ids)
    }

    fun restart() = startSession(restrictTo = null)

    private fun startSession(restrictTo: Set<Long>?) {
        stopListening()
        gradedWordIds.clear()
        wrongCounts.clear()
        _uiState.value = TrainerUiState(mode = mode, isLoading = true)
        viewModelScope.launch {
            val cards = reviewRepository.getAllCards()
            cardsById = cards.associateBy { it.word.id }
            allWords = cards.map { it.word }

            val eligible = cards.filter { factory.canBuild(it.word, mode) && (restrictTo == null || it.word.id in restrictTo) }
            val words = if (restrictTo != null) {
                eligible.map { it.word }.shuffled()
            } else {
                TrainerSessionPlanner.select(eligible, pool, System.currentTimeMillis())
            }
            val exercises = factory.build(words, mode, allWords)
            _uiState.update { it.copy(isLoading = false, queue = exercises, totalWords = exercises.size) }
            speakPromptIfNeeded()
        }
    }

    fun onInputChanged(text: String) {
        if (_uiState.value.verdict != null) return
        _uiState.update { it.copy(input = text) }
    }

    fun onHint() {
        val exercise = _uiState.value.current as? Exercise.Typing ?: return
        if (_uiState.value.verdict != null) return
        _uiState.update { it.copy(hintLevel = (it.hintLevel + 1).coerceAtMost(MAX_HINT_LEVEL)) }
        if (exercise.kind == TypingKind.DICTATION) speak(exercise.audioText)
    }

    fun onSubmitTyped() {
        val state = _uiState.value
        if (state.verdict != null) return
        val verdict = when (val exercise = state.current) {
            is Exercise.Typing -> if (exercise.kind == TypingKind.PERFEKT) {
                val (auxiliary, participle) = exercise.expected.split(" ", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                AnswerChecker.checkPerfekt(state.input, auxiliary, participle)
            } else {
                AnswerChecker.check(state.input, exercise.expected, exercise.requiredArticle, exercise.nearMisses)
            }
            is Exercise.Speaking -> AnswerChecker.check(state.input, exercise.expected, exercise.requiredArticle)
            else -> return
        }
        // Any hint means the word was not recalled freely: never better than "almost"
        val capped = if (state.hintLevel > 0 && verdict.result == AnswerResult.CORRECT) {
            AnswerVerdict(AnswerResult.ALMOST, verdict.note)
        } else {
            verdict
        }
        applyVerdict(capped)
    }

    fun onDontKnow() {
        if (_uiState.value.verdict != null) return
        applyVerdict(AnswerVerdict(AnswerResult.WRONG))
    }

    fun onOptionSelected(index: Int) {
        val state = _uiState.value
        if (state.verdict != null) return
        val correct = when (val exercise = state.current) {
            is Exercise.Choice -> index == exercise.correctIndex
            else -> return
        }
        _uiState.update { it.copy(selectedOption = index) }
        applyVerdict(AnswerVerdict(if (correct) AnswerResult.CORRECT else AnswerResult.WRONG))
    }

    fun onArticleSelected(article: String) {
        val state = _uiState.value
        if (state.verdict != null) return
        val exercise = state.current as? Exercise.Article ?: return
        _uiState.update { it.copy(input = article) }
        applyVerdict(AnswerVerdict(if (article == exercise.correctArticle) AnswerResult.CORRECT else AnswerResult.WRONG))
    }

    private fun applyVerdict(verdict: AnswerVerdict) {
        val exercise = _uiState.value.current ?: return
        val word = exercise.word
        val isFirstAnswer = gradedWordIds.add(word.id)

        _uiState.update { state ->
            var queue = state.queue
            if (verdict.result == AnswerResult.WRONG) {
                // Forgotten words come back a few cards later, until they are answered
                val insertAt = (state.index + REQUEUE_DISTANCE).coerceAtMost(queue.size)
                queue = queue.toMutableList().apply { add(insertAt, reshuffled(exercise)) }
            }
            state.copy(
                verdict = verdict,
                queue = queue,
                firstTryCorrect = state.firstTryCorrect + if (isFirstAnswer && verdict.result == AnswerResult.CORRECT) 1 else 0,
                mistakes = if (verdict.result == AnswerResult.WRONG && state.mistakes.none { it.id == word.id }) {
                    state.mistakes + word
                } else {
                    state.mistakes
                }
            )
        }

        speak(spokenAnswer(exercise))
        viewModelScope.launch {
            statsStore.recordAnswer()
            if (isFirstAnswer) schedule(word, verdict.result)
            if (verdict.result == AnswerResult.WRONG) markLeechIfNeeded(word)
        }
    }

    /**
     * Practice outside the schedule must not inflate intervals: a forgotten word is always reset,
     * a remembered one only moves forward when it was actually due.
     */
    private suspend fun schedule(word: WordEntry, result: AnswerResult) {
        val card = cardsById[word.id] ?: return
        val isDue = card.sm2State.dueAt <= System.currentTimeMillis()
        val grade = when {
            result == AnswerResult.WRONG -> Sm2Grade.AGAIN
            !isDue -> return
            result == AnswerResult.ALMOST -> Sm2Grade.HARD
            else -> Sm2Grade.GOOD
        }
        runSuspendCatching { reviewRepository.reviewCard(word.id, grade) }
    }

    private suspend fun markLeechIfNeeded(word: WordEntry) {
        val count = (wrongCounts[word.id] ?: 0) + 1
        wrongCounts[word.id] = count
        if (count == LEECH_THRESHOLD && !word.isDifficult) {
            runSuspendCatching { vocabularyRepository.updateWordDifficulty(word.id, true) }
            _uiState.update { it.copy(autoMarkedDifficult = it.autoMarkedDifficult + word) }
        }
    }

    fun onNext() {
        stopListening()
        _uiState.update {
            it.copy(index = it.index + 1, input = "", hintLevel = 0, verdict = null, selectedOption = null, speechError = null)
        }
        speakPromptIfNeeded()
    }

    fun onSpeak(text: String) = speak(text)

    fun onToggleListening() {
        if (_uiState.value.isListening) {
            stopListening()
            return
        }
        if (_uiState.value.current !is Exercise.Speaking || _uiState.value.verdict != null) return
        _uiState.update { it.copy(isListening = true, input = "", speechError = null) }
        sttJob = viewModelScope.launch {
            val preferOffline = settingsRepository.settingsFlow.first().sttPreferOnDevice
            sttEngine.startListening("de-DE", preferOffline).collect { event ->
                when (event) {
                    is SttEvent.Partial -> _uiState.update { it.copy(input = event.text) }
                    is SttEvent.Final -> {
                        _uiState.update { it.copy(input = event.text, isListening = false) }
                        onSubmitTyped()
                        stopListening()
                    }
                    is SttEvent.Error -> if (!event.isRecoverable) {
                        _uiState.update { it.copy(isListening = false, speechError = event.message) }
                        stopListening()
                    }
                    else -> Unit
                }
            }
        }
    }

    fun onPermissionDenied() {
        _uiState.update { it.copy(speechError = null, isListening = false) }
    }

    private fun stopListening() {
        sttJob?.cancel()
        sttJob = null
        if (_uiState.value.isListening) _uiState.update { it.copy(isListening = false) }
    }

    private fun speakPromptIfNeeded() {
        val exercise = _uiState.value.current as? Exercise.Typing ?: return
        if (exercise.kind == TypingKind.DICTATION) speak(exercise.audioText)
    }

    private fun speak(text: String?) {
        if (text.isNullOrBlank()) return
        ttsManager.speak(text) {}
    }

    private fun spokenAnswer(exercise: Exercise): String? = when (exercise) {
        is Exercise.Typing -> exercise.audioText ?: exercise.expected
        is Exercise.Article -> "${exercise.correctArticle} ${exercise.word.lemma}"
        is Exercise.Choice -> exercise.prompt
        is Exercise.Speaking -> listOfNotNull(exercise.requiredArticle, exercise.expected).joinToString(" ")
    }

    private fun reshuffled(exercise: Exercise): Exercise =
        if (exercise is Exercise.Choice) {
            val options = exercise.options.shuffled()
            exercise.copy(options = options, correctIndex = options.indexOf(exercise.options[exercise.correctIndex]))
        } else {
            exercise
        }

    override fun onCleared() {
        super.onCleared()
        sttJob?.cancel()
        ttsManager.stop()
    }

    companion object {
        const val ARG_MODE = "mode"
        const val ARG_POOL = "pool"
        const val MAX_HINT_LEVEL = 2
        private const val REQUEUE_DISTANCE = 3
        // Two misses in one session is a strong "keeps slipping away" signal
        private const val LEECH_THRESHOLD = 2
    }
}
