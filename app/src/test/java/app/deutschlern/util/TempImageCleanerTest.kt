package app.deutschlern.util

import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
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
    fun `deleteTempUri deletes file if file scheme`() {
        val mockContext = mockk<Context>()
        val file = tempFolder.newFile("test_ocr.jpg")
        assertTrue(file.exists())

        val uri = mockk<Uri>()
        every { uri.scheme } returns "file"
        every { uri.path } returns file.absolutePath

        TempImageCleaner.deleteTempUri(mockContext, uri)

        assertFalse(file.exists())
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
