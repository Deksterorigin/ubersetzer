package app.deutschlern.util

import android.content.Context
import android.net.Uri
import java.io.File

object TempImageCleaner {

    /**
     * Deletes a temporary image by its URI (either content:// or file://).
     * If the URI points to a file inside the app cache, it deletes the file.
     * Also calls contentResolver.delete if applicable.
     */
    fun deleteTempUri(context: Context, uri: Uri?) {
        if (uri == null) return
        try {
            if (uri.scheme == "file") {
                val path = uri.path
                if (path != null) {
                    val file = File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            } else if (uri.scheme == "content") {
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (_: Exception) {
                    // Ignored if provider does not support deletion
                }
            }
        } catch (_: Exception) {
            // Best effort cleanup
        }
    }

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
