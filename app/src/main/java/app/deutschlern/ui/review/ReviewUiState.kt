package app.deutschlern.ui.review

import app.deutschlern.domain.model.FlashcardDirection
import app.deutschlern.domain.model.ReviewCard
import app.deutschlern.domain.sm2.Sm2Grade

data class ReviewUiState(
    val cards: List<ReviewCard> = emptyList(),
    val currentIndex: Int = 0,
    val isFlipped: Boolean = false,
    val direction: FlashcardDirection = FlashcardDirection.DE_RU,
    val isLastLessonMode: Boolean = false,
    val isLoading: Boolean = false,
    val totalDueTodayCount: Int = 0,
    val reviewedInSessionCount: Int = 0
) {
    val currentCard: ReviewCard?
        get() = cards.getOrNull(currentIndex)

    val isQueueCompleted: Boolean
        get() = !isLoading && (cards.isEmpty() || currentIndex >= cards.size)

    val remainingCount: Int
        get() = maxOf(0, cards.size - currentIndex)
}

sealed interface ReviewUiEvent {
    object OnFlipCard : ReviewUiEvent
    data class OnGradeAnswer(val grade: Sm2Grade) : ReviewUiEvent
    object OnToggleDirection : ReviewUiEvent
    data class OnToggleLastLessonMode(val enabled: Boolean) : ReviewUiEvent
    data class OnSpeakWord(val text: String) : ReviewUiEvent
    object OnRestartQueue : ReviewUiEvent
}
