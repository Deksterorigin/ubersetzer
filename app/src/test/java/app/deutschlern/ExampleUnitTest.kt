package app.deutschlern

import app.deutschlern.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun package_name_matches_specification() {
        assertEquals("app.deutschlern", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun gemini_default_model_is_configured() {
        assertEquals("gemini-3.5-flash", BuildConfig.GEMINI_MODEL)
    }
}
