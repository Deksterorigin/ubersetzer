package app.deutschlern.domain.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonModeDowngradeTest {

    @Test
    fun jsonMode_downgradesSequentially() {
        val initial = JsonMode.JSON_SCHEMA
        val second = initial.downgrade()
        assertEquals(JsonMode.JSON_OBJECT, second)

        val third = second?.downgrade()
        assertEquals(JsonMode.PLAIN_TEXT, third)

        val terminal = third?.downgrade()
        assertNull(terminal)
    }
}
