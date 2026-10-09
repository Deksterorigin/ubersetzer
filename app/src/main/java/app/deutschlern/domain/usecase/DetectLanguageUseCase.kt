package app.deutschlern.domain.usecase

import app.deutschlern.domain.model.PartnerLanguage
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.util.LanguageDetector
import javax.inject.Inject

class DetectLanguageUseCase @Inject constructor() {
    operator fun invoke(
        text: CharSequence?,
        partner: PartnerLanguage = PartnerLanguage.RUSSIAN
    ): TranslationDirection {
        return LanguageDetector.detectLanguage(text, partner)
    }
}
