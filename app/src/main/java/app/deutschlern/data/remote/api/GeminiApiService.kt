package app.deutschlern.data.remote.api

import app.deutschlern.data.remote.dto.GeminiRequestDto
import app.deutschlern.data.remote.dto.GeminiResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface GeminiApiService {

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Body request: GeminiRequestDto
    ): Response<GeminiResponseDto>
}
