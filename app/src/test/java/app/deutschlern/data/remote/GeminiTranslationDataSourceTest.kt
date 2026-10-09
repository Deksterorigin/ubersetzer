package app.deutschlern.data.remote

import app.deutschlern.data.remote.api.GeminiApiService
import app.deutschlern.data.remote.datasource.GeminiTranslationDataSource
import app.deutschlern.data.remote.dto.GeminiCandidateDto
import app.deutschlern.data.remote.dto.GeminiContentDto
import app.deutschlern.data.remote.dto.GeminiPartDto
import app.deutschlern.data.remote.dto.GeminiPromptFeedbackDto
import app.deutschlern.data.remote.dto.GeminiResponseDto
import app.deutschlern.domain.model.TranslationDirection
import app.deutschlern.domain.model.TranslationError
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import app.deutschlern.data.remote.dto.GeminiGenerationConfigDto
import app.deutschlern.data.remote.dto.GeminiRequestDto
import app.deutschlern.data.remote.dto.GeminiThinkingConfigDto
import app.deutschlern.di.NetworkModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class GeminiTranslationDataSourceTest {

    private val apiService = mockk<GeminiApiService>()
    private val json = Json { ignoreUnknownKeys = true }
    private val modelName = "gemini-3.5-flash"
    private val dataSource = GeminiTranslationDataSource(
        apiService = apiService,
        modelName = modelName,
        json = json
    )

    @Test
    fun translateText_successfulResponse_returnsTranslatedText() = runTest {
        val fakeResponse = GeminiResponseDto(
            candidates = listOf(
                GeminiCandidateDto(
                    content = GeminiContentDto(
                        parts = listOf(
                            GeminiPartDto(text = "Привет, мир!")
                        )
                    )
                )
            )
        )

        coEvery { apiService.generateContent(modelName, any()) } returns Response.success(fakeResponse)

        val result = dataSource.translateText("Hallo, Welt!", TranslationDirection.DE_RU)
        assertEquals("Привет, мир!", result)
    }

    @Test
    fun translateText_responseWithThinking_filtersOutThoughts() = runTest {
        val fakeResponse = GeminiResponseDto(
            candidates = listOf(
                GeminiCandidateDto(
                    content = GeminiContentDto(
                        parts = listOf(
                            GeminiPartDto(text = "Internal model reasoning...", thought = true),
                            GeminiPartDto(text = "Guten Tag!", thought = false)
                        )
                    )
                )
            )
        )

        coEvery { apiService.generateContent(modelName, any()) } returns Response.success(fakeResponse)

        val result = dataSource.translateText("Добрый день!", TranslationDirection.RU_DE)
        assertEquals("Guten Tag!", result)
    }

    @Test
    fun translateText_networkException_throwsNoInternet() = runTest {
        coEvery { apiService.generateContent(modelName, any()) } throws IOException("Connection reset")

        assertThrows(TranslationError.NoInternet::class.java) {
            kotlinx.coroutines.runBlocking {
                dataSource.translateText("Hallo", TranslationDirection.DE_RU)
            }
        }
    }

    @Test
    fun translateText_http429_throwsQuotaExceeded() = runTest {
        val errorBody = "Quota exceeded".toResponseBody("application/json".toMediaType())
        coEvery { apiService.generateContent(modelName, any()) } returns Response.error(429, errorBody)

        assertThrows(TranslationError.QuotaExceeded::class.java) {
            kotlinx.coroutines.runBlocking {
                dataSource.translateText("Hallo", TranslationDirection.DE_RU)
            }
        }
    }

    @Test
    fun translateText_http503_throwsServerUnavailable() = runTest {
        val errorBody = "Service Unavailable".toResponseBody("application/json".toMediaType())
        coEvery { apiService.generateContent(modelName, any()) } returns Response.error(503, errorBody)

        val error = assertThrows(TranslationError.ServerUnavailable::class.java) {
            kotlinx.coroutines.runBlocking {
                dataSource.translateText("Hallo", TranslationDirection.DE_RU)
            }
        }
        assertEquals(503, error.code)
    }

    @Test
    fun translateText_http400InvalidApiKey_throwsInvalidApiKey() = runTest {
        val errorJson = """{"error":{"code":400,"message":"API_KEY_INVALID: API key not valid","status":"INVALID_ARGUMENT"}}"""
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())
        coEvery { apiService.generateContent(modelName, any()) } returns Response.error(400, errorBody)

        assertThrows(TranslationError.InvalidApiKey::class.java) {
            kotlinx.coroutines.runBlocking {
                dataSource.translateText("Hallo", TranslationDirection.DE_RU)
            }
        }
    }

    @Test
    fun translateText_safetyBlock_throwsSafetyBlock() = runTest {
        val fakeResponse = GeminiResponseDto(
            promptFeedback = GeminiPromptFeedbackDto(blockReason = "SAFETY")
        )
        coEvery { apiService.generateContent(modelName, any()) } returns Response.success(fakeResponse)

        assertThrows(TranslationError.SafetyBlock::class.java) {
            kotlinx.coroutines.runBlocking {
                dataSource.translateText("Hazardous text", TranslationDirection.DE_RU)
            }
        }
    }

    @Test
    fun translateText_emptyCandidates_throwsEmptyResponse() = runTest {
        val fakeResponse = GeminiResponseDto(candidates = emptyList())
        coEvery { apiService.generateContent(modelName, any()) } returns Response.success(fakeResponse)

        assertThrows(TranslationError.EmptyResponse::class.java) {
            kotlinx.coroutines.runBlocking {
                dataSource.translateText("Test", TranslationDirection.DE_RU)
            }
        }
    }

    @Test
    fun translateText_http401Unauthorized_throwsInvalidApiKey() = runTest {
        val errorJson = """{"error":{"code":401,"message":"Request had invalid authentication credentials.","status":"UNAUTHENTICATED"}}"""
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())
        coEvery { apiService.generateContent(modelName, any()) } returns Response.error(401, errorBody)

        assertThrows(TranslationError.InvalidApiKey::class.java) {
            kotlinx.coroutines.runBlocking {
                dataSource.translateText("Hallo", TranslationDirection.DE_RU)
            }
        }
    }

    @Test
    fun translateText_http403PermissionDenied_throwsInvalidApiKey() = runTest {
        val errorJson = """{"error":{"code":403,"message":"The caller does not have permission","status":"PERMISSION_DENIED"}}"""
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())
        coEvery { apiService.generateContent(modelName, any()) } returns Response.error(403, errorBody)

        assertThrows(TranslationError.InvalidApiKey::class.java) {
            kotlinx.coroutines.runBlocking {
                dataSource.translateText("Hallo", TranslationDirection.DE_RU)
            }
        }
    }

    @Test
    fun requestSerialization_encodesThinkingBudgetZeroAndTemperature() {
        val json = NetworkModule.provideJson()
        val request = GeminiRequestDto(
            contents = listOf(
                GeminiContentDto(
                    parts = listOf(GeminiPartDto(text = "Hallo")),
                    role = "user"
                )
            ),
            systemInstruction = GeminiContentDto(
                parts = listOf(GeminiPartDto(text = "System prompt"))
            ),
            generationConfig = GeminiGenerationConfigDto(
                temperature = 0.2,
                thinkingConfig = GeminiThinkingConfigDto(thinkingBudget = 0)
            )
        )

        val serialized = json.encodeToString(GeminiRequestDto.serializer(), request)

        // Verify thinkingBudget: 0 is explicitly present in serialized JSON
        assertTrue("Expected thinkingBudget in serialized JSON: $serialized", serialized.contains("\"thinkingBudget\":0"))
        // Verify temperature: 0.2 is present
        assertTrue("Expected temperature in serialized JSON: $serialized", serialized.contains("\"temperature\":0.2"))
        // Verify input text is present
        assertTrue("Expected text part in serialized JSON: $serialized", serialized.contains("\"text\":\"Hallo\""))
    }

    @Test
    fun translateWord_successfulJsonResponse_returnsDto() = runTest {
        val wordJson = """
        {
          "lemma": "Schloss",
          "article": "das",
          "pos": "noun",
          "plural": "Schlösser",
          "meanings": [
            {
              "translation": "замок",
              "example_de": "Das Schloss ist alt.",
              "example_ru": "Замок старый."
            }
          ]
        }
        """.trimIndent()

        val fakeResponse = GeminiResponseDto(
            candidates = listOf(
                GeminiCandidateDto(
                    content = GeminiContentDto(
                        parts = listOf(GeminiPartDto(text = wordJson))
                    )
                )
            )
        )

        coEvery { apiService.generateContent(modelName, any()) } returns Response.success(fakeResponse)

        val result = dataSource.translateWord("Schloss", TranslationDirection.DE_RU)
        assertEquals("Schloss", result.dto.lemma)
        assertEquals("das", result.dto.article)
        assertEquals(1, result.dto.meanings?.size)
    }

    @Test
    fun translateWord_wrappedInMarkdownFences_cleansAndReturnsDto() = runTest {
        val wordJson = """
        ```json
        {
          "lemma": "Haus",
          "article": "das",
          "pos": "noun",
          "meanings": []
        }
        ```
        """.trimIndent()

        val fakeResponse = GeminiResponseDto(
            candidates = listOf(
                GeminiCandidateDto(
                    content = GeminiContentDto(
                        parts = listOf(GeminiPartDto(text = wordJson))
                    )
                )
            )
        )

        coEvery { apiService.generateContent(modelName, any()) } returns Response.success(fakeResponse)

        val result = dataSource.translateWord("Haus", TranslationDirection.DE_RU)
        assertEquals("Haus", result.dto.lemma)
        assertEquals("das", result.dto.article)
    }

    @Test
    fun translateWord_withConversationalPreambleAndFences_cleansAndReturnsDto() = runTest {
        val wordJson = """
        Certainly, here is the dictionary entry:
        ```json
        {
          "lemma": "Buch",
          "article": "das",
          "pos": "noun",
          "meanings": []
        }
        ```
        Let me know if you need more details!
        """.trimIndent()

        val fakeResponse = GeminiResponseDto(
            candidates = listOf(
                GeminiCandidateDto(
                    content = GeminiContentDto(
                        parts = listOf(GeminiPartDto(text = wordJson))
                    )
                )
            )
        )

        coEvery { apiService.generateContent(modelName, any()) } returns Response.success(fakeResponse)

        val result = dataSource.translateWord("Buch", TranslationDirection.DE_RU)
        assertEquals("Buch", result.dto.lemma)
        assertEquals("das", result.dto.article)
    }

    @Test
    fun translateWord_firstAttemptMalformedJson_retriesOnceAndSucceeds() = runTest {
        val brokenJson = """{"lemma": "Schloss", "unclosed"""
        val validJson = """{"lemma": "Schloss", "pos": "noun", "meanings": []}"""

        val brokenResponse = GeminiResponseDto(
            candidates = listOf(
                GeminiCandidateDto(content = GeminiContentDto(parts = listOf(GeminiPartDto(text = brokenJson))))
            )
        )
        val validResponse = GeminiResponseDto(
            candidates = listOf(
                GeminiCandidateDto(content = GeminiContentDto(parts = listOf(GeminiPartDto(text = validJson))))
            )
        )

        coEvery { apiService.generateContent(modelName, any()) } returnsMany listOf(
            Response.success(brokenResponse),
            Response.success(validResponse)
        )

        val result = dataSource.translateWord("Schloss", TranslationDirection.DE_RU)
        assertEquals("Schloss", result.dto.lemma)
    }

    @Test
    fun translateWord_bothAttemptsMalformedJson_throwsGenericError() = runTest {
        val brokenJson = """{"lemma": "Schloss", "unclosed"""

        val brokenResponse = GeminiResponseDto(
            candidates = listOf(
                GeminiCandidateDto(content = GeminiContentDto(parts = listOf(GeminiPartDto(text = brokenJson))))
            )
        )

        coEvery { apiService.generateContent(modelName, any()) } returns Response.success(brokenResponse)

        assertThrows(TranslationError.Generic::class.java) {
            kotlinx.coroutines.runBlocking {
                dataSource.translateWord("Schloss", TranslationDirection.DE_RU)
            }
        }
    }
}

