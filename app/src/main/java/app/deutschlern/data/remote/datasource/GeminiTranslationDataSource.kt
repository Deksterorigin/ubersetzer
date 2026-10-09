package app.deutschlern.data.remote.datasource

import app.deutschlern.data.remote.api.GeminiApiService
import app.deutschlern.data.remote.dto.GeminiContentDto
import app.deutschlern.data.remote.dto.GeminiErrorResponseDto
import app.deutschlern.data.remote.dto.GeminiGenerationConfigDto
import app.deutschlern.data.remote.dto.GeminiPartDto
import app.deutschlern.data.remote.dto.GeminiRequestDto
import app.deutschlern.data.remote.dto.GeminiThinkingConfigDto
import app.deutschlern.data.remote.dto.WordTranslationDto
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.TranslationError
import app.deutschlern.util.Prompts
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named

data class WordTranslationResult(
    val dto: WordTranslationDto,
    val rawJson: String,
    val cleanJson: String = rawJson
)

class GeminiTranslationDataSource @Inject constructor(
    private val apiService: GeminiApiService,
    @Named("GeminiModel") private val modelName: String,
    private val json: Json
) {

    /**
     * Executes generateContent REST request for TEXT translation.
     */
    suspend fun translateText(text: String, direction: TranslationDirection): String {
        val request = GeminiRequestDto(
            contents = listOf(
                GeminiContentDto(
                    parts = listOf(GeminiPartDto(text = text)),
                    role = "user"
                )
            ),
            systemInstruction = GeminiContentDto(
                parts = listOf(GeminiPartDto(text = Prompts.textTranslationSystemPrompt(direction)))
            ),
            generationConfig = GeminiGenerationConfigDto(
                temperature = 0.2,
                thinkingConfig = GeminiThinkingConfigDto(thinkingBudget = 0)
            )
        )

        return executeGenerateContent(request)
    }

    /**
     * Executes generateContent REST request for WORD translation (lexicographer mode).
     * Requests structured JSON output with responseSchema.
     * Automatically retries once if JSON parsing fails before surfacing an error.
     */
    suspend fun translateWord(
        word: String,
        direction: TranslationDirection,
        context: String? = null
    ): WordTranslationResult {
        val userPrompt = Prompts.buildWordQueryUserPrompt(word, context)
        val systemPrompt = Prompts.wordTranslationSystemPrompt(direction)
        val schema = Prompts.wordTranslationResponseSchema()

        val request = GeminiRequestDto(
            contents = listOf(
                GeminiContentDto(
                    parts = listOf(GeminiPartDto(text = userPrompt)),
                    role = "user"
                )
            ),
            systemInstruction = GeminiContentDto(
                parts = listOf(GeminiPartDto(text = systemPrompt))
            ),
            generationConfig = GeminiGenerationConfigDto(
                temperature = 0.2,
                responseMimeType = "application/json",
                responseSchema = schema,
                thinkingConfig = GeminiThinkingConfigDto(thinkingBudget = 0)
            )
        )

        // Attempt 1
        val firstRaw = executeGenerateContent(request)
        val firstParsed = tryParseWordTranslation(firstRaw)
        if (firstParsed != null) {
            val (dto, cleanJson) = firstParsed
            return WordTranslationResult(dto = dto, rawJson = firstRaw, cleanJson = cleanJson)
        }

        // Retry once on JSON parsing error
        val retryRaw = executeGenerateContent(request)
        val retryParsed = tryParseWordTranslation(retryRaw)
        if (retryParsed != null) {
            val (dto, cleanJson) = retryParsed
            return WordTranslationResult(dto = dto, rawJson = retryRaw, cleanJson = cleanJson)
        }

        throw TranslationError.Generic("Не удалось обработать ответ словаря. Попробуйте повторить запрос.")
    }

    private fun tryParseWordTranslation(raw: String): Pair<WordTranslationDto, String>? {
        val cleaned = cleanJsonText(raw)
        return try {
            val dto = json.decodeFromString<WordTranslationDto>(cleaned)
            Pair(dto, cleaned)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    companion object {
        /**
         * Cleans JSON response text:
         * 1. Strips markdown fences (```json ... ``` or ``` ... ```).
         * 2. Extracts from the first '{' to the last '}' to eliminate preambles/postambles.
         */
        fun cleanJsonText(raw: String): String {
            var text = raw.trim()
            if (text.contains("```json", ignoreCase = true)) {
                text = text.substringAfter("```json", text).substringAfter("```JSON", text)
                if (text.contains("```")) {
                    text = text.substringBeforeLast("```")
                }
            } else if (text.contains("```")) {
                text = text.substringAfter("```", text)
                if (text.contains("```")) {
                    text = text.substringBeforeLast("```")
                }
            }
            text = text.trim()

            // Extract from first '{' to last '}' to strip any external preamble or commentary
            val firstBrace = text.indexOf('{')
            val lastBrace = text.lastIndexOf('}')
            if (firstBrace in 0..lastBrace) {
                text = text.substring(firstBrace, lastBrace + 1)
            }

            return text.trim()
        }
    }

    private suspend fun executeGenerateContent(request: GeminiRequestDto): String {
        val response = try {
            apiService.generateContent(model = modelName, request = request)
        } catch (e: IOException) {
            throw TranslationError.NoInternet(e)
        } catch (e: TranslationError) {
            throw e
        } catch (e: Exception) {
            throw TranslationError.Generic(
                message = e.localizedMessage ?: "Ошибка при выполнении запроса",
                cause = e
            )
        }

        if (!response.isSuccessful) {
            val code = response.code()
            val errorRaw = response.errorBody()?.string().orEmpty()
            val parsedError = runCatching {
                json.decodeFromString<GeminiErrorResponseDto>(errorRaw)
            }.getOrNull()

            val errorMessage = parsedError?.error?.message ?: errorRaw
            val errorStatus = parsedError?.error?.status.orEmpty()

            val isAuthError = code == 401 ||
                (code in listOf(400, 403) && (
                    errorStatus in listOf("UNAUTHENTICATED", "PERMISSION_DENIED") ||
                    errorMessage.contains("API_KEY", ignoreCase = true) ||
                    errorMessage.contains("API key", ignoreCase = true) ||
                    errorMessage.contains("unregistered callers", ignoreCase = true) ||
                    errorMessage.contains("PERMISSION_DENIED", ignoreCase = true) ||
                    errorMessage.contains("UNAUTHENTICATED", ignoreCase = true)
                ))

            when {
                code == 429 -> {
                    throw TranslationError.QuotaExceeded()
                }
                code in 500..599 -> {
                    throw TranslationError.ServerUnavailable(code = code)
                }
                isAuthError -> {
                    throw TranslationError.InvalidApiKey()
                }
                code == 400 && errorMessage.contains("SAFETY", ignoreCase = true) -> {
                    throw TranslationError.SafetyBlock(errorMessage)
                }
                else -> {
                    throw TranslationError.Generic(
                        message = "Ошибка сервера Gemini ($code): ${errorMessage.take(150)}"
                    )
                }
            }
        }

        val body = response.body() ?: throw TranslationError.EmptyResponse()

        if (!body.promptFeedback?.blockReason.isNullOrBlank()) {
            throw TranslationError.SafetyBlock(body.promptFeedback?.blockReason)
        }

        val candidates = body.candidates
        if (candidates.isNullOrEmpty()) {
            throw TranslationError.EmptyResponse()
        }

        val firstCandidate = candidates.first()
        if (firstCandidate.finishReason.equals("SAFETY", ignoreCase = true)) {
            throw TranslationError.SafetyBlock("SAFETY")
        }

        // Filter out thinking parts if model used thinking
        val actualParts = firstCandidate.content?.parts
            .orEmpty()
            .filter { it.thought != true }

        val text = actualParts
            .mapNotNull { it.text }
            .joinToString(separator = "")

        if (text.isBlank()) {
            throw TranslationError.EmptyResponse()
        }

        return text.trim()
    }
}
