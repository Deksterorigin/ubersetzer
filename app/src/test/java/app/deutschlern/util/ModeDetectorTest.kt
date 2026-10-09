package app.deutschlern.util

import app.deutschlern.domain.model.TranslationMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ModeDetectorTest {

    @Test
    fun detectMode_singleTokenWithoutPunctuation_returnsWord() {
        assertEquals(TranslationMode.WORD, ModeDetector.detectMode("Haus"))
        assertEquals(TranslationMode.WORD, ModeDetector.detectMode("aufstehen"))
        assertEquals(TranslationMode.WORD, ModeDetector.detectMode("wunderschön"))
    }

    @Test
    fun detectMode_twoToThreeTokensWithoutPunctuation_returnsWord() {
        assertEquals(TranslationMode.WORD, ModeDetector.detectMode("sich freuen"))
        assertEquals(TranslationMode.WORD, ModeDetector.detectMode("ab und zu"))
        assertEquals(TranslationMode.WORD, ModeDetector.detectMode("sich freuen auf"))
        assertEquals(TranslationMode.WORD, ModeDetector.detectMode("sich ärgern über"))
    }

    @Test
    fun detectMode_fourOrMoreTokens_returnsText() {
        assertEquals(TranslationMode.TEXT, ModeDetector.detectMode("das ist ein Haus"))
        assertEquals(TranslationMode.TEXT, ModeDetector.detectMode("ich gehe heute nach Hause"))
        assertEquals(TranslationMode.TEXT, ModeDetector.detectMode("eins zwei drei vier fünf"))
    }

    @Test
    fun detectMode_punctuationDot_returnsText() {
        assertEquals(TranslationMode.TEXT, ModeDetector.detectMode("Haus."))
        assertEquals(TranslationMode.TEXT, ModeDetector.detectMode("Das Haus."))
    }

    @Test
    fun detectMode_punctuationExclamation_returnsText() {
        assertEquals(TranslationMode.TEXT, ModeDetector.detectMode("Hallo!"))
        assertEquals(TranslationMode.TEXT, ModeDetector.detectMode("Guten Tag!"))
    }

    @Test
    fun detectMode_punctuationQuestionMark_returnsText() {
        assertEquals(TranslationMode.TEXT, ModeDetector.detectMode("Warum?"))
        assertEquals(TranslationMode.TEXT, ModeDetector.detectMode("Wie geht's?"))
    }

    @Test
    fun detectMode_punctuationSemicolon_returnsText() {
        assertEquals(TranslationMode.TEXT, ModeDetector.detectMode("Wort; anderes Wort"))
    }

    @Test
    fun detectMode_withNewlines_returnsText() {
        assertEquals(TranslationMode.TEXT, ModeDetector.detectMode("Hallo\nWelt"))
        assertEquals(TranslationMode.TEXT, ModeDetector.detectMode("Wort\r\nZwei"))
        assertEquals(TranslationMode.TEXT, ModeDetector.detectMode("Haus\n"))
        assertEquals(TranslationMode.TEXT, ModeDetector.detectMode("Haus\r\n"))
        assertEquals(TranslationMode.TEXT, ModeDetector.detectMode("\nHaus"))
    }

    @Test
    fun detectMode_multipleSpaces_stillWordMode() {
        assertEquals(TranslationMode.WORD, ModeDetector.detectMode("sich   freuen"))
        assertEquals(TranslationMode.WORD, ModeDetector.detectMode("  das   Schloss  "))
    }

    @Test
    fun detectMode_emptyAndBlank_returnsWord() {
        assertEquals(TranslationMode.WORD, ModeDetector.detectMode(""))
        assertEquals(TranslationMode.WORD, ModeDetector.detectMode("   "))
        assertEquals(TranslationMode.WORD, ModeDetector.detectMode(null))
    }
}
