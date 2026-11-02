package app.deutschlern.domain.stt

import kotlinx.coroutines.flow.Flow

interface SpeechToTextEngine {
    /**
     * Listens continuously until the collecting coroutine is cancelled; cancelling the collection
     * is the only way to stop. There is deliberately no global stop/cancel: the recognizer is a
     * singleton, and one screen must never be able to stop recognition owned by another.
     */
    fun startListening(languageCode: String, preferOffline: Boolean = false): Flow<SttEvent>
    fun checkSupport(languageCode: String, onDevice: Boolean = false): SttSupportStatus
    fun triggerModelDownload(languageCode: String)
}
