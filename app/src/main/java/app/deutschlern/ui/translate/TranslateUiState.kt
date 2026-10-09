package app.deutschlern.ui.translate

import app.deutschlern.domain.model.PartnerLanguage
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.TranslationMode
import app.deutschlern.domain.model.WordTranslation

data class TranslateUiState(
    val inputText: String = "",
    val contextText: String = "",
    val isContextExpanded: Boolean = false,
    val selectedPartnerLanguage: PartnerLanguage = PartnerLanguage.RUSSIAN,
    val direction: TranslationDirection = TranslationDirection.DE_RU,
    val mode: TranslationMode = TranslationMode.WORD,
    val translatedText: String? = null,
    val wordTranslation: WordTranslation? = null,
    val selectedMeaningIndex: Int? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val hasManualDirectionOverride: Boolean = false,
    val saveSuccessMessage: String? = null,
    val showTtsInstallSnackbar: Boolean = false,
    val sourceModelAttribution: String? = null,
    val fallbackNotification: String? = null,
    val showModelCaption: Boolean = true,
    val hasAnyKeyConfigured: Boolean = true,
    val isDownloadingMlKit: Boolean = false,
    val isMlKitDownloaded: Boolean = false,
    val isListening: Boolean = false,
    val isListeningContext: Boolean = false,
    val speechPartialText: String? = null,
    val speechAlternatives: List<String> = emptyList(),
    val speechSoundLevel: Float = 0f,
    val speechErrorMessage: String? = null,
    val showSpeechFirstUseNotice: Boolean = false,
    val activeSpeechLanguageCode: String? = null,
    val speechModelDownloadLanguage: String? = null
) {
    val canTranslate: Boolean
        get() = inputText.isNotBlank() && inputText.length <= 5000 && !isLoading

    val charCount: Int
        get() = inputText.length

    val isOverLimit: Boolean
        get() = inputText.length > 5000

    val isWordMode: Boolean
        get() = mode == TranslationMode.WORD

    val isInputRtl: Boolean
        get() = direction.sourceCode == "ar" || direction.sourceCode == "fa"

    val isResultRtl: Boolean
        get() = direction.targetCode == "ar" || direction.targetCode == "fa"
}
