package app.deutschlern.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeminiResponseDto(
    val candidates: List<GeminiCandidateDto>? = null,
    val promptFeedback: GeminiPromptFeedbackDto? = null
)

@Serializable
data class GeminiCandidateDto(
    val content: GeminiContentDto? = null,
    val finishReason: String? = null
)

@Serializable
data class GeminiPromptFeedbackDto(
    val blockReason: String? = null
)

@Serializable
data class GeminiErrorResponseDto(
    val error: GeminiErrorDetailDto? = null
)

@Serializable
data class GeminiErrorDetailDto(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

@Serializable
data class GeminiModelListResponseDto(
    val models: List<GeminiModelItemDto>? = null
)

@Serializable
data class GeminiModelItemDto(
    val name: String,
    val supportedGenerationMethods: List<String>? = null
)
