package app.deutschlern.data.remote.provider

import app.deutschlern.data.local.security.SecureKeyStorage
import app.deutschlern.data.remote.dto.OpenAiChatRequestDto
import app.deutschlern.data.remote.dto.OpenAiChatResponseDto
import app.deutschlern.data.remote.dto.OpenAiErrorEnvelopeDto
import app.deutschlern.data.remote.dto.OpenAiMessageDto
import app.deutschlern.data.remote.dto.OpenAiModelListResponseDto
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.WordTranslation
import app.deutschlern.domain.parser.RobustWordJsonParser
import app.deutschlern.domain.provider.JsonMode
import app.deutschlern.domain.provider.JsonModeCapabilityStore
import app.deutschlern.domain.provider.ProviderCapabilities
import app.deutschlern.domain.provider.ProviderConfig
import app.deutschlern.domain.provider.ProviderError
import app.deutschlern.domain.provider.RateLimiter
import app.deutschlern.domain.provider.TranslationProvider
import app.deutschlern.util.Prompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

import app.deutschlern.domain.provider.StructuredGenerationProvider

class OpenAiCompatibleProvider(
    val config: ProviderConfig,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val secureKeyStorage: SecureKeyStorage,
    private val rateLimiter: RateLimiter,
    private val jsonModeCapabilityStore: JsonModeCapabilityStore
) : TranslationProvider, StructuredGenerationProvider {

    override val id: String = config.id
    override val displayName: String = config.name
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsWordMode = true,
        supportsTextMode = true,
        isOffline = false
    )

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val SCHEMA_SPEC = """
{
  "type": "object",
  "properties": {
    "lemma": { "type": "string" },
    "article": { "type": ["string", "null"] },
    "pos": { "type": "string" },
    "plural": { "type": ["string", "null"] },
    "genitive": { "type": ["string", "null"] },
    "verb_forms": {
      "type": ["object", "null"],
      "properties": {
        "praesens_er": { "type": "string" },
        "praeteritum": { "type": "string" },
        "partizip2": { "type": "string" },
        "auxiliary": { "type": "string" },
        "separable": { "type": "boolean" },
        "prefix": { "type": ["string", "null"] }
      },
      "additionalProperties": false,
      "required": ["praesens_er", "praeteritum", "partizip2", "auxiliary", "separable", "prefix"]
    },
    "government": { "type": ["string", "null"] },
    "comparative": { "type": ["string", "null"] },
    "superlative": { "type": ["string", "null"] },
    "meanings": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "translation": { "type": "string" },
          "note": { "type": ["string", "null"] },
          "register": { "type": ["string", "null"] },
          "example_de": { "type": "string" },
          "example_ru": { "type": "string" },
          "best_match": { "type": "boolean" }
        },
        "additionalProperties": false,
        "required": ["translation", "note", "register", "example_de", "example_ru", "best_match"]
      }
    },
    "synonyms": {
      "type": "array",
      "items": { "type": "string" }
    },
    "common_collocations": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "de": { "type": "string" },
          "ru": { "type": "string" }
        },
        "additionalProperties": false,
        "required": ["de", "ru"]
      }
    }
  },
  "additionalProperties": false,
  "required": [
    "lemma", "article", "pos", "plural", "genitive",
    "verb_forms", "government", "comparative", "superlative",
    "meanings", "synonyms", "common_collocations"
  ]
}
"""

        private val jsonSchemaFormat: JsonElement by lazy {
            Json.parseToJsonElement("""
            {
              "type": "json_schema",
              "json_schema": {
                "name": "word_translation",
                "strict": true,
                "schema": $SCHEMA_SPEC
              }
            }
            """.trimIndent())
        }

        private val jsonObjectFormat: JsonElement by lazy {
            Json.parseToJsonElement("""{"type": "json_object"}""")
        }

        const val SCHEMA_PROMPT_INSTRUCTION = """
Return a JSON object conforming strictly to this specification:
$SCHEMA_SPEC
"""
    }

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

        if (id == "custom" && !config.baseUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext Result.failure(
                ProviderError.Generic(displayName, "Пользовательский сервер должен использовать протокол HTTPS.")
            )
        }

        val userPrompt = Prompts.buildWordQueryUserPrompt(word, context)
        val systemPromptBase = Prompts.wordTranslationSystemPrompt(direction)

        var currentMode = jsonModeCapabilityStore.getMode(id, model)

        // Attempt execution with potential downgrade on HTTP 400
        val executionResult: Result<String>
        var activeSystemPrompt: String
        var activeResponseFormat: JsonElement?

        while (true) {
            when (currentMode) {
                JsonMode.JSON_SCHEMA -> {
                    activeSystemPrompt = systemPromptBase
                    activeResponseFormat = jsonSchemaFormat
                }
                JsonMode.JSON_OBJECT -> {
                    activeSystemPrompt = "$systemPromptBase\n\n$SCHEMA_PROMPT_INSTRUCTION"
                    activeResponseFormat = jsonObjectFormat
                }
                JsonMode.PLAIN_TEXT -> {
                    activeSystemPrompt = "$systemPromptBase\n\n$SCHEMA_PROMPT_INSTRUCTION\nOutput raw JSON only. Do NOT use markdown code fences."
                    activeResponseFormat = null
                }
            }

            val request = OpenAiChatRequestDto(
                model = model,
                messages = listOf(
                    OpenAiMessageDto(role = "system", content = activeSystemPrompt),
                    OpenAiMessageDto(role = "user", content = userPrompt)
                ),
                temperature = 0.2,
                responseFormat = activeResponseFormat
            )

            rateLimiter.acquire(id, config.minDelayBetweenRequestsMs)
            val callResult = executeChatCompletion(model, apiKey, request)

            if (callResult.isSuccess) {
                executionResult = callResult
                break
            } else {
                val error = callResult.exceptionOrNull()
                // Check if downgrade is needed due to unsupported response_format
                val isBadRequest = error is ProviderError.Generic && (
                        error.message?.contains("response_format", ignoreCase = true) == true ||
                        error.message?.contains("400", ignoreCase = true) == true ||
                        error.message?.contains("schema", ignoreCase = true) == true
                )

                if (isBadRequest && currentMode != JsonMode.PLAIN_TEXT) {
                    val downgraded = jsonModeCapabilityStore.downgradeMode(id, model)
                    if (downgraded != null) {
                        currentMode = downgraded
                        continue
                    }
                }
                return@withContext Result.failure(error ?: ProviderError.Generic(displayName, "Ошибка запроса"))
            }
        }

        val rawOutput = executionResult.getOrThrow()
        val parsed = RobustWordJsonParser.parse(rawOutput, direction, displayName)
        if (parsed.isSuccess) {
            return@withContext parsed
        }

        // Retry once with repair prompt
        val repairRequest = OpenAiChatRequestDto(
            model = model,
            messages = listOf(
                OpenAiMessageDto(role = "system", content = activeSystemPrompt),
                OpenAiMessageDto(role = "user", content = userPrompt),
                OpenAiMessageDto(role = "assistant", content = rawOutput),
                OpenAiMessageDto(role = "user", content = RobustWordJsonParser.createRepairPrompt(direction))
            ),
            temperature = 0.2,
            responseFormat = activeResponseFormat
        )

        rateLimiter.acquire(id, config.minDelayBetweenRequestsMs)
        val repairCallResult = executeChatCompletion(model, apiKey, repairRequest)
        if (repairCallResult.isFailure) {
            return@withContext Result.failure(repairCallResult.exceptionOrNull() ?: ProviderError.Generic(displayName, "Ошибка выполнения"))
        }

        val repairParsed = RobustWordJsonParser.parse(repairCallResult.getOrThrow(), direction, displayName)
        if (repairParsed.isSuccess) {
            return@withContext repairParsed
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

        if (id == "custom" && !config.baseUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext Result.failure(
                ProviderError.Generic(displayName, "Пользовательский сервер должен использовать протокол HTTPS.")
            )
        }

        rateLimiter.acquire(id, config.minDelayBetweenRequestsMs)

        val request = OpenAiChatRequestDto(
            model = model,
            messages = listOf(
                OpenAiMessageDto(role = "system", content = Prompts.textTranslationSystemPrompt(direction)),
                OpenAiMessageDto(role = "user", content = text)
            ),
            temperature = 0.2
        )

        executeChatCompletion(model, apiKey, request)
    }

    override suspend fun testConnection(model: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = secureKeyStorage.getKey(id)
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(ProviderError.NoKeyConfigured(displayName))
        }

        if (id == "custom" && !config.baseUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext Result.failure(
                ProviderError.Generic(displayName, "Пользовательский сервер должен использовать протокол HTTPS.")
            )
        }

        val request = OpenAiChatRequestDto(
            model = model,
            messages = listOf(
                OpenAiMessageDto(role = "user", content = "Hi")
            ),
            maxTokens = 5,
            temperature = 0.0
        )

        val result = executeChatCompletion(model, apiKey, request)
        if (result.isSuccess) {
            Result.success("Работает")
        } else {
            Result.failure(result.exceptionOrNull() ?: ProviderError.Generic(displayName, "Ошибка соединения"))
        }
    }

    override suspend fun generateStructured(
        prompt: String,
        systemPrompt: String,
        schemaJson: String?,
        model: String
    ): Result<String> = withContext<Result<String>>(Dispatchers.IO) {
        val apiKey = secureKeyStorage.getKey(id)
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(ProviderError.NoKeyConfigured(displayName))
        }

        if (id == "custom" && !config.baseUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext Result.failure(
                ProviderError.Generic(displayName, "Пользовательский сервер должен использовать протокол HTTPS.")
            )
        }

        var currentMode = jsonModeCapabilityStore.getMode(id, model)
        val schemaFormat = schemaJson?.let {
            runCatching {
                json.parseToJsonElement(
                    """{"type":"json_schema","json_schema":{"name":"structured_output","strict":true,"schema":$it}}"""
                )
            }.getOrNull()
        }

        val executionResult: Result<String>
        while (true) {
            val (activeSystemPrompt, activeResponseFormat) = when (currentMode) {
                JsonMode.JSON_SCHEMA -> Pair(systemPrompt, schemaFormat ?: jsonObjectFormat)
                JsonMode.JSON_OBJECT -> Pair(
                    if (schemaJson != null) "$systemPrompt\n\nReturn a JSON object conforming strictly to this specification:\n$schemaJson" else systemPrompt,
                    jsonObjectFormat
                )
                JsonMode.PLAIN_TEXT -> Pair(
                    if (schemaJson != null) "$systemPrompt\n\nReturn a JSON object conforming strictly to this specification:\n$schemaJson\nOutput raw JSON only. Do NOT use markdown code fences." else systemPrompt,
                    null
                )
            }

            val request = OpenAiChatRequestDto(
                model = model,
                messages = listOf(
                    OpenAiMessageDto(role = "system", content = activeSystemPrompt),
                    OpenAiMessageDto(role = "user", content = prompt)
                ),
                temperature = 0.1,
                responseFormat = activeResponseFormat
            )

            rateLimiter.acquire(id, config.minDelayBetweenRequestsMs)
            val callResult = executeChatCompletion(model, apiKey, request)
            if (callResult.isSuccess) {
                executionResult = callResult
                break
            } else {
                val error = callResult.exceptionOrNull()
                val isBadRequest = error is ProviderError.Generic && (
                    error.message?.contains("response_format", ignoreCase = true) == true ||
                    error.message?.contains("400", ignoreCase = true) == true ||
                    error.message?.contains("schema", ignoreCase = true) == true
                )

                if (isBadRequest && currentMode != JsonMode.PLAIN_TEXT) {
                    val downgraded = jsonModeCapabilityStore.downgradeMode(id, model)
                    if (downgraded != null) {
                        currentMode = downgraded
                        continue
                    }
                }
                return@withContext Result.failure(error ?: ProviderError.Generic(displayName, "Ошибка запроса"))
            }
        }
        executionResult
    }

    override suspend fun listModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        val apiKey = secureKeyStorage.getKey(id)
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(ProviderError.NoKeyConfigured(displayName))
        }

        if (id == "custom" && !config.baseUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext Result.failure(
                ProviderError.Generic(displayName, "Пользовательский сервер должен использовать протокол HTTPS.")
            )
        }

        val url = "${config.baseUrl.trimEnd('/')}/models"
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .get()

        config.extraHeaders.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }

        try {
            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val retryAfter = response.header("Retry-After")
                    return@withContext Result.failure(parseError(response.code, bodyStr, "models", retryAfter))
                }

                val listDto = json.decodeFromString<OpenAiModelListResponseDto>(bodyStr)
                val modelIds = listDto.data.orEmpty().map { it.id }

                Result.success(modelIds)
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    private fun executeChatCompletion(
        model: String,
        apiKey: String,
        requestDto: OpenAiChatRequestDto
    ): Result<String> {
        val url = "${config.baseUrl.trimEnd('/')}/chat/completions"
        val jsonPayload = json.encodeToString(requestDto)
        val body = jsonPayload.toRequestBody(jsonMediaType)

        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)

        config.extraHeaders.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }

        return try {
            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val retryAfter = response.header("Retry-After")
                    return Result.failure(parseError(response.code, responseBody, model, retryAfter))
                }

                val parsed = json.decodeFromString<OpenAiChatResponseDto>(responseBody)
                val choice = parsed.choices?.firstOrNull()
                    ?: return Result.failure(ProviderError.Generic(displayName, "Ответ не содержит данных"))

                val content = choice.message?.content?.trim().orEmpty()
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
            errorBody?.let { json.decodeFromString<OpenAiErrorEnvelopeDto>(it) }
        }.getOrNull()

        val message = parsed?.error?.message ?: errorBody.orEmpty()
        val errorCode = parsed?.error?.code.orEmpty()
        val errorType = parsed?.error?.type.orEmpty()

        if (code == 404 || errorCode == "model_not_found" || message.contains("does not exist", ignoreCase = true) ||
            message.contains("not found", ignoreCase = true) || message.contains("deprecated", ignoreCase = true)) {
            return ProviderError.ModelNotFound(displayName, model)
        }

        if (code == 429 || errorCode == "rate_limit_exceeded") {
            val seconds = retryAfterHeader?.toLongOrNull()
            if (seconds != null) {
                rateLimiter.recordRetryAfter(id, seconds)
            }
            return ProviderError.RateLimitExceeded(displayName, seconds)
        }

        if (code == 401 || code == 403 || errorCode == "invalid_api_key" || message.contains("api key", ignoreCase = true)) {
            return ProviderError.InvalidKey(displayName)
        }

        if (code in 500..599) {
            return ProviderError.ServerError(displayName, code)
        }

        if (code == 400 && (message.contains("moderation", ignoreCase = true) || message.contains("safety", ignoreCase = true))) {
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
