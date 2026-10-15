package app.deutschlern.update.data

import app.deutschlern.update.model.UpdateError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ApkVerifierTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeApkMetadataProvider : ApkMetadataProvider {
        var fileSha256Result = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
        var archiveInfoResult: ArchiveMetadata? = ArchiveMetadata(
            packageName = "app.deutschlern",
            versionCode = 2L,
            signingCertSha256 = "11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff"
        )
        var installedSigningCertSha256Result: String? = "11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff"
        var installedVersionCodeResult: Long = 1L
        var installedPackageNameResult: String = "app.deutschlern"

        override fun computeFileSha256(file: File): String = fileSha256Result
        override fun getArchiveInfo(file: File): ArchiveMetadata? = archiveInfoResult
        override fun getInstalledSigningCertSha256(): String? = installedSigningCertSha256Result
        override fun getInstalledVersionCode(): Long = installedVersionCodeResult
        override fun getInstalledPackageName(): String = installedPackageNameResult
    }

    private lateinit var fakeMetadataProvider: FakeApkMetadataProvider
    private lateinit var verifier: ApkVerifier
    private lateinit var testApk: File

    @Before
    fun setUp() {
        fakeMetadataProvider = FakeApkMetadataProvider()
        verifier = ApkVerifier(metadataProvider = fakeMetadataProvider, maxFileSizeBytes = 10L * 1024L * 1024L) // 10MB limit for test
        testApk = tempFolder.newFile("test.apk").apply {
            writeBytes(ByteArray(1024) { 0x42 })
        }
    }

    @Test
    fun verify_validApkWithMatchingDigest_succeeds() {
        val expectedDigest = "sha256:${fakeMetadataProvider.fileSha256Result}"
        val result = verifier.verify(testApk, expectedDigest)

        assertTrue(result.isSuccess)
    }

    @Test
    fun verify_validApkWithoutDigest_succeeds() {
        val result = verifier.verify(testApk, expectedDigest = null)

        assertTrue(result.isSuccess)
    }

    @Test
    fun verify_hashMismatch_failsWithHashMismatch() {
        val wrongDigest = "sha256:0000000000000000000000000000000000000000000000000000000000000000"
        val result = verifier.verify(testApk, wrongDigest)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Expected HashMismatch error but got: $error", error is UpdateError.HashMismatch)
        assertTrue(testApk.exists().not()) // File must be deleted on failure
    }

    @Test
    fun verify_wrongPackageName_failsWithInvalidPackageName() {
        fakeMetadataProvider.archiveInfoResult = fakeMetadataProvider.archiveInfoResult?.copy(
            packageName = "com.other.malicious.app"
        )

        val result = verifier.verify(testApk, expectedDigest = null)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Expected InvalidPackageName error but got: $error", error is UpdateError.InvalidPackageName)
        assertEquals("app.deutschlern", (error as UpdateError.InvalidPackageName).expected)
        assertEquals("com.other.malicious.app", error.actual)
    }

    @Test
    fun verify_downgradeVersionCode_failsWithDowngradeAttempt() {
        fakeMetadataProvider.installedVersionCodeResult = 5L
        fakeMetadataProvider.archiveInfoResult = fakeMetadataProvider.archiveInfoResult?.copy(
            versionCode = 4L
        )

        val result = verifier.verify(testApk, expectedDigest = null)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Expected DowngradeAttempt error but got: $error", error is UpdateError.DowngradeAttempt)
        assertEquals(5L, (error as UpdateError.DowngradeAttempt).installedVersionCode)
        assertEquals(4L, error.downloadedVersionCode)
    }

    @Test
    fun verify_equalVersionCode_failsWithDowngradeAttempt() {
        fakeMetadataProvider.installedVersionCodeResult = 2L
        fakeMetadataProvider.archiveInfoResult = fakeMetadataProvider.archiveInfoResult?.copy(
            versionCode = 2L
        )

        val result = verifier.verify(testApk, expectedDigest = null)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Expected DowngradeAttempt error but got: $error", error is UpdateError.DowngradeAttempt)
    }

    @Test
    fun verify_signatureMismatch_failsWithSignatureMismatch() {
        fakeMetadataProvider.archiveInfoResult = fakeMetadataProvider.archiveInfoResult?.copy(
            signingCertSha256 = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
        )

        val result = verifier.verify(testApk, expectedDigest = null)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Expected SignatureMismatch error but got: $error", error is UpdateError.SignatureMismatch)
    }

    @Test
    fun verify_fileTooLarge_failsWithFileTooLarge() {
        val largeFile = tempFolder.newFile("large.apk").apply {
            writeBytes(ByteArray(11 * 1024 * 1024) { 0x01 }) // 11 MB > 10 MB limit
        }

        val result = verifier.verify(largeFile, expectedDigest = null)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Expected FileTooLarge error but got: $error", error is UpdateError.FileTooLarge)
    }

    @Test
    fun verify_nonExistentFile_fails() {
        val nonExistent = File(tempFolder.root, "does_not_exist.apk")
        val result = verifier.verify(nonExistent, expectedDigest = null)

        assertTrue(result.isFailure)
    }
}
