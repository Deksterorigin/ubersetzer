package app.deutschlern.ui.words

import app.deutschlern.domain.model.SessionWithWords
import app.deutschlern.domain.model.WordEntry
import app.deutschlern.domain.model.WordTranslation

enum class WordFilter {
    ALL,
    NEW,
    LEARNING,
    KNOWN
}

enum class WordSort {
    BY_DATE,
    ALPHABETICAL,
    MOST_TRANSLATED
}

enum class AddWordTab {
    TRANSLATE,
    MANUAL,
    BULK
}

data class DuplicateWordData(
    val existingWord: WordEntry,
    val pendingWordTranslation: WordTranslation? = null
)

data class WordsUiState(
    val sessionsWithWords: List<SessionWithWords> = emptyList(),
    val rawSessionsWithWords: List<SessionWithWords> = emptyList(),
    val filter: WordFilter = WordFilter.ALL,
    val sort: WordSort = WordSort.BY_DATE,
    val searchQuery: String = "",
    val selectedWordForDetail: WordEntry? = null,
    val lastDeletedWord: WordEntry? = null,
    val snackbarMessage: String? = null,
    val showUndoAction: Boolean = false,
    val isLoading: Boolean = true,
    // Add Word Sheet
    val isAddWordSheetVisible: Boolean = false,
    val addWordTab: AddWordTab = AddWordTab.TRANSLATE,
    // Translate Tab
    val addWordTranslateInput: String = "",
    val addWordTranslatedResult: WordTranslation? = null,
    val isAddWordTranslating: Boolean = false,
    val addWordSelectedMeaningIndex: Int = 0,
    val addWordTranslateError: String? = null,
    // Manual Tab
    val manualGermanWord: String = "",
    val manualArticle: String? = null,
    val manualPos: String = "noun",
    val manualTranslation: String = "",
    val manualExample: String = "",
    val isEnhancingWithAi: Boolean = false,
    val manualError: String? = null,
    // Bulk Tab
    val bulkInputText: String = "",
    val isBulkTranslating: Boolean = false,
    val bulkProgressCurrent: Int = 0,
    val bulkProgressTotal: Int = 0,
    val bulkStatusMessage: String? = null,
    // Duplicate Dialog
    val duplicateWordDialogData: DuplicateWordData? = null
) {
    val isLibraryEmpty: Boolean
        get() = rawSessionsWithWords.all { it.words.isEmpty() }

    val isSearchOrFilterEmpty: Boolean
        get() = !isLibraryEmpty && sessionsWithWords.all { it.words.isEmpty() }

    val bulkWordsCount: Int
        get() = bulkInputText.split("[\n,]+".toRegex()).map { it.trim() }.filter { it.isNotBlank() }.size
}
