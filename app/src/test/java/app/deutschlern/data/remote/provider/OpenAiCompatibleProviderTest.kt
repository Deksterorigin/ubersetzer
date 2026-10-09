package app.deutschlern.data.remote.provider

import app.deutschlern.data.local.security.SecureKeyStorage
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.provider.JsonMode
import app.deutschlern.domain.provider.JsonModeCapabilityStore
import app.deutschlern.domain.provider.ProviderConfig
import app.deutschlern.domain.provider.ProviderError
import app.deutschlern.domain.provider.RateLimiter
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OpenAiCompatibleProviderTest {

    private lateinit var mockWebServer: MockWebServer
    private val secureKeyStorage = mockk<SecureKeyStorage>()
    private val jsonModeStore = mockk<JsonModeCapabilityStore>()
    private val rateLimiter = RateLimiter()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    private val okHttpClient = OkHttpClient()

    private lateinit var provider: OpenAiCompatibleProvider

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        coEvery { secureKeyStorage.getKey(any()) } returns "sk-test-valid-key"

        val config = ProviderConfig(
            id = "groq",
            name = "Groq",
            baseUrl = mockWebServer.url("/v1").toString().removeSuffix("/"),
            defaultModel = "llama-3.3-70b-versatile",
            rateLimitDocsUrl = "https://groq.com",
            freeTierNote = "Free LPU"
        )

        provider = OpenAiCompatibleProvider(
            config = config,
            okHttpClient = okHttpClient,
            json = json,
            secureKeyStorage = secureKeyStorage,
            rateLimiter = rateLimiter,
            jsonModeCapabilityStore = jsonModeStore
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun translateWord_jsonSchemaSuccess_parsesWordCorrectly() = runTest {
        coEvery { jsonModeStore.getMode(any(), any()) } returns JsonMode.JSON_SCHEMA

        val mockResponseBody = """
        {
          "id": "chatcmpl-123",
          "choices": [
            {
              "message": {
                "role": "assistant",
                "content": "{\"lemma\": \"Haus\", \"article\": \"das\", \"pos\": \"noun\", \"meanings\": [{\"translation\": \"дом\", \"example_de\": \"Schönes Haus.\", \"example_ru\": \"Красивый дом.\"}]}"
              }
            }
          ]
        }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(mockResponseBody))

        val result = provider.translateWord("Haus", TranslationDirection.DE_RU, null, "llama-3.3-70b-versatile")

        assertTrue(result.isSuccess)
        val word = result.getOrThrow()
        assertEquals("Haus", word.lemma)
        assertEquals("das", word.article)

        val recordedRequest = mockWebServer.takeRequest()
        assertTrue(recordedRequest.body.readUtf8().contains("json_schema"))
    }

    @Test
    fun translateWord_http400OnJsonSchema_downgradesToJsonObjectAndSucceeds() = runTest {
        var currentMode = JsonMode.JSON_SCHEMA
        coEvery { jsonModeStore.getMode(any(), any()) } answers { currentMode }
        coEvery { jsonModeStore.downgradeMode(any(), any()) } answers {
            currentMode = JsonMode.JSON_OBJECT
            JsonMode.JSON_OBJECT
        }

        // 1st request: returns 400 Bad Request indicating response_format schema unsupported
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"error": {"message": "response_format json_schema is not supported", "type": "invalid_request_error"}}""")
        )

        // 2nd request (with json_object): succeeds
        val mockResponseBody = """
        {
          "choices": [
            {
              "message": {
                "role": "assistant",
                "content": "{\"lemma\": \"Baum\", \"article\": \"der\", \"pos\": \"noun\", \"meanings\": [{\"translation\": \"дерево\", \"example_de\": \"Ein hoher Baum.\", \"example_ru\": \"Высокое дерево.\"}]}"
              }
            }
          ]
        }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(mockResponseBody))

        val result = provider.translateWord("Baum", TranslationDirection.DE_RU, null, "llama-3.3-70b-versatile")

        assertTrue(result.isSuccess)
        assertEquals("Baum", result.getOrThrow().lemma)

        // Verify two requests were executed
        val req1 = mockWebServer.takeRequest()
        assertTrue(req1.body.readUtf8().contains("json_schema"))

        val req2 = mockWebServer.takeRequest()
        assertTrue(req2.body.readUtf8().contains("json_object"))
    }

    @Test
    fun translateWord_http404_returnsModelNotFound() = runTest {
        coEvery { jsonModeStore.getMode(any(), any()) } returns JsonMode.JSON_SCHEMA

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error": {"message": "The model 'deprecated-model' does not exist", "code": "model_not_found"}}""")
        )

        val result = provider.translateWord("Haus", TranslationDirection.DE_RU, null, "deprecated-model")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is ProviderError.ModelNotFound)
        assertTrue((error as ProviderError.ModelNotFound).triggersFallback)
    }

    @Test
    fun translateWord_http429_returnsRateLimitExceededAndRecordsRetryAfter() = runTest {
        coEvery { jsonModeStore.getMode(any(), any()) } returns JsonMode.JSON_SCHEMA

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "12")
                .setBody("""{"error": {"message": "Rate limit exceeded", "code": "rate_limit_exceeded"}}""")
        )

        val result = provider.translateWord("Haus", TranslationDirection.DE_RU, null, "llama-3.3-70b-versatile")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is ProviderError.RateLimitExceeded)
        assertEquals(12L, (error as ProviderError.RateLimitExceeded).retryAfterSeconds)
        assertTrue(error.triggersFallback)
        assertTrue(rateLimiter.getRemainingCooldownSeconds("groq") > 0)
    }

    @Test
    fun translateWord_http401_returnsInvalidKeyWithoutFallbackTrigger() = runTest {
        coEvery { jsonModeStore.getMode(any(), any()) } returns JsonMode.JSON_SCHEMA

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error": {"message": "Incorrect API key provided", "code": "invalid_api_key"}}""")
        )

        val result = provider.translateWord("Haus", TranslationDirection.DE_RU, null, "llama-3.3-70b-versatile")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is ProviderError.InvalidKey)
        // 401 must NOT trigger fallback per requirement
        assertEquals(false, (error as ProviderError.InvalidKey).triggersFallback)
    }
}
