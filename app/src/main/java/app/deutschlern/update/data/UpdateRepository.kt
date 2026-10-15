package app.deutschlern.update.data

import app.deutschlern.update.model.ReleaseInfo
import app.deutschlern.update.receiver.InstallEvent
import kotlinx.coroutines.flow.SharedFlow
import java.io.File

/**
 * Repository interface for GitHub Releases self-update operations.
 */
interface UpdateRepository {

    /**
     * Observable stream of installation events from PackageInstaller.
     */
    val installEvents: SharedFlow<InstallEvent>

    /**
     * Checks GitHub Releases for a newer version than current.
     * Returns Result.success(ReleaseInfo) if newer version available,
     * Result.success(null) if app is up to date,
     * or Result.failure(UpdateError) on error.
     */
    suspend fun checkForUpdates(): Result<ReleaseInfo?>

    /**
     * Downloads the APK file to app-private cache with progress tracking and cancellation.
     */
    suspend fun downloadApk(
        release: ReleaseInfo,
        requireWifi: Boolean,
        onProgress: (progressPercent: Int, bytesRead: Long, totalBytes: Long) -> Unit
    ): Result<File>

    /**
     * Cancels an ongoing download if active.
     */
    fun cancelDownload()

    /**
     * Verifies the downloaded APK before installation.
     */
    fun verifyApk(file: File, expectedDigest: String?): Result<Unit>

    /**
     * Initiates PackageInstaller session.
     */
    fun installApk(file: File): Result<Unit>

    /**
     * Checks if the app has permission to request package installs.
     */
    fun canRequestPackageInstalls(): Boolean

    /**
     * Cleans up temporary update files.
     */
    fun cleanupTempFiles()

    /**
     * Returns current installed versionName.
     */
    fun getCurrentVersionName(): String

    /**
     * Returns current installed versionCode.
     */
    fun getCurrentVersionCode(): Long
}
