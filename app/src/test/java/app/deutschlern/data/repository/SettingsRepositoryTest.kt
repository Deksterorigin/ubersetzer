package app.deutschlern.data.repository

import app.deutschlern.domain.model.AppLanguage
import app.deutschlern.domain.repository.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {

    @Test
    fun defaultUserSettings_hasEmptyAppLanguageCode_andSystemDefault() {
        val settings = UserSettings()
        assertEquals("", settings.appLanguageCode)
        assertEquals(AppLanguage.SYSTEM, settings.selectedAppLanguage)
        assertFalse(settings.selectedAppLanguage.isRtl)
    }

    @Test
    fun userSettings_mapsAllSupportedLanguagesCorrectly() {
        val cases = mapOf(
            "" to AppLanguage.SYSTEM,
            "system" to AppLanguage.SYSTEM,
            "ru" to AppLanguage.RUSSIAN,
            "en" to AppLanguage.ENGLISH,
            "de" to AppLanguage.GERMAN,
            "uk" to AppLanguage.UKRAINIAN,
            "ar" to AppLanguage.ARABIC,
            "fa" to AppLanguage.DARI,
            "ro" to AppLanguage.ROMANIAN,
            "unknown" to AppLanguage.SYSTEM
        )

        cases.forEach { (code, expectedLang) ->
            val settings = UserSettings(appLanguageCode = code)
            assertEquals("Expected $expectedLang for code '$code'", expectedLang, settings.selectedAppLanguage)
        }
    }

    @Test
    fun appLanguage_rtlFlagsAreAccurate() {
        assertTrue(AppLanguage.ARABIC.isRtl)
        assertTrue(AppLanguage.DARI.isRtl)

        assertFalse(AppLanguage.SYSTEM.isRtl)
        assertFalse(AppLanguage.RUSSIAN.isRtl)
        assertFalse(AppLanguage.ENGLISH.isRtl)
        assertFalse(AppLanguage.GERMAN.isRtl)
        assertFalse(AppLanguage.UKRAINIAN.isRtl)
        assertFalse(AppLanguage.ROMANIAN.isRtl)
    }

    @Test
    fun appLanguage_fromCode_handlesVariations() {
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromCode("DE"))
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromCode(" de "))
        assertEquals(AppLanguage.DARI, AppLanguage.fromCode("fa-AF"))
        assertEquals(AppLanguage.ARABIC, AppLanguage.fromCode("ar-SA"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode("   "))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode("xyz"))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.fromCode("ru-RU"))
        assertEquals(AppLanguage.UKRAINIAN, AppLanguage.fromCode("uk-UA"))
    }

    @Test
    fun appLanguage_bcp47Tags_and_names_areValid() {
        AppLanguage.entries.forEach { lang ->
            assertTrue(lang.displayName.isNotBlank())
            assertTrue(lang.nativeName.isNotBlank())
            if (lang != AppLanguage.SYSTEM) {
                assertTrue(lang.code.isNotBlank())
                assertTrue(lang.bcp47Tag.isNotBlank())
            }
        }
        assertEquals("ar", AppLanguage.ARABIC.bcp47Tag)
        assertEquals("fa", AppLanguage.DARI.bcp47Tag)
        assertEquals("de", AppLanguage.GERMAN.bcp47Tag)
        assertEquals("ro", AppLanguage.ROMANIAN.bcp47Tag)
    }

    @Test
    fun defaultUserSettings_hasCorrectSttDefaults() {
        val settings = UserSettings()
        assertFalse(settings.sttPreferOnDevice)
        assertTrue(settings.sttAutoDetect)
        assertFalse(settings.sttTranslateImmediately)
    }

    @Test
    fun userSettings_preservesCustomSttValues() {
        val settings = UserSettings(
            sttPreferOnDevice = true,
            sttAutoDetect = false,
            sttTranslateImmediately = true
        )
        assertTrue(settings.sttPreferOnDevice)
        assertFalse(settings.sttAutoDetect)
        assertTrue(settings.sttTranslateImmediately)
    }
}
