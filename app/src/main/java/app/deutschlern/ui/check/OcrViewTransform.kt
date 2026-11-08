package app.deutschlern.ui.check

import app.deutschlern.domain.ocr.OcrPoint
import kotlin.math.max
import kotlin.math.min

/**
 * Maps between image pixels and view pixels for a photo fitted into a view (letterboxed and
 * centred) with zoom and pan on top. All drawing and hit-testing go through this one mapping.
 */
data class OcrViewTransform(
    val imageWidth: Int,
    val imageHeight: Int,
    val viewWidth: Float = 0f,
    val viewHeight: Float = 0f,
    val zoom: Float = MIN_ZOOM,
    val panX: Float = 0f,
    val panY: Float = 0f
) {
    private val fitScale: Float
        get() = if (viewWidth <= 0f || viewHeight <= 0f) {
            1f
        } else {
            min(viewWidth / imageWidth, viewHeight / imageHeight)
        }

    val scale: Float get() = fitScale * zoom
    val originX: Float get() = (viewWidth - imageWidth * scale) / 2f + panX
    val originY: Float get() = (viewHeight - imageHeight * scale) / 2f + panY

    fun toImage(viewX: Float, viewY: Float): OcrPoint =
        OcrPoint((viewX - originX) / scale, (viewY - originY) / scale)

    fun toView(imageX: Float, imageY: Float): OcrPoint =
        OcrPoint(originX + imageX * scale, originY + imageY * scale)

    fun resized(width: Float, height: Float): OcrViewTransform =
        copy(viewWidth = width, viewHeight = height).clamped()

    /** Keeps the image point under the fingers' centroid under the fingers while zooming. */
    fun transformed(
        centroidX: Float,
        centroidY: Float,
        zoomChange: Float = 1f,
        panDx: Float = 0f,
        panDy: Float = 0f
    ): OcrViewTransform {
        val anchor = toImage(centroidX, centroidY)
        val newZoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val newScale = fitScale * newZoom
        val newOriginX = centroidX + panDx - anchor.x * newScale
        val newOriginY = centroidY + panDy - anchor.y * newScale
        return copy(
            zoom = newZoom,
            panX = newOriginX - (viewWidth - imageWidth * newScale) / 2f,
            panY = newOriginY - (viewHeight - imageHeight * newScale) / 2f
        ).clamped()
    }

    fun reset(): OcrViewTransform = copy(zoom = MIN_ZOOM, panX = 0f, panY = 0f)

    fun interpolatedTo(target: OcrViewTransform, fraction: Float): OcrViewTransform = copy(
        zoom = zoom + (target.zoom - zoom) * fraction,
        panX = panX + (target.panX - panX) * fraction,
        panY = panY + (target.panY - panY) * fraction
    )

    private fun clamped(): OcrViewTransform {
        val limitX = max(0f, (imageWidth * scale - viewWidth) / 2f)
        val limitY = max(0f, (imageHeight * scale - viewHeight) / 2f)
        return copy(panX = panX.coerceIn(-limitX, limitX), panY = panY.coerceIn(-limitY, limitY))
    }

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 6f
        const val DOUBLE_TAP_ZOOM = 2.5f
    }
}
