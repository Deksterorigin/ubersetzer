package app.deutschlern.domain.check

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverEditGuardTest {

    @Test
    fun normalizeText_replacesWindowsLineEndings() {
        val input = "Zeile 1\r\nZeile 2\r\nZeile 3"
        val normalized = OverEditGuard.normalizeText(input)
        assertEquals("Zeile 1\nZeile 2\nZeile 3", normalized)
    }

    @Test
    fun evaluate_lineBreakMismatch_triggersOverEdit() {
        val orig = "Erste Zeile.\nZweite Zeile."
        val corr = "Erste Zeile. Zweite Zeile zusammengefasst."
        val result = OverEditGuard.evaluate(orig, corr)

        assertTrue(result.isOverEdit)
        assertTrue(result.reason?.contains("Line break count mismatch") == true)
    }

    @Test
    fun evaluate_paragraphCountMismatch_triggersOverEdit() {
        val orig = "Absatz eins.\n\nAbsatz zwei."
        val corr = "Absatz eins und zwei zusammen."
        val result = OverEditGuard.evaluate(orig, corr)

        assertTrue(result.isOverEdit)
    }

    @Test
    fun evaluate_invariantUrlViolation_triggersOverEdit() {
        val orig = "Besuchen Sie https://deutschlern.app für mehr Informationen."
        val corr = "Besuchen Sie unsere Webseite für mehr Informationen."
        val result = OverEditGuard.evaluate(orig, corr)

        assertTrue(result.isOverEdit)
        assertTrue(result.invariantViolations.any { it.contains("URL") })
    }

    @Test
    fun evaluate_invariantEmailViolation_triggersOverEdit() {
        val orig = "Schreiben Sie an info@example.com bitte."
        val corr = "Schreiben Sie an uns bitte."
        val result = OverEditGuard.evaluate(orig, corr)

        assertTrue(result.isOverEdit)
        assertTrue(result.invariantViolations.any { it.contains("Email") })
    }

    @Test
    fun evaluate_invariantDateViolation_triggersOverEdit() {
        val orig = "Das Treffen ist am 15.10.2026 um 14:30."
        val corr = "Das Treffen ist morgen Nachmittag."
        val result = OverEditGuard.evaluate(orig, corr)

        assertTrue(result.isOverEdit)
        assertTrue(result.invariantViolations.any { it.contains("Date") || it.contains("time") })
    }

    @Test
    fun evaluate_invariantDigitViolation_triggersOverEdit() {
        val orig = "Wir haben 42 Teilnehmer im Kurs."
        val corr = "Wir haben viele Teilnehmer im Kurs."
        val result = OverEditGuard.evaluate(orig, corr)

        assertTrue(result.isOverEdit)
        assertTrue(result.invariantViolations.any { it.contains("Number or digit") })
    }

    @Test
    fun evaluate_caseAndPunctuationOnly_doesNotTriggerOverEdit() {
        // A 25-word text where multiple nouns are lowercase and commas are missing
        val orig = "heute habe ich ein buch gekauft und meine schwester hat einen apfel gegessen weil sie hunger hatte und das wetter schön war."
        val corr = "Heute habe ich ein Buch gekauft, und meine Schwester hat einen Apfel gegessen, weil sie Hunger hatte, und das Wetter schön war."

        val result = OverEditGuard.evaluate(orig, corr)

        assertFalse("Case and punctuation fixes must not be counted as over-edits", result.isOverEdit)
        assertEquals(0, result.identityChangedWordCount)
    }

    @Test
    fun evaluate_excessiveStylisticRewriting_triggersOverEdit() {
        // Text where author's wording is replaced stylistically (> 30% of words)
        val orig = "Ich denke dass dieser Film sehr gut und interessant ist weil die Geschichte spannend erzählt wird von den Schauspielern."
        val corr = "Meiner Ansicht nach zeichnet sich dieses filmische Werk durch eine fesselnde Narration und herausragende schauspielerische Leistungen aus."

        val result = OverEditGuard.evaluate(orig, corr)

        assertTrue("Complete stylistic rewriting must trigger over-edit warning", result.isOverEdit)
        assertTrue(result.ratio > 0.30f)
    }

    @Test
    fun evaluate_legitimateGrammarCorrection_passes() {
        val orig = "Ich habe gestern mit meine Freunde ins Kino gegangen und ein schöner Film gesehen."
        val corr = "Ich bin gestern mit meinen Freunden ins Kino gegangen und habe einen schönen Film gesehen."

        val result = OverEditGuard.evaluate(orig, corr)

        assertFalse("Legitimate grammatical correction should not trigger over-edit", result.isOverEdit)
    }
}
