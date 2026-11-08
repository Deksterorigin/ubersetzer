package app.deutschlern.domain.provider

import androidx.annotation.StringRes

data class ProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val keyUrl: String? = null,
    val rateLimitDocsUrl: String,
    @StringRes val freeTierNoteRes: Int,
    val minDelayBetweenRequestsMs: Long = 0L,
    val extraHeaders: Map<String, String> = emptyMap(),
    @StringRes val nameRes: Int? = null
)
