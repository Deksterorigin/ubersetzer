package app.deutschlern.data.remote.provider

import app.deutschlern.data.local.security.SecureKeyStorage
import app.deutschlern.data.remote.dto.GeminiContentDto
import app.deutschlern.data.remote.dto.GeminiErrorResponseDto
import app.deutschlern.data.remote.dto.GeminiGenerationConfigDto
import app.deutschlern.data.remote.dto.GeminiModelListResponseDto
import app.deutschlern.data.remote.dto.GeminiPartDto
import app.deutschlern.data.remote.dto.GeminiRequestDto
import app.deutschlern.data.remote.dto.GeminiResponseDto
import app.deutschlern.data.remote.dto.GeminiThinkingConfigDto
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.WordTranslation
import app.deutschlern.domain.parser.RobustWordJsonParser
import app.deutschlern.domain.provider.ProviderCapabilities
import app.deutschlern.domain.provider.ProviderError
import app.deutschlern.domain.provider.RateLimiter
import app.deutschlern.domain.provider.TranslationProvider
import app.deutschlern.util.Prompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

import app.deutschlern.domain.provider.StructuredGenerationProvider

@Singleton
class GeminiProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val secureKeyStorage: SecureKeyStorage,
    private val rateLimiter: RateLimiter
) : TranslationProvider, StructuredGenerationProvider {

    override val id: String = "gemini"
    override val displayName: String = "Google Gemini"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsWordMode = true,
        supportsTextMode = true,
        isOffline = false
    )

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta"

    override suspend fun translateWord(
        word: String,
        direction: TranslationDirection,
        context: String?,
        model: String
    ): Result<WordTranslation> = withContext(Dispatchers.IO) {
        val apiKey = secureKeyStorage.getKey(id)
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(ProviderError.NoKeyConfigured(displayName))
        }

        rateLimiter.acquire(id)

        val cleanModel = model.removePrefix("models/")
        val userPrompt = Prompts.buildWordQueryUserPrompt(word, context)
        val systemPrompt = Prompts.wordTranslationSystemPrompt(direction)
        val schema = Prompts.wordTranslationResponseSchema()

        val requestDto = GeminiRequestDto(
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

        val firstAttempt = executeGenerateContent(cleanModel, apiKey, requestDto)
        if (firstAttempt.isFailure) {
            return@withContext Result.failure(firstAttempt.exceptionOrNull() ?: ProviderError.Generic(displayName, "Ошибка выполнения"))
        }

        val firstParsed = RobustWordJsonParser.parse(firstAttempt.getOrThrow(), direction, displayName)
        if (firstParsed.isSuccess) {
            return@withContext firstParsed
        }

        // Retry once on validation failure with repair hint
        val repairDto = GeminiRequestDto(
            contents = listOf(
                GeminiContentDto(
                    parts = listOf(GeminiPartDto(text = userPrompt)),
                    role = "user"
                ),
                GeminiContentDto(
                    parts = listOf(GeminiPartDto(text = firstAttempt.getOrThrow())),
                    role = "model"
                ),
                GeminiContentDto(
                    parts = listOf(GeminiPartDto(text = RobustWordJsonParser.createRepairPrompt(direction))),
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

        val retryAttempt = executeGenerateContent(cleanModel, apiKey, repairDto)
        if (retryAttempt.isFailure) {
            return@withContext Result.failure(retryAttempt.exceptionOrNull() ?: ProviderError.Generic(displayName, "Ошибка выполнения"))
        }

        val retryParsed = RobustWordJsonParser.parse(retryAttempt.getOrThrow(), direction, displayName)
        if (retryParsed.isSuccess) {
            return@withContext retryParsed
        }

        Result.failure(ProviderError.MalformedJson(displayName))
    }

    override suspend fun translateText(
        text: String,
        direction: TranslationDirection,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = secureKeyStorage.getKey(id)
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(ProviderError.NoKeyConfigured(displayName))
        }

        rateLimiter.acquire(id)

        val cleanModel = model.removePrefix("models/")
        val requestDto = GeminiRequestDto(
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

        executeGenerateContent(cleanModel, apiKey, requestDto)
    }

    override suspend fun testConnection(model: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = secureKeyStorage.getKey(id)
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(ProviderError.NoKeyConfigured(displayName))
        }

        val cleanModel = model.removePrefix("models/")
        val url = "$baseUrl/models/$cleanModel"
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .get()
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success("Работает")
                } else {
                    Result.failure(parseError(response.code, response.body?.string(), cleanModel, response.header("Retry-After")))
                }
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    override suspend fun listModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        val apiKey = secureKeyStorage.getKey(id)
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(ProviderError.NoKeyConfigured(displayName))
        }

        val url = "$baseUrl/models?pageSize=100"
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .get()
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        parseError(response.code, response.body?.string(), "models", response.header("Retry-After"))
                    )
                }

                val bodyStr = response.body?.string().orEmpty()
                val listDto = json.decodeFromString<GeminiModelListResponseDto>(bodyStr)
                val supported = listDto.models.orEmpty()
                    .filter { it.supportedGenerationMethods?.contains("generateContent") == true }
                    .map { it.name.removePrefix("models/") }

                Result.success(supported)
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    override suspend fun generateStructured(
        prompt: String,
        systemPrompt: String,
        schemaJson: String?,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = secureKeyStorage.getKey(id)
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(ProviderError.NoKeyConfigured(displayName))
        }

        val request = GeminiRequestDto(
            contents = listOf(
                GeminiContentDto(
                    role = "user",
                    parts = listOf(GeminiPartDto(text = prompt))
                )
            ),
            systemInstruction = if (systemPrompt.isNotBlank()) {
                GeminiContentDto(
                    role = "system",
                    parts = listOf(GeminiPartDto(text = systemPrompt))
                )
            } else null,
            generationConfig = GeminiGenerationConfigDto(
                temperature = 0.1,
                responseMimeType = "application/json"
            )
        )

        rateLimiter.acquire(id, 1000L)
        executeGenerateContent(model, apiKey, request)
    }

    private fun executeGenerateContent(
        model: String,
        apiKey: String,
        requestDto: GeminiRequestDto
    ): Result<String> {
        val url = "$baseUrl/models/$model:generateContent"
        val jsonPayload = json.encodeToString(requestDto)
        val body = jsonPayload.toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .post(body)
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val retryAfter = response.header("Retry-After")
                    return Result.failure(parseError(response.code, responseBody, model, retryAfter))
                }

                val parsed = json.decodeFromString<GeminiResponseDto>(responseBody)
                if (!parsed.promptFeedback?.blockReason.isNullOrBlank()) {
                    return Result.failure(ProviderError.SafetyBlocked(displayName))
                }

                val candidate = parsed.candidates?.firstOrNull()
                    ?: return Result.failure(ProviderError.Generic(displayName, "Пустой ответ от модели"))

                if (candidate.finishReason.equals("SAFETY", ignoreCase = true)) {
                    return Result.failure(ProviderError.SafetyBlocked(displayName))
                }

                val content = candidate.content?.parts.orEmpty()
                    .filter { it.thought != true }
                    .mapNotNull { it.text }
                    .joinToString("")
                    .trim()

                if (content.isEmpty()) {
                    Result.failure(ProviderError.Generic(displayName, "Пустой ответ от модели"))
                } else {
                    Result.success(content)
                }
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    private fun parseError(code: Int, errorBody: String?, model: String, retryAfterHeader: String?): ProviderError {
        val parsed = runCatching {
            errorBody?.let { json.decodeFromString<GeminiErrorResponseDto>(it) }
        }.getOrNull()

        val message = parsed?.error?.message ?: errorBody.orEmpty()
        val status = parsed?.error?.status.orEmpty()

        if (code == 404 || status == "NOT_FOUND" || message.contains("not found", ignoreCase = true)) {
            return ProviderError.ModelNotFound(displayName, model)
        }

        if (code == 429 || status == "RESOURCE_EXHAUSTED") {
            val seconds = retryAfterHeader?.toLongOrNull()
            if (seconds != null) {
                rateLimiter.recordRetryAfter(id, seconds)
            }
            return ProviderError.RateLimitExceeded(displayName, seconds)
        }

        val isAuthError = code == 401 ||
                (code in listOf(400, 403) && (
                        status in listOf("UNAUTHENTICATED", "PERMISSION_DENIED") ||
                        message.contains("API_KEY", ignoreCase = true) ||
                        message.contains("API key", ignoreCase = true) ||
                        message.contains("unregistered callers", ignoreCase = true)
                ))

        if (isAuthError) {
            return ProviderError.InvalidKey(displayName)
        }

        if (code in 500..599) {
            return ProviderError.ServerError(displayName, code)
        }

        if (code == 400 && message.contains("SAFETY", ignoreCase = true)) {
            return ProviderError.SafetyBlocked(displayName)
        }

        return ProviderError.Generic(displayName, "HTTP $code: ${message.take(150)}")
    }

    private fun mapException(e: Exception): ProviderError {
        return when (e) {
            is SocketTimeoutException -> ProviderError.Timeout(displayName)
            is UnknownHostException, is IOException -> ProviderError.Offline
            is ProviderError -> e
            else -> ProviderError.Generic(displayName, e.localizedMessage ?: "Сетевая ошибка")
        }
    }
}
