package app.deutschlern.domain.provider

data class ProviderCapabilities(
    val supportsWordMode: Boolean,
    val supportsTextMode: Boolean,
    val isOffline: Boolean = false
)
