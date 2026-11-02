package app.deutschlern.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class WordTopicsResponseDto(
    val items: List<WordTopicsItemDto> = emptyList()
)

@Serializable
data class WordTopicsItemDto(
    val id: Long,
    val topics: List<String> = emptyList()
)
