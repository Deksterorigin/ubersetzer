package app.deutschlern.domain.provider

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RateLimiter @Inject constructor() {

    private val mutexes = ConcurrentHashMap<String, Mutex>()
    private val lastRequestTimes = ConcurrentHashMap<String, Long>()
    private val retryAfterUntil = ConcurrentHashMap<String, Long>()

    private fun getMutex(providerId: String): Mutex =
        mutexes.computeIfAbsent(providerId) { Mutex() }

    /**
     * Waits until both the provider's min delay between requests and any active Retry-After expire.
     */
    suspend fun acquire(providerId: String, minDelayMs: Long = 0L) {
        val mutex = getMutex(providerId)
        mutex.withLock {
            val now = System.currentTimeMillis()

            // Check Retry-After block
            val retryUntil = retryAfterUntil[providerId] ?: 0L
            if (retryUntil > now) {
                val waitTime = retryUntil - now
                delay(waitTime)
            }

            // Check min delay between requests
            if (minDelayMs > 0) {
                val lastTime = lastRequestTimes[providerId] ?: 0L
                val elapsed = System.currentTimeMillis() - lastTime
                if (elapsed < minDelayMs) {
                    delay(minDelayMs - elapsed)
                }
            }

            lastRequestTimes[providerId] = System.currentTimeMillis()
        }
    }

    /**
     * Records a 429 Retry-After duration in seconds for this provider.
     */
    fun recordRetryAfter(providerId: String, seconds: Long) {
        val now = System.currentTimeMillis()
        val retryUntil = now + (seconds * 1000L)
        retryAfterUntil[providerId] = retryUntil
    }

    /**
     * Returns remaining cooldown in seconds, or 0 if not rate limited.
     */
    fun getRemainingCooldownSeconds(providerId: String): Long {
        val now = System.currentTimeMillis()
        val retryUntil = retryAfterUntil[providerId] ?: 0L
        return if (retryUntil > now) (retryUntil - now + 999) / 1000 else 0L
    }

    fun clear(providerId: String) {
        lastRequestTimes.remove(providerId)
        retryAfterUntil.remove(providerId)
    }
}
