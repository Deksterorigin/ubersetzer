package app.deutschlern.domain.model

sealed class TranslationError(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    class NoInternet(cause: Throwable? = null) : TranslationError(
        message = "Нет подключения к интернету. Проверьте сеть и повторите попытку.",
        cause = cause
    )

    class QuotaExceeded(cause: Throwable? = null) : TranslationError(
        message = "Превышен лимит запросов к переводчику. Подождите немного и повторите.",
        cause = cause
    )

    class ServerUnavailable(val code: Int = 500, cause: Throwable? = null) : TranslationError(
        message = "Сервис перевода временно недоступен. Попробуйте снова через минуту.",
        cause = cause
    )

    class SafetyBlock(val reason: String? = null) : TranslationError(
        message = "Запрос не может быть обработан из-за ограничений безопасности содержимого."
    )

    class EmptyResponse(cause: Throwable? = null) : TranslationError(
        message = "Не удалось получить перевод. Попробуйте повторить запрос.",
        cause = cause
    )

    class InvalidApiKey(cause: Throwable? = null) : TranslationError(
        message = "Недействительный или отсутствующий API-ключ Gemini. Укажите GEMINI_API_KEY в local.properties.",
        cause = cause
    )

    class Generic(message: String, cause: Throwable? = null) : TranslationError(
        message = message,
        cause = cause
    )
}
