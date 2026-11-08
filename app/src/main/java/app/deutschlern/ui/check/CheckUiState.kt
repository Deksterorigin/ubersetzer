package app.deutschlern.ui.check

import android.graphics.Bitmap
import app.deutschlern.domain.model.PartnerLanguage
import app.deutschlern.domain.model.check.CheckCategory
import app.deutschlern.domain.model.check.CheckEditHunk
import app.deutschlern.domain.model.check.TextCheckResult
import app.deutschlern.domain.ocr.OcrPageLayout
import app.deutschlern.domain.ocr.OcrPageSelection
import app.deutschlern.domain.ocr.OcrTextAssembler

const val MAX_CHECK_INPUT_CHARS = 5000

enum class CheckViewMode {
    CORRECTED,
    ORIGINAL
}

enum class OcrError {
    NO_TEXT,
    RECOGNITION_FAILED,
    SOURCE_UNAVAILABLE
}

data class OcrSelectionPage(
    val bitmap: Bitmap,
    val layout: OcrPageLayout,
    val selectedIds: Set<Int> = emptySet()
)

data class OcrSelectionState(
    val pages: List<OcrSelectionPage>,
    val currentPageIndex: Int = 0,
    val failedPageCount: Int = 0
) {
    val currentPage: OcrSelectionPage
        get() = pages[currentPageIndex]

    val selectedWordCount: Int
        get() = pages.sumOf { it.selectedIds.size }

    val selectedText: String by lazy {
        OcrTextAssembler.assemble(pages.map { OcrPageSelection(it.layout.words, it.selectedIds) })
    }
}

data class CheckUiState(
    val inputText: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val result: TextCheckResult? = null,
    val viewMode: CheckViewMode = CheckViewMode.CORRECTED,
    val categoryFilter: CheckCategory? = null,
    val activeHunkId: String? = null,
    val isAddingWordToVocab: Boolean = false,
    val snackbarMessage: String? = null,
    val showConsentDialog: Boolean = false,
    val partnerLanguage: PartnerLanguage = PartnerLanguage.RUSSIAN,
    val providerModelAttribution: String? = null,
    val hasProviderKey: Boolean = true,
    val isOcrLoading: Boolean = false,
    val ocrSelection: OcrSelectionState? = null,
    val ocrPreviewText: String? = null,
    val ocrError: OcrError? = null,
    val showPhotoSourceDialog: Boolean = false
) {
    val canCheck: Boolean
        get() = inputText.isNotBlank() && !isLoading && hasProviderKey

    val charCount: Int
        get() = inputText.length

    /** Looked up from the result on every read, so it can never disagree with the hunk list. */
    val activeHunkDetail: CheckEditHunk?
        get() = result?.hunks?.firstOrNull { it.id == activeHunkId }

    val filteredHunks: List<CheckEditHunk>
        get() = if (result == null) emptyList() else {
            if (categoryFilter == null) result.hunks else result.hunks.filter { it.category == categoryFilter }
        }

    val categoryCounts: Map<CheckCategory, Int>
        get() = result?.hunks.orEmpty().groupingBy { it.category }.eachCount()

    val totalEditsCount: Int
        get() = result?.hunks?.size ?: 0

    val acceptedEditsCount: Int
        get() = result?.hunks?.count { it.isAccepted } ?: 0

    val allEditsAccepted: Boolean
        get() = acceptedEditsCount == totalEditsCount
}
