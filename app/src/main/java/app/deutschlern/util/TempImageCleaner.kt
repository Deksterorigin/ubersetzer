package app.deutschlern.util

import android.content.Context
import android.net.Uri
import java.io.File

object TempImageCleaner {

    /**
     * Deletes an image only if it is the app's own temporary copy: a file in the app cache (camera
     * capture, document scanner) or a URI of the app's own FileProvider. Anything else is the
     * user's media picked from the gallery and must survive, whatever grants the URI carries.
     */
    fun deleteTempUri(context: Context, uri: Uri?) {
        if (uri == null) return
        try {
            when (uri.scheme) {
                "file" -> {
                    val file = File(uri.path ?: return)
                    if (file.isInside(context.cacheDir)) file.delete()
                }
                "content" -> {
                    if (uri.authority == "${context.packageName}.fileprovider") {
                        context.contentResolver.delete(uri, null, null)
                    }
                }
            }
        } catch (_: Exception) {
            // Best effort: temp_ocr is also wiped on the next app start
        }
    }

    private fun File.isInside(directory: File): Boolean =
        canonicalPath.startsWith(directory.canonicalPath + File.separator)

    /**
     * Clears all temporary images inside the `temp_ocr` cache directory.
     */
    fun clearTempOcrDirectory(context: Context) {
        try {
            val dir = File(context.cacheDir, "temp_ocr")
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    try {
                        file.delete()
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Creates a new temporary file in `cacheDir/temp_ocr` for camera capture.
     */
    fun createTempImageFile(context: Context): File {
        val dir = File(context.cacheDir, "temp_ocr").apply {
            if (!exists()) mkdirs()
        }
        return File.createTempFile("ocr_capture_", ".jpg", dir)
    }
}
