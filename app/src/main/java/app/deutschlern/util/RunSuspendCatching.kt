package app.deutschlern.util

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] for suspending code: never swallows [CancellationException], otherwise a
 * cancelled coroutine (screen closed) would keep going and update state it no longer owns.
 */
inline fun <T> runSuspendCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
