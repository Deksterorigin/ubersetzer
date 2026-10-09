package app.deutschlern.domain.stt

sealed class SttEvent {
    data class Partial(val text: String) : SttEvent()
    data class Final(val text: String, val alternatives: List<String> = emptyList()) : SttEvent()
    data class Error(val message: String, val isRecoverable: Boolean = false, val errorCode: Int? = null) : SttEvent()
    data class StateChanged(val isListening: Boolean) : SttEvent()
    data class SoundLevel(val rmsDb: Float) : SttEvent()
}
