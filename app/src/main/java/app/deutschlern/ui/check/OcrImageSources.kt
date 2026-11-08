package app.deutschlern.ui.check

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import app.deutschlern.util.TempImageCleaner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

private const val MAX_PAGES = 3

class OcrImageSources(
    val launchScanner: () -> Unit,
    val launchCamera: () -> Unit,
    val launchGallery: () -> Unit
)

@Composable
fun rememberOcrImageSources(
    onImages: (List<Uri>) -> Unit,
    onUnavailable: () -> Unit
): OcrImageSources {
    val context = LocalContext.current
    // Saveable: rotating the phone in the camera app recreates the activity before the result arrives
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = result.data
                ?.let { GmsDocumentScanningResult.fromActivityResultIntent(it) }
                ?.pages
                .orEmpty()
                .mapNotNull { it.imageUri }
            if (uris.isNotEmpty()) onImages(uris)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (uri != null) {
            if (success) onImages(listOf(uri)) else TempImageCleaner.deleteTempUri(context, uri)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_PAGES)
    ) { uris ->
        if (uris.isNotEmpty()) onImages(uris)
    }

    val launchCamera = {
        val file = runCatching { TempImageCleaner.createTempImageFile(context) }.getOrNull()
        try {
            if (file == null) error("No temp file")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (_: Exception) {
            file?.delete()
            pendingCameraUri = null
            onUnavailable()
        }
    }

    val launchScanner = {
        val activity = context.findActivity()
        if (activity == null) {
            launchCamera()
        } else {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(MAX_PAGES)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            try {
                GmsDocumentScanning.getClient(options)
                    .getStartScanIntent(activity)
                    .addOnSuccessListener { intentSender ->
                        try {
                            scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                        } catch (_: Exception) {
                            launchCamera()
                        }
                    }
                    // Devices without Play Services' scanner module still get the plain camera
                    .addOnFailureListener { launchCamera() }
            } catch (_: Exception) {
                launchCamera()
            }
        }
    }

    val launchGallery = {
        try {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (_: Exception) {
            onUnavailable()
        }
    }

    return OcrImageSources(
        launchScanner = { launchScanner() },
        launchCamera = { launchCamera() },
        launchGallery = { launchGallery() }
    )
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
