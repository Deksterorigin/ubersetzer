package app.deutschlern.domain.usecase

import app.deutschlern.domain.model.TranslationMode
import app.deutschlern.util.ModeDetector
import javax.inject.Inject

class DetectModeUseCase @Inject constructor() {
    operator fun invoke(text: String?): TranslationMode {
        return ModeDetector.detectMode(text)
    }
}
