package app.deutschlern.update.model

import java.io.File

/**
 * State of the self-update feature in Settings.
 */
sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpToDate(val currentVersion: String) : UpdateState()
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateState()
    data class Downloading(
        val release: ReleaseInfo,
        val progressPercent: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : UpdateState()
    data class Verifying(val release: ReleaseInfo) : UpdateState()
    data class PermissionRequired(val release: ReleaseInfo, val apkFile: File) : UpdateState()
    data class ReadyToInstall(val release: ReleaseInfo, val apkFile: File) : UpdateState()
    data class Installing(val release: ReleaseInfo) : UpdateState()
    data class Error(val error: UpdateError) : UpdateState()
}
