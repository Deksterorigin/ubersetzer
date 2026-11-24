package app.deutschlern.domain.exam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamScoringTest {

    private val abc = listOf(Option("a", "eins"), Option("b", "zwei"), Option("c", "drei"))

    private fun variant(vararg questions: Question, shared: List<Option> = emptyList()) = ExamVariant(
        id = "v1",
        part = ExamPart.LESEN_4,
        title = "Test",
        segments = listOf(ExamSegment(sharedOptions = shared, questions = questions.toList()))
    )

    @Test
    fun `score counts correct answers and excludes questions without key`() {
        val v = variant(
            Question(1, options = abc, answer = "a"),
            Question(2, options = abc, answer = "b"),
            Question(3, options = abc, answer = null),
            Question(4, options = abc, answer = "c")
        )
        val result = ExamScoring.score(v, mapOf(1 to "a", 2 to "c", 3 to "a"))

        assertEquals(3, result.gradable)
        assertEquals(1, result.correct)
        assertEquals(33, result.percent)
        assertNull(result.results[2].correct)
        assertEquals(false, result.results[3].correct)
        assertNull(result.results[3].given)
    }

    @Test
    fun `score with no gradable questions is zero percent`() {
        val result = ExamScoring.score(variant(Question(1, options = abc)), mapOf(1 to "a"))
        assertEquals(0, result.gradable)
        assertEquals(0, result.percent)
    }

    @Test
    fun `optionsFor prefers own options and falls back to shared ones`() {
        val shared = listOf(Option("x", "geteilt"))
        val segment = ExamSegment(sharedOptions = shared)
        assertEquals(abc, ExamScoring.optionsFor(segment, Question(1, options = abc)))
        assertEquals(shared, ExamScoring.optionsFor(segment, Question(2)))
    }

    @Test
    fun `wordCount ignores punctuation and extra whitespace`() {
        assertEquals(0, ExamScoring.wordCount(""))
        assertEquals(0, ExamScoring.wordCount("  \n  - , "))
        assertEquals(5, ExamScoring.wordCount("Sehr geehrte Damen und Herren,"))
        assertEquals(5, ExamScoring.wordCount("Ich  komme\num 10 Uhr –"))
        assertEquals(3, ExamScoring.wordCount("Grüße aus Köln"))
    }

    @Test
    fun `phone numbers match by digits only`() {
        assertTrue(ExamScoring.noteFieldMatches("0171 88 45 545", "0171/8845545"))
        assertTrue(ExamScoring.noteFieldMatches("0171 88 45 545", "Tel. 0171-88-45-545"))
        assertFalse(ExamScoring.noteFieldMatches("0171 88 45 545", "0171 88 45 54"))
    }

    @Test
    fun `words match loosely ignoring case articles and order`() {
        assertTrue(ExamScoring.noteFieldMatches("Schmitt", "schmitt"))
        assertTrue(ExamScoring.noteFieldMatches("das Auto mit vollem Tank Benzin", "Auto vollem Tank Benzin"))
        assertTrue(ExamScoring.noteFieldMatches("Termin am Montag verschieben", "verschieben Montag Termin"))
        assertFalse(ExamScoring.noteFieldMatches("Termin am Montag verschieben", "Dienstag"))
        assertFalse(ExamScoring.noteFieldMatches("Schmitt", "   "))
    }

    @Test
    fun `umlauts match their transliteration`() {
        assertTrue(ExamScoring.noteFieldMatches("Müller", "Mueller"))
        assertTrue(ExamScoring.noteFieldMatches("Größe prüfen", "groesse pruefen"))
        assertTrue(ExamScoring.noteFieldMatches("Straße", "STRASSE"))
    }
}
