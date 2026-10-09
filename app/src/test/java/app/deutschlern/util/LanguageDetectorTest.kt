package app.deutschlern.util

import app.deutschlern.domain.model.TranslationDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageDetectorTest {

    @Test
    fun detectLanguage_pureGerman_returnsDeRu() {
        assertEquals(TranslationDirection.DE_RU, LanguageDetector.detectLanguage("Guten Morgen"))
        assertEquals(TranslationDirection.DE_RU, LanguageDetector.detectLanguage("Das ist ein einfaches deutsches Haus."))
    }

    @Test
    fun detectLanguage_germanWithUmlautsAndEszett_returnsDeRu() {
        assertEquals(TranslationDirection.DE_RU, LanguageDetector.detectLanguage("Überraschung"))
        assertEquals(TranslationDirection.DE_RU, LanguageDetector.detectLanguage("schöne Grüße"))
        assertEquals(TranslationDirection.DE_RU, LanguageDetector.detectLanguage("Fußgängerzone"))
        assertEquals(TranslationDirection.DE_RU, LanguageDetector.detectLanguage("Ärzte und Apotheken"))
        assertEquals(TranslationDirection.DE_RU, LanguageDetector.detectLanguage("Großmütter"))
        // Capital Eszett 'ẞ' (U+1E9E) in all-caps German
        assertEquals(TranslationDirection.DE_RU, LanguageDetector.detectLanguage("GROẞE VERÄNDERUNG"))
    }

    @Test
    fun detectLanguage_pureRussian_returnsRuDe() {
        assertEquals(TranslationDirection.RU_DE, LanguageDetector.detectLanguage("Привет"))
        assertEquals(TranslationDirection.RU_DE, LanguageDetector.detectLanguage("Как дела?"))
        assertEquals(TranslationDirection.RU_DE, LanguageDetector.detectLanguage("Сегодня отличная солнечная погода."))
    }

    @Test
    fun detectLanguage_russianWithYoAndSpecialLetters_returnsRuDe() {
        assertEquals(TranslationDirection.RU_DE, LanguageDetector.detectLanguage("Ещё один тёплый день"))
        assertEquals(TranslationDirection.RU_DE, LanguageDetector.detectLanguage("Подъём на гору"))
    }

    @Test
    fun detectLanguage_mixedText_favorsDominantScript() {
        // 2 Russian words, 1 German word -> Russian dominant
        assertEquals(TranslationDirection.RU_DE, LanguageDetector.detectLanguage("Привет Berlin мир"))

        // 3 German words, 1 Russian word -> German dominant
        assertEquals(TranslationDirection.DE_RU, LanguageDetector.detectLanguage("Guten Tag Welt Москва"))
    }

    @Test
    fun detectLanguage_tieBreak_matchesFirstDetectedScript() {
        // "Berlin" (6 Latin) and "Москва" (6 Cyrillic)
        assertEquals(TranslationDirection.DE_RU, LanguageDetector.detectLanguage("Berlin Москва"))
        assertEquals(TranslationDirection.RU_DE, LanguageDetector.detectLanguage("Москва Berlin"))
    }

    @Test
    fun detectLanguage_emptyOrBlank_defaultsToDeRu() {
        assertEquals(TranslationDirection.DE_RU, LanguageDetector.detectLanguage(""))
        assertEquals(TranslationDirection.DE_RU, LanguageDetector.detectLanguage("   \n\t  "))
        assertEquals(TranslationDirection.DE_RU, LanguageDetector.detectLanguage(null))
    }

    @Test
    fun detectLanguage_numbersAndPunctuationOnly_defaultsToDeRu() {
        assertEquals(TranslationDirection.DE_RU, LanguageDetector.detectLanguage("1234567890"))
        assertEquals(TranslationDirection.DE_RU, LanguageDetector.detectLanguage("!@#$%^&*()_+-=[]{}|;':,./<>?"))
        assertEquals(TranslationDirection.DE_RU, LanguageDetector.detectLanguage("   123 --- ???   "))
    }
}
