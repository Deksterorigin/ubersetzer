package app.deutschlern.update.model

/**
 * Validated release info from GitHub Releases.
 */
data class ReleaseInfo(
    val tagName: String,
    val version: AppVersion,
    val releaseNotes: String,
    val apkFileName: String,
    val downloadUrl: String,
    val apkSizeBytes: Long,
    val expectedDigest: String?
) {
    /**
     * Human-readable formatted size in MB.
     */
    val formattedSize: String
        get() {
            val mb = apkSizeBytes.toDouble() / (1024.0 * 1024.0)
            return "%.1f MB".format(mb)
        }
}
