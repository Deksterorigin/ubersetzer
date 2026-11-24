package app.deutschlern.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import app.deutschlern.domain.exam.Gender
import app.deutschlern.domain.exam.TranscriptLine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject

data class DialoguePlayback(
    val playing: Boolean = false,
    /** Index of the line being spoken, -1 when idle. */
    val lineIndex: Int = -1,
    val voiceMissing: Boolean = false
)

/**
 * Reads an exam transcript aloud line by line, giving every speaker their own German voice
 * (when the device has several) and a pitch that fits their gender.
 * One instance per screen: call [release] when the screen goes away.
 */
class DialogueSpeaker @Inject constructor(
    @ApplicationContext context: Context
) {
    private val _state = MutableStateFlow(DialoguePlayback())
    val state: StateFlow<DialoguePlayback> = _state.asStateFlow()

    private var ready = false
    private var pending: List<TranscriptLine>? = null
    private var lines: List<TranscriptLine> = emptyList()
    private var speakerVoices: Map<String?, Voice?> = emptyMap()
    private var rate = 1f

    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (!ready) {
            _state.value = DialoguePlayback(voiceMissing = true)
        } else {
            pending?.let { pending = null; play(it) }
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                val index = utteranceId?.toIntOrNull() ?: return
                if (_state.value.playing && _state.value.lineIndex == index) speakLine(index + 1)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = stop()
        })
    }

    fun setRate(value: Float) {
        rate = value
    }

    fun play(transcript: List<TranscriptLine>) {
        if (transcript.isEmpty()) return
        if (!ready) {
            pending = transcript
            return
        }
        val available = tts.isLanguageAvailable(Locale.GERMANY)
        if (available < TextToSpeech.LANG_AVAILABLE) {
            _state.value = DialoguePlayback(voiceMissing = true)
            return
        }
        tts.language = Locale.GERMANY
        lines = transcript
        speakerVoices = assignVoices(transcript)
        speakLine(0)
    }

    fun stop() {
        pending = null
        _state.value = _state.value.copy(playing = false, lineIndex = -1)
        runCatching { tts.stop() }
    }

    fun release() {
        stop()
        runCatching { tts.shutdown() }
    }

    private fun speakLine(index: Int) {
        if (index >= lines.size) {
            _state.value = DialoguePlayback()
            return
        }
        val line = lines[index]
        _state.value = DialoguePlayback(playing = true, lineIndex = index)
        speakerVoices[line.speaker]?.let { runCatching { tts.voice = it } }
        tts.setPitch(
            when (line.gender) {
                Gender.MALE -> 0.85f
                Gender.FEMALE -> 1.15f
                null -> 1f
            }
        )
        tts.setSpeechRate(rate)
        tts.speak(line.text, TextToSpeech.QUEUE_FLUSH, null, index.toString())
    }

    /** Speakers get the installed offline German voices in turn; with one voice only pitch tells them apart. */
    private fun assignVoices(transcript: List<TranscriptLine>): Map<String?, Voice?> {
        val voices = runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { it.locale.language == "de" && !it.isNetworkConnectionRequired }
            .sortedBy { it.name }
        if (voices.isEmpty()) return emptyMap()
        return transcript.map { it.speaker }.distinct()
            .mapIndexed { i, speaker -> speaker to voices[i % voices.size] }
            .toMap()
    }
}
