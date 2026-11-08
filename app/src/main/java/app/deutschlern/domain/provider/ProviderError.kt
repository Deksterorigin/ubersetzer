package app.deutschlern.domain.provider

import app.deutschlern.R
import app.deutschlern.util.AppStrings

sealed class ProviderError : Exception() {

    /** Resolved on every read, so a message shown after a language switch uses the new language. */
    abstract val userMessage: String

    override val message: String
        get() = userMessage

    data class InvalidKey(val providerName: String) : ProviderError() {
        override val userMessage get() = AppStrings.get(R.string.err_provider_invalid_key, providerName)
    }

    data class ModelNotFound(val providerName: String, val model: String) : ProviderError() {
        override val userMessage get() = AppStrings.get(R.string.err_provider_model_not_found, model)
    }

    data class RateLimitExceeded(val providerName: String, val retryAfterSeconds: Long? = null) : ProviderError() {
        override val userMessage
            get() = if (retryAfterSeconds != null && retryAfterSeconds > 0) {
                AppStrings.get(R.string.err_provider_rate_limit_retry, retryAfterSeconds)
            } else {
                AppStrings.get(R.string.err_provider_rate_limit, providerName)
            }
    }

    data class ServerError(val providerName: String, val code: Int) : ProviderError() {
        override val userMessage get() = AppStrings.get(R.string.err_provider_server, providerName, code)
    }

    data class Timeout(val providerName: String) : ProviderError() {
        override val userMessage get() = AppStrings.get(R.string.err_provider_timeout, providerName)
    }

    data object Offline : ProviderError() {
        private fun readResolve(): Any = Offline
        override val userMessage get() = AppStrings.get(R.string.err_provider_offline)
    }

    data class MalformedJson(val providerName: String) : ProviderError() {
        override val userMessage get() = AppStrings.get(R.string.err_provider_malformed, providerName)
    }

    data class SafetyBlocked(val providerName: String) : ProviderError() {
        override val userMessage get() = AppStrings.get(R.string.err_provider_safety, providerName)
    }

    data class NoKeyConfigured(val providerName: String) : ProviderError() {
        override val userMessage get() = AppStrings.get(R.string.err_provider_no_key, providerName)
    }

    data object NoProvidersConfigured : ProviderError() {
        private fun readResolve(): Any = NoProvidersConfigured
        override val userMessage get() = AppStrings.get(R.string.err_provider_none_configured)
    }

    data class Generic(val providerName: String, val details: String) : ProviderError() {
        override val userMessage get() = AppStrings.get(R.string.err_provider_generic, providerName, details)
    }

    /**
     * Determines whether this error should automatically trigger fallback to the next provider.
     * 401/403 (InvalidKey) strictly does NOT trigger fallback per requirement.
     */
    val triggersFallback: Boolean
        get() = when (this) {
            is InvalidKey -> false
            is NoKeyConfigured -> false
            is NoProvidersConfigured -> false
            is ModelNotFound -> true
            is RateLimitExceeded -> true
            is ServerError -> true
            is Timeout -> true
            is MalformedJson -> true
            is SafetyBlocked -> true
            is Offline -> true
            is Generic -> true
        }
}
