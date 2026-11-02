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
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    /**
     * State of one startListening() collection. Kept per session so that a late cleanup of a
     * previous session can never stop or destroy the recognizer of the current one.
     */
    private class ListeningSession {
        @Volatile
        var active: Boolean = true
        var recognizer: SpeechRecognizer? = null

        fun destroyRecognizer() {
            val current = recognizer ?: return
            recognizer = null
            try {
                current.cancel()
                current.destroy()
            } catch (_: Exception) {
            }
        }
    }

    // The recognition service is shared system-wide, so only one session may own it at a time.
    // A new session synchronously tears down the previous one before creating its recognizer.
    @Volatile
    private var currentSession: ListeningSession? = null

    private fun createRecognizer(preferOffline: Boolean): SpeechRecognizer? = try {
        recognizerFactory(context, preferOffline)
    } catch (_: Exception) {
        null
    }

    private fun buildIntent(language: String, onDevice: Boolean): Intent =
        intentFactory(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            if (onDevice) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            // No EXTRA_ENABLE_LANGUAGE_DETECTION: the language is known, and auto-detection made the
            // recognizer switch languages mid-dialogue and fail with language errors on some devices.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // A String naming the criterion that ends the whole session, not a Boolean: Google's
                // service logs "Wrong value passed to EXTRA_SEGMENTED_SESSION; ignoring it" for true
                // and then closes the microphone after every phrase. With this value the microphone
                // stays open, phrases arrive via onSegmentResults, and the session ends only after
                // COMPLETE_SILENCE_LENGTH of silence.
                putExtra(
                    RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS
                )
            }
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
        }

    override fun startListening(languageCode: String, preferOffline: Boolean): Flow<SttEvent> = callbackFlow {
        val session = ListeningSession()
        var lastPartialText: String? = null
        // Restarts are launched from recognizer callbacks long after withContext() below has
        // returned, so they must use the flow's own scope: a launch in the completed withContext
        // scope is cancelled immediately and the restart silently never happens.
        val producerScope = this

        withContext(mainDispatcher) {
            currentSession?.let { previous ->
                previous.active = false
                previous.destroyRecognizer()
            }
            currentSession = session

            var onDevice = preferOffline
            val recognizer = createRecognizer(onDevice)
            if (recognizer == null) {
                trySend(SttEvent.Error("Сервис распознавания речи недоступен на этом устройстве", isRecoverable = false))
                trySend(SttEvent.StateChanged(false))
                close()
                return@withContext
            }
            session.recognizer = recognizer

            val resolvedLanguage = resolveLanguageCode(languageCode, onDevice = onDevice)
            var recognitionIntent = buildIntent(resolvedLanguage, onDevice)
            SpeechDebugLogger.resetStats()

            // A "cycle" is one recognizer.startListening() call. It ends with onResults, onError or
            // onEndOfSegmentedSession; some services report more than one of them, so restarts are
            // funnelled through one scheduler and a cycle that already ended is never restarted twice.
            var cycleActive = false
            var restartJob: Job? = null
            var consecutiveTransientErrors = 0
            var consecutiveBenignErrors = 0
            var firstTransientErrorTimestamp = 0L

            lateinit var listener: RecognitionListener

            fun resetErrorCounters() {
                consecutiveTransientErrors = 0
                consecutiveBenignErrors = 0
                firstTransientErrorTimestamp = 0L
            }

            fun recreateRecognizer() {
                session.destroyRecognizer()
                session.recognizer = createRecognizer(onDevice)?.also { it.setRecognitionListener(listener) }
            }

            fun flushPartialAsFinal() {
                lastPartialText?.takeIf { it.isNotBlank() }?.let { trySend(SttEvent.Final(it)) }
                lastPartialText = null
            }

            fun stopWithError(errorCode: Int, message: String, reason: String) {
                session.active = false
                restartJob?.cancel()
                SpeechDebugLogger.onError(errorCode, message, isRecoverable = false, willRestart = false)
                SpeechDebugLogger.onEndOfSession(reason)
                trySend(SttEvent.Error(message, isRecoverable = false, errorCode = errorCode))
                trySend(SttEvent.StateChanged(false))
            }

            lateinit var onTransientError: (Int, String) -> Unit

            fun startCycle() {
                if (!session.active) return
                val current = session.recognizer ?: createRecognizer(onDevice)?.also {
                    it.setRecognitionListener(listener)
                    session.recognizer = it
                }
                if (current == null) {
                    onTransientError(SpeechRecognizer.ERROR_CLIENT, mapErrorCode(SpeechRecognizer.ERROR_CLIENT).first)
                    return
                }
                try {
                    SpeechDebugLogger.onSessionStart(onDevice, resolvedLanguage)
                    current.startListening(recognitionIntent)
                    cycleActive = true
                } catch (e: Exception) {
                    SpeechDebugLogger.onError(-1, "start_failed: ${e.message}", true, false)
                    onTransientError(SpeechRecognizer.ERROR_CLIENT, mapErrorCode(SpeechRecognizer.ERROR_CLIENT).first)
                }
            }

            fun scheduleRestart(delayMs: Long, recreate: Boolean) {
                if (!session.active) return
                restartJob?.cancel()
                restartJob = producerScope.launch {
                    delay(delayMs)
                    if (!session.active) return@launch
                    if (recreate) recreateRecognizer()
                    startCycle()
                }
            }

            onTransientError = { errorCode, errorMessage ->
                val now = System.currentTimeMillis()
                if (firstTransientErrorTimestamp == 0L) {
                    firstTransientErrorTimestamp = now
                }
                consecutiveTransientErrors++
                val isExhausted = consecutiveTransientErrors > MAX_TRANSIENT_ERRORS ||
                    now - firstTransientErrorTimestamp > MAX_TRANSIENT_WINDOW_MS

                if (isExhausted) {
                    stopWithError(errorCode, errorMessage, "transient_retry_exhausted_$errorCode")
                } else {
                    SpeechDebugLogger.onError(errorCode, errorMessage, isRecoverable = true, willRestart = true)
                    trySend(SttEvent.Error(errorMessage, isRecoverable = true, errorCode = errorCode))
                    // A recognizer that reported a client/busy/disconnect error is unusable: recreate it
                    val recreate = errorCode in RECREATE_IMMEDIATELY_ERRORS || consecutiveTransientErrors >= 2
                    val backoffDelayMs = minOf(300L * (1L shl (consecutiveTransientErrors - 1)), 4000L)
                    scheduleRestart(backoffDelayMs, recreate)
                }
            }

            fun onCycleEnded() {
                if (!cycleActive) return
                cycleActive = false
                if (session.active) {
                    scheduleRestart(RESTART_DELAY_MS, recreate = false)
                } else {
                    SpeechDebugLogger.onEndOfSession("cycle_ended_not_active")
                    trySend(SttEvent.StateChanged(false))
                }
            }

            listener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    SpeechDebugLogger.onCallback("onReadyForSpeech")
                    resetErrorCounters()
                    trySend(SttEvent.StateChanged(true))
                }

                override fun onBeginningOfSpeech() {
                    SpeechDebugLogger.onCallback("onBeginningOfSpeech")
                    resetErrorCounters()
                }

                override fun onRmsChanged(rmsdB: Float) {
                    trySend(SttEvent.SoundLevel(rmsdB))
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    SpeechDebugLogger.onCallback("onEndOfSpeech")
                }

                override fun onError(errorCode: Int) {
                    flushPartialAsFinal()
                    cycleActive = false
                    if (!session.active) return

                    val (errorMessage, _) = mapErrorCode(errorCode)
                    when {
                        isBenignError(errorCode) -> {
                            // Silence: restart quietly. Several in a row usually mean a wedged binder.
                            consecutiveBenignErrors++
                            SpeechDebugLogger.onError(errorCode, errorMessage, isRecoverable = true, willRestart = true)
                            val recreate = consecutiveBenignErrors >= 3
                            if (recreate) consecutiveBenignErrors = 0
                            scheduleRestart(RESTART_DELAY_MS, recreate)
                        }

                        isLanguageError(errorCode) && onDevice -> {
                            // The offline model for this language is missing: continue online instead of stopping
                            onDevice = false
                            recognitionIntent = buildIntent(resolvedLanguage, onDevice = false)
                            SpeechDebugLogger.onError(errorCode, errorMessage, isRecoverable = true, willRestart = true)
                            trySend(
                                SttEvent.Error(
                                    "Офлайн-модель недоступна, используется онлайн-распознавание",
                                    isRecoverable = true,
                                    errorCode = errorCode
                                )
                            )
                            scheduleRestart(RESTART_DELAY_MS, recreate = true)
                        }

                        isFatalError(errorCode) -> stopWithError(errorCode, errorMessage, "fatal_error_$errorCode")

                        else -> onTransientError(errorCode, errorMessage)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                    val finalText = matches.firstOrNull()?.trim().orEmpty()

                    SpeechDebugLogger.onCallback("onResults", "matches=${matches.size}, len=${finalText.length}")
                    SpeechDebugLogger.onPartialToFinal(lastPartialText?.length ?: 0, finalText.length)
                    resetErrorCounters()

                    if (finalText.isNotEmpty()) {
                        trySend(SttEvent.Final(finalText, matches.drop(1)))
                        lastPartialText = null
                    } else {
                        flushPartialAsFinal()
                    }
                    onCycleEnded()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    consecutiveBenignErrors = 0
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                    if (!text.isNullOrEmpty()) {
                        lastPartialText = text
                        SpeechDebugLogger.onCallback("onPartialResults", "len=${text.length}")
                        trySend(SttEvent.Partial(text))
                    }
                }

                override fun onSegmentResults(segmentResults: Bundle) {
                    resetErrorCounters()
                    val matches = segmentResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                    val text = matches.firstOrNull()?.trim().orEmpty()
                    SpeechDebugLogger.onCallback("onSegmentResults", "len=${text.length}")
                    if (text.isNotEmpty()) {
                        trySend(SttEvent.Final(text, matches.drop(1)))
                    }
                    lastPartialText = null
                }

                override fun onEndOfSegmentedSession() {
                    SpeechDebugLogger.onCallback("onEndOfSegmentedSession")
                    flushPartialAsFinal()
                    onCycleEnded()
                }

                override fun onLanguageDetection(results: Bundle) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            }

            recognizer.setRecognitionListener(listener)
            trySend(SttEvent.StateChanged(true))
            startCycle()
        }

        awaitClose {
            session.active = false
            SpeechDebugLogger.onEndOfSession("awaitClose")
            engineScope.launch {
                session.destroyRecognizer()
                if (currentSession === session) {
                    currentSession = null
                }
            }
        }
    }

    open fun isLanguageError(errorCode: Int): Boolean {
        return errorCode == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
               errorCode == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
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
            recognizer.destroy()
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
                createRecognizer(preferOffline = true)?.triggerModelDownload(intent)
            } catch (_: Exception) {
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

    open fun isBenignError(errorCode: Int): Boolean {
        return errorCode == SpeechRecognizer.ERROR_NO_MATCH ||
               errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
    }

    open fun isFatalError(errorCode: Int): Boolean {
        return errorCode == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
               errorCode == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
               errorCode == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
               errorCode == SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT ||
               errorCode == SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS
    }

    open fun mapErrorCode(errorCode: Int): Pair<String, Boolean> {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Ошибка аудиозаписи" to true
            SpeechRecognizer.ERROR_CLIENT -> "Внутренняя ошибка сервиса распознавания" to true
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

    private companion object {
        const val RESTART_DELAY_MS = 150L
        const val MAX_TRANSIENT_ERRORS = 8
        const val MAX_TRANSIENT_WINDOW_MS = 25_000L
        val RECREATE_IMMEDIATELY_ERRORS = setOf(
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED
        )
    }
}
