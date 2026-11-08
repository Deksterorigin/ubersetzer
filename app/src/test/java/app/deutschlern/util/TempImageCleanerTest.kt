package app.deutschlern.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TempImageCleanerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `createTempImageFile creates file in temp_ocr directory`() {
        val mockContext = mockk<Context>()
        val cacheDir = tempFolder.newFolder("cache")
        every { mockContext.cacheDir } returns cacheDir

        val file = TempImageCleaner.createTempImageFile(mockContext)

        assertTrue(file.exists())
        assertTrue(file.parentFile?.name == "temp_ocr")
        assertTrue(file.name.startsWith("ocr_capture_"))
        assertTrue(file.name.endsWith(".jpg"))
    }

    @Test
    fun `deleteTempUri deletes a file inside the app cache`() {
        val mockContext = mockk<Context>()
        val cacheDir = tempFolder.newFolder("cache3")
        every { mockContext.cacheDir } returns cacheDir
        val file = File(cacheDir, "mlkit_docscan/page.jpg").apply {
            parentFile?.mkdirs()
            writeText("x")
        }

        TempImageCleaner.deleteTempUri(mockContext, fileUri(file.absolutePath))

        assertFalse(file.exists())
    }

    @Test
    fun `deleteTempUri leaves a file outside the app cache alone`() {
        val mockContext = mockk<Context>()
        every { mockContext.cacheDir } returns tempFolder.newFolder("cache4")
        val outside = tempFolder.newFile("user_photo.jpg")

        TempImageCleaner.deleteTempUri(mockContext, fileUri(outside.absolutePath))

        assertTrue(outside.exists())
    }

    @Test
    fun `deleteTempUri is not fooled by a path that climbs out of the cache`() {
        val mockContext = mockk<Context>()
        val cacheDir = tempFolder.newFolder("cache5")
        every { mockContext.cacheDir } returns cacheDir
        val outside = tempFolder.newFile("escape.jpg")

        TempImageCleaner.deleteTempUri(
            mockContext,
            fileUri("${cacheDir.absolutePath}${File.separator}..${File.separator}${outside.name}")
        )

        assertTrue(outside.exists())
    }

    @Test
    fun `deleteTempUri deletes through the app's own FileProvider`() {
        val resolver = mockk<ContentResolver>(relaxed = true)
        val mockContext = mockk<Context>()
        every { mockContext.packageName } returns "app.deutschlern"
        every { mockContext.contentResolver } returns resolver
        val uri = contentUri("app.deutschlern.fileprovider")

        TempImageCleaner.deleteTempUri(mockContext, uri)

        verify(exactly = 1) { resolver.delete(uri, null, null) }
    }

    @Test
    fun `deleteTempUri never deletes gallery media`() {
        val resolver = mockk<ContentResolver>(relaxed = true)
        val mockContext = mockk<Context>()
        every { mockContext.packageName } returns "app.deutschlern"
        every { mockContext.contentResolver } returns resolver

        TempImageCleaner.deleteTempUri(mockContext, contentUri("media"))
        TempImageCleaner.deleteTempUri(mockContext, contentUri("com.android.providers.media.documents"))

        verify(exactly = 0) { resolver.delete(any(), any(), any()) }
    }

    private fun fileUri(path: String): Uri = mockk<Uri>().also {
        every { it.scheme } returns "file"
        every { it.path } returns path
    }

    private fun contentUri(uriAuthority: String): Uri = mockk<Uri>().also {
        every { it.scheme } returns "content"
        every { it.authority } returns uriAuthority
    }

    @Test
    fun `clearTempOcrDirectory deletes all files in temp_ocr`() {
        val mockContext = mockk<Context>()
        val cacheDir = tempFolder.newFolder("cache2")
        every { mockContext.cacheDir } returns cacheDir

        val ocrDir = File(cacheDir, "temp_ocr").apply { mkdirs() }
        val file1 = File(ocrDir, "file1.jpg").apply { writeText("1") }
        val file2 = File(ocrDir, "file2.jpg").apply { writeText("2") }
        assertTrue(file1.exists() && file2.exists())

        TempImageCleaner.clearTempOcrDirectory(mockContext)

        assertFalse(file1.exists())
        assertFalse(file2.exists())
    }
}
