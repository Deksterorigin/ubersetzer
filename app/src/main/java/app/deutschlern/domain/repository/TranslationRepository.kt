package app.deutschlern.domain.repository

import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.TranslationResult
import app.deutschlern.domain.model.WordTranslation

interface TranslationRepository {
    suspend fun translateText(
        text: String,
        direction: TranslationDirection,
        bypassCache: Boolean = false
    ): Result<TranslationResult<String>>

    suspend fun translateWord(
        word: String,
        direction: TranslationDirection,
        context: String? = null,
        bypassCache: Boolean = false
    ): Result<TranslationResult<WordTranslation>>
}
