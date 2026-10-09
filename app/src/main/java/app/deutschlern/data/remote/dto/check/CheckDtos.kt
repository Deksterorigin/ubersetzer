package app.deutschlern.data.remote.dto.check

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CorrectionResponseDto(
    @SerialName("corrected_text") val correctedText: String = "",
    val edits: List<EditItemDto> = emptyList(),
    val comments: List<CommentItemDto> = emptyList()
)

@Serializable
data class EditItemDto(
    val original: String = "",
    val corrected: String = "",
    val category: String = "grammar",
    val explanation: String = "",
    val rule: String? = null
)

@Serializable
data class CommentItemDto(
    val fragment: String = "",
    val issue: String = "unclear_reference",
    val explanation: String = ""
)
