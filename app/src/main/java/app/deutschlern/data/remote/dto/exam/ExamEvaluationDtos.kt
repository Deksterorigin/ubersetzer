package app.deutschlern.data.remote.dto.exam

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExamEvaluationResponseDto(
    val score: Int = 0,
    val summary: String = "",
    val criteria: List<ExamCriterionDto> = emptyList(),
    val corrections: List<ExamCorrectionDto> = emptyList(),
    @SerialName("corrected_text") val correctedText: String = ""
)

@Serializable
data class ExamCriterionDto(
    val name: String = "",
    val score: Int? = null,
    val comment: String = ""
)

@Serializable
data class ExamCorrectionDto(
    val original: String = "",
    val corrected: String = "",
    val reason: String = ""
)
