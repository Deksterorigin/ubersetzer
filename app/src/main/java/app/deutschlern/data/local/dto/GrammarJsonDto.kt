package app.deutschlern.data.local.dto

import app.deutschlern.data.remote.dto.VerbFormsDto
import kotlinx.serialization.Serializable

@Serializable
data class GrammarJsonDto(
    val verb_forms: VerbFormsDto? = null,
    val government: String? = null,
    val comparative: String? = null,
    val superlative: String? = null
)
