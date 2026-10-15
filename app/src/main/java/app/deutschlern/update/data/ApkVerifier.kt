package app.deutschlern.update.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import app.deutschlern.BuildConfig
import app.deutschlern.update.model.UpdateError
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Metadata extracted from an APK archive file.
 */
data class ArchiveMetadata(
    val packageName: String,
    val versionCode: Long,
    val signingCertSha256: String?
)

/**
 * Provider interface for extracting APK and installed app metadata.
 * Enables 100% unit testing of the verification pipeline with fakes.
 */
interface ApkMetadataProvider {
    fun computeFileSha256(file: File): String
    fun getArchiveInfo(file: File): ArchiveMetadata?
    fun getInstalledSigningCertSha256(): String?
    fun getInstalledVersionCode(): Long
    fun getInstalledPackageName(): String
}

/**
 * Default Android implementation using PackageManager and MessageDigest.
 */
class DefaultApkMetadataProvider(
    private val context: Context
) : ApkMetadataProvider {

    override fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun getArchiveInfo(file: File): ArchiveMetadata? {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val packageInfo = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return null
        val pkgName = packageInfo.packageName ?: return null

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        val certSha256 = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo
            if (signingInfo != null) {
                val signatures = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
                signatures.firstOrNull()?.let { computeCertSha256(it.toByteArray()) }
            } else null
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures?.firstOrNull()?.let { computeCertSha256(it.toByteArray()) }
        }

        return ArchiveMetadata(
            packageName = pkgName,
            versionCode = versionCode,
            signingCertSha256 = certSha256
        )
    }

    override fun getInstalledSigningCertSha256(): String? {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val packageInfo = pm.getPackageInfo(context.packageName, flags)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return null
            val signatures = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            signatures.firstOrNull()?.let { computeCertSha256(it.toByteArray()) }
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures?.firstOrNull()?.let { computeCertSha256(it.toByteArray()) }
        }
    }

    override fun getInstalledVersionCode(): Long {
        return BuildConfig.VERSION_CODE.toLong()
    }

    override fun getInstalledPackageName(): String {
        return context.packageName
    }

    private fun computeCertSha256(certBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}

/**
 * APK Verifier enforcing the 5 verification rules before any installation:
 * 1. SHA-256 equals asset digest (only if GitHub provides it).
 * 2. PackageManager.getPackageArchiveInfo parses; package name matches.
 * 3. versionCode is strictly greater than installed.
 * 4. Signing certificate digest equals installed app's certificate digest.
 * 5. Sanity limit on file size (<= 250 MB).
 */
class ApkVerifier(
    private val metadataProvider: ApkMetadataProvider,
    private val maxFileSizeBytes: Long = 250L * 1024L * 1024L // 250 MB
) {

    /**
     * Verifies the downloaded APK file.
     * Returns Result.success(Unit) or Result.failure(UpdateError).
     */
    fun verify(file: File, expectedDigest: String?): Result<Unit> {
        // 5. Sanity check on file size
        if (!file.exists()) {
            return Result.failure(UpdateError.GenericError("APK file does not exist"))
        }

        val fileSize = file.length()
        if (fileSize <= 0 || fileSize > maxFileSizeBytes) {
            file.delete()
            return Result.failure(UpdateError.FileTooLarge(fileSize, maxFileSizeBytes))
        }

        // 1. SHA-256 equals asset digest, only if GitHub provides it
        if (!expectedDigest.isNullOrBlank()) {
            val normalizedExpected = expectedDigest.removePrefix("sha256:").trim().lowercase()
            val actualHash = metadataProvider.computeFileSha256(file).lowercase()
            if (actualHash != normalizedExpected) {
                file.delete()
                return Result.failure(UpdateError.HashMismatch(expected = normalizedExpected, actual = actualHash))
            }
        }

        // 2. PackageManager parses archive; package name equals ours
        val archiveInfo = metadataProvider.getArchiveInfo(file)
        if (archiveInfo == null) {
            file.delete()
            return Result.failure(UpdateError.GenericError("Failed to parse APK archive metadata"))
        }

        val expectedPackage = metadataProvider.getInstalledPackageName()
        if (archiveInfo.packageName != expectedPackage) {
            file.delete()
            return Result.failure(UpdateError.InvalidPackageName(expected = expectedPackage, actual = archiveInfo.packageName))
        }

        // 3. versionCode strictly greater than installed (no downgrade)
        val installedVersionCode = metadataProvider.getInstalledVersionCode()
        if (archiveInfo.versionCode <= installedVersionCode) {
            file.delete()
            return Result.failure(
                UpdateError.DowngradeAttempt(
                    installedVersionCode = installedVersionCode,
                    downloadedVersionCode = archiveInfo.versionCode
                )
            )
        }

        // 4. Signing certificate digest equals installed app's certificate digest
        val installedCert = metadataProvider.getInstalledSigningCertSha256()
        val downloadedCert = archiveInfo.signingCertSha256

        if (installedCert.isNullOrBlank() || downloadedCert.isNullOrBlank()) {
            file.delete()
            return Result.failure(UpdateError.GenericError("Missing signing certificate information"))
        }

        val normalizedInstalled = installedCert.replace(":", "").lowercase()
        val normalizedDownloaded = downloadedCert.replace(":", "").lowercase()

        if (normalizedInstalled != normalizedDownloaded) {
            file.delete()
            return Result.failure(
                UpdateError.SignatureMismatch(
                    installedCert = normalizedInstalled,
                    downloadedCert = normalizedDownloaded
                )
            )
        }

        return Result.success(Unit)
    }
}
