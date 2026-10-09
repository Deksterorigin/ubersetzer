package app.deutschlern.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.Response

class GeminiAuthInterceptor(
    private val apiKeyProvider: () -> String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val apiKey = apiKeyProvider().trim()

        val request = if (apiKey.isNotEmpty()) {
            original.newBuilder()
                .header("x-goog-api-key", apiKey)
                .build()
        } else {
            original
        }

        return chain.proceed(request)
    }
}
