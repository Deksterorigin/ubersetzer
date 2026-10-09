package app.deutschlern.domain.provider

/**
 * Capability interface for providers that support structured LLM generation with custom schemas.
 */
interface StructuredGenerationProvider {
    val id: String
    val displayName: String

    suspend fun generateStructured(
        prompt: String,
        systemPrompt: String,
        schemaJson: String?,
        model: String
    ): Result<String>
}
