package app.deutschlern.domain.provider

sealed class ProviderError(open val userMessage: String) : Exception(userMessage) {

    data class InvalidKey(val providerName: String) :
        ProviderError("Неверный API-ключ для $providerName. Проверьте ключ в настройках.")

    data class ModelNotFound(val providerName: String, val model: String) :
        ProviderError("Модель «$model» недоступна, выберите другую.")

    data class RateLimitExceeded(val providerName: String, val retryAfterSeconds: Long? = null) :
        ProviderError(
            if (retryAfterSeconds != null && retryAfterSeconds > 0)
                "Лимит исчерпан, повторите через $retryAfterSeconds сек."
            else
                "Превышен лимит запросов к $providerName. Подождите немного и повторите."
        )

    data class ServerError(val providerName: String, val code: Int) :
        ProviderError("Сервер $providerName временно недоступен (код $code). Попробуйте снова через минуту.")

    data class Timeout(val providerName: String) :
        ProviderError("Превышено время ожидания ответа от $providerName.")

    data object Offline :
        ProviderError("Нет подключения к интернету. Проверьте сеть и повторите попытку.")

    data class MalformedJson(val providerName: String) :
        ProviderError("Не удалось обработать ответ словаря от $providerName.")

    data class SafetyBlocked(val providerName: String) :
        ProviderError("Запрос заблокирован фильтром безопасности содержимого $providerName.")

    data class NoKeyConfigured(val providerName: String) :
        ProviderError("API-ключ для $providerName не настроен. Добавьте его в настройках.")

    data object NoProvidersConfigured :
        ProviderError("Не настроен ни один провайдер для перевода. Добавьте API-ключ в настройках.")

    data class Generic(val providerName: String, val details: String) :
        ProviderError("Ошибка при обращении к $providerName: $details")

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
