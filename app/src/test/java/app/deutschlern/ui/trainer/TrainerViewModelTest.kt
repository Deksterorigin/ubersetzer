package app.deutschlern.ui.trainer

import androidx.lifecycle.SavedStateHandle
import app.deutschlern.data.trainer.TrainerStatsStore
import app.deutschlern.domain.model.Meaning
import app.deutschlern.domain.model.ReviewCard
import app.deutschlern.domain.model.WordEntry
import app.deutschlern.domain.model.WordStatus
import app.deutschlern.domain.repository.ReviewRepository
import app.deutschlern.domain.repository.SettingsRepository
import app.deutschlern.domain.repository.VocabularyRepository
import app.deutschlern.domain.sm2.Sm2Grade
import app.deutschlern.domain.sm2.Sm2State
import app.deutschlern.domain.stt.SpeechToTextEngine
import app.deutschlern.domain.trainer.AnswerResult
import app.deutschlern.domain.trainer.Exercise
import app.deutschlern.domain.trainer.TrainerMode
import app.deutschlern.util.TtsManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrainerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val reviewRepository = mockk<ReviewRepository>(relaxed = true)
    private val vocabularyRepository = mockk<VocabularyRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val sttEngine = mockk<SpeechToTextEngine>(relaxed = true)
    private val ttsManager = mockk<TtsManager>(relaxed = true)
    private val statsStore = mockk<TrainerStatsStore>(relaxed = true)

    private fun noun(id: Long, lemma: String, article: String) = WordEntry(
        id = id,
        sessionId = 1L,
        lemma = lemma,
        article = article,
        pos = "noun",
        meanings = listOf(Meaning(translation = "перевод $id")),
        status = WordStatus.LEARNING
    )

    private val tisch = noun(1L, "Tisch", "der")
    private val lampe = noun(2L, "Lampe", "die")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(mode: TrainerMode, cards: List<ReviewCard>): TrainerViewModel {
        coEvery { reviewRepository.getAllCards() } returns cards
        return TrainerViewModel(
            savedStateHandle = SavedStateHandle(mapOf(TrainerViewModel.ARG_MODE to mode.name)),
            reviewRepository = reviewRepository,
            vocabularyRepository = vocabularyRepository,
            settingsRepository = settingsRepository,
            sttEngine = sttEngine,
            ttsManager = ttsManager,
            statsStore = statsStore
        )
    }

    private fun TrainerViewModel.answerArticleWrong() {
        val exercise = uiState.value.current as Exercise.Article
        onArticleSelected(listOf("der", "die", "das").first { it != exercise.correctArticle })
    }

    @Test
    fun wrongAnswer_requeuesWord_schedulesAgainOnce_andSecondMissMarksDifficult() = runTest(testDispatcher) {
        val vm = viewModel(TrainerMode.ARTICLES, listOf(ReviewCard(tisch, Sm2State(dueAt = Long.MAX_VALUE))))
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.queue.size)

        vm.answerArticleWrong()
        advanceUntilIdle()
        assertEquals(AnswerResult.WRONG, vm.uiState.value.verdict?.result)
        assertEquals(2, vm.uiState.value.queue.size)

        vm.onNext()
        vm.answerArticleWrong()
        advanceUntilIdle()

        // Only the first answer per word reaches SM-2, even when it is wrong twice
        coVerify(exactly = 1) { reviewRepository.reviewCard(1L, Sm2Grade.AGAIN, any()) }
        coVerify(exactly = 1) { vocabularyRepository.updateWordDifficulty(1L, true) }
        assertEquals(listOf(1L), vm.uiState.value.autoMarkedDifficult.map { it.id })
        assertEquals(listOf(1L), vm.uiState.value.mistakes.map { it.id })
    }

    @Test
    fun correctAnswer_onlyAdvancesScheduleWhenWordIsDue() = runTest(testDispatcher) {
        val vm = viewModel(
            TrainerMode.ARTICLES,
            listOf(ReviewCard(tisch, Sm2State(dueAt = 0L)), ReviewCard(lampe, Sm2State(dueAt = Long.MAX_VALUE)))
        )
        advanceUntilIdle()

        repeat(2) {
            val exercise = vm.uiState.value.current as Exercise.Article
            vm.onArticleSelected(exercise.correctArticle)
            advanceUntilIdle()
            vm.onNext()
        }

        coVerify(exactly = 1) { reviewRepository.reviewCard(1L, Sm2Grade.GOOD, any()) }
        coVerify(exactly = 0) { reviewRepository.reviewCard(2L, any(), any()) }
        assertEquals(2, vm.uiState.value.firstTryCorrect)
        assertTrue(vm.uiState.value.isFinished)
    }

    @Test
    fun hintUsed_capsCorrectAnswerToAlmost() = runTest(testDispatcher) {
        val vm = viewModel(TrainerMode.RECALL, listOf(ReviewCard(tisch, Sm2State(dueAt = 0L))))
        advanceUntilIdle()

        vm.onHint()
        vm.onInputChanged("der Tisch")
        vm.onSubmitTyped()
        advanceUntilIdle()

        assertEquals(AnswerResult.ALMOST, vm.uiState.value.verdict?.result)
        coVerify(exactly = 1) { reviewRepository.reviewCard(1L, Sm2Grade.HARD, any()) }
    }

    @Test
    fun restartWithMistakes_trainsOnlyMissedWords() = runTest(testDispatcher) {
        val vm = viewModel(
            TrainerMode.ARTICLES,
            listOf(ReviewCard(tisch, Sm2State(dueAt = 0L)), ReviewCard(lampe, Sm2State(dueAt = 0L)))
        )
        advanceUntilIdle()

        // Miss the first word once, then answer everything correctly
        val missedId = vm.uiState.value.current!!.word.id
        vm.answerArticleWrong()
        vm.onNext()
        while (!vm.uiState.value.isFinished) {
            val exercise = vm.uiState.value.current as Exercise.Article
            vm.onArticleSelected(exercise.correctArticle)
            vm.onNext()
        }
        advanceUntilIdle()

        vm.restartWithMistakes()
        advanceUntilIdle()

        assertEquals(listOf(missedId), vm.uiState.value.queue.map { it.word.id })
    }
}
