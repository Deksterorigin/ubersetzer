package app.deutschlern.domain.provider

data class ProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val keyUrl: String? = null,
    val rateLimitDocsUrl: String,
    val freeTierNote: String,
    val minDelayBetweenRequestsMs: Long = 0L,
    val extraHeaders: Map<String, String> = emptyMap()
)
