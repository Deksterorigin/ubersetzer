package app.deutschlern.domain.model

enum class TranslationDirection(
    val sourceCode: String,
    val targetCode: String,
    val sourceDisplayName: String,
    val targetDisplayName: String
) {
    DE_RU("de", "ru", "Deutsch", "Русский"),
    RU_DE("ru", "de", "Русский", "Deutsch"),
    DE_EN("de", "en", "Deutsch", "English"),
    EN_DE("en", "de", "English", "Deutsch"),
    DE_UK("de", "uk", "Deutsch", "Українська"),
    UK_DE("uk", "de", "Українська", "Deutsch"),
    DE_AR("de", "ar", "Deutsch", "العربية"),
    AR_DE("ar", "de", "العربية", "Deutsch"),
    DE_FA("de", "fa", "Deutsch", "دری"),
    FA_DE("fa", "de", "دری", "Deutsch"),
    DE_RO("de", "ro", "Deutsch", "Română"),
    RO_DE("ro", "de", "Română", "Deutsch");

    fun opposite(): TranslationDirection = when (this) {
        DE_RU -> RU_DE
        RU_DE -> DE_RU
        DE_EN -> EN_DE
        EN_DE -> DE_EN
        DE_UK -> UK_DE
        UK_DE -> DE_UK
        DE_AR -> AR_DE
        AR_DE -> DE_AR
        DE_FA -> FA_DE
        FA_DE -> DE_FA
        DE_RO -> RO_DE
        RO_DE -> DE_RO
    }

    val isFromGerman: Boolean
        get() = sourceCode == "de"

    val partnerLanguage: PartnerLanguage
        get() = when (this) {
            DE_RU, RU_DE -> PartnerLanguage.RUSSIAN
            DE_EN, EN_DE -> PartnerLanguage.ENGLISH
            DE_UK, UK_DE -> PartnerLanguage.UKRAINIAN
            DE_AR, AR_DE -> PartnerLanguage.ARABIC
            DE_FA, FA_DE -> PartnerLanguage.DARI
            DE_RO, RO_DE -> PartnerLanguage.ROMANIAN
        }

    companion object {
        fun getDirection(partner: PartnerLanguage, fromGerman: Boolean): TranslationDirection {
            return when (partner) {
                PartnerLanguage.RUSSIAN -> if (fromGerman) DE_RU else RU_DE
                PartnerLanguage.ENGLISH -> if (fromGerman) DE_EN else EN_DE
                PartnerLanguage.UKRAINIAN -> if (fromGerman) DE_UK else UK_DE
                PartnerLanguage.ARABIC -> if (fromGerman) DE_AR else AR_DE
                PartnerLanguage.DARI -> if (fromGerman) DE_FA else FA_DE
                PartnerLanguage.ROMANIAN -> if (fromGerman) DE_RO else RO_DE
            }
        }
    }
}
