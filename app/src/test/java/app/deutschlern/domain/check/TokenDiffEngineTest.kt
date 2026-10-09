package app.deutschlern.domain.check

import app.deutschlern.data.remote.dto.check.EditItemDto
import app.deutschlern.domain.model.check.CheckCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenDiffEngineTest {

    @Test
    fun tokenize_separatesWordsPunctuationAndWhitespace() {
        val input = "Hallo, Welt! Wie geht's?"
        val tokens = TokenDiffEngine.tokenize(input)

        val words = tokens.filter { it.type == TokenDiffEngine.TokenType.WORD }.map { it.text }
        val punct = tokens.filter { it.type == TokenDiffEngine.TokenType.PUNCTUATION }.map { it.text }

        assertTrue(words.contains("Hallo"))
        assertTrue(words.contains("Welt"))
        assertTrue(words.contains("Wie"))
        assertTrue(punct.contains(","))
        assertTrue(punct.contains("!"))
        assertTrue(punct.contains("?"))
    }

    @Test
    fun tokenize_handlesUmlautsAndEszett() {
        val input = "Groß, süß, schön, über, Käse."
        val tokens = TokenDiffEngine.tokenize(input)
        val words = tokens.filter { it.type == TokenDiffEngine.TokenType.WORD }.map { it.text }

        assertEquals(listOf("Groß", "süß", "schön", "über", "Käse"), words)
    }

    @Test
    fun computeDiff_detectsReplacement() {
        val orig = "Ich habe ein Hund."
        val corr = "Ich habe einen Hund."
        val hunks = TokenDiffEngine.computeDiff(orig, corr)

        assertEquals(1, hunks.size)
        assertEquals("ein", hunks[0].originalText.trim())
        assertEquals("einen", hunks[0].correctedText.trim())
    }

    @Test
    fun computeDiff_detectsInsertionAndDeletion() {
        val orig = "Er geht Schule heute."
        val corr = "Er geht in die Schule."
        val hunks = TokenDiffEngine.computeDiff(orig, corr)

        assertTrue(hunks.isNotEmpty())
        val combinedCorr = hunks.joinToString(" ") { it.correctedText }
        assertTrue(combinedCorr.contains("in die") || combinedCorr.contains("Schule"))
    }

    @Test
    fun computeDiff_coalescesAdjacentChangesSeparatedOnlyByWhitespace() {
        val orig = "der gross Haus"
        val corr = "das große Haus"
        val hunks = TokenDiffEngine.computeDiff(orig, corr)

        // "der gross" -> "das große" should be coalesced into a single hunk
        assertEquals(1, hunks.size)
        assertEquals("der gross", hunks[0].originalText.trim())
        assertEquals("das große", hunks[0].correctedText.trim())
    }

    @Test
    fun isPunctuationOnly_detectsCorrectly() {
        assertTrue(TokenDiffEngine.isPunctuationOnly("", ","))
        assertTrue(TokenDiffEngine.isPunctuationOnly(".", "!"))
        assertTrue(TokenDiffEngine.isPunctuationOnly(", ", " "))
        assertFalse(TokenDiffEngine.isPunctuationOnly("Haus", "Häuser"))
        assertFalse(TokenDiffEngine.isPunctuationOnly("gut,", "besser,"))
    }

    @Test
    fun isCaseOnly_detectsCorrectly() {
        assertTrue(TokenDiffEngine.isCaseOnly("haus", "Haus"))
        assertTrue(TokenDiffEngine.isCaseOnly("SCHULE", "Schule"))
        assertFalse(TokenDiffEngine.isCaseOnly("Haus", "Haus"))
        assertFalse(TokenDiffEngine.isCaseOnly("Haus", "Maus"))
    }

    @Test
    fun isSingleGermanWord_validatesProperly() {
        assertTrue(TokenDiffEngine.isSingleGermanWord("Haus"))
        assertTrue(TokenDiffEngine.isSingleGermanWord("großartig"))
        assertTrue(TokenDiffEngine.isSingleGermanWord("E-Mail"))
        assertFalse(TokenDiffEngine.isSingleGermanWord("das Haus"))
        assertFalse(TokenDiffEngine.isSingleGermanWord("Haus!"))
        assertFalse(TokenDiffEngine.isSingleGermanWord(""))
    }

    @Test
    fun matchHunksToModelEdits_groupsAndAssignsCategories() {
        val orig = "Ich habe gestern ein grose fehler gemacht."
        val corr = "Ich habe gestern einen großen Fehler gemacht."

        val rawHunks = TokenDiffEngine.computeDiff(orig, corr)
        val modelEdits = listOf(
            EditItemDto(
                original = "ein grose fehler",
                corrected = "einen großen Fehler",
                category = "grammar",
                explanation = "Akkusativ maskulin und Substantiv-Großschreibung",
                rule = "Akkusativ maskulin"
            )
        )

        val matchedHunks = TokenDiffEngine.matchHunksToModelEdits(rawHunks, modelEdits)

        assertTrue(matchedHunks.isNotEmpty())
        for (hunk in matchedHunks) {
            assertEquals(CheckCategory.GRAMMAR, hunk.category)
            assertNotNull(hunk.groupId)
            assertEquals("Akkusativ maskulin und Substantiv-Großschreibung", hunk.explanation)
        }
    }

    @Test
    fun assembleFinalText_respectsAcceptAndReject() {
        val orig = "Er fahr mit der Zug."
        val corr = "Er fährt mit dem Zug."
        val rawHunks = TokenDiffEngine.computeDiff(orig, corr)
        val hunks = TokenDiffEngine.matchHunksToModelEdits(rawHunks, emptyList())

        assertEquals(2, hunks.size) // "fahr" -> "fährt" and "der" -> "dem"

        // 1. Both accepted
        val bothAccepted = TokenDiffEngine.assembleFinalText(orig, hunks)
        assertEquals("Er fährt mit dem Zug.", bothAccepted)

        // 2. Both rejected
        val bothRejectedHunks = hunks.map { it.copy(isAccepted = false) }
        val bothRejected = TokenDiffEngine.assembleFinalText(orig, bothRejectedHunks)
        assertEquals(orig, bothRejected)

        // 3. First accepted, second rejected
        val mixedHunks = listOf(
            hunks[0].copy(isAccepted = true),
            hunks[1].copy(isAccepted = false)
        )
        val mixed = TokenDiffEngine.assembleFinalText(orig, mixedHunks)
        assertEquals("Er fährt mit der Zug.", mixed)
    }
}
