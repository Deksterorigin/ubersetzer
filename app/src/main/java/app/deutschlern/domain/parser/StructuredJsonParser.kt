package app.deutschlern.domain.parser

import app.deutschlern.domain.provider.ProviderError
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

object StructuredJsonParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun <T> parse(
        rawText: String,
        serializer: KSerializer<T>,
        providerName: String,
        validator: ((T) -> Boolean)? = null
    ): Result<T> {
        val jsonString = extractJsonString(rawText)
            ?: return Result.failure(ProviderError.MalformedJson(providerName))

        val parsed = try {
            json.decodeFromString(serializer, jsonString)
        } catch (e: Exception) {
            return Result.failure(ProviderError.MalformedJson(providerName))
        }

        if (validator != null && !validator(parsed)) {
            return Result.failure(ProviderError.MalformedJson(providerName))
        }

        return Result.success(parsed)
    }

    fun extractJsonString(raw: String): String? {
        val trimmed = raw.trim()

        // 1. Check for markdown code fences
        val fencePattern = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""", RegexOption.IGNORE_CASE)
        val match = fencePattern.find(trimmed)
        if (match != null) {
            val inside = match.groupValues[1].trim()
            if (isValidJsonStructure(inside)) return inside
        }

        // 2. Direct JSON object or array check
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            val candidate = trimmed.substring(firstBrace, lastBrace + 1).trim()
            if (isValidJsonStructure(candidate)) return candidate
        }

        val firstBracket = trimmed.indexOf('[')
        val lastBracket = trimmed.lastIndexOf(']')
        if (firstBracket != -1 && lastBracket != -1 && lastBracket > firstBracket) {
            val candidate = trimmed.substring(firstBracket, lastBracket + 1).trim()
            if (isValidJsonStructure(candidate)) return candidate
        }

        return null
    }

    private fun isValidJsonStructure(str: String): Boolean {
        val t = str.trim()
        return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))
    }
}
