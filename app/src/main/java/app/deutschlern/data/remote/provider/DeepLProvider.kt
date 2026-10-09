package app.deutschlern.data.remote.provider

import app.deutschlern.data.local.security.SecureKeyStorage
import app.deutschlern.data.remote.dto.DeepLTranslateRequestDto
import app.deutschlern.data.remote.dto.DeepLTranslateResponseDto
import app.deutschlern.data.remote.dto.DeepLUsageResponseDto
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.WordTranslation
import app.deutschlern.domain.provider.ProviderCapabilities
import app.deutschlern.domain.provider.ProviderError
import app.deutschlern.domain.provider.RateLimiter
import app.deutschlern.domain.provider.TranslationProvider
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

data class DeepLUsage(
    val characterCount: Long,
    val characterLimit: Long
)

@Singleton
class DeepLProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val secureKeyStorage: SecureKeyStorage,
    private val rateLimiter: RateLimiter
) : TranslationProvider {

    override val id: String = "deepl"
    override val displayName: String = "DeepL"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsWordMode = false,
        supportsTextMode = true,
        isOffline = false
    )

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun getBaseUrl(apiKey: String): String {
        return if (apiKey.endsWith(":fx", ignoreCase = true)) {
            "https://api-free.deepl.com/v2"
        } else {
            "https://api.deepl.com/v2"
        }
    }

    override suspend fun translateWord(
        word: String,
        direction: TranslationDirection,
        context: String?,
        model: String
    ): Result<WordTranslation> {
        return Result.failure(
            ProviderError.Generic(displayName, "DeepL доступен только для перевода текстов.")
        )
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

        val baseUrl = getBaseUrl(apiKey)
        val sourceCode = direction.sourceCode.uppercase()
        val targetCode = direction.targetCode.uppercase()

        if (sourceCode == "FA" || targetCode == "FA") {
            return@withContext Result.failure(
                ProviderError.Generic(displayName, "Язык Дари не поддерживается сервисом DeepL.")
            )
        }

        val sourceLang = sourceCode
        val targetLang = when (targetCode) {
            "EN" -> "EN-US"
            else -> targetCode
        }

        val requestDto = DeepLTranslateRequestDto(
            text = listOf(text),
            sourceLang = sourceLang,
            targetLang = targetLang
        )

        val body = json.encodeToString(requestDto).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/translate")
            .addHeader("Authorization", "DeepL-Auth-Key $apiKey")
            .post(body)
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val retryAfter = response.header("Retry-After")
                    return@withContext Result.failure(parseError(response.code, responseBody, retryAfter))
                }

                val parsed = json.decodeFromString<DeepLTranslateResponseDto>(responseBody)
                val translated = parsed.translations?.firstOrNull()?.text
                if (translated.isNullOrBlank()) {
                    Result.failure(ProviderError.Generic(displayName, "Пустой ответ от DeepL"))
                } else {
                    Result.success(translated)
                }
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    override suspend fun testConnection(model: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = secureKeyStorage.getKey(id)
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(ProviderError.NoKeyConfigured(displayName))
        }

        val usageResult = getUsage()
        if (usageResult.isSuccess) {
            Result.success("Работает")
        } else {
            Result.failure(usageResult.exceptionOrNull() ?: ProviderError.Generic(displayName, "Ошибка соединения"))
        }
    }

    suspend fun getUsage(): Result<DeepLUsage> = withContext(Dispatchers.IO) {
        val apiKey = secureKeyStorage.getKey(id)
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(ProviderError.NoKeyConfigured(displayName))
        }

        val baseUrl = getBaseUrl(apiKey)
        val request = Request.Builder()
            .url("$baseUrl/usage")
            .addHeader("Authorization", "DeepL-Auth-Key $apiKey")
            .get()
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val retryAfter = response.header("Retry-After")
                    return@withContext Result.failure(parseError(response.code, responseBody, retryAfter))
                }

                val parsed = json.decodeFromString<DeepLUsageResponseDto>(responseBody)
                Result.success(DeepLUsage(parsed.characterCount, parsed.characterLimit))
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    override suspend fun listModels(): Result<List<String>> {
        return Result.success(listOf("default"))
    }

    private fun parseError(code: Int, errorBody: String?, retryAfterHeader: String?): ProviderError {
        if (code == 401 || code == 403) {
            return ProviderError.InvalidKey(displayName)
        }

        if (code == 429 || code == 456) { // 456 is DeepL Quota Exceeded
            val seconds = retryAfterHeader?.toLongOrNull()
            if (seconds != null) {
                rateLimiter.recordRetryAfter(id, seconds)
            }
            return ProviderError.RateLimitExceeded(displayName, seconds)
        }

        if (code in 500..599) {
            return ProviderError.ServerError(displayName, code)
        }

        return ProviderError.Generic(displayName, "HTTP $code: ${errorBody?.take(150)}")
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
