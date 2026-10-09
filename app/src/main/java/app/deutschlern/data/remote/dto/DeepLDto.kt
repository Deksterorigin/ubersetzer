package app.deutschlern.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeepLTranslateRequestDto(
    val text: List<String>,
    @SerialName("source_lang")
    val sourceLang: String? = null,
    @SerialName("target_lang")
    val targetLang: String
)

@Serializable
data class DeepLTranslateResponseDto(
    val translations: List<DeepLTranslationDto>? = null,
    val message: String? = null
)

@Serializable
data class DeepLTranslationDto(
    @SerialName("detected_source_language")
    val detectedSourceLanguage: String? = null,
    val text: String
)

@Serializable
data class DeepLUsageResponseDto(
    @SerialName("character_count")
    val characterCount: Long = 0L,
    @SerialName("character_limit")
    val characterLimit: Long = 0L,
    val message: String? = null
)
