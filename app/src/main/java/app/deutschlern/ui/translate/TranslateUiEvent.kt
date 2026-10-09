package app.deutschlern.ui.translate

import app.deutschlern.domain.model.PartnerLanguage

sealed interface TranslateUiEvent {
    data class OnInputTextChanged(val text: String) : TranslateUiEvent
    data class OnContextTextChanged(val text: String) : TranslateUiEvent
    data object OnToggleContextExpanded : TranslateUiEvent
    data object OnClearInputClicked : TranslateUiEvent
    data class OnPasteText(val text: String) : TranslateUiEvent
    data object OnSwapDirectionClicked : TranslateUiEvent
    data class OnSelectPartnerLanguage(val language: PartnerLanguage) : TranslateUiEvent
    data object OnTranslateClicked : TranslateUiEvent
    data object OnRefreshWithDifferentModelClicked : TranslateUiEvent
    data object OnRetryClicked : TranslateUiEvent
    data object OnDismissError : TranslateUiEvent
    data class OnSelectMeaning(val index: Int) : TranslateUiEvent
    data class OnSaveEquivalent(val meaningIndex: Int) : TranslateUiEvent
    data class OnSpeakWord(val text: String) : TranslateUiEvent
    data object OnDismissTtsSnackbar : TranslateUiEvent
    data object OnDismissSaveMessage : TranslateUiEvent
    data object OnDismissFallbackNotification : TranslateUiEvent
    data object OnDownloadMlKit : TranslateUiEvent
    data class OnStartListening(val isContext: Boolean = false, val languageCode: String? = null) : TranslateUiEvent
    data object OnStopListening : TranslateUiEvent
    data class OnSelectSpeechAlternative(val text: String) : TranslateUiEvent
    data object OnDismissSpeechError : TranslateUiEvent
    data object OnDismissSpeechFirstUseNotice : TranslateUiEvent
    data class OnDownloadSpeechModel(val languageCode: String) : TranslateUiEvent
    data class OnSelectSpeechLanguage(val languageCode: String) : TranslateUiEvent
}
