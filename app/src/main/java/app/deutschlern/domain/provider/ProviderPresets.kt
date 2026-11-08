package app.deutschlern.domain.provider

import app.deutschlern.R
object ProviderPresets {

    val GROQ = ProviderConfig(
        id = "groq",
        name = "Groq",
        baseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "llama-3.3-70b-versatile",
        keyUrl = "https://console.groq.com/keys",
        rateLimitDocsUrl = "https://console.groq.com/docs/rate-limits",
        freeTierNoteRes = R.string.tier_groq
    )

    val MISTRAL = ProviderConfig(
        id = "mistral",
        name = "Mistral AI",
        baseUrl = "https://api.mistral.ai/v1",
        defaultModel = "mistral-small-latest",
        keyUrl = "https://console.mistral.ai/api-keys/",
        rateLimitDocsUrl = "https://docs.mistral.ai/deployment/limits/",
        freeTierNoteRes = R.string.tier_mistral,
        minDelayBetweenRequestsMs = 1000L
    )

    val CEREBRAS = ProviderConfig(
        id = "cerebras",
        name = "Cerebras",
        baseUrl = "https://api.cerebras.ai/v1",
        defaultModel = "gpt-oss-120b",
        keyUrl = "https://cloud.cerebras.ai/",
        rateLimitDocsUrl = "https://cerebras.ai/developers",
        freeTierNoteRes = R.string.tier_cerebras
    )

    val OPENROUTER = ProviderConfig(
        id = "openrouter",
        name = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "meta-llama/llama-3.3-70b-instruct:free",
        keyUrl = "https://openrouter.ai/settings/keys",
        rateLimitDocsUrl = "https://openrouter.ai/docs/limits",
        freeTierNoteRes = R.string.tier_openrouter,
        extraHeaders = mapOf(
            "HTTP-Referer" to "https://app.deutschlern",
            "X-Title" to "DeutschLern"
        )
    )

    val CUSTOM = ProviderConfig(
        id = "custom",
        name = "Custom (OpenAI API)",
        nameRes = R.string.provider_custom_name,
        baseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        rateLimitDocsUrl = "https://platform.openai.com/docs/guides/rate-limits",
        freeTierNoteRes = R.string.tier_custom
    )

    val ALL_OPENAI_PRESETS = listOf(GROQ, MISTRAL, CEREBRAS, OPENROUTER, CUSTOM)
}
