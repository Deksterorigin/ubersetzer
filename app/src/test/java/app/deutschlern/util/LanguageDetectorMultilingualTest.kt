package app.deutschlern.util

import app.deutschlern.domain.model.PartnerLanguage
import app.deutschlern.domain.model.TranslationDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageDetectorMultilingualTest {

    @Test
    fun detectLanguage_englishPartner() {
        assertEquals(
            TranslationDirection.EN_DE,
            LanguageDetector.detectLanguage("Hello, how are you today?", PartnerLanguage.ENGLISH)
        )
        assertEquals(
            TranslationDirection.DE_EN,
            LanguageDetector.detectLanguage("Guten Tag, wie geht es Ihnen?", PartnerLanguage.ENGLISH)
        )
        assertEquals(
            TranslationDirection.DE_EN,
            LanguageDetector.detectLanguage("Überraschung", PartnerLanguage.ENGLISH)
        )
    }

    @Test
    fun detectLanguage_ukrainianPartner() {
        assertEquals(
            TranslationDirection.UK_DE,
            LanguageDetector.detectLanguage("Доброго ранку, як справи?", PartnerLanguage.UKRAINIAN)
        )
        assertEquals(
            TranslationDirection.UK_DE,
            LanguageDetector.detectLanguage("Дякую дуже за допомогу", PartnerLanguage.UKRAINIAN)
        )
        assertEquals(
            TranslationDirection.DE_UK,
            LanguageDetector.detectLanguage("Vielen Dank für Ihre Hilfe", PartnerLanguage.UKRAINIAN)
        )
    }

    @Test
    fun detectLanguage_arabicPartner() {
        // Arabic script detects AR_DE
        assertEquals(
            TranslationDirection.AR_DE,
            LanguageDetector.detectLanguage("مرحبا كيف حالك", PartnerLanguage.ARABIC)
        )
        assertEquals(
            TranslationDirection.AR_DE,
            LanguageDetector.detectLanguage("شكرا جزيلا", PartnerLanguage.ARABIC)
        )
        // German input with Arabic partner detects DE_AR
        assertEquals(
            TranslationDirection.DE_AR,
            LanguageDetector.detectLanguage("Guten Morgen mein Freund", PartnerLanguage.ARABIC)
        )
    }

    @Test
    fun detectLanguage_dariPartner() {
        // Dari/Persian script detects FA_DE
        assertEquals(
            TranslationDirection.FA_DE,
            LanguageDetector.detectLanguage("سلام دوست عزیز", PartnerLanguage.DARI)
        )
        // German input detects DE_FA
        assertEquals(
            TranslationDirection.DE_FA,
            LanguageDetector.detectLanguage("Willkommen in Deutschland", PartnerLanguage.DARI)
        )
    }

    @Test
    fun detectLanguage_romanianPartner() {
        assertEquals(
            TranslationDirection.RO_DE,
            LanguageDetector.detectLanguage("Bună ziua, ce mai faci?", PartnerLanguage.ROMANIAN)
        )
        // Romanian diacritics
        assertEquals(
            TranslationDirection.RO_DE,
            LanguageDetector.detectLanguage("Mulțumesc foarte mult", PartnerLanguage.ROMANIAN)
        )
        assertEquals(
            TranslationDirection.DE_RO,
            LanguageDetector.detectLanguage("Das ist eine schöne Überraschung", PartnerLanguage.ROMANIAN)
        )
    }

    @Test
    fun detectLanguage_blankOrEmpty_defaultsToGermanToPartner() {
        assertEquals(
            TranslationDirection.DE_EN,
            LanguageDetector.detectLanguage("", PartnerLanguage.ENGLISH)
        )
        assertEquals(
            TranslationDirection.DE_UK,
            LanguageDetector.detectLanguage("   ", PartnerLanguage.UKRAINIAN)
        )
        assertEquals(
            TranslationDirection.DE_AR,
            LanguageDetector.detectLanguage(null, PartnerLanguage.ARABIC)
        )
        assertEquals(
            TranslationDirection.DE_FA,
            LanguageDetector.detectLanguage("", PartnerLanguage.DARI)
        )
        assertEquals(
            TranslationDirection.DE_RO,
            LanguageDetector.detectLanguage("", PartnerLanguage.ROMANIAN)
        )
    }
}
