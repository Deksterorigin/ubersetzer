package app.deutschlern.ui.dialog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import app.deutschlern.data.stt.SpeechDebugLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

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
            val initialTitle = "Dialog $timeStr"
            val newDialog = Dialog(
                title = initialTitle,
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
            val existingSegments = dialogRepository.getSegmentsSync(dialogId)
            val elapsed = existing.durationMs / 1000L
            val lastSpeaker = existingSegments.lastOrNull()?.speakerLabel ?: "A"

            withContext(NonCancellable) {
                dialogRepository.updateDialog(existing.copy(status = DialogStatus.RECORDING))
            }

            _uiState.update {
                it.copy(
                    draftDialogId = dialogId,
                    isRecording = true,
                    isPaused = false,
                    isReconnecting = false,
                    elapsedSeconds = elapsed,
                    activeSpeaker = lastSpeaker,
                    segments = existingSegments,
                    currentPartialText = ""
                )
            }

            startTimer()
            startListening()
        }
    }

    fun onPermissionGranted() {
        if (!_uiState.value.hasAudioPermission) {
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
        sttEngine.stopListening()
        sttJob?.cancel()
        timerJob?.cancel()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000L)
                if (_uiState.value.isRecording && !_uiState.value.isPaused) {
                    _uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
                }
            }
        }
    }

    private fun startListening() {
        sttJob?.cancel()
        sttJob = viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val preferOffline = settings.sttPreferOnDevice

            sttEngine.startListening(languageCode = "de-DE", preferOffline = preferOffline).collect { event ->
                when (event) {
                    is SttEvent.Partial -> {
                        _uiState.update {
                            it.copy(
                                currentPartialText = event.text,
                                isReconnecting = false,
                                reconnectingMessage = null
                            )
                        }
                    }
                    is SttEvent.Final -> {
                        val text = event.text.trim()
                        if (text.isNotBlank()) {
                            commitSegment(text)
                        } else {
                            _uiState.update { it.copy(currentPartialText = "") }
                        }
                        _uiState.update {
                            it.copy(isReconnecting = false, reconnectingMessage = null)
                        }
                    }
                    is SttEvent.SoundLevel -> {
                        _uiState.update { it.copy(soundLevelRms = event.rmsDb) }
                    }
                    is SttEvent.Error -> {
                        if (event.isRecoverable) {
                            _uiState.update {
                                it.copy(
                                    isReconnecting = true,
                                    reconnectingMessage = event.message
                                )
                            }
                        } else {
                            // Non-recoverable error or retries exhausted: pause recording with reason (no silent loss)
                            val partial = _uiState.value.currentPartialText.trim()
                            if (partial.isNotBlank()) {
                                commitSegment(partial)
                            }
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
                        // Handled by engine lifecycle
                    }
                }
            }
        }
    }

    private suspend fun commitSegment(text: String) {
        val currentState = _uiState.value
        val dialogId = currentState.draftDialogId
        if (dialogId == 0L || text.isBlank()) return

        withContext(NonCancellable) {
            val segment = DialogSegment(
                dialogId = dialogId,
                orderIndex = currentState.segments.size,
                text = text,
                speakerLabel = currentState.activeSpeaker,
                startOffsetMs = currentState.elapsedSeconds * 1000L
            )
            val insertedId = dialogRepository.insertSegment(segment)
            val savedSegment = segment.copy(id = insertedId)

            val updatedList = currentState.segments + savedSegment
            _uiState.update {
                it.copy(
                    segments = updatedList,
                    currentPartialText = ""
                )
            }

            val currentDialog = dialogRepository.getDialogById(dialogId)
            if (currentDialog != null) {
                dialogRepository.updateDialog(
                    currentDialog.copy(
                        segmentCount = updatedList.size,
                        durationMs = currentState.elapsedSeconds * 1000L
                    )
                )
            }

            SpeechDebugLogger.onSegmentCommitted(
                speaker = currentState.activeSpeaker,
                charCount = text.length,
                totalSegments = updatedList.size
            )
        }
    }

    fun toggleSpeaker() {
        viewModelScope.launch {
            val partial = _uiState.value.currentPartialText.trim()
            if (partial.isNotBlank()) {
                commitSegment(partial)
            }
            val newSpeaker = if (_uiState.value.activeSpeaker == "A") "B" else "A"
            _uiState.update { it.copy(activeSpeaker = newSpeaker) }
        }
    }

    fun setSpeaker(speaker: String) {
        if (_uiState.value.activeSpeaker == speaker) return
        viewModelScope.launch {
            val partial = _uiState.value.currentPartialText.trim()
            if (partial.isNotBlank()) {
                commitSegment(partial)
            }
            _uiState.update { it.copy(activeSpeaker = speaker) }
        }
    }

    fun pauseRecording(reason: String? = null) {
        if (_uiState.value.isPaused) return
        viewModelScope.launch {
            val partial = _uiState.value.currentPartialText.trim()
            if (partial.isNotBlank()) {
                commitSegment(partial)
            }
            _uiState.update {
                it.copy(
                    isRecording = false,
                    isPaused = true,
                    pauseReason = reason,
                    isReconnecting = false,
                    reconnectingMessage = null
                )
            }
            sttEngine.stopListening()
            sttJob?.cancel()
            timerJob?.cancel()
        }
    }

    fun resumeRecording() {
        if (_uiState.value.isRecording && !_uiState.value.isPaused) return
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

    fun onAudioFocusLost(isTransient: Boolean) {
        if (_uiState.value.isRecording && !_uiState.value.isPaused) {
            val reason = if (isTransient) "Аудиофокус временно потерян" else "Другое приложение использует микрофон"
            pauseRecording(reason)
        }
    }

    fun onAudioBecomingNoisy() {
        if (_uiState.value.isRecording && !_uiState.value.isPaused) {
            pauseRecording("Наушники отключены")
        }
    }

    fun stopAndSave() {
        if (_uiState.value.isSaving || _uiState.value.savedDialogId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, isRecording = false, isPaused = true) }
            sttEngine.stopListening()
            sttJob?.cancel()
            timerJob?.cancel()

            withContext(NonCancellable) {
                val currentState = _uiState.value
                val partial = currentState.currentPartialText.trim()
                if (partial.isNotBlank()) {
                    commitSegment(partial)
                }

                val finalState = _uiState.value
                val dialogId = finalState.draftDialogId
                val existing = dialogRepository.getDialogById(dialogId)
                if (existing != null) {
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
            sttEngine.cancel()
            sttJob?.cancel()
            timerJob?.cancel()

            withContext(NonCancellable) {
                val dialogId = _uiState.value.draftDialogId
                if (dialogId > 0L) {
                    dialogRepository.deleteDialog(dialogId)
                }
                _uiState.update { it.copy(isDismissed = true) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sttEngine.cancel()
        timerJob?.cancel()
        sttJob?.cancel()
    }
}
