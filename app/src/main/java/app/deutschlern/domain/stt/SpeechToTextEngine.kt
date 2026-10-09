package app.deutschlern.domain.stt

import kotlinx.coroutines.flow.Flow

interface SpeechToTextEngine {
    fun startListening(languageCode: String, preferOffline: Boolean = false): Flow<SttEvent>
    fun stopListening()
    fun cancel()
    fun checkSupport(languageCode: String, onDevice: Boolean = false): SttSupportStatus
    fun triggerModelDownload(languageCode: String)
}
