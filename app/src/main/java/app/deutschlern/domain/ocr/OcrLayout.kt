package app.deutschlern.domain.ocr

data class OcrPoint(val x: Float, val y: Float)

/**
 * One recognised word. Corners are in image pixels, clockwise from top-left: a quad rather than a
 * rect because text on a hand-held photo is tilted and upright boxes would overlap the next line.
 * Words of a page are kept in the recogniser's reading order.
 */
data class OcrWord(
    val id: Int,
    val text: String,
    val blockIndex: Int,
    val lineIndex: Int,
    val corners: List<OcrPoint>
)

data class OcrPageLayout(
    val width: Int,
    val height: Int,
    val words: List<OcrWord>
)

data class OcrPageSelection(
    val words: List<OcrWord>,
    val selectedIds: Set<Int>
)
