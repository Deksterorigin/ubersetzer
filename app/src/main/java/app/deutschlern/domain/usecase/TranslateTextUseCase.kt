package app.deutschlern.domain.usecase

import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.TranslationResult
import app.deutschlern.domain.repository.TranslationRepository
import javax.inject.Inject

class TranslateTextUseCase @Inject constructor(
    private val translationRepository: TranslationRepository
) {
    suspend operator fun invoke(
        text: String,
        direction: TranslationDirection,
        bypassCache: Boolean = false
    ): Result<TranslationResult<String>> {
        return translationRepository.translateText(
            text = text,
            direction = direction,
            bypassCache = bypassCache
        )
    }
}
