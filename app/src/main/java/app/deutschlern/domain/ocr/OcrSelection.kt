package app.deutschlern.domain.ocr

import kotlin.math.hypot
import kotlin.math.max

enum class OcrPaintMode { ADD, REMOVE }

/** One finger stroke: the first word it touches decides whether the whole stroke adds or removes. */
class OcrPaintStroke(initialSelection: Set<Int>) {

    var selection: Set<Int> = initialSelection
        private set

    var mode: OcrPaintMode? = null
        private set

    /** Returns true when the touch changed the selection. */
    fun touch(wordId: Int): Boolean {
        val strokeMode = mode
            ?: (if (wordId in selection) OcrPaintMode.REMOVE else OcrPaintMode.ADD).also { mode = it }
        val updated = when (strokeMode) {
            OcrPaintMode.ADD -> selection + wordId
            OcrPaintMode.REMOVE -> selection - wordId
        }
        val changed = updated.size != selection.size
        selection = updated
        return changed
    }
}

object OcrSelection {

    fun toggleWord(selected: Set<Int>, wordId: Int): Set<Int> =
        if (wordId in selected) selected - wordId else selected + wordId

    /** Selects the block, or clears it when it is already fully selected. */
    fun toggleBlock(words: List<OcrWord>, selected: Set<Int>, blockIndex: Int): Set<Int> {
        val blockIds = words.filter { it.blockIndex == blockIndex }.map { it.id }.toSet()
        return if (selected.containsAll(blockIds)) selected - blockIds else selected + blockIds
    }

    /** The word under a point; among overlapping or nearby candidates, the closest within [slop]. */
    fun wordAt(words: List<OcrWord>, x: Float, y: Float, slop: Float): OcrWord? {
        var best: OcrWord? = null
        var bestDistance = Float.MAX_VALUE
        var bestCenterDistance = Float.MAX_VALUE
        for (word in words) {
            val distance = distanceToQuad(word.corners, x, y)
            if (distance > slop || distance > bestDistance) continue
            val centerDistance = distanceToCenter(word.corners, x, y)
            if (distance < bestDistance || centerDistance < bestCenterDistance) {
                best = word
                bestDistance = distance
                bestCenterDistance = centerDistance
            }
        }
        return best
    }

    /**
     * Words a stroke segment passes over, in stroke order. Touch events arrive far apart during a
     * fast swipe, so the segment is sampled instead of testing only its end points.
     */
    fun wordsAlongSegment(
        words: List<OcrWord>,
        from: OcrPoint,
        to: OcrPoint,
        slop: Float
    ): List<OcrWord> {
        val length = hypot(to.x - from.x, to.y - from.y)
        val step = max(slop, 1f)
        val samples = (length / step).toInt() + 1
        val touched = LinkedHashSet<OcrWord>()
        for (i in 0..samples) {
            val t = i.toFloat() / samples
            wordAt(words, from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t, slop)
                ?.let { touched += it }
        }
        return touched.toList()
    }

    private fun distanceToCenter(corners: List<OcrPoint>, x: Float, y: Float): Float =
        hypot(corners.map { it.x }.average().toFloat() - x, corners.map { it.y }.average().toFloat() - y)

    private fun distanceToQuad(corners: List<OcrPoint>, x: Float, y: Float): Float {
        if (isInsideConvex(corners, x, y)) return 0f
        return corners.indices.minOf { i ->
            distanceToSegment(corners[i], corners[(i + 1) % corners.size], x, y)
        }
    }

    private fun isInsideConvex(corners: List<OcrPoint>, x: Float, y: Float): Boolean {
        var hasPositive = false
        var hasNegative = false
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            val cross = (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x)
            if (cross > 0f) hasPositive = true
            if (cross < 0f) hasNegative = true
        }
        return !(hasPositive && hasNegative)
    }

    private fun distanceToSegment(a: OcrPoint, b: OcrPoint, x: Float, y: Float): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0f) return hypot(x - a.x, y - a.y)
        val t = (((x - a.x) * dx + (y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
        return hypot(x - (a.x + t * dx), y - (a.y + t * dy))
    }
}
