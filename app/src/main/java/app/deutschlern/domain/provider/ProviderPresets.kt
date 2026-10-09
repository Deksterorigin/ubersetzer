package app.deutschlern.domain.provider

object ProviderPresets {

    val GROQ = ProviderConfig(
        id = "groq",
        name = "Groq",
        baseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "llama-3.3-70b-versatile",
        keyUrl = "https://console.groq.com/keys",
        rateLimitDocsUrl = "https://console.groq.com/docs/rate-limits",
        freeTierNote = "Сверхбыстрый инференс на LPU. Бесплатный тариф для разработчиков:"
    )

    val MISTRAL = ProviderConfig(
        id = "mistral",
        name = "Mistral AI",
        baseUrl = "https://api.mistral.ai/v1",
        defaultModel = "mistral-small-latest",
        keyUrl = "https://console.mistral.ai/api-keys/",
        rateLimitDocsUrl = "https://docs.mistral.ai/deployment/limits/",
        freeTierNote = "Европейские модели Mistral. Бесплатный Experimentation tier (1 запрос/сек):",
        minDelayBetweenRequestsMs = 1000L
    )

    val CEREBRAS = ProviderConfig(
        id = "cerebras",
        name = "Cerebras",
        baseUrl = "https://api.cerebras.ai/v1",
        defaultModel = "gpt-oss-120b",
        keyUrl = "https://cloud.cerebras.ai/",
        rateLimitDocsUrl = "https://cerebras.ai/developers",
        freeTierNote = "Сверхбыстрый ускоритель CS-3. Бесплатный тариф для разработчиков:"
    )

    val OPENROUTER = ProviderConfig(
        id = "openrouter",
        name = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "meta-llama/llama-3.3-70b-instruct:free",
        keyUrl = "https://openrouter.ai/settings/keys",
        rateLimitDocsUrl = "https://openrouter.ai/docs/limits",
        freeTierNote = "Агрегатор моделей с поддержкой бесплатных вариантов (:free):",
        extraHeaders = mapOf(
            "HTTP-Referer" to "https://app.deutschlern",
            "X-Title" to "DeutschLern"
        )
    )

    val CUSTOM = ProviderConfig(
        id = "custom",
        name = "Пользовательский (OpenAI API)",
        baseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        rateLimitDocsUrl = "https://platform.openai.com/docs/guides/rate-limits",
        freeTierNote = "Собственный совместимый сервер. Должен использовать протокол HTTPS."
    )

    val ALL_OPENAI_PRESETS = listOf(GROQ, MISTRAL, CEREBRAS, OPENROUTER, CUSTOM)
}
