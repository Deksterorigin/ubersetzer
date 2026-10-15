package app.deutschlern.update.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun parse_withAndWithoutVPrefix_parsesCorrectly() {
        val v1 = AppVersion.parseOrNull("v1.2.3")
        val v2 = AppVersion.parseOrNull("1.2.3")
        val vUpper = AppVersion.parseOrNull("V2.0")

        assertNotNull(v1)
        assertNotNull(v2)
        assertNotNull(vUpper)

        assertEquals(listOf(1, 2, 3), v1?.segments)
        assertEquals(listOf(1, 2, 3), v2?.segments)
        assertEquals(listOf(2, 0), vUpper?.segments)
    }

    @Test
    fun compare_nineVsTen_tenIsGreater() {
        val v9 = AppVersion.parse("1.9")
        val v10 = AppVersion.parse("1.10")

        assertTrue("1.10 must be greater than 1.9", v10 > v9)
        assertFalse(v9 > v10)
    }

    @Test
    fun compare_differentSegmentCounts_padsWithZeros() {
        val v1 = AppVersion.parse("1.0")
        val v2 = AppVersion.parse("1.0.0")
        val v3 = AppVersion.parse("1.0.1")

        assertEquals(0, v1.compareTo(v2))
        assertTrue(v3 > v1)
        assertTrue(v3 > v2)
    }

    @Test
    fun compare_preRelease_normalIsGreaterThanPreRelease() {
        val release = AppVersion.parse("1.0.0")
        val beta = AppVersion.parse("1.0.0-beta.1")
        val rc = AppVersion.parse("1.0.0-rc1")

        assertTrue(release > beta)
        assertTrue(release > rc)
        assertTrue(rc > beta)
    }

    @Test
    fun parse_malformedStrings_returnsNull() {
        assertNull(AppVersion.parseOrNull(""))
        assertNull(AppVersion.parseOrNull("   "))
        assertNull(AppVersion.parseOrNull("v"))
        assertNull(AppVersion.parseOrNull("abc"))
        assertNull(AppVersion.parseOrNull("1.x.3"))
        assertNull(AppVersion.parseOrNull("1.-2.3"))
        assertNull(AppVersion.parseOrNull(".."))
    }
}
