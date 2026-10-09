package app.deutschlern.data.stt

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.annotation.RequiresApi
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import app.deutschlern.domain.stt.SpeechToTextEngine
import app.deutschlern.domain.stt.SttEvent
import app.deutschlern.domain.stt.SttSupportStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class AndroidSpeechEngine(
    private val context: Context,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val recognizerFactory: (Context, Boolean) -> SpeechRecognizer?,
    private val intentFactory: (String) -> Intent = { Intent(it) }
) : SpeechToTextEngine {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context = context,
        mainDispatcher = Dispatchers.Main,
        recognizerFactory = { ctx, onDevice ->
            if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
                } catch (e: Exception) {
                    SpeechRecognizer.createSpeechRecognizer(ctx)
                }
            } else {
                SpeechRecognizer.createSpeechRecognizer(ctx)
            }
        },
        intentFactory = { Intent(it) }
    )

    private val engineScope = CoroutineScope(mainDispatcher + SupervisorJob())
    private val supportCache = ConcurrentHashMap<String, SttSupportStatus>()

    @Volatile
    private var activeRecognizer: SpeechRecognizer? = null

    @Volatile
    private var isListeningActive: Boolean = false

    @Volatile
    private var activeChannel: ProducerScope<SttEvent>? = null

    override fun startListening(languageCode: String, preferOffline: Boolean): Flow<SttEvent> = callbackFlow {
        activeChannel = this
        isListeningActive = true

        withContext(mainDispatcher) {
            // Cancel any previously running recognizer
            activeRecognizer?.let { prev ->
                try {
                    prev.cancel()
                    prev.destroy()
                } catch (e: Exception) {
                    // ignore
                }
            }

            val recognizer = try {
                recognizerFactory(context, preferOffline)
            } catch (e: Exception) {
                null
            }

            if (recognizer == null) {
                trySend(SttEvent.Error("Сервис распознавания речи недоступен на этом устройстве", isRecoverable = false))
                trySend(SttEvent.StateChanged(false))
                close()
                return@withContext
            }

            activeRecognizer = recognizer

            val resolvedLanguage = resolveLanguageCode(languageCode, onDevice = preferOffline)
            val recognitionIntent = intentFactory(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, resolvedLanguage)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                if (preferOffline) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_SEGMENTED_SESSION)
                }
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            }

            var silenceTimerJob: Job? = null
            var hasReceivedSegments = false
            var lastPartialText: String? = null

            fun resetSilenceTimer() {
                silenceTimerJob?.cancel()
                silenceTimerJob = launch {
                    delay(3000L)
                    if (isListeningActive) {
                        val partial = lastPartialText
                        if (!partial.isNullOrBlank()) {
                            trySend(SttEvent.Final(partial))
                            lastPartialText = null
                        }
                        // Restart recognizer on 3-second silence timeout
                        try {
                            recognizer.cancel()
                            recognizer.startListening(recognitionIntent)
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
            }

            val listener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    trySend(SttEvent.StateChanged(true))
                    resetSilenceTimer()
                }

                override fun onBeginningOfSpeech() {
                    resetSilenceTimer()
                }

                override fun onRmsChanged(rmsdB: Float) {
                    trySend(SttEvent.SoundLevel(rmsdB))
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    resetSilenceTimer()
                }

                override fun onError(errorCode: Int) {
                    silenceTimerJob?.cancel()
                    val (errorMessage, isRecoverable) = mapErrorCode(errorCode)
                    trySend(SttEvent.Error(errorMessage, isRecoverable, errorCode))

                    if (isRecoverable && isListeningActive) {
                        val partial = lastPartialText
                        if (!partial.isNullOrBlank()) {
                            trySend(SttEvent.Final(partial))
                            lastPartialText = null
                        }
                        // Auto-restart for recoverable errors (e.g. silence timeout / no match)
                        launch {
                            delay(300L)
                            if (isListeningActive) {
                                try {
                                    recognizer.startListening(recognitionIntent)
                                } catch (e: Exception) {
                                    // ignore
                                }
                            }
                        }
                    } else if (!isRecoverable) {
                        isListeningActive = false
                        trySend(SttEvent.StateChanged(false))
                    }
                }

                override fun onResults(results: Bundle?) {
                    silenceTimerJob?.cancel()
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                    val finalText = matches.firstOrNull()?.trim().orEmpty()
                    val alternatives = if (matches.size > 1) matches.drop(1) else emptyList()

                    if (finalText.isNotEmpty()) {
                        trySend(SttEvent.Final(finalText, alternatives))
                    } else {
                        lastPartialText?.let { partial ->
                            if (partial.isNotBlank()) {
                                trySend(SttEvent.Final(partial))
                            }
                        }
                    }
                    lastPartialText = null

                    if (isListeningActive && !hasReceivedSegments) {
                        // Fallback restart loop if segmented session is not supported by engine
                        launch {
                            try {
                                recognizer.startListening(recognitionIntent)
                            } catch (e: Exception) {
                                // ignore
                            }
                        }
                    } else if (!isListeningActive) {
                        trySend(SttEvent.StateChanged(false))
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim()
                    if (!text.isNullOrEmpty()) {
                        lastPartialText = text
                        trySend(SttEvent.Partial(text))
                        resetSilenceTimer()
                    }
                }

                override fun onSegmentResults(segmentResults: Bundle) {
                    hasReceivedSegments = true
                    silenceTimerJob?.cancel()
                    val matches = segmentResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                    val text = matches.firstOrNull()?.trim().orEmpty()
                    val alternatives = if (matches.size > 1) matches.drop(1) else emptyList()
                    if (text.isNotEmpty()) {
                        trySend(SttEvent.Final(text, alternatives))
                    }
                    lastPartialText = null
                    resetSilenceTimer()
                }

                override fun onEndOfSegmentedSession() {
                    trySend(SttEvent.StateChanged(false))
                }

                override fun onLanguageDetection(results: Bundle) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            }

            recognizer.setRecognitionListener(listener)
            trySend(SttEvent.StateChanged(true))
            try {
                recognizer.startListening(recognitionIntent)
            } catch (e: Exception) {
                trySend(SttEvent.Error(e.localizedMessage ?: "Не удалось запустить распознавание", false))
                trySend(SttEvent.StateChanged(false))
            }
        }

        awaitClose {
            isListeningActive = false
            activeChannel = null
            engineScope.launch {
                try {
                    activeRecognizer?.cancel()
                    activeRecognizer?.destroy()
                } catch (e: Exception) {
                    // ignore
                } finally {
                    activeRecognizer = null
                }
            }
        }
    }

    override fun stopListening() {
        isListeningActive = false
        engineScope.launch {
            try {
                activeRecognizer?.stopListening()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    override fun cancel() {
        isListeningActive = false
        engineScope.launch {
            try {
                activeRecognizer?.cancel()
            } catch (e: Exception) {
                // ignore
            }
            activeChannel?.trySend(SttEvent.StateChanged(false))
        }
    }

    open override fun checkSupport(languageCode: String, onDevice: Boolean): SttSupportStatus {
        val normalized = normalizeLanguage(languageCode)
        if (normalized.equals("fa-AF", ignoreCase = true)) {
            val afStatus = checkLanguageSupportInternal("fa-AF", onDevice)
            if (afStatus == SttSupportStatus.NOT_SUPPORTED) {
                val irStatus = checkLanguageSupportInternal("fa-IR", onDevice)
                if (irStatus != SttSupportStatus.NOT_SUPPORTED) {
                    return irStatus
                }
            }
            return afStatus
        }
        return checkLanguageSupportInternal(normalized, onDevice)
    }

    private fun checkLanguageSupportInternal(normalizedLang: String, onDevice: Boolean): SttSupportStatus {
        val cacheKey = "$normalizedLang|$onDevice"
        supportCache[cacheKey]?.let { return it }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val status = if (onDevice) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    SttSupportStatus.SUPPORTED
                } else {
                    SttSupportStatus.NOT_SUPPORTED
                }
            } else {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    SttSupportStatus.SUPPORTED
                } else {
                    SttSupportStatus.NOT_SUPPORTED
                }
            }
            supportCache[cacheKey] = status
            return status
        }

        // Avoid blocking if called on Main looper
        if (Looper.myLooper() == Looper.getMainLooper()) {
            val fallback = if (SpeechRecognizer.isRecognitionAvailable(context)) SttSupportStatus.SUPPORTED else SttSupportStatus.NOT_SUPPORTED
            engineScope.launch(Dispatchers.IO) {
                checkLanguageSupportInternal(normalizedLang, onDevice)
            }
            return fallback
        }

        val recognizer = try {
            recognizerFactory(context, onDevice)
        } catch (e: Exception) {
            null
        } ?: return if (SpeechRecognizer.isRecognitionAvailable(context)) SttSupportStatus.SUPPORTED else SttSupportStatus.NOT_SUPPORTED

        val intent = intentFactory(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, normalizedLang)
        }
        val latch = CountDownLatch(1)
        var resultStatus = SttSupportStatus.SUPPORTED
        val executor = Executors.newSingleThreadExecutor()

        try {
            recognizer.checkRecognitionSupport(
                intent,
                executor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                        resultStatus = evaluateSupport(recognitionSupport, normalizedLang, onDevice)
                        latch.countDown()
                    }

                    override fun onError(errorCode: Int) {
                        resultStatus = SttSupportStatus.ERROR
                        latch.countDown()
                    }
                }
            )
            val completed = latch.await(1000, TimeUnit.MILLISECONDS)
            if (!completed) {
                resultStatus = if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    SttSupportStatus.SUPPORTED
                } else {
                    SttSupportStatus.NOT_SUPPORTED
                }
            }
        } catch (e: Exception) {
            resultStatus = if (SpeechRecognizer.isRecognitionAvailable(context)) {
                SttSupportStatus.SUPPORTED
            } else {
                SttSupportStatus.NOT_SUPPORTED
            }
        } finally {
            executor.shutdown()
        }

        supportCache[cacheKey] = resultStatus
        return resultStatus
    }

    override fun triggerModelDownload(languageCode: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val resolved = resolveLanguageCode(languageCode, onDevice = true)
            val intent = intentFactory(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, resolved)
            }
            try {
                val recognizer = recognizerFactory(context, true)
                recognizer?.triggerModelDownload(intent)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun normalizeLanguage(code: String): String {
        val trimmed = code.trim()
        return when (trimmed.lowercase()) {
            "de" -> "de-DE"
            "ru" -> "ru-RU"
            "en" -> "en-US"
            "uk" -> "uk-UA"
            "ro" -> "ro-RO"
            "ar" -> "ar"
            "fa" -> "fa-AF"
            else -> trimmed
        }
    }

    fun resolveLanguageCode(code: String, onDevice: Boolean = false): String {
        val normalized = normalizeLanguage(code)
        if (normalized.equals("fa-AF", ignoreCase = true)) {
            val afStatus = checkSupport("fa-AF", onDevice)
            if (afStatus == SttSupportStatus.NOT_SUPPORTED) {
                val irStatus = checkSupport("fa-IR", onDevice)
                if (irStatus != SttSupportStatus.NOT_SUPPORTED) {
                    return "fa-IR"
                }
            }
        }
        return normalized
    }

    fun mapErrorCode(errorCode: Int): Pair<String, Boolean> {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Ошибка аудиозаписи" to false
            SpeechRecognizer.ERROR_CLIENT -> "Внутренняя ошибка распознавания речи" to false
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Отсутствует разрешение на запись аудио" to false
            SpeechRecognizer.ERROR_NETWORK -> "Ошибка сети при распознавании" to true
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Таймаут соединения с сетью" to true
            SpeechRecognizer.ERROR_NO_MATCH -> "Речь не распознана" to true
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Служба распознавания речи занята" to true
            SpeechRecognizer.ERROR_SERVER -> "Ошибка сервера распознавания" to true
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Речь не обнаружена" to true
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Сервер распознавания отключен" to true
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Слишком много запросов на распознавание" to true
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Язык не поддерживается" to false
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Языковая модель недоступна" to false
            SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "Не удалось проверить поддержку языка" to false
            SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS -> "Не удалось отследить загрузку модели" to false
            else -> "Неизвестная ошибка распознавания ($errorCode)" to false
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    internal fun evaluateSupport(support: RecognitionSupport, lang: String, onDevice: Boolean): SttSupportStatus {
        fun matches(list: List<String>): Boolean = list.any { item ->
            item.equals(lang, ignoreCase = true) ||
            item.startsWith("$lang-", ignoreCase = true) ||
            lang.startsWith("$item-", ignoreCase = true)
        }

        return if (onDevice) {
            when {
                matches(support.installedOnDeviceLanguages) -> SttSupportStatus.SUPPORTED
                matches(support.pendingOnDeviceLanguages) -> SttSupportStatus.DOWNLOAD_REQUIRED
                matches(support.supportedOnDeviceLanguages) -> SttSupportStatus.DOWNLOAD_REQUIRED
                else -> SttSupportStatus.NOT_SUPPORTED
            }
        } else {
            when {
                matches(support.onlineLanguages) ||
                matches(support.installedOnDeviceLanguages) ||
                matches(support.supportedOnDeviceLanguages) -> SttSupportStatus.SUPPORTED
                else -> SttSupportStatus.NOT_SUPPORTED
            }
        }
    }
}
