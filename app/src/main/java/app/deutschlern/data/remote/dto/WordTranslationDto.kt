package app.deutschlern.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WordTranslationDto(
    val lemma: String = "",
    val article: String? = null,
    val pos: String = "other",
    val plural: String? = null,
    val genitive: String? = null,
    @SerialName("verb_forms")
    val verb_forms: VerbFormsDto? = null,
    val government: String? = null,
    val comparative: String? = null,
    val superlative: String? = null,
    val meanings: List<MeaningDto>? = emptyList(),
    val synonyms: List<String>? = emptyList(),
    @SerialName("common_collocations")
    val common_collocations: List<CollocationDto>? = emptyList()
)

@Serializable
data class VerbFormsDto(
    @SerialName("praesens_er")
    val praesens_er: String? = "",
    val praeteritum: String? = "",
    val partizip2: String? = "",
    val auxiliary: String? = "",
    val separable: Boolean? = false,
    val prefix: String? = null
)

@Serializable
data class MeaningDto(
    val translation: String = "",
    val note: String? = null,
    val register: String? = null,
    @SerialName("example_de")
    val example_de: String = "",
    @SerialName("example_ru")
    val example_ru: String = "",
    @SerialName("best_match")
    val best_match: Boolean? = false
)

@Serializable
data class CollocationDto(
    val de: String = "",
    val ru: String = ""
)
