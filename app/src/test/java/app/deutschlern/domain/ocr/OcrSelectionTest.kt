package app.deutschlern.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrSelectionTest {

    private fun word(id: Int, left: Float, top: Float, right: Float, bottom: Float, block: Int = 0) = OcrWord(
        id = id,
        text = "w$id",
        blockIndex = block,
        lineIndex = 0,
        corners = listOf(
            OcrPoint(left, top),
            OcrPoint(right, top),
            OcrPoint(right, bottom),
            OcrPoint(left, bottom)
        )
    )

    private val row = listOf(
        word(0, 0f, 0f, 40f, 20f),
        word(1, 50f, 0f, 90f, 20f),
        word(2, 100f, 0f, 140f, 20f, block = 1)
    )

    @Test
    fun `stroke starting on an unselected word adds`() {
        val stroke = OcrPaintStroke(setOf(5))
        assertTrue(stroke.touch(1))
        assertTrue(stroke.touch(2))
        assertEquals(OcrPaintMode.ADD, stroke.mode)
        assertEquals(setOf(5, 1, 2), stroke.selection)
    }

    @Test
    fun `stroke starting on a selected word removes and keeps removing`() {
        val stroke = OcrPaintStroke(setOf(1, 2))
        assertTrue(stroke.touch(1))
        assertFalse(stroke.touch(3))
        assertTrue(stroke.touch(2))
        assertEquals(OcrPaintMode.REMOVE, stroke.mode)
        assertEquals(emptySet<Int>(), stroke.selection)
    }

    @Test
    fun `touching the same word twice in a stroke reports no change`() {
        val stroke = OcrPaintStroke(emptySet())
        assertTrue(stroke.touch(1))
        assertFalse(stroke.touch(1))
        assertEquals(setOf(1), stroke.selection)
    }

    @Test
    fun `toggle word flips membership`() {
        assertEquals(setOf(1), OcrSelection.toggleWord(emptySet(), 1))
        assertEquals(emptySet<Int>(), OcrSelection.toggleWord(setOf(1), 1))
    }

    @Test
    fun `toggle block fills a partial block then clears a full one`() {
        val filled = OcrSelection.toggleBlock(row, setOf(1, 2), blockIndex = 0)
        assertEquals(setOf(0, 1, 2), filled)
        assertEquals(setOf(2), OcrSelection.toggleBlock(row, filled, blockIndex = 0))
    }

    @Test
    fun `point inside a word hits it`() {
        assertEquals(1, OcrSelection.wordAt(row, 70f, 10f, slop = 0f)?.id)
    }

    @Test
    fun `point near a word hits it only within the slop`() {
        assertEquals(1, OcrSelection.wordAt(row, 70f, 26f, slop = 8f)?.id)
        assertNull(OcrSelection.wordAt(row, 70f, 40f, slop = 8f))
    }

    @Test
    fun `point in a gap goes to the nearest word`() {
        assertEquals(0, OcrSelection.wordAt(row, 43f, 10f, slop = 10f)?.id)
        assertEquals(1, OcrSelection.wordAt(row, 47f, 10f, slop = 10f)?.id)
    }

    @Test
    fun `tilted word is hit by its quad and not by its bounding box`() {
        val tilted = OcrWord(
            id = 0,
            text = "schief",
            blockIndex = 0,
            lineIndex = 0,
            corners = listOf(
                OcrPoint(0f, 40f),
                OcrPoint(100f, 0f),
                OcrPoint(110f, 20f),
                OcrPoint(10f, 60f)
            )
        )
        assertEquals(0, OcrSelection.wordAt(listOf(tilted), 50f, 30f, slop = 0f)?.id)
        assertNull(OcrSelection.wordAt(listOf(tilted), 5f, 5f, slop = 0f))
    }

    @Test
    fun `fast swipe touches the words between its end points in order`() {
        val touched = OcrSelection.wordsAlongSegment(
            words = row,
            from = OcrPoint(10f, 10f),
            to = OcrPoint(130f, 10f),
            slop = 2f
        )
        assertEquals(listOf(0, 1, 2), touched.map { it.id })
    }

    @Test
    fun `zero length segment touches the word under it`() {
        val touched = OcrSelection.wordsAlongSegment(row, OcrPoint(10f, 10f), OcrPoint(10f, 10f), slop = 2f)
        assertEquals(listOf(0), touched.map { it.id })
    }
}
