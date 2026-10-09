package app.deutschlern.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.deutschlern.domain.model.FlashcardDirection
import app.deutschlern.domain.model.ReviewCard
import app.deutschlern.domain.repository.ReviewRepository
import app.deutschlern.domain.sm2.Sm2Grade
import app.deutschlern.util.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val reviewRepository: ReviewRepository,
    private val ttsManager: TtsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState(isLoading = true))
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private var queueCollectionJob: Job? = null

    init {
        loadQueue(isLastLesson = false)
        observeDueCount()
    }

    fun onEvent(event: ReviewUiEvent) {
        when (event) {
            is ReviewUiEvent.OnFlipCard -> {
                _uiState.update { it.copy(isFlipped = !it.isFlipped) }
            }
            is ReviewUiEvent.OnGradeAnswer -> {
                handleGradeAnswer(event.grade)
            }
            is ReviewUiEvent.OnToggleDirection -> {
                _uiState.update {
                    it.copy(
                        direction = if (it.direction == FlashcardDirection.DE_RU) {
                            FlashcardDirection.RU_DE
                        } else {
                            FlashcardDirection.DE_RU
                        },
                        isFlipped = false
                    )
                }
            }
            is ReviewUiEvent.OnToggleLastLessonMode -> {
                if (_uiState.value.isLastLessonMode != event.enabled) {
                    _uiState.update {
                        it.copy(
                            isLastLessonMode = event.enabled,
                            currentIndex = 0,
                            isFlipped = false,
                            isLoading = true
                        )
                    }
                    loadQueue(isLastLesson = event.enabled)
                }
            }
            is ReviewUiEvent.OnSpeakWord -> {
                ttsManager.speak(event.text) {
                    // voice missing callback
                }
            }
            is ReviewUiEvent.OnRestartQueue -> {
                _uiState.update {
                    it.copy(
                        currentIndex = 0,
                        isFlipped = false,
                        reviewedInSessionCount = 0,
                        isLoading = true
                    )
                }
                loadQueue(isLastLesson = _uiState.value.isLastLessonMode)
            }
        }
    }

    private fun loadQueue(isLastLesson: Boolean) {
        queueCollectionJob?.cancel()
        val flow = if (isLastLesson) {
            reviewRepository.getLastLessonReviewCards()
        } else {
            reviewRepository.getDueReviewCards()
        }

        queueCollectionJob = flow.onEach { cards ->
            _uiState.update { current ->
                val adjustedIndex = current.currentIndex.coerceAtMost(cards.size)
                current.copy(
                    cards = cards,
                    currentIndex = adjustedIndex,
                    isLoading = false
                )
            }
        }.launchIn(viewModelScope)
    }

    private fun observeDueCount() {
        reviewRepository.getDueCardsCount().onEach { count ->
            _uiState.update { it.copy(totalDueTodayCount = count) }
        }.launchIn(viewModelScope)
    }

    private fun handleGradeAnswer(grade: Sm2Grade) {
        val current = _uiState.value
        val card = current.currentCard ?: return

        viewModelScope.launch {
            reviewRepository.reviewCard(card.word.id, grade)

            _uiState.update { state ->
                val currentCards = state.cards.toMutableList()
                val nextIndex = state.currentIndex + 1

                // If marked AGAIN, add to end of queue for immediate re-test
                if (grade == Sm2Grade.AGAIN) {
                    currentCards.add(card)
                }

                state.copy(
                    cards = currentCards,
                    currentIndex = nextIndex,
                    isFlipped = false,
                    reviewedInSessionCount = state.reviewedInSessionCount + 1
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.stop()
    }
}
