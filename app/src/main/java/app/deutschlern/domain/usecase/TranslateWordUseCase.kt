package app.deutschlern.domain.usecase

import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.TranslationResult
import app.deutschlern.domain.model.WordTranslation
import app.deutschlern.domain.repository.TranslationRepository
import javax.inject.Inject

class TranslateWordUseCase @Inject constructor(
    private val translationRepository: TranslationRepository
) {
    suspend operator fun invoke(
        word: String,
        direction: TranslationDirection,
        context: String? = null,
        bypassCache: Boolean = false
    ): Result<TranslationResult<WordTranslation>> {
        return translationRepository.translateWord(
            word = word,
            direction = direction,
            context = context,
            bypassCache = bypassCache
        )
    }
}
