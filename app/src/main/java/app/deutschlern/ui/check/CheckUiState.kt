package app.deutschlern.ui.check

import app.deutschlern.domain.model.PartnerLanguage
import app.deutschlern.domain.model.check.CheckCategory
import app.deutschlern.domain.model.check.CheckEditHunk
import app.deutschlern.domain.model.check.TextCheckResult

enum class CheckViewMode {
    CORRECTED,
    ORIGINAL
}

data class CheckUiState(
    val inputText: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val result: TextCheckResult? = null,
    val viewMode: CheckViewMode = CheckViewMode.CORRECTED,
    val categoryFilter: CheckCategory? = null,
    val activeHunkDetail: CheckEditHunk? = null,
    val isAddingWordToVocab: Boolean = false,
    val addWordSuccessMessage: String? = null,
    val showConsentDialog: Boolean = false,
    val partnerLanguage: PartnerLanguage = PartnerLanguage.RUSSIAN,
    val providerModelAttribution: String? = null,
    val isOcrLoading: Boolean = false,
    val ocrPreviewText: String? = null,
    val ocrErrorMessage: String? = null,
    val showPhotoSourceDialog: Boolean = false
) {
    val canCheck: Boolean
        get() = inputText.isNotBlank() && !isLoading

    val charCount: Int
        get() = inputText.length

    val filteredHunks: List<CheckEditHunk>
        get() = if (result == null) emptyList() else {
            if (categoryFilter == null) result.hunks else result.hunks.filter { it.category == categoryFilter }
        }

    val totalEditsCount: Int
        get() = result?.hunks?.size ?: 0
}
