package app.deutschlern.domain.trainer

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerCheckerTest {

    private fun correct(note: AnswerNote = AnswerNote.NONE) = AnswerVerdict(AnswerResult.CORRECT, note)
    private fun almost(note: AnswerNote) = AnswerVerdict(AnswerResult.ALMOST, note)

    @Test
    fun exactMatchIsCorrect() {
        assertEquals(correct(), AnswerChecker.check("Haus", "Haus"))
    }

    @Test
    fun caseAndPunctuationAreIgnored() {
        assertEquals(correct(), AnswerChecker.check("  haus!  ", "Haus"))
        assertEquals(correct(), AnswerChecker.check("auf dem Laufenden.", "Auf dem laufenden"))
    }

    @Test
    fun aeAcceptedAsUmlautWithSpellingVariantNote() {
        assertEquals(correct(AnswerNote.SPELLING_VARIANT), AnswerChecker.check("Aepfel", "Äpfel"))
    }

    @Test
    fun ssAcceptedAsEszettWithSpellingVariantNote() {
        assertEquals(correct(AnswerNote.SPELLING_VARIANT), AnswerChecker.check("Strasse", "Straße"))
    }

    @Test
    fun oneTypoInLongWordIsAlmost() {
        assertEquals(almost(AnswerNote.TYPO), AnswerChecker.check("Schlos", "Schloss"))
        assertEquals(almost(AnswerNote.TYPO), AnswerChecker.check("besichtikt", "besichtigt"))
    }

    @Test
    fun typoInShortWordIsWrong() {
        assertEquals(AnswerResult.WRONG, AnswerChecker.check("Hous", "Haus").result)
        assertEquals(AnswerResult.WRONG, AnswerChecker.check("Hund", "Hand").result)
    }

    @Test
    fun twoTyposInLongWordAreWrong() {
        assertEquals(AnswerResult.WRONG, AnswerChecker.check("Schlas", "Schloss").result)
    }

    @Test
    fun missingArticleIsAlmost() {
        assertEquals(almost(AnswerNote.MISSING_ARTICLE), AnswerChecker.check("Hund", "Hund", requiredArticle = "der"))
    }

    @Test
    fun wrongArticleIsAlmost() {
        assertEquals(almost(AnswerNote.WRONG_ARTICLE), AnswerChecker.check("die Hund", "Hund", requiredArticle = "der"))
    }

    @Test
    fun correctArticleIsCorrect() {
        assertEquals(correct(), AnswerChecker.check("der Hund", "Hund", requiredArticle = "der"))
        assertEquals(correct(), AnswerChecker.check("Der HUND", "Hund", requiredArticle = "der"))
    }

    @Test
    fun articleWithWrongNounIsWrong() {
        assertEquals(AnswerResult.WRONG, AnswerChecker.check("der Katze", "Hund", requiredArticle = "der").result)
    }

    @Test
    fun articleIsOptionalWhenNotRequired() {
        assertEquals(correct(), AnswerChecker.check("der Hund", "Hund"))
    }

    @Test
    fun expectedWithLeadingArticleMatchesBareNoun() {
        assertEquals(correct(), AnswerChecker.check("Daumen", "die Daumen"))
        assertEquals(correct(), AnswerChecker.check("die Daumen", "die Daumen"))
    }

    @Test
    fun nearMissGivesBaseFormAlmost() {
        val verdict = AnswerChecker.check("besichtigen", "besichtigt", nearMisses = listOf("besichtigen"))
        assertEquals(almost(AnswerNote.BASE_FORM), verdict)
    }

    @Test
    fun unrelatedAnswerIgnoresNearMisses() {
        val verdict = AnswerChecker.check("laufen", "besichtigt", nearMisses = listOf("besichtigen"))
        assertEquals(AnswerResult.WRONG, verdict.result)
    }

    @Test
    fun emptyInputIsWrong() {
        assertEquals(AnswerResult.WRONG, AnswerChecker.check("", "Haus").result)
        assertEquals(AnswerResult.WRONG, AnswerChecker.check("  ?! ", "Haus").result)
    }

    @Test
    fun perfektWithAuxiliaryIsCorrect() {
        assertEquals(correct(), AnswerChecker.checkPerfekt("hat gemacht", "hat", "gemacht"))
    }

    @Test
    fun perfektWithoutAuxiliaryIsAlmost() {
        assertEquals(almost(AnswerNote.MISSING_AUXILIARY), AnswerChecker.checkPerfekt("gemacht", "hat", "gemacht"))
    }

    @Test
    fun perfektWithWrongAuxiliaryIsWrong() {
        assertEquals(AnswerResult.WRONG, AnswerChecker.checkPerfekt("ist gemacht", "hat", "gemacht").result)
    }

    @Test
    fun perfektSeinVerbCorrect() {
        assertEquals(correct(), AnswerChecker.checkPerfekt("ist gegangen", "ist", "gegangen"))
    }

    @Test
    fun levenshteinBasics() {
        assertEquals(0, AnswerChecker.levenshtein("abc", "abc"))
        assertEquals(1, AnswerChecker.levenshtein("abc", "abd"))
        assertEquals(3, AnswerChecker.levenshtein("", "abc"))
    }
}
