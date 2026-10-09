package app.deutschlern.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class OpenAiChatRequestDto(
    val model: String,
    val messages: List<OpenAiMessageDto>,
    val temperature: Double? = 0.2,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    @SerialName("response_format")
    val responseFormat: JsonElement? = null
)

@Serializable
data class OpenAiMessageDto(
    val role: String,
    val content: String
)

@Serializable
data class OpenAiChatResponseDto(
    val id: String? = null,
    val choices: List<OpenAiChoiceDto>? = null,
    val error: OpenAiErrorDetailDto? = null
)

@Serializable
data class OpenAiChoiceDto(
    val index: Int? = null,
    val message: OpenAiMessageDto? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class OpenAiErrorEnvelopeDto(
    val error: OpenAiErrorDetailDto? = null
)

@Serializable
data class OpenAiErrorDetailDto(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

@Serializable
data class OpenAiModelListResponseDto(
    val data: List<OpenAiModelItemDto>? = null
)

@Serializable
data class OpenAiModelItemDto(
    val id: String
)
