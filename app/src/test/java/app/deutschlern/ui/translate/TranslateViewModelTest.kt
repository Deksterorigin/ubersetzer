package app.deutschlern.ui.translate

import app.deutschlern.data.local.security.SecureKeyStorage
import app.deutschlern.data.remote.provider.MlKitOfflineProvider
import app.deutschlern.domain.model.Meaning
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.TranslationError
import app.deutschlern.domain.model.TranslationMode
import app.deutschlern.domain.model.TranslationResult
import app.deutschlern.domain.model.WordEntry
import app.deutschlern.domain.model.WordTranslation
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.repository.UserSettings
import app.deutschlern.domain.repository.VocabularyRepository
import app.deutschlern.domain.usecase.DetectLanguageUseCase
import app.deutschlern.domain.usecase.DetectModeUseCase
import app.deutschlern.domain.usecase.TranslateTextUseCase
import app.deutschlern.domain.usecase.TranslateWordUseCase
import app.deutschlern.util.TtsManager
import app.deutschlern.domain.stt.SpeechToTextEngine
import app.deutschlern.domain.stt.SttEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranslateViewModelTest {

    private val translateTextUseCase = mockk<TranslateTextUseCase>()
    private val translateWordUseCase = mockk<TranslateWordUseCase>()
    private val detectLanguageUseCase = DetectLanguageUseCase()
    private val detectModeUseCase = DetectModeUseCase()
    private val vocabularyRepository = mockk<VocabularyRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>()
    private val secureKeyStorage = mockk<SecureKeyStorage>()
    private val mlKitOfflineProvider = mockk<MlKitOfflineProvider>(relaxed = true)
    private val ttsManager = mockk<TtsManager>(relaxed = true)
    private val speechToTextEngine = mockk<SpeechToTextEngine>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val settingsFlow = MutableStateFlow(UserSettings())
    private val sttEventsFlow = MutableSharedFlow<SttEvent>()

    private lateinit var viewModel: TranslateViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        coEvery { settingsRepository.settingsFlow } returns settingsFlow
        coEvery { settingsRepository.getSettings() } returns UserSettings()
        coEvery { secureKeyStorage.getKey(any()) } returns "test-key"
        coEvery { mlKitOfflineProvider.areModelsDownloaded() } returns true
        every { speechToTextEngine.startListening(any(), any()) } returns sttEventsFlow

        viewModel = TranslateViewModel(
            translateTextUseCase = translateTextUseCase,
            translateWordUseCase = translateWordUseCase,
            detectLanguageUseCase = detectLanguageUseCase,
            detectModeUseCase = detectModeUseCase,
            vocabularyRepository = vocabularyRepository,
            settingsRepository = settingsRepository,
            secureKeyStorage = secureKeyStorage,
            mlKitOfflineProvider = mlKitOfflineProvider,
            ttsManager = ttsManager,
            speechToTextEngine = speechToTextEngine
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isCorrect() {
        val state = viewModel.uiState.value
        assertEquals("", state.inputText)
        assertEquals(TranslationDirection.DE_RU, state.direction)
        assertEquals(TranslationMode.WORD, state.mode)
        assertNull(state.translatedText)
        assertNull(state.wordTranslation)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertFalse(state.canTranslate)
    }

    @Test
    fun onInputTextChanged_autoDetectsLanguageAndMode() {
        // German single word -> DE_RU, WORD mode
        viewModel.onEvent(TranslateUiEvent.OnInputTextChanged("Haus"))
        assertEquals(TranslationDirection.DE_RU, viewModel.uiState.value.direction)
        assertEquals(TranslationMode.WORD, viewModel.uiState.value.mode)

        // Russian sentence -> RU_DE, TEXT mode
        viewModel.onEvent(TranslateUiEvent.OnInputTextChanged("Это длинное предложение для проверки."))
        assertEquals(TranslationDirection.RU_DE, viewModel.uiState.value.direction)
        assertEquals(TranslationMode.TEXT, viewModel.uiState.value.mode)
    }

    @Test
    fun onSwapDirectionClicked_overridesAutoDetection() {
        viewModel.onEvent(TranslateUiEvent.OnInputTextChanged("Haus"))
        assertEquals(TranslationDirection.DE_RU, viewModel.uiState.value.direction)

        viewModel.onEvent(TranslateUiEvent.OnSwapDirectionClicked)
        assertEquals(TranslationDirection.RU_DE, viewModel.uiState.value.direction)
        assertTrue(viewModel.uiState.value.hasManualDirectionOverride)

        viewModel.onEvent(TranslateUiEvent.OnInputTextChanged("Häuser"))
        assertEquals(TranslationDirection.RU_DE, viewModel.uiState.value.direction)
    }

    @Test
    fun onClearInputClicked_resetsStateAndOverride() {
        viewModel.onEvent(TranslateUiEvent.OnInputTextChanged("Привет"))
        viewModel.onEvent(TranslateUiEvent.OnContextTextChanged("Контекст"))
        viewModel.onEvent(TranslateUiEvent.OnClearInputClicked)

        val state = viewModel.uiState.value
        assertEquals("", state.inputText)
        assertEquals("", state.contextText)
        assertNull(state.translatedText)
        assertNull(state.wordTranslation)
        assertFalse(state.hasManualDirectionOverride)
    }

    @Test
    fun onTranslateClicked_textMode_callsTranslateTextUseCase() = runTest {
        val sentence = "Das ist ein ganzer Satz für den Textmodus."
        coEvery { translateTextUseCase(sentence, TranslationDirection.DE_RU, any()) } returns Result.success(
            TranslationResult("Это целое предложение для текстового режима.", "Gemini · gemini-2.5-flash")
        )

        viewModel.onEvent(TranslateUiEvent.OnInputTextChanged(sentence))
        assertEquals(TranslationMode.TEXT, viewModel.uiState.value.mode)

        viewModel.onEvent(TranslateUiEvent.OnTranslateClicked)
        assertTrue(viewModel.uiState.value.isLoading)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Это целое предложение для текстового режима.", state.translatedText)
        assertEquals("Gemini · gemini-2.5-flash", state.sourceModelAttribution)
        assertNull(state.wordTranslation)
        assertNull(state.errorMessage)
    }

    @Test
    fun onTranslateClicked_wordMode_callsTranslateWordUseCaseWithContext() = runTest {
        val word = "Schloss"
        val context = "Er steckte den Schlüssel ins Schloss."
        val fakeWordTranslation = WordTranslation(
            lemma = "Schloss",
            article = "das",
            pos = "noun",
            meanings = listOf(
                Meaning(translation = "замок (дворец)", exampleDe = "Das Schloss ist alt.", exampleRu = "Замок старый.", bestMatch = false),
                Meaning(translation = "замок (дверной)", exampleDe = "Der Schlüssel passt ins Schloss.", exampleRu = "Ключ подходит к замку.", bestMatch = true)
            ),
            synonyms = listOf("Burg"),
            collocations = emptyList()
        )

        coEvery { translateWordUseCase(word, TranslationDirection.DE_RU, context, any()) } returns Result.success(
            TranslationResult(fakeWordTranslation, "Groq · llama-3.3-70b-versatile", fallbackMessage = "Переключено на Groq")
        )

        viewModel.onEvent(TranslateUiEvent.OnInputTextChanged(word))
        viewModel.onEvent(TranslateUiEvent.OnContextTextChanged(context))
        assertEquals(TranslationMode.WORD, viewModel.uiState.value.mode)

        viewModel.onEvent(TranslateUiEvent.OnTranslateClicked)
        assertTrue(viewModel.uiState.value.isLoading)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.translatedText)
        assertEquals("Schloss", state.wordTranslation?.lemma)
        assertEquals("das", state.wordTranslation?.article)
        assertEquals(1, state.selectedMeaningIndex)
        assertEquals("Groq · llama-3.3-70b-versatile", state.sourceModelAttribution)
        assertEquals("Переключено на Groq", state.fallbackNotification)
    }

    @Test
    fun onSelectMeaning_updatesSelectedMeaningIndex() = runTest {
        val fakeWordTranslation = WordTranslation(
            lemma = "Bank",
            article = "die",
            pos = "noun",
            meanings = listOf(
                Meaning(translation = "банк", exampleDe = "Geld auf der Bank.", exampleRu = "Деньги в банке."),
                Meaning(translation = "скамейка", exampleDe = "Auf der Bank sitzen.", exampleRu = "Сидеть на скамейке.")
            ),
            synonyms = emptyList(),
            collocations = emptyList()
        )
        coEvery { translateWordUseCase("Bank", TranslationDirection.DE_RU, null, any()) } returns Result.success(
            TranslationResult(fakeWordTranslation, "Gemini")
        )

        viewModel.onEvent(TranslateUiEvent.OnInputTextChanged("Bank"))
        viewModel.onEvent(TranslateUiEvent.OnTranslateClicked)
        advanceUntilIdle()

        viewModel.onEvent(TranslateUiEvent.OnSelectMeaning(1))
        assertEquals(1, viewModel.uiState.value.selectedMeaningIndex)

        viewModel.onEvent(TranslateUiEvent.OnSelectMeaning(1))
        assertNull(viewModel.uiState.value.selectedMeaningIndex)
    }

    @Test
    fun onSaveEquivalent_setsSaveSuccessMessage() = runTest {
        val fakeWordTranslation = WordTranslation(
            lemma = "Schloss",
            article = "das",
            pos = "noun",
            meanings = listOf(
                Meaning(translation = "das Schloss", exampleDe = "Altes Schloss", exampleRu = "Старый замок"),
                Meaning(translation = "die Burg", exampleDe = "Alte Burg", exampleRu = "Старая крепость")
            ),
            synonyms = emptyList(),
            collocations = emptyList()
        )
        val savedEntry = WordEntry(id = 55L, lemma = "Burg", article = "die", pos = "noun", sessionId = 1L)
        coEvery { translateWordUseCase("замок", TranslationDirection.RU_DE, null, any()) } returns Result.success(
            TranslationResult(fakeWordTranslation, "Gemini")
        )
        coEvery { vocabularyRepository.saveGermanEquivalent("die Burg", "замок", "noun") } returns savedEntry

        viewModel.onEvent(TranslateUiEvent.OnInputTextChanged("замок"))
        viewModel.onEvent(TranslateUiEvent.OnTranslateClicked)
        advanceUntilIdle()

        viewModel.onEvent(TranslateUiEvent.OnSaveEquivalent(1))
        advanceUntilIdle()
        assertEquals("die Burg", viewModel.uiState.value.saveSuccessMessage)
    }

    @Test
    fun onTranslateClicked_deRuWordTranslation_autoSavesWord() = runTest {
        val fakeTranslation = WordTranslation(
            lemma = "Haus",
            article = "das",
            pos = "noun",
            meanings = listOf(
                Meaning(translation = "дом", note = "здание", exampleDe = "Das Haus", exampleRu = "Дом")
            ),
            synonyms = emptyList(),
            collocations = emptyList()
        )
        val savedEntry = WordEntry(id = 42L, lemma = "Haus", article = "das", pos = "noun", sessionId = 1L)
        coEvery { translateWordUseCase("Haus", TranslationDirection.DE_RU, null, any()) } returns Result.success(
            TranslationResult(fakeTranslation, "Gemini")
        )
        coEvery { vocabularyRepository.saveWordTranslation(fakeTranslation) } returns savedEntry

        viewModel.onEvent(TranslateUiEvent.OnInputTextChanged("Haus"))
        viewModel.onEvent(TranslateUiEvent.OnTranslateClicked)
        advanceUntilIdle()

        coVerify(exactly = 1) { vocabularyRepository.saveWordTranslation(fakeTranslation) }
    }

    @Test
    fun onSpeakWord_delegatesToTtsManager() {
        viewModel.onEvent(TranslateUiEvent.OnSpeakWord("das Schloss"))
        verify { ttsManager.speak(eq("das Schloss"), any()) }
    }

    @Test
    fun onTranslateClicked_failureFlow_showsErrorMessage() = runTest {
        val error = TranslationError.NoInternet()
        coEvery { translateWordUseCase(any(), any(), any(), any()) } returns Result.failure(error)

        viewModel.onEvent(TranslateUiEvent.OnInputTextChanged("Haus"))
        viewModel.onEvent(TranslateUiEvent.OnTranslateClicked)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(error.message, state.errorMessage)
    }

    @Test
    fun onStartListening_initiatesSpeechRecognition_andUpdatesState() = runTest {
        viewModel.onEvent(TranslateUiEvent.OnStartListening(isContext = false))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isListening)
        assertFalse(viewModel.uiState.value.isListeningContext)
        verify { speechToTextEngine.startListening(any(), any()) }
    }

    @Test
    fun onSpeechPartial_updatesSpeechPartialText() = runTest {
        viewModel.onEvent(TranslateUiEvent.OnStartListening(isContext = false))
        advanceUntilIdle()

        sttEventsFlow.emit(SttEvent.Partial("Guten"))
        advanceUntilIdle()

        assertEquals("Guten", viewModel.uiState.value.speechPartialText)
    }

    @Test
    fun onSpeechFinal_stripsTrailingPunctuation_when3TokensOrFewer() = runTest {
        viewModel.onEvent(TranslateUiEvent.OnStartListening(isContext = false))
        advanceUntilIdle()

        sttEventsFlow.emit(SttEvent.Final("das Haus.", listOf("die Maus")))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("das Haus", state.inputText)
        assertEquals(listOf("die Maus"), state.speechAlternatives)
        assertNull(state.speechPartialText)
        assertFalse(state.isListening)
    }

    @Test
    fun onSpeechFinal_keepsTrailingPunctuation_whenMoreThan3Tokens() = runTest {
        viewModel.onEvent(TranslateUiEvent.OnStartListening(isContext = false))
        advanceUntilIdle()

        sttEventsFlow.emit(SttEvent.Final("Ich gehe heute nach Hause.", emptyList()))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Ich gehe heute nach Hause.", state.inputText)
        assertFalse(state.isListening)
    }

    @Test
    fun onSpeechFinal_appendsToExistingInputText() = runTest {
        viewModel.onEvent(TranslateUiEvent.OnInputTextChanged("Hallo"))
        viewModel.onEvent(TranslateUiEvent.OnStartListening(isContext = false))
        advanceUntilIdle()

        sttEventsFlow.emit(SttEvent.Final("Welt", emptyList()))
        advanceUntilIdle()

        assertEquals("Hallo Welt", viewModel.uiState.value.inputText)
    }

    @Test
    fun onSpeechFinal_forContext_updatesContextText() = runTest {
        viewModel.onEvent(TranslateUiEvent.OnStartListening(isContext = true))
        advanceUntilIdle()

        sttEventsFlow.emit(SttEvent.Final("Im Kontext.", emptyList()))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Im Kontext", state.contextText)
        assertEquals("", state.inputText)
    }

    @Test
    fun onSelectSpeechAlternative_updatesInputText_andClearsAlternatives() = runTest {
        viewModel.onEvent(TranslateUiEvent.OnStartListening(isContext = false))
        advanceUntilIdle()
        sttEventsFlow.emit(SttEvent.Final("Haus", listOf("Maus.")))
        advanceUntilIdle()

        assertEquals(listOf("Maus."), viewModel.uiState.value.speechAlternatives)

        viewModel.onEvent(TranslateUiEvent.OnSelectSpeechAlternative("Maus."))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Maus", state.inputText)
        assertTrue(state.speechAlternatives.isEmpty())
    }

    @Test
    fun onStopListening_stopsEngineAndResetsListeningState() = runTest {
        viewModel.onEvent(TranslateUiEvent.OnStartListening(isContext = false))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isListening)

        viewModel.onEvent(TranslateUiEvent.OnStopListening)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isListening)
        verify { speechToTextEngine.stopListening() }
    }
}
