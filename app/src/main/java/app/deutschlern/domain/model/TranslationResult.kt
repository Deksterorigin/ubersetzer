package app.deutschlern.domain.model

data class TranslationResult<T>(
    val data: T,
    val modelAttribution: String,
    val fallbackMessage: String? = null
)
