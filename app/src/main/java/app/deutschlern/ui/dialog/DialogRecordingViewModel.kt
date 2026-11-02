package app.deutschlern.ui.dialog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deutschlern.data.stt.SpeechDebugLogger
import app.deutschlern.domain.model.dialog.Dialog
import app.deutschlern.domain.model.dialog.DialogSegment
import app.deutschlern.domain.model.dialog.DialogStatus
import app.deutschlern.domain.repository.DialogRepository
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.stt.SpeechToTextEngine
import app.deutschlern.domain.stt.SttEvent
import app.deutschlern.ui.navigation.getNavigationLong
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

data class DialogRecordingUiState(
    val draftDialogId: Long = 0,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val isReconnecting: Boolean = false,
    val reconnectingMessage: String? = null,
    val pauseReason: String? = null,
    val elapsedSeconds: Long = 0,
    val activeSpeaker: String = "A", // "A" or "B"
    val segments: List<DialogSegment> = emptyList(),
    val currentPartialText: String = "",
    val soundLevelRms: Float = 0f,
    val isSaving: Boolean = false,
    val savedDialogId: Long? = null,
    val isDismissed: Boolean = false,
    val errorMessage: String? = null,
    val hasAudioPermission: Boolean = true
)

@HiltViewModel
class DialogRecordingViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val sttEngine: SpeechToTextEngine,
    private val dialogRepository: DialogRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DialogRecordingUiState())
    val uiState: StateFlow<DialogRecordingUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var sttJob: Job? = null

    // Segments are committed from the STT collector and from user actions concurrently;
    // the lock keeps orderIndex unique and the in-memory list in sync with the database.
    private val commitMutex = Mutex()

    // Dialog as it was before "continue recording"; cancel must restore it, not delete it.
    private var resumedOriginal: Dialog? = null
    private val segmentIdsAddedThisSession = mutableListOf<Long>()

    // A partial committed manually (speaker switch, pause) is usually delivered again by the
    // recognizer as the beginning of the next Final; this prefix is stripped to avoid duplicates.
    private var committedPartialPrefix: String? = null

    init {
        val targetDialogId = savedStateHandle.getNavigationLong("dialogId")
        if (targetDialogId > 0L) {
            resumeRecordingSession(targetDialogId)
        } else {
            startNewRecordingSession()
        }
    }

    private fun startNewRecordingSession() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val timeStr = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(now))
            val newDialog = Dialog(
                title = "Dialog $timeStr",
                startedAt = now,
                durationMs = 0,
                segmentCount = 0,
                status = DialogStatus.RECORDING
            )
            val dialogId = withContext(NonCancellable) {
                dialogRepository.saveDialog(newDialog)
            }

            _uiState.update {
                it.copy(
                    draftDialogId = dialogId,
                    isRecording = true,
                    isPaused = false,
                    isReconnecting = false,
                    elapsedSeconds = 0,
                    activeSpeaker = "A",
                    segments = emptyList(),
                    currentPartialText = ""
                )
            }

            startTimer()
            startListening()
        }
    }

    private fun resumeRecordingSession(dialogId: Long) {
        viewModelScope.launch {
            val existing = dialogRepository.getDialogById(dialogId)
            if (existing == null) {
                startNewRecordingSession()
                return@launch
            }
            resumedOriginal = existing
            val existingSegments = dialogRepository.getSegmentsSync(dialogId)

            withContext(NonCancellable) {
                dialogRepository.updateDialog(existing.copy(status = DialogStatus.RECORDING))
            }

            _uiState.update {
                it.copy(
                    draftDialogId = dialogId,
                    isRecording = true,
                    isPaused = false,
                    isReconnecting = false,
                    elapsedSeconds = existing.durationMs / 1000L,
                    activeSpeaker = existingSegments.lastOrNull()?.speakerLabel ?: "A",
                    segments = existingSegments,
                    currentPartialText = ""
                )
            }

            startTimer()
            startListening()
        }
    }

    fun onPermissionGranted() {
        if (_uiState.value.hasAudioPermission) return
        _uiState.update {
            it.copy(
                hasAudioPermission = true,
                isRecording = true,
                isPaused = false,
                pauseReason = null,
                errorMessage = null
            )
        }
        startTimer()
        startListening()
    }

    fun onPermissionDenied() {
        _uiState.update {
            it.copy(
                hasAudioPermission = false,
                isRecording = false,
                isPaused = true,
                pauseReason = "Отсутствует доступ к микрофону"
            )
        }
        stopEngine()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000L)
                _uiState.update {
                    if (it.isRecording && !it.isPaused) it.copy(elapsedSeconds = it.elapsedSeconds + 1) else it
                }
            }
        }
    }

    private fun stopEngine() {
        sttJob?.cancel()
        timerJob?.cancel()
    }

    private fun startListening() {
        sttJob?.cancel()
        sttJob = viewModelScope.launch {
            val preferOffline = settingsRepository.settingsFlow.first().sttPreferOnDevice

            sttEngine.startListening(languageCode = "de-DE", preferOffline = preferOffline).collect { event ->
                when (event) {
                    is SttEvent.Partial -> {
                        val visible = stripCommittedPrefix(event.text, consume = false)
                        _uiState.update {
                            it.copy(currentPartialText = visible, isReconnecting = false, reconnectingMessage = null)
                        }
                    }

                    is SttEvent.Final -> {
                        val text = stripCommittedPrefix(event.text.trim(), consume = true)
                        if (text.isNotBlank()) {
                            commitSegment(text)
                        } else {
                            _uiState.update { it.copy(currentPartialText = "") }
                        }
                        _uiState.update { it.copy(isReconnecting = false, reconnectingMessage = null) }
                    }

                    is SttEvent.SoundLevel -> {
                        // RMS arrives ~20x per second; skip imperceptible changes to avoid recomposing the screen
                        _uiState.update {
                            if (abs(it.soundLevelRms - event.rmsDb) < 1f) it else it.copy(soundLevelRms = event.rmsDb)
                        }
                    }

                    is SttEvent.Error -> {
                        if (event.isRecoverable) {
                            _uiState.update { it.copy(isReconnecting = true, reconnectingMessage = event.message) }
                        } else {
                            // Retries exhausted: keep what was heard and pause with a visible reason
                            commitPendingPartial()
                            _uiState.update {
                                it.copy(
                                    isRecording = false,
                                    isPaused = true,
                                    isReconnecting = false,
                                    reconnectingMessage = null,
                                    pauseReason = event.message,
                                    errorMessage = event.message
                                )
                            }
                            timerJob?.cancel()
                        }
                    }

                    is SttEvent.StateChanged -> {
                        // Recognizer is ready again after a retry: drop the "reconnecting" banner
                        if (event.isListening) {
                            _uiState.update { it.copy(isReconnecting = false, reconnectingMessage = null) }
                        }
                    }
                }
            }
        }
    }

    /**
     * Removes the words that were already committed manually from the start of a recognizer result.
     * Compared word by word ignoring case and punctuation, because the recognizer usually
     * re-punctuates and re-capitalizes the final text ("guten tag" -> "Guten Tag.").
     */
    private fun stripCommittedPrefix(text: String, consume: Boolean): String {
        val committed = committedPartialPrefix ?: return text
        val committedWords = words(committed).map { it.second }
        val tokens = words(text)
        val overlap = minOf(committedWords.size, tokens.size)
        if (overlap == 0 || tokens.take(overlap).map { it.second } != committedWords.take(overlap)) {
            committedPartialPrefix = null
            return text
        }
        if (consume) committedPartialPrefix = null
        return tokens.drop(overlap).joinToString(" ") { it.first }
    }

    private fun words(text: String): List<Pair<String, String>> =
        text.split(WHITESPACE)
            .map { token -> token to token.lowercase().filter(Char::isLetterOrDigit) }
            .filter { it.second.isNotEmpty() }

    private suspend fun commitPendingPartial() {
        val partial = _uiState.value.currentPartialText.trim()
        if (partial.isBlank()) return
        commitSegment(partial)
        committedPartialPrefix = (committedPartialPrefix?.let { "$it " } ?: "") + partial
    }

    private suspend fun commitSegment(text: String) {
        if (text.isBlank()) return
        withContext(NonCancellable) {
            commitMutex.withLock {
                val state = _uiState.value
                val dialogId = state.draftDialogId
                if (dialogId == 0L) return@withLock

                val segment = DialogSegment(
                    dialogId = dialogId,
                    orderIndex = state.segments.size,
                    text = text,
                    speakerLabel = state.activeSpeaker,
                    startOffsetMs = state.elapsedSeconds * 1000L
                )
                val savedSegment = segment.copy(id = dialogRepository.insertSegment(segment))
                segmentIdsAddedThisSession += savedSegment.id

                _uiState.update { it.copy(segments = it.segments + savedSegment, currentPartialText = "") }
                val segmentCount = _uiState.value.segments.size

                dialogRepository.getDialogById(dialogId)?.let { current ->
                    dialogRepository.updateDialog(
                        current.copy(segmentCount = segmentCount, durationMs = state.elapsedSeconds * 1000L)
                    )
                }

                SpeechDebugLogger.onSegmentCommitted(
                    speaker = state.activeSpeaker,
                    charCount = text.length,
                    totalSegments = segmentCount
                )
            }
        }
    }

    fun toggleSpeaker() {
        setSpeaker(if (_uiState.value.activeSpeaker == "A") "B" else "A")
    }

    fun setSpeaker(speaker: String) {
        if (_uiState.value.activeSpeaker == speaker) return
        viewModelScope.launch {
            commitPendingPartial()
            _uiState.update { it.copy(activeSpeaker = speaker) }
        }
    }

    fun pauseRecording(reason: String? = null) {
        if (_uiState.value.isPaused) return
        viewModelScope.launch {
            commitPendingPartial()
            _uiState.update {
                it.copy(
                    isRecording = false,
                    isPaused = true,
                    pauseReason = reason,
                    isReconnecting = false,
                    reconnectingMessage = null
                )
            }
            stopEngine()
        }
    }

    fun resumeRecording() {
        if (_uiState.value.isRecording && !_uiState.value.isPaused) return
        committedPartialPrefix = null
        _uiState.update {
            it.copy(
                isRecording = true,
                isPaused = false,
                pauseReason = null,
                errorMessage = null,
                isReconnecting = false,
                reconnectingMessage = null
            )
        }
        startTimer()
        startListening()
    }

    fun onAudioBecomingNoisy() {
        if (_uiState.value.isRecording && !_uiState.value.isPaused) {
            pauseRecording("Наушники отключены")
        }
    }

    /** Background apps lose microphone access on Android 11+, so recording pauses explicitly. */
    fun onAppBackgrounded() {
        if (_uiState.value.isRecording && !_uiState.value.isPaused) {
            pauseRecording("Приложение свёрнуто")
        }
    }

    fun stopAndSave() {
        if (_uiState.value.isSaving || _uiState.value.savedDialogId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, isRecording = false, isPaused = true) }
            stopEngine()

            withContext(NonCancellable) {
                commitPendingPartial()

                val finalState = _uiState.value
                val dialogId = finalState.draftDialogId
                dialogRepository.getDialogById(dialogId)?.let { existing ->
                    dialogRepository.updateDialog(
                        existing.copy(
                            durationMs = finalState.elapsedSeconds * 1000L,
                            segmentCount = finalState.segments.size,
                            status = DialogStatus.COMPLETE
                        )
                    )
                }

                _uiState.update { it.copy(isSaving = false, savedDialogId = dialogId) }
            }
        }
    }

    fun cancelRecording() {
        if (_uiState.value.isDismissed) return
        viewModelScope.launch {
            sttJob?.cancel()
            timerJob?.cancel()

            withContext(NonCancellable) {
                commitMutex.withLock {
                    val dialogId = _uiState.value.draftDialogId
                    val original = resumedOriginal
                    when {
                        original != null -> {
                            // Discard only what was recorded now; the earlier dialog stays intact
                            dialogRepository.deleteSegments(segmentIdsAddedThisSession.toList())
                            dialogRepository.updateDialog(original)
                        }
                        dialogId > 0L -> dialogRepository.deleteDialog(dialogId)
                    }
                }
                _uiState.update { it.copy(isDismissed = true) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        sttJob?.cancel()
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
