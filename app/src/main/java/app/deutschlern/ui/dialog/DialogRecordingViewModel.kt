package app.deutschlern.ui.dialog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deutschlern.domain.model.dialog.Dialog
import app.deutschlern.domain.model.dialog.DialogSegment
import app.deutschlern.domain.repository.DialogRepository
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.stt.SpeechToTextEngine
import app.deutschlern.domain.stt.SttEvent
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class DialogRecordingUiState(
    val draftDialogId: Long = 0,
    val isRecording: Boolean = false,
    val elapsedSeconds: Long = 0,
    val activeSpeaker: String = "A", // "A" or "B"
    val segments: List<DialogSegment> = emptyList(),
    val currentPartialText: String = "",
    val soundLevelRms: Float = 0f,
    val isSaving: Boolean = false,
    val savedDialogId: Long? = null,
    val isDismissed: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class DialogRecordingViewModel @Inject constructor(
    private val sttEngine: SpeechToTextEngine,
    private val dialogRepository: DialogRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DialogRecordingUiState())
    val uiState: StateFlow<DialogRecordingUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var sttJob: Job? = null

    init {
        startNewRecordingSession()
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
                segmentCount = 0
            )
            val dialogId = dialogRepository.saveDialog(newDialog)

            _uiState.update {
                it.copy(
                    draftDialogId = dialogId,
                    isRecording = true,
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

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000L)
                if (_uiState.value.isRecording) {
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
                        _uiState.update { it.copy(currentPartialText = event.text) }
                    }
                    is SttEvent.Final -> {
                        val text = event.text.trim()
                        if (text.isNotBlank()) {
                            commitSegment(text)
                        } else {
                            _uiState.update { it.copy(currentPartialText = "") }
                        }
                    }
                    is SttEvent.SoundLevel -> {
                        _uiState.update { it.copy(soundLevelRms = event.rmsDb) }
                    }
                    is SttEvent.Error -> {
                        if (!event.isRecoverable) {
                            _uiState.update { it.copy(errorMessage = event.message) }
                        }
                    }
                    is SttEvent.StateChanged -> {
                        // handled
                    }
                }
            }
        }
    }

    private suspend fun commitSegment(text: String) {
        val currentState = _uiState.value
        val dialogId = currentState.draftDialogId
        if (dialogId == 0L) return

        val segment = DialogSegment(
            dialogId = dialogId,
            orderIndex = currentState.segments.size,
            text = text,
            speakerLabel = currentState.activeSpeaker,
            startOffsetMs = currentState.elapsedSeconds * 1000L
        )
        // Crash-proof incremental persistence to Room
        val insertedId = dialogRepository.insertSegment(segment)
        val savedSegment = segment.copy(id = insertedId)

        val updatedList = currentState.segments + savedSegment
        _uiState.update {
            it.copy(
                segments = updatedList,
                currentPartialText = ""
            )
        }

        // Update dialog segment count in background
        val currentDialog = dialogRepository.getDialogById(dialogId)
        if (currentDialog != null) {
            dialogRepository.updateDialog(currentDialog.copy(segmentCount = updatedList.size))
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

    fun pauseRecording() {
        _uiState.update { it.copy(isRecording = false) }
        sttEngine.stopListening()
        sttJob?.cancel()
        timerJob?.cancel()
    }

    fun resumeRecording() {
        _uiState.update { it.copy(isRecording = true) }
        startTimer()
        startListening()
    }

    fun stopAndSave() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, isRecording = false) }
            sttEngine.stopListening()
            sttJob?.cancel()
            timerJob?.cancel()

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
                        segmentCount = finalState.segments.size
                    )
                )
            }

            _uiState.update { it.copy(isSaving = false, savedDialogId = dialogId) }
        }
    }

    fun cancelRecording() {
        viewModelScope.launch {
            sttEngine.cancel()
            sttJob?.cancel()
            timerJob?.cancel()

            val dialogId = _uiState.value.draftDialogId
            if (dialogId > 0L) {
                dialogRepository.deleteDialog(dialogId)
            }

            _uiState.update { it.copy(isDismissed = true) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sttEngine.cancel()
        timerJob?.cancel()
        sttJob?.cancel()
    }
}
