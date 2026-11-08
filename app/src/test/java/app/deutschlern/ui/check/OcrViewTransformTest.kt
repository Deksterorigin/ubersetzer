package app.deutschlern.ui.check

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrViewTransformTest {

    private val delta = 0.01f

    // 1000x2000 image in a 500x500 view: fit scale 0.25, 125 px letterbox left and right
    private val fitted = OcrViewTransform(1000, 2000).resized(500f, 500f)

    @Test
    fun `fit scale uses the tighter side and centres the image`() {
        assertEquals(0.25f, fitted.scale, delta)
        assertEquals(125f, fitted.originX, delta)
        assertEquals(0f, fitted.originY, delta)
    }

    @Test
    fun `image corners map onto the letterboxed area`() {
        assertEquals(125f, fitted.toView(0f, 0f).x, delta)
        assertEquals(375f, fitted.toView(1000f, 2000f).x, delta)
        assertEquals(500f, fitted.toView(1000f, 2000f).y, delta)
    }

    @Test
    fun `view to image inverts image to view under zoom and pan`() {
        val zoomed = fitted.transformed(300f, 200f, zoomChange = 3f, panDx = 10f, panDy = -20f)
        val view = zoomed.toView(321f, 654f)
        val image = zoomed.toImage(view.x, view.y)
        assertEquals(321f, image.x, delta)
        assertEquals(654f, image.y, delta)
    }

    @Test
    fun `zoom keeps the image point under the centroid`() {
        val before = fitted.toImage(250f, 250f)
        val zoomed = fitted.transformed(250f, 250f, zoomChange = 2f)
        val after = zoomed.toImage(250f, 250f)
        assertEquals(before.x, after.x, delta)
        assertEquals(before.y, after.y, delta)
        assertEquals(0.5f, zoomed.scale, delta)
    }

    @Test
    fun `zoom is limited at both ends`() {
        assertEquals(OcrViewTransform.MIN_ZOOM, fitted.transformed(0f, 0f, zoomChange = 0.1f).zoom, delta)
        assertEquals(OcrViewTransform.MAX_ZOOM, fitted.transformed(0f, 0f, zoomChange = 100f).zoom, delta)
    }

    @Test
    fun `pan cannot move a fitted image`() {
        val panned = fitted.transformed(100f, 100f, panDx = 300f, panDy = 300f)
        assertEquals(0f, panned.panX, delta)
        assertEquals(0f, panned.panY, delta)
    }

    @Test
    fun `pan stops at the image edge when zoomed`() {
        // Zoom 4: the image is 1000x2000 on screen, so it can travel 250 and 750 from centre
        val zoomed = fitted.transformed(250f, 250f, zoomChange = 4f)
        val panned = zoomed.transformed(250f, 250f, panDx = 5000f, panDy = -5000f)
        assertEquals(250f, panned.panX, delta)
        assertEquals(-750f, panned.panY, delta)
    }

    @Test
    fun `shrinking the view after a rotation clamps the pan`() {
        val zoomed = fitted.transformed(0f, 0f, zoomChange = 2f)
        val rotated = zoomed.resized(2000f, 500f)
        assertEquals(0f, rotated.panX, delta)
    }

    @Test
    fun `reset returns to the fitted state`() {
        val back = fitted.transformed(100f, 100f, zoomChange = 3f).reset()
        assertEquals(fitted, back)
    }
}
