package app.deutschlern.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeminiRequestDto(
    val contents: List<GeminiContentDto>,
    @SerialName("systemInstruction")
    val systemInstruction: GeminiContentDto? = null,
    @SerialName("generationConfig")
    val generationConfig: GeminiGenerationConfigDto? = null
)

@Serializable
data class GeminiContentDto(
    val parts: List<GeminiPartDto>,
    val role: String? = null
)

@Serializable
data class GeminiPartDto(
    val text: String? = null,
    val thought: Boolean? = null
)

@Serializable
data class GeminiGenerationConfigDto(
    val temperature: Double? = 0.2,
    @SerialName("responseMimeType")
    val responseMimeType: String? = null,
    @SerialName("responseSchema")
    val responseSchema: GeminiSchemaDto? = null,
    @SerialName("thinkingConfig")
    val thinkingConfig: GeminiThinkingConfigDto? = GeminiThinkingConfigDto(thinkingBudget = 0)
)

@Serializable
data class GeminiSchemaDto(
    val type: String,
    val description: String? = null,
    val properties: Map<String, GeminiSchemaDto>? = null,
    val required: List<String>? = null,
    val items: GeminiSchemaDto? = null,
    val enum: List<String>? = null
)

@Serializable
data class GeminiThinkingConfigDto(
    @SerialName("thinkingBudget")
    val thinkingBudget: Int = 0
)
