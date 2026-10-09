package app.deutschlern.domain.provider

import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.WordTranslation

interface TranslationProvider {
    val id: String
    val displayName: String
    val capabilities: ProviderCapabilities

    suspend fun translateWord(
        word: String,
        direction: TranslationDirection,
        context: String? = null,
        model: String
    ): Result<WordTranslation>

    suspend fun translateText(
        text: String,
        direction: TranslationDirection,
        model: String
    ): Result<String>

    suspend fun testConnection(model: String): Result<String>

    suspend fun listModels(): Result<List<String>>
}
