package app.deutschlern.data.stt

import android.util.Log
import app.deutschlern.BuildConfig

object SpeechDebugLogger {
    private const val TAG = "SpeechRecognizerDebug"

    var testLogHook: ((String, String) -> Unit)? = null

    private var sessionStartTimeMs: Long = 0L
    private var lastSessionEndTimeMs: Long = 0L
    private var restartCount: Int = 0
    private var totalCommittedSegments: Int = 0
    private var totalCommittedChars: Int = 0

    fun onSessionStart(preferOffline: Boolean, language: String) {
        val now = System.currentTimeMillis()
        val gapMs = if (lastSessionEndTimeMs > 0L) now - lastSessionEndTimeMs else 0L
        restartCount++
        sessionStartTimeMs = now
        log(
            "START: session #$restartCount, gapSinceLastEnd=${gapMs}ms, offline=$preferOffline, lang=$language"
        )
    }

    fun onCallback(callbackName: String, extra: String = "") {
        val elapsed = if (sessionStartTimeMs > 0L) System.currentTimeMillis() - sessionStartTimeMs else 0L
        log("CALLBACK [$elapsed ms]: $callbackName $extra".trim())
    }

    fun onError(errorCode: Int, errorName: String, isRecoverable: Boolean, willRestart: Boolean) {
        lastSessionEndTimeMs = System.currentTimeMillis()
        log(
            "ERROR: code=$errorCode ($errorName), recoverable=$isRecoverable, willRestart=$willRestart, restartCount=$restartCount"
        )
    }

    fun onEndOfSession(reason: String) {
        lastSessionEndTimeMs = System.currentTimeMillis()
        log("END_OF_SESSION: reason=$reason, restartCount=$restartCount")
    }

    fun onPartialToFinal(partialLength: Int, finalLength: Int) {
        log("TRANSITION: partialLen=$partialLength -> finalLen=$finalLength")
    }

    fun onSegmentCommitted(speaker: String, charCount: Int, totalSegments: Int) {
        totalCommittedSegments = totalSegments
        totalCommittedChars += charCount
        log("COMMITTED: speaker=$speaker, chars=$charCount, totalSegs=$totalCommittedSegments, totalChars=$totalCommittedChars")
    }

    fun resetStats() {
        sessionStartTimeMs = 0L
        lastSessionEndTimeMs = 0L
        restartCount = 0
        totalCommittedSegments = 0
        totalCommittedChars = 0
    }

    private fun log(message: String) {
        testLogHook?.invoke(TAG, message)
        if (BuildConfig.DEBUG) {
            try {
                Log.d(TAG, message)
            } catch (e: Exception) {
                // In non-mocked JVM unit tests where android.util.Log is not available
            }
        }
    }
}
