package app.deutschlern.domain.exam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GapTextTest {

    @Test
    fun `parse splits text around gap markers`() {
        val tokens = GapText.parse("Sehr {{46}} Damen, wir {{47}}.")
        assertEquals(
            listOf(
                GapToken.Text("Sehr "),
                GapToken.Gap(46),
                GapToken.Text(" Damen, wir "),
                GapToken.Gap(47),
                GapToken.Text(".")
            ),
            tokens
        )
    }

    @Test
    fun `parse handles markers at the edges and adjacent markers`() {
        assertEquals(listOf(GapToken.Gap(1), GapToken.Gap(2)), GapText.parse("{{1}}{{2}}"))
        assertEquals(listOf(GapToken.Gap(3), GapToken.Text(" x")), GapText.parse("{{ 3 }} x"))
    }

    @Test
    fun `text without markers stays one token`() {
        assertEquals(listOf(GapToken.Text("Kein {Platz} hier")), GapText.parse("Kein {Platz} hier"))
        assertEquals(emptyList<GapToken>(), GapText.parse(""))
        assertFalse(GapText.hasGaps("Kein {Platz} hier"))
    }

    @Test
    fun `gapNumbers lists markers in order`() {
        assertTrue(GapText.hasGaps("a {{21}} b"))
        assertEquals(listOf(21, 22, 30), GapText.gapNumbers("{{21}} und {{22}}\n{{30}}"))
    }
}
