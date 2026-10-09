package app.deutschlern.domain.model

enum class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val isRtl: Boolean = false,
    val bcp47Tag: String = code
) {
    SYSTEM(code = "", displayName = "По умолчанию", nativeName = "Системный (по умолчанию)", isRtl = false, bcp47Tag = ""),
    RUSSIAN(code = "ru", displayName = "Русский", nativeName = "Русский", isRtl = false, bcp47Tag = "ru"),
    ENGLISH(code = "en", displayName = "English", nativeName = "English", isRtl = false, bcp47Tag = "en"),
    GERMAN(code = "de", displayName = "Deutsch", nativeName = "Deutsch", isRtl = false, bcp47Tag = "de"),
    UKRAINIAN(code = "uk", displayName = "Українська", nativeName = "Українська", isRtl = false, bcp47Tag = "uk"),
    ARABIC(code = "ar", displayName = "العربية", nativeName = "العربية", isRtl = true, bcp47Tag = "ar"),
    DARI(code = "fa", displayName = "دری (Дари)", nativeName = "دری (Дари)", isRtl = true, bcp47Tag = "fa"),
    ROMANIAN(code = "ro", displayName = "Română", nativeName = "Română", isRtl = false, bcp47Tag = "ro");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            if (code.isNullOrBlank() || code.equals("system", ignoreCase = true)) return SYSTEM
            val trimmed = code.trim().lowercase()
            return entries.firstOrNull {
                it.code.equals(trimmed, ignoreCase = true) ||
                (it.bcp47Tag.isNotBlank() && it.bcp47Tag.equals(trimmed, ignoreCase = true)) ||
                (it.code.isNotBlank() && trimmed.startsWith(it.code + "-"))
            } ?: SYSTEM
        }
    }
}
