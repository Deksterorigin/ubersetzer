package app.deutschlern.ui.words

import app.deutschlern.domain.model.Meaning
import app.deutschlern.domain.model.Session
import app.deutschlern.domain.model.SessionWithWords
import app.deutschlern.domain.model.WordEntry
import app.deutschlern.domain.model.WordStatus
import app.deutschlern.domain.repository.VocabularyRepository
import app.deutschlern.util.TtsManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import app.deutschlern.domain.repository.UserSettings
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.stt.SpeechToTextEngine
import app.deutschlern.domain.usecase.TranslateWordUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class WordsViewModelTest {

    private val vocabularyRepository = mockk<VocabularyRepository>(relaxed = true)
    private val ttsManager = mockk<TtsManager>(relaxed = true)
    private val translateWordUseCase = mockk<TranslateWordUseCase>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>()
    private val speechToTextEngine = mockk<SpeechToTextEngine>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val sessionsFlow = MutableStateFlow<List<SessionWithWords>>(emptyList())
    private val userSettingsFlow = MutableStateFlow(UserSettings())

    private lateinit var viewModel: WordsViewModel

    private val sampleSession = Session(
        id = 1L,
        title = "Урок, 4 окт",
        date = 1000L,
        isManual = false
    )

    private val wordNew = WordEntry(
        id = 1L,
        sessionId = 1L,
        lemma = "Apfel",
        article = "der",
        pos = "noun",
        meanings = listOf(Meaning(translation = "яблоко", exampleDe = "Ein Apfel", exampleRu = "Яблоко")),
        timesTranslated = 1,
        status = WordStatus.NEW
    )

    private val wordLearning = WordEntry(
        id = 2L,
        sessionId = 1L,
        lemma = "Buch",
        article = "das",
        pos = "noun",
        meanings = listOf(Meaning(translation = "книга", exampleDe = "Ein Buch", exampleRu = "Книга")),
        timesTranslated = 4,
        status = WordStatus.LEARNING
    )

    private val wordKnown = WordEntry(
        id = 3L,
        sessionId = 1L,
        lemma = "überall",
        pos = "adverb",
        meanings = listOf(Meaning(translation = "везде", exampleDe = "Überall", exampleRu = "Везде")),
        timesTranslated = 2,
        status = WordStatus.KNOWN
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { vocabularyRepository.getSessionsWithWords() } returns sessionsFlow
        coEvery { settingsRepository.settingsFlow } returns userSettingsFlow
        sessionsFlow.value = listOf(
            SessionWithWords(
                session = sampleSession,
                words = listOf(wordNew, wordLearning, wordKnown)
            )
        )
        viewModel = WordsViewModel(
            vocabularyRepository = vocabularyRepository,
            ttsManager = ttsManager,
            translateWordUseCase = translateWordUseCase,
            settingsRepository = settingsRepository,
            speechToTextEngine = speechToTextEngine
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_loadsSessionsAndWords() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertEquals(1, state.sessionsWithWords.size)
        assertEquals(3, state.sessionsWithWords.first().words.size)
    }

    @Test
    fun onFilterChanged_filtersWordsByStatus() = runTest {
        advanceUntilIdle()

        // Filter NEW
        viewModel.onEvent(WordsUiEvent.OnFilterChanged(WordFilter.NEW))
        var state = viewModel.uiState.value
        assertEquals(1, state.sessionsWithWords.first().words.size)
        assertEquals("Apfel", state.sessionsWithWords.first().words.first().lemma)

        // Filter LEARNING
        viewModel.onEvent(WordsUiEvent.OnFilterChanged(WordFilter.LEARNING))
        state = viewModel.uiState.value
        assertEquals(1, state.sessionsWithWords.first().words.size)
        assertEquals("Buch", state.sessionsWithWords.first().words.first().lemma)

        // Filter KNOWN
        viewModel.onEvent(WordsUiEvent.OnFilterChanged(WordFilter.KNOWN))
        state = viewModel.uiState.value
        assertEquals(1, state.sessionsWithWords.first().words.size)
        assertEquals("überall", state.sessionsWithWords.first().words.first().lemma)

        // Filter ALL
        viewModel.onEvent(WordsUiEvent.OnFilterChanged(WordFilter.ALL))
        state = viewModel.uiState.value
        assertEquals(3, state.sessionsWithWords.first().words.size)
    }

    @Test
    fun onSearchQueryChanged_searchesTolerantToUmlautsAndCyrillic() = runTest {
        advanceUntilIdle()

        // Search "uber" matches "überall"
        viewModel.onEvent(WordsUiEvent.OnSearchQueryChanged("uber"))
        var state = viewModel.uiState.value
        assertEquals(1, state.sessionsWithWords.first().words.size)
        assertEquals("überall", state.sessionsWithWords.first().words.first().lemma)

        // Search "книга" matches "Buch"
        viewModel.onEvent(WordsUiEvent.OnSearchQueryChanged("книга"))
        state = viewModel.uiState.value
        assertEquals(1, state.sessionsWithWords.first().words.size)
        assertEquals("Buch", state.sessionsWithWords.first().words.first().lemma)

        // Clear query
        viewModel.onEvent(WordsUiEvent.OnSearchQueryChanged(""))
        state = viewModel.uiState.value
        assertEquals(3, state.sessionsWithWords.first().words.size)
    }

    @Test
    fun onSortChanged_sortsAlphabeticalAndMostTranslated() = runTest {
        advanceUntilIdle()

        // Sort ALPHABETICAL (Apfel, Buch, überall)
        viewModel.onEvent(WordsUiEvent.OnSortChanged(WordSort.ALPHABETICAL))
        var words = viewModel.uiState.value.sessionsWithWords.first().words
        assertEquals(listOf("Apfel", "Buch", "überall"), words.map { it.lemma })

        // Sort MOST_TRANSLATED (Buch: 4, überall: 2, Apfel: 1)
        viewModel.onEvent(WordsUiEvent.OnSortChanged(WordSort.MOST_TRANSLATED))
        words = viewModel.uiState.value.sessionsWithWords.first().words
        assertEquals(listOf("Buch", "überall", "Apfel"), words.map { it.lemma })
    }

    @Test
    fun onWordClicked_setsSelectedWordForDetail() = runTest {
        advanceUntilIdle()

        viewModel.onEvent(WordsUiEvent.OnWordClicked(wordLearning))
        val state = viewModel.uiState.value
        assertNotNull(state.selectedWordForDetail)
        assertEquals("Buch", state.selectedWordForDetail?.lemma)

        viewModel.onEvent(WordsUiEvent.OnDismissBottomSheet)
        assertNull(viewModel.uiState.value.selectedWordForDetail)
    }

    @Test
    fun onStatusChanged_delegatesToRepository() = runTest {
        advanceUntilIdle()

        viewModel.onEvent(WordsUiEvent.OnStatusChanged(1L, WordStatus.KNOWN))
        advanceUntilIdle()

        coVerify(exactly = 1) { vocabularyRepository.updateWordStatus(1L, WordStatus.KNOWN) }
    }

    @Test
    fun onSelectMeaning_delegatesToRepository() = runTest {
        advanceUntilIdle()

        viewModel.onEvent(WordsUiEvent.OnSelectMeaning(1L, 2))
        advanceUntilIdle()

        coVerify(exactly = 1) { vocabularyRepository.updateSelectedMeaning(1L, 2) }
    }

    @Test
    fun onDeleteWord_and_onUndoDelete() = runTest {
        advanceUntilIdle()
        coEvery { vocabularyRepository.deleteWord(1L) } returns wordNew

        viewModel.onEvent(WordsUiEvent.OnDeleteWord(wordNew))
        advanceUntilIdle()

        coVerify(exactly = 1) { vocabularyRepository.deleteWord(1L) }
        var state = viewModel.uiState.value
        assertTrue(state.showUndoAction)
        assertEquals("Слово «Apfel» удалено", state.snackbarMessage)
        assertEquals(wordNew, state.lastDeletedWord)

        // Undo
        viewModel.onEvent(WordsUiEvent.OnUndoDelete)
        advanceUntilIdle()

        coVerify(exactly = 1) { vocabularyRepository.restoreWord(wordNew) }
        state = viewModel.uiState.value
        assertFalse(state.showUndoAction)
        assertNull(state.lastDeletedWord)
    }

    @Test
    fun onCreateNewSession_delegatesToRepository() = runTest {
        advanceUntilIdle()
        val newSession = Session(id = 2L, title = "Урок, 4 окт (2)", date = 2000L, isManual = true)
        coEvery { vocabularyRepository.createManualSession() } returns newSession

        viewModel.onEvent(WordsUiEvent.OnCreateNewSession)
        advanceUntilIdle()

        coVerify(exactly = 1) { vocabularyRepository.createManualSession() }
        assertEquals("Создан новый урок: Урок, 4 окт (2)", viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun onOpenAndDismissAddWordSheet_updatesState() = runTest {
        viewModel.onEvent(WordsUiEvent.OnOpenAddWordClicked)
        assertTrue(viewModel.uiState.value.isAddWordSheetVisible)

        viewModel.onEvent(WordsUiEvent.OnDismissAddWordSheet)
        assertFalse(viewModel.uiState.value.isAddWordSheetVisible)
    }

    @Test
    fun onSelectAddWordTab_switchesTab() = runTest {
        viewModel.onEvent(WordsUiEvent.OnSelectAddWordTab(AddWordTab.MANUAL))
        assertEquals(AddWordTab.MANUAL, viewModel.uiState.value.addWordTab)

        viewModel.onEvent(WordsUiEvent.OnSelectAddWordTab(AddWordTab.BULK))
        assertEquals(AddWordTab.BULK, viewModel.uiState.value.addWordTab)
    }

    @Test
    fun onManualSave_savesWord_whenNotDuplicate() = runTest {
        coEvery { vocabularyRepository.findWord("Haus", "noun", "ru") } returns null

        viewModel.onEvent(
            WordsUiEvent.OnManualWordChange(
                word = "Haus",
                article = "das",
                pos = "noun",
                translation = "дом",
                example = "Das Haus ist groß"
            )
        )
        viewModel.onEvent(WordsUiEvent.OnManualSaveClicked)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            vocabularyRepository.saveWordTranslation(
                match { it.lemma == "Haus" && it.article == "das" && it.pos == "noun" }
            )
        }
        assertFalse(viewModel.uiState.value.isAddWordSheetVisible)
        assertEquals("Слово успешно добавлено", viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun onManualSave_triggersDuplicateDialog_whenWordExists() = runTest {
        coEvery { vocabularyRepository.findWord("Apfel", "noun", "ru") } returns wordNew

        viewModel.onEvent(
            WordsUiEvent.OnManualWordChange(
                word = "Apfel",
                article = "der",
                pos = "noun",
                translation = "яблоко",
                example = ""
            )
        )
        viewModel.onEvent(WordsUiEvent.OnManualSaveClicked)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.duplicateWordDialogData)
        assertEquals("Apfel", viewModel.uiState.value.duplicateWordDialogData?.existingWord?.lemma)
    }

    @Test
    fun onAddDuplicateToLesson_callsRepositoryAndClosesDialog() = runTest {
        viewModel.onEvent(WordsUiEvent.OnAddDuplicateToLesson(1L))
        advanceUntilIdle()

        coVerify(exactly = 1) { vocabularyRepository.addWordToCurrentSession(1L) }
        assertNull(viewModel.uiState.value.duplicateWordDialogData)
        assertFalse(viewModel.uiState.value.isAddWordSheetVisible)
        assertEquals("Слово добавлено в текущий урок", viewModel.uiState.value.snackbarMessage)
    }
}
