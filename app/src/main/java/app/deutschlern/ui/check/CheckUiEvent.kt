package app.deutschlern.ui.check

import app.deutschlern.domain.model.check.CheckCategory
import app.deutschlern.domain.model.check.CheckEditHunk

sealed interface CheckUiEvent {
    data class OnInputChanged(val text: String) : CheckUiEvent
    data object OnClearInput : CheckUiEvent
    data class OnPasteText(val text: String) : CheckUiEvent
    data object OnCheckClicked : CheckUiEvent
    data object OnRetryClicked : CheckUiEvent
    data object OnDismissError : CheckUiEvent
    data class OnSelectViewMode(val mode: CheckViewMode) : CheckUiEvent
    data class OnSelectCategoryFilter(val category: CheckCategory?) : CheckUiEvent
    data class OnOpenHunkDetail(val hunk: CheckEditHunk) : CheckUiEvent
    data object OnDismissHunkDetail : CheckUiEvent
    data class OnToggleHunkAcceptance(val hunkId: String) : CheckUiEvent
    data object OnAcceptAllHunks : CheckUiEvent
    data class OnAddHunkToVocabulary(val hunk: CheckEditHunk) : CheckUiEvent
    data object OnDismissAddWordMessage : CheckUiEvent
    data object OnResetCheck : CheckUiEvent
    data object OnAcceptConsent : CheckUiEvent
    data object OnDeclineConsent : CheckUiEvent
    data class OnLoadSavedCheck(val checkId: Long) : CheckUiEvent
    data object OnOpenPhotoSourceDialog : CheckUiEvent
    data object OnDismissPhotoSourceDialog : CheckUiEvent
    data class OnOcrImagesSelected(val uris: List<android.net.Uri>) : CheckUiEvent
    data class OnOcrPreviewTextChanged(val text: String) : CheckUiEvent
    data object OnInsertOcrText : CheckUiEvent
    data object OnDismissOcrPreview : CheckUiEvent
    data object OnDismissOcrError : CheckUiEvent
}
