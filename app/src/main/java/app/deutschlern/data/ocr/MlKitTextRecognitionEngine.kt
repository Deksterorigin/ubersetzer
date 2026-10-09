package app.deutschlern.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import app.deutschlern.domain.ocr.OcrPostProcessor
import app.deutschlern.domain.ocr.TextRecognitionEngine
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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

    override suspend fun recognizeTextFromUri(imageUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromFilePath(context, imageUri)
            val visionText = recognizer.process(inputImage).await()
            val rawText = visionText.text
            val processed = OcrPostProcessor.process(rawText)
            Result.success(processed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recognizeTextFromBitmap(bitmap: Bitmap): Result<String> = withContext(Dispatchers.Default) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(inputImage).await()
            val rawText = visionText.text
            val processed = OcrPostProcessor.process(rawText)
            Result.success(processed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            cont.resume(result)
        }
        addOnFailureListener { exception ->
            cont.resumeWithException(exception)
        }
        cont.invokeOnCancellation {
            // Task cancellation is best-effort
        }
    }
}
