package app.deutschlern.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** Decodes an image upright and downsampled, into the one bitmap both OCR and the UI work on. */
internal object OcrImageDecoder {

    private const val MAX_SIDE_PX = 2048

    fun decode(context: Context, uri: Uri): Bitmap {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeWithImageDecoder(context, uri)
        } else {
            decodeWithBitmapFactory(context, uri)
        }
        // ML Kit accepts only ARGB_8888 and an HDR source may still arrive as RGBA_F16
        return if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw IOException("Cannot convert bitmap from ${bitmap.config}")
        }
    }

    fun fitWithin(width: Int, height: Int, maxSide: Int = MAX_SIDE_PX): Pair<Int, Int> {
        val longest = max(width, height)
        if (longest <= maxSide) return width to height
        val ratio = maxSide.toFloat() / longest
        return (width * ratio).roundToInt().coerceAtLeast(1) to
            (height * ratio).roundToInt().coerceAtLeast(1)
    }

    fun sampleSizeFor(width: Int, height: Int, maxSide: Int = MAX_SIDE_PX): Int {
        var sampleSize = 1
        while (max(width, height) / (sampleSize * 2) >= maxSide) sampleSize *= 2
        return sampleSize
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(context: Context, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Hardware bitmaps cannot be read by ML Kit
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            val (width, height) = fitWithin(info.size.width, info.size.height)
            decoder.setTargetSize(width, height)
        }
    }

    private fun decodeWithBitmapFactory(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Cannot decode $uri")
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val (targetWidth, _) = fitWithin(decoded.width, decoded.height)
        val matrix = exifMatrix(orientation).apply {
            val scale = targetWidth.toFloat() / decoded.width
            postScale(scale, scale)
        }
        if (matrix.isIdentity) return decoded
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (upright !== decoded) decoded.recycle()
        return upright
    }

    private fun exifMatrix(orientation: Int): Matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                postRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                postRotate(-90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(-90f)
        }
    }
}
