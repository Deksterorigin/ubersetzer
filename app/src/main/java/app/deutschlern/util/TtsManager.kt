package app.deutschlern.util

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized: Boolean = false
    private var pendingSpeech: Pair<String, () -> Unit>? = null

    init {
        initializeTts()
    }

    private fun initializeTts() {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            isInitialized = false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            pendingSpeech?.let { (text, onVoiceMissing) ->
                pendingSpeech = null
                speak(text, onVoiceMissing)
            }
        } else {
            isInitialized = false
            pendingSpeech?.let { (_, onVoiceMissing) ->
                pendingSpeech = null
                onVoiceMissing()
            }
        }
    }

    /**
     * Pronounces the given German text.
     * If German voice data is missing or not supported, invokes [onVoiceMissing].
     */
    fun speak(text: String, onVoiceMissing: () -> Unit) {
        if (text.isBlank()) return

        val currentTts = tts
        if (currentTts == null || !isInitialized) {
            pendingSpeech = Pair(text, onVoiceMissing)
            if (currentTts == null) {
                initializeTts()
            }
            return
        }

        val langResult = try {
            currentTts.isLanguageAvailable(Locale.GERMANY)
        } catch (e: Exception) {
            TextToSpeech.LANG_NOT_SUPPORTED
        }

        if (langResult == TextToSpeech.LANG_MISSING_DATA ||
            langResult == TextToSpeech.LANG_NOT_SUPPORTED ||
            langResult < TextToSpeech.LANG_AVAILABLE
        ) {
            onVoiceMissing()
            return
        }

        try {
            val setResult = currentTts.setLanguage(Locale.GERMANY)
            if (setResult == TextToSpeech.LANG_MISSING_DATA ||
                setResult == TextToSpeech.LANG_NOT_SUPPORTED ||
                setResult < TextToSpeech.LANG_AVAILABLE
            ) {
                onVoiceMissing()
                return
            }
            currentTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "deutschlern_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            onVoiceMissing()
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            // ignore
        } finally {
            tts = null
            isInitialized = false
            pendingSpeech = null
        }
    }
}
