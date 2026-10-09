package app.deutschlern.domain.model

enum class PartnerLanguage(
    val code: String,
    val displayName: String,
    val isRtl: Boolean = false,
    val bcp47Tag: String = code
) {
    RUSSIAN(code = "ru", displayName = "Русский", isRtl = false, bcp47Tag = "ru-RU"),
    ENGLISH(code = "en", displayName = "English", isRtl = false, bcp47Tag = "en-US"),
    UKRAINIAN(code = "uk", displayName = "Українська", isRtl = false, bcp47Tag = "uk-UA"),
    ARABIC(code = "ar", displayName = "العربية", isRtl = true, bcp47Tag = "ar-SA"),
    DARI(code = "fa", displayName = "دری (Дари)", isRtl = true, bcp47Tag = "fa-AF"),
    ROMANIAN(code = "ro", displayName = "Română", isRtl = false, bcp47Tag = "ro-RO");

    companion object {
        fun fromCode(code: String): PartnerLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: RUSSIAN
        }
    }
}
