package app.deutschlern.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTextAssemblerTest {

    private fun layout(vararg blocks: List<List<String>>): List<OcrWord> {
        val words = mutableListOf<OcrWord>()
        var lineIndex = 0
        blocks.forEachIndexed { blockIndex, lines ->
            lines.forEach { line ->
                line.forEach { text ->
                    words += OcrWord(words.size, text, blockIndex, lineIndex, emptyList())
                }
                lineIndex++
            }
        }
        return words
    }

    private fun assemble(words: List<OcrWord>, vararg selected: String): String {
        val ids = words.filter { it.text in selected }.map { it.id }.toSet()
        return OcrTextAssembler.assemble(listOf(OcrPageSelection(words, ids)))
    }

    @Test
    fun `nothing selected gives empty text`() {
        val words = layout(listOf(listOf("Guten", "Tag")))
        assertEquals("", OcrTextAssembler.assemble(listOf(OcrPageSelection(words, emptySet()))))
    }

    @Test
    fun `partial line joins selected words with spaces`() {
        val words = layout(listOf(listOf("Heute", "ist", "ein", "schöner", "Tag")))
        assertEquals("ist schöner", assemble(words, "ist", "schöner"))
    }

    @Test
    fun `lines of a block become one paragraph`() {
        val words = layout(listOf(listOf("Ich", "lerne"), listOf("Deutsch")))
        assertEquals("Ich lerne Deutsch", assemble(words, "Ich", "lerne", "Deutsch"))
    }

    @Test
    fun `blocks are separated by a blank line`() {
        val words = layout(listOf(listOf("Erstens")), listOf(listOf("Zweitens")))
        assertEquals("Erstens\n\nZweitens", assemble(words, "Erstens", "Zweitens"))
    }

    @Test
    fun `pages are separated by a blank line and empty pages are skipped`() {
        val first = layout(listOf(listOf("Seite", "eins")))
        val empty = layout(listOf(listOf("Rauschen")))
        val last = layout(listOf(listOf("Seite", "drei")))
        val text = OcrTextAssembler.assemble(
            listOf(
                OcrPageSelection(first, first.map { it.id }.toSet()),
                OcrPageSelection(empty, emptySet()),
                OcrPageSelection(last, last.map { it.id }.toSet())
            )
        )
        assertEquals("Seite eins\n\nSeite drei", text)
    }

    @Test
    fun `selection follows reading order not the order of ids in the set`() {
        val words = layout(listOf(listOf("eins", "zwei", "drei")))
        val text = OcrTextAssembler.assemble(listOf(OcrPageSelection(words, linkedSetOf(2, 0, 1))))
        assertEquals("eins zwei drei", text)
    }

    @Test
    fun `dehyphenates across selected lines`() {
        val words = layout(listOf(listOf("die", "Spra-"), listOf("che", "lernen")))
        assertEquals(
            "die Sprache lernen",
            assemble(words, "die", "Spra-", "che", "lernen")
        )
    }

    @Test
    fun `dehyphenates when only the end and start of adjoining lines are selected`() {
        val words = layout(listOf(listOf("die", "Spra-"), listOf("che", "lernen")))
        assertEquals("Sprache", assemble(words, "Spra-", "che"))
    }

    @Test
    fun `hyphen is not glued to an unrelated word when the ending was left out`() {
        val words = layout(listOf(listOf("die", "Spra-"), listOf("che", "lernen")))
        assertEquals("Spra- lernen", assemble(words, "Spra-", "lernen"))
    }

    @Test
    fun `hyphen is not glued across a skipped line`() {
        val words = layout(listOf(listOf("Spra-"), listOf("che"), listOf("weiter")))
        assertEquals("Spra- weiter", assemble(words, "Spra-", "weiter"))
    }

    @Test
    fun `hyphen in the middle of a selected word's line is kept when words follow unselected`() {
        val words = layout(listOf(listOf("Spra-", "Test"), listOf("che")))
        assertEquals("Spra- che", assemble(words, "Spra-", "che"))
    }
}
