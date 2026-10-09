package app.deutschlern.data.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.SpeechRecognizer
import app.cash.turbine.test
import app.deutschlern.domain.stt.SttEvent
import app.deutschlern.domain.stt.SttSupportStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidSpeechEngineTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context = mockk<Context>(relaxed = true)
    private val mockRecognizer = mockk<SpeechRecognizer>(relaxed = true)
    private val mockIntent = mockk<Intent>(relaxed = true)
    private val listenerSlot = slot<RecognitionListener>()

    private lateinit var engine: AndroidSpeechEngine

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockRecognizer.setRecognitionListener(capture(listenerSlot)) } returns Unit

        engine = AndroidSpeechEngine(
            context = context,
            mainDispatcher = testDispatcher,
            recognizerFactory = { _, _ -> mockRecognizer },
            intentFactory = { mockIntent }
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun normalizeLanguage_mapsStandardCodesCorrectly() {
        assertEquals("de-DE", engine.normalizeLanguage("de"))
        assertEquals("de-DE", engine.normalizeLanguage("DE"))
        assertEquals("ru-RU", engine.normalizeLanguage("ru"))
        assertEquals("en-US", engine.normalizeLanguage("en"))
        assertEquals("uk-UA", engine.normalizeLanguage("uk"))
        assertEquals("ro-RO", engine.normalizeLanguage("ro"))
        assertEquals("ar", engine.normalizeLanguage("ar"))
        assertEquals("fa-AF", engine.normalizeLanguage("fa"))
        assertEquals("de-AT", engine.normalizeLanguage("de-AT"))
    }

    @Test
    fun resolveLanguageCode_fallsBackToFaIr_whenFaAfUnsupported() {
        val customEngine = object : AndroidSpeechEngine(
            context = context,
            mainDispatcher = testDispatcher,
            recognizerFactory = { _, _ -> mockRecognizer },
            intentFactory = { mockIntent }
        ) {
            override fun checkSupport(languageCode: String, onDevice: Boolean): SttSupportStatus {
                return if (languageCode.equals("fa-AF", ignoreCase = true)) {
                    SttSupportStatus.NOT_SUPPORTED
                } else if (languageCode.equals("fa-IR", ignoreCase = true)) {
                    SttSupportStatus.SUPPORTED
                } else {
                    SttSupportStatus.SUPPORTED
                }
            }
        }

        val resolved = customEngine.resolveLanguageCode("fa")
        assertEquals("fa-IR", resolved)
    }

    @Test
    fun resolveLanguageCode_keepsFaAf_whenSupported() {
        val customEngine = object : AndroidSpeechEngine(
            context = context,
            mainDispatcher = testDispatcher,
            recognizerFactory = { _, _ -> mockRecognizer },
            intentFactory = { mockIntent }
        ) {
            override fun checkSupport(languageCode: String, onDevice: Boolean): SttSupportStatus {
                return SttSupportStatus.SUPPORTED
            }
        }

        val resolved = customEngine.resolveLanguageCode("fa")
        assertEquals("fa-AF", resolved)
    }

    @Test
    fun mapErrorCode_classifiesRecoverableErrorsCorrectly() {
        val (noMatchMsg, noMatchRecoverable) = engine.mapErrorCode(SpeechRecognizer.ERROR_NO_MATCH)
        assertTrue(noMatchRecoverable)
        assertTrue(noMatchMsg.isNotBlank())

        val (timeoutMsg, timeoutRecoverable) = engine.mapErrorCode(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        assertTrue(timeoutRecoverable)
        assertTrue(timeoutMsg.isNotBlank())

        val (busyMsg, busyRecoverable) = engine.mapErrorCode(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        assertTrue(busyRecoverable)
        assertTrue(busyMsg.isNotBlank())

        val (permMsg, permRecoverable) = engine.mapErrorCode(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        assertFalse(permRecoverable)
        assertTrue(permMsg.isNotBlank())

        val (audioMsg, audioRecoverable) = engine.mapErrorCode(SpeechRecognizer.ERROR_AUDIO)
        assertFalse(audioRecoverable)
        assertTrue(audioMsg.isNotBlank())
    }

    @Test
    fun evaluateSupport_onDevice_installedReturnsSupported() {
        val support = mockk<RecognitionSupport>()
        every { support.installedOnDeviceLanguages } returns listOf("de-DE", "en-US")
        every { support.pendingOnDeviceLanguages } returns emptyList()
        every { support.supportedOnDeviceLanguages } returns listOf("de-DE", "en-US", "ru-RU")
        every { support.onlineLanguages } returns emptyList()

        val status = engine.evaluateSupport(support, "de-DE", onDevice = true)
        assertEquals(SttSupportStatus.SUPPORTED, status)
    }

    @Test
    fun evaluateSupport_onDevice_supportedNotInstalledReturnsDownloadRequired() {
        val support = mockk<RecognitionSupport>()
        every { support.installedOnDeviceLanguages } returns listOf("en-US")
        every { support.pendingOnDeviceLanguages } returns emptyList()
        every { support.supportedOnDeviceLanguages } returns listOf("en-US", "de-DE")
        every { support.onlineLanguages } returns emptyList()

        val status = engine.evaluateSupport(support, "de-DE", onDevice = true)
        assertEquals(SttSupportStatus.DOWNLOAD_REQUIRED, status)
    }

    @Test
    fun evaluateSupport_onDevice_missingReturnsNotSupported() {
        val support = mockk<RecognitionSupport>()
        every { support.installedOnDeviceLanguages } returns listOf("en-US")
        every { support.pendingOnDeviceLanguages } returns emptyList()
        every { support.supportedOnDeviceLanguages } returns listOf("en-US")
        every { support.onlineLanguages } returns emptyList()

        val status = engine.evaluateSupport(support, "de-DE", onDevice = true)
        assertEquals(SttSupportStatus.NOT_SUPPORTED, status)
    }

    @Test
    fun evaluateSupport_online_returnsSupportedIfInOnlineOrSupported() {
        val support = mockk<RecognitionSupport>()
        every { support.installedOnDeviceLanguages } returns emptyList()
        every { support.pendingOnDeviceLanguages } returns emptyList()
        every { support.supportedOnDeviceLanguages } returns emptyList()
        every { support.onlineLanguages } returns listOf("de-DE")

        val status = engine.evaluateSupport(support, "de-DE", onDevice = false)
        assertEquals(SttSupportStatus.SUPPORTED, status)
    }

    @Test
    fun startListening_emitsStateChangedAndResults() = runTest {
        val flow = engine.startListening("de")

        flow.test {
            advanceUntilIdle()

            val stateEvent = awaitItem()
            assertTrue(stateEvent is SttEvent.StateChanged && stateEvent.isListening)

            assertTrue(listenerSlot.isCaptured)
            val listener = listenerSlot.captured

            // Simulate Sound Level
            listener.onRmsChanged(5.5f)
            val soundEvent = awaitItem()
            assertTrue(soundEvent is SttEvent.SoundLevel && soundEvent.rmsDb == 5.5f)

            // Simulate Partial Results
            val partialBundle = mockk<Bundle>()
            every { partialBundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) } returns arrayListOf("Hallo W")
            listener.onPartialResults(partialBundle)
            val partialEvent = awaitItem()
            assertEquals(SttEvent.Partial("Hallo W"), partialEvent)

            // Simulate Final Results
            val finalBundle = mockk<Bundle>()
            every { finalBundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) } returns arrayListOf("Hallo Welt", "Hallo Wal")
            listener.onResults(finalBundle)
            val finalEvent = awaitItem()
            assertEquals(SttEvent.Final("Hallo Welt", listOf("Hallo Wal")), finalEvent)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun startListening_handlesSegmentResults() = runTest {
        val flow = engine.startListening("de")

        flow.test {
            advanceUntilIdle()

            val stateEvent = awaitItem()
            assertTrue(stateEvent is SttEvent.StateChanged && stateEvent.isListening)

            val listener = listenerSlot.captured

            val segmentBundle = mockk<Bundle>()
            every { segmentBundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) } returns arrayListOf("Guten Tag")
            listener.onSegmentResults(segmentBundle)

            val finalEvent = awaitItem()
            assertEquals(SttEvent.Final("Guten Tag", emptyList()), finalEvent)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun startListening_emitsErrorOnFatalError() = runTest {
        val flow = engine.startListening("de")

        flow.test {
            advanceUntilIdle()

            awaitItem() // StateChanged(true)

            val listener = listenerSlot.captured
            listener.onError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)

            val errorEvent = awaitItem()
            assertTrue(errorEvent is SttEvent.Error)
            val error = errorEvent as SttEvent.Error
            assertFalse(error.isRecoverable)
            assertEquals(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, error.errorCode)

            val stateChangedFalse = awaitItem()
            assertTrue(stateChangedFalse is SttEvent.StateChanged && !stateChangedFalse.isListening)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun startListening_nullRecognizerEmitsError() = runTest {
        val nullEngine = AndroidSpeechEngine(
            context = context,
            mainDispatcher = testDispatcher,
            recognizerFactory = { _, _ -> null },
            intentFactory = { mockIntent }
        )

        val flow = nullEngine.startListening("de")

        flow.test {
            advanceUntilIdle()

            val errorEvent = awaitItem()
            assertTrue(errorEvent is SttEvent.Error && !errorEvent.isRecoverable)

            val stateFalse = awaitItem()
            assertTrue(stateFalse is SttEvent.StateChanged && !stateFalse.isListening)

            awaitComplete()
        }
    }

    @Test
    fun stopListening_callsSpeechRecognizerStop() = runTest {
        engine.startListening("de").test {
            advanceUntilIdle()
            awaitItem() // StateChanged(true)

            engine.stopListening()
            advanceUntilIdle()

            verify(atLeast = 1) { mockRecognizer.stopListening() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun cancel_callsSpeechRecognizerCancel() = runTest {
        engine.startListening("de").test {
            advanceUntilIdle()
            awaitItem() // StateChanged(true)

            engine.cancel()
            advanceUntilIdle()

            verify(atLeast = 1) { mockRecognizer.cancel() }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
