package app.deutschlern.ui.words

import app.deutschlern.domain.model.WordEntry
import app.deutschlern.domain.model.WordStatus

sealed interface WordsUiEvent {
    data class OnFilterChanged(val filter: WordFilter) : WordsUiEvent
    data class OnSortChanged(val sort: WordSort) : WordsUiEvent
    data class OnSearchQueryChanged(val query: String) : WordsUiEvent
    data class OnWordClicked(val word: WordEntry) : WordsUiEvent
    data object OnDismissBottomSheet : WordsUiEvent
    data class OnStatusChanged(val wordId: Long, val newStatus: WordStatus) : WordsUiEvent
    data class OnSelectMeaning(val wordId: Long, val meaningIndex: Int?) : WordsUiEvent
    data class OnDeleteWord(val word: WordEntry) : WordsUiEvent
    data object OnUndoDelete : WordsUiEvent
    data object OnCreateNewSession : WordsUiEvent
    data class OnSpeakWord(val text: String) : WordsUiEvent
    data object OnDismissSnackbar : WordsUiEvent
    // Add Word events
    data object OnOpenAddWordClicked : WordsUiEvent
    data object OnDismissAddWordSheet : WordsUiEvent
    data class OnSelectAddWordTab(val tab: AddWordTab) : WordsUiEvent
    data class OnAddWordInputChange(val text: String) : WordsUiEvent
    data object OnAddWordMicClicked : WordsUiEvent
    data object OnAddWordTranslateClicked : WordsUiEvent
    data class OnAddWordSelectMeaning(val index: Int) : WordsUiEvent
    data object OnAddWordSaveClicked : WordsUiEvent
    data class OnManualWordChange(
        val word: String,
        val article: String?,
        val pos: String,
        val translation: String,
        val example: String
    ) : WordsUiEvent
    data object OnManualEnhanceWithAiClicked : WordsUiEvent
    data object OnManualSaveClicked : WordsUiEvent
    data class OnBulkInputChange(val text: String) : WordsUiEvent
    data object OnBulkStartClicked : WordsUiEvent
    data object OnBulkStopClicked : WordsUiEvent
    data object OnDismissDuplicateDialog : WordsUiEvent
    data class OnOpenDuplicateWord(val word: WordEntry) : WordsUiEvent
    data class OnAddDuplicateToLesson(val wordId: Long) : WordsUiEvent
}
