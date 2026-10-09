package app.deutschlern.domain.dialog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GapFillEvaluatorTest {

    @Test
    fun exactMatch_returnsTrue() {
        assertTrue(
            GapFillEvaluator.isAnswerCorrect(
                userAnswer = "Bahnhof",
                expectedAnswer = "Bahnhof"
            )
        )
    }

    @Test
    fun caseInsensitive_returnsTrue() {
        assertTrue(
            GapFillEvaluator.isAnswerCorrect(
                userAnswer = "bahnhof",
                expectedAnswer = "Bahnhof"
            )
        )
        assertTrue(
            GapFillEvaluator.isAnswerCorrect(
                userAnswer = "BAHNHOF",
                expectedAnswer = "Bahnhof"
            )
        )
    }

    @Test
    fun trailingPunctuationAndWhitespace_returnsTrue() {
        assertTrue(
            GapFillEvaluator.isAnswerCorrect(
                userAnswer = " Bahnhof.  ",
                expectedAnswer = "Bahnhof"
            )
        )
        assertTrue(
            GapFillEvaluator.isAnswerCorrect(
                userAnswer = "Bahnhof!",
                expectedAnswer = "Bahnhof"
            )
        )
    }

    @Test
    fun umlautEquivalence_returnsTrue() {
        // ä <-> ae
        assertTrue(
            GapFillEvaluator.isAnswerCorrect(
                userAnswer = "Zuege",
                expectedAnswer = "Züge"
            )
        )
        assertTrue(
            GapFillEvaluator.isAnswerCorrect(
                userAnswer = "Züge",
                expectedAnswer = "Zuege"
            )
        )
        // ß <-> ss
        assertTrue(
            GapFillEvaluator.isAnswerCorrect(
                userAnswer = "Strasse",
                expectedAnswer = "Straße"
            )
        )
        assertTrue(
            GapFillEvaluator.isAnswerCorrect(
                userAnswer = "Straße",
                expectedAnswer = "Strasse"
            )
        )
        // ö <-> oe
        assertTrue(
            GapFillEvaluator.isAnswerCorrect(
                userAnswer = "schoen",
                expectedAnswer = "schön"
            )
        )
    }

    @Test
    fun acceptedAlternatives_returnsTrue() {
        assertTrue(
            GapFillEvaluator.isAnswerCorrect(
                userAnswer = "Zug",
                expectedAnswer = "Bahn",
                alternatives = listOf("Zug", "Eisenbahn")
            )
        )
    }

    @Test
    fun incorrectAnswer_returnsFalse() {
        assertFalse(
            GapFillEvaluator.isAnswerCorrect(
                userAnswer = "Flughafen",
                expectedAnswer = "Bahnhof"
            )
        )
        assertFalse(
            GapFillEvaluator.isAnswerCorrect(
                userAnswer = "",
                expectedAnswer = "Bahnhof"
            )
        )
        assertFalse(
            GapFillEvaluator.isAnswerCorrect(
                userAnswer = "   ",
                expectedAnswer = "Bahnhof"
            )
        )
    }
}
