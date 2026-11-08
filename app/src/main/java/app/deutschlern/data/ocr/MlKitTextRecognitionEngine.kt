package app.deutschlern.data.ocr

import android.content.Context
import android.net.Uri
import app.deutschlern.domain.ocr.OcrPageLayout
import app.deutschlern.domain.ocr.OcrPoint
import app.deutschlern.domain.ocr.OcrScannedPage
import app.deutschlern.domain.ocr.OcrWord
import app.deutschlern.domain.ocr.TextRecognitionEngine
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class MlKitTextRecognitionEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : TextRecognitionEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun scanPage(imageUri: Uri): Result<OcrScannedPage> = withContext(Dispatchers.IO) {
        try {
            // fromBitmap with rotation 0 on the same bitmap the UI draws: fromFilePath would rotate
            // internally and the boxes would no longer match a plain decode of the file
            val bitmap = OcrImageDecoder.decode(context, imageUri)
            val visionText = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            Result.success(OcrScannedPage(bitmap, visionText.toLayout(bitmap.width, bitmap.height)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } catch (e: OutOfMemoryError) {
            Result.failure(IOException("Image too large", e))
        }
    }

    private fun Text.toLayout(width: Int, height: Int): OcrPageLayout {
        val words = mutableListOf<OcrWord>()
        var lineIndex = 0
        textBlocks.forEachIndexed { blockIndex, block ->
            block.lines.forEach { line ->
                line.elements.forEach { element ->
                    val corners = element.corners()
                    if (corners != null && element.text.isNotBlank()) {
                        words += OcrWord(words.size, element.text, blockIndex, lineIndex, corners)
                    }
                }
                lineIndex++
            }
        }
        return OcrPageLayout(width, height, words)
    }

    private fun Text.Element.corners(): List<OcrPoint>? {
        cornerPoints?.takeIf { it.size == 4 }?.let { points ->
            return points.map { OcrPoint(it.x.toFloat(), it.y.toFloat()) }
        }
        return boundingBox?.let { box ->
            listOf(
                OcrPoint(box.left.toFloat(), box.top.toFloat()),
                OcrPoint(box.right.toFloat(), box.top.toFloat()),
                OcrPoint(box.right.toFloat(), box.bottom.toFloat()),
                OcrPoint(box.left.toFloat(), box.bottom.toFloat())
            )
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            cont.resume(result)
        }
        addOnFailureListener { exception ->
            cont.resumeWithException(exception)
        }
    }
}
