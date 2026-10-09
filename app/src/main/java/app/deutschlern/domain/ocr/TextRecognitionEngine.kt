package app.deutschlern.domain.ocr

import android.graphics.Bitmap
import android.net.Uri

interface TextRecognitionEngine {
    /**
     * Recognizes German text from an image URI (e.g. from scanner or camera).
     * Applies OcrPostProcessor to dehyphenate, clean whitespace and assemble paragraphs.
     */
    suspend fun recognizeTextFromUri(imageUri: Uri): Result<String>

    /**
     * Recognizes German text from an in-memory Bitmap.
     */
    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): Result<String>
}
