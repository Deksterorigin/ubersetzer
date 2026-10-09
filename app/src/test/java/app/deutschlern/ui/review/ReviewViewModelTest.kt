package app.deutschlern.ui.review

import app.deutschlern.domain.model.FlashcardDirection
import app.deutschlern.domain.model.Meaning
import app.deutschlern.domain.model.ReviewCard
import app.deutschlern.domain.model.WordEntry
import app.deutschlern.domain.model.WordStatus
import app.deutschlern.domain.repository.ReviewRepository
import app.deutschlern.domain.sm2.Sm2Grade
import app.deutschlern.domain.sm2.Sm2State
import app.deutschlern.util.TtsManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {

    private val reviewRepository = mockk<ReviewRepository>(relaxed = true)
    private val ttsManager = mockk<TtsManager>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val dueCardsFlow = MutableStateFlow<List<ReviewCard>>(emptyList())
    private val lastLessonCardsFlow = MutableStateFlow<List<ReviewCard>>(emptyList())
    private val dueCountFlow = MutableStateFlow(0)

    private lateinit var viewModel: ReviewViewModel

    private val card1 = ReviewCard(
        word = WordEntry(
            id = 10L,
            sessionId = 1L,
            lemma = "der Hund",
            article = "der",
            pos = "noun",
            meanings = listOf(Meaning(translation = "собака", exampleDe = "Der Hund bellt", exampleRu = "Собака лает")),
            timesTranslated = 3,
            status = WordStatus.LEARNING
        ),
        sm2State = Sm2State(
            easeFactor = 2.5,
            intervalDays = 1,
            repetitions = 1,
            dueAt = 1000L
        )
    )

    private val card2 = ReviewCard(
        word = WordEntry(
            id = 20L,
            sessionId = 1L,
            lemma = "die Katze",
            article = "die",
            pos = "noun",
            meanings = listOf(Meaning(translation = "кошка", exampleDe = "Die Katze schläft", exampleRu = "Кошка спит")),
            timesTranslated = 1,
            status = WordStatus.NEW
        ),
        sm2State = Sm2State(
            easeFactor = 2.5,
            intervalDays = 0,
            repetitions = 0,
            dueAt = 0L
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { reviewRepository.getDueReviewCards(any()) } returns dueCardsFlow
        coEvery { reviewRepository.getLastLessonReviewCards() } returns lastLessonCardsFlow
        coEvery { reviewRepository.getDueCardsCount(any()) } returns dueCountFlow
        coEvery { reviewRepository.reviewCard(any(), any(), any()) } returns card1

        dueCardsFlow.value = listOf(card1, card2)
        dueCountFlow.value = 2

        viewModel = ReviewViewModel(
            reviewRepository = reviewRepository,
            ttsManager = ttsManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_loadsDueCardsAndCount() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertEquals(2, state.cards.size)
        assertEquals(2, state.totalDueTodayCount)
        assertEquals(0, state.currentIndex)
        assertEquals(card1, state.currentCard)
        assertFalse(state.isFlipped)
        assertFalse(state.isQueueCompleted)
    }

    @Test
    fun onFlipCard_togglesFlippedState() = runTest {
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFlipped)

        viewModel.onEvent(ReviewUiEvent.OnFlipCard)
        assertTrue(viewModel.uiState.value.isFlipped)

        viewModel.onEvent(ReviewUiEvent.OnFlipCard)
        assertFalse(viewModel.uiState.value.isFlipped)
    }

    @Test
    fun onToggleDirection_swapsDirectionAndResetsFlipped() = runTest {
        advanceUntilIdle()
        viewModel.onEvent(ReviewUiEvent.OnFlipCard)
        assertTrue(viewModel.uiState.value.isFlipped)

        viewModel.onEvent(ReviewUiEvent.OnToggleDirection)
        assertEquals(FlashcardDirection.RU_DE, viewModel.uiState.value.direction)
        assertFalse(viewModel.uiState.value.isFlipped)

        viewModel.onEvent(ReviewUiEvent.OnToggleDirection)
        assertEquals(FlashcardDirection.DE_RU, viewModel.uiState.value.direction)
    }

    @Test
    fun onToggleLastLessonMode_switchesQueueSource() = runTest {
        advanceUntilIdle()
        lastLessonCardsFlow.value = listOf(card2)

        viewModel.onEvent(ReviewUiEvent.OnToggleLastLessonMode(true))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isLastLessonMode)
        assertEquals(1, state.cards.size)
        assertEquals(card2, state.currentCard)
        coVerify(exactly = 1) { reviewRepository.getLastLessonReviewCards() }

        // Switch back to due cards
        viewModel.onEvent(ReviewUiEvent.OnToggleLastLessonMode(false))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLastLessonMode)
        assertEquals(2, viewModel.uiState.value.cards.size)
    }

    @Test
    fun onGradeAnswer_good_advancesCardAndUpdatesRepository() = runTest {
        advanceUntilIdle()
        viewModel.onEvent(ReviewUiEvent.OnFlipCard)
        assertTrue(viewModel.uiState.value.isFlipped)

        viewModel.onEvent(ReviewUiEvent.OnGradeAnswer(Sm2Grade.GOOD))
        advanceUntilIdle()

        coVerify(exactly = 1) { reviewRepository.reviewCard(10L, Sm2Grade.GOOD, any()) }

        val state = viewModel.uiState.value
        assertEquals(1, state.currentIndex)
        assertEquals(card2, state.currentCard)
        assertFalse(state.isFlipped)
        assertEquals(1, state.reviewedInSessionCount)
    }

    @Test
    fun onGradeAnswer_again_appendsCardToEndForImmediateRetest() = runTest {
        advanceUntilIdle()
        viewModel.onEvent(ReviewUiEvent.OnFlipCard)

        viewModel.onEvent(ReviewUiEvent.OnGradeAnswer(Sm2Grade.AGAIN))
        advanceUntilIdle()

        coVerify(exactly = 1) { reviewRepository.reviewCard(10L, Sm2Grade.AGAIN, any()) }

        val state = viewModel.uiState.value
        // Current index moved to 1 (card2)
        assertEquals(1, state.currentIndex)
        assertEquals(card2, state.currentCard)
        // Original 2 cards + re-added card1 at the end = 3 cards total
        assertEquals(3, state.cards.size)
        assertEquals(card1, state.cards[2])
    }

    @Test
    fun queueCompleted_whenAllCardsReviewed() = runTest {
        advanceUntilIdle()

        // Review card 1
        viewModel.onEvent(ReviewUiEvent.OnGradeAnswer(Sm2Grade.GOOD))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isQueueCompleted)

        // Review card 2
        viewModel.onEvent(ReviewUiEvent.OnGradeAnswer(Sm2Grade.EASY))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isQueueCompleted)
        assertEquals(2, state.reviewedInSessionCount)
    }

    @Test
    fun onRestartQueue_resetsSession() = runTest {
        advanceUntilIdle()
        viewModel.onEvent(ReviewUiEvent.OnGradeAnswer(Sm2Grade.GOOD))
        viewModel.onEvent(ReviewUiEvent.OnGradeAnswer(Sm2Grade.GOOD))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isQueueCompleted)

        viewModel.onEvent(ReviewUiEvent.OnRestartQueue)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.currentIndex)
        assertEquals(0, state.reviewedInSessionCount)
        assertFalse(state.isQueueCompleted)
    }

    @Test
    fun onSpeakWord_callsTtsManager() = runTest {
        advanceUntilIdle()

        viewModel.onEvent(ReviewUiEvent.OnSpeakWord("der Hund"))

        verify(exactly = 1) { ttsManager.speak("der Hund", any()) }
    }
}
