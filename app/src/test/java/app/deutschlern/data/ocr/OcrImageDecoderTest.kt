package app.deutschlern.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrImageDecoderTest {

    @Test
    fun `small image keeps its size`() {
        assertEquals(800 to 600, OcrImageDecoder.fitWithin(800, 600))
    }

    @Test
    fun `large image is scaled to the longest side keeping proportions`() {
        assertEquals(1536 to 2048, OcrImageDecoder.fitWithin(3000, 4000))
        assertEquals(2048 to 1536, OcrImageDecoder.fitWithin(4000, 3000))
    }

    @Test
    fun `extreme panorama never collapses to zero`() {
        assertEquals(2048 to 1, OcrImageDecoder.fitWithin(20000, 2))
    }

    @Test
    fun `sample size stays at or above the target resolution`() {
        assertEquals(1, OcrImageDecoder.sampleSizeFor(2048, 1000))
        assertEquals(1, OcrImageDecoder.sampleSizeFor(4000, 3000))
        assertEquals(2, OcrImageDecoder.sampleSizeFor(4096, 3000))
        assertEquals(4, OcrImageDecoder.sampleSizeFor(9000, 6000))
    }
}
