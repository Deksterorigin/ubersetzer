package app.deutschlern.domain.provider

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RateLimiterTest {

    private lateinit var rateLimiter: RateLimiter

    @Before
    fun setUp() {
        rateLimiter = RateLimiter()
    }

    @Test
    fun recordRetryAfter_setsRemainingCooldown() {
        rateLimiter.recordRetryAfter("mistral", 5)

        val remaining = rateLimiter.getRemainingCooldownSeconds("mistral")
        assertTrue(remaining in 4..5)
    }

    @Test
    fun recordRetryAfter_otherProviderUnaffected() {
        rateLimiter.recordRetryAfter("mistral", 10)

        val groqRemaining = rateLimiter.getRemainingCooldownSeconds("groq")
        assertEquals(0L, groqRemaining)
    }

    @Test
    fun clear_removesCooldown() {
        rateLimiter.recordRetryAfter("cerebras", 30)
        assertTrue(rateLimiter.getRemainingCooldownSeconds("cerebras") > 0)

        rateLimiter.clear("cerebras")
        assertEquals(0L, rateLimiter.getRemainingCooldownSeconds("cerebras"))
    }
}
