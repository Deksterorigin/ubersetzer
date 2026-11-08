package app.deutschlern.domain.ocr

import android.graphics.Bitmap
import android.net.Uri

/**
 * A page ready for word selection. The layout coordinates refer to [bitmap] itself, so whatever
 * draws the bitmap can overlay the words without knowing about EXIF rotation or downsampling.
 */
data class OcrScannedPage(
    val bitmap: Bitmap,
    val layout: OcrPageLayout
)

interface TextRecognitionEngine {
    /** Recognises the words of an image, on the device only. A page without text is a success. */
    suspend fun scanPage(imageUri: Uri): Result<OcrScannedPage>
}
