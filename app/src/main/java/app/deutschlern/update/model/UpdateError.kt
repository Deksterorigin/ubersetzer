package app.deutschlern.update.model

/**
 * Domain errors for the self-update feature.
 */
sealed class UpdateError : Exception() {
    object NetworkError : UpdateError()
    object RateLimitExceeded : UpdateError()
    object NoReleasesFound : UpdateError()
    data class ParseVersionError(val rawTag: String) : UpdateError()
    object NoApkAsset : UpdateError()
    data class MultipleApkAssets(val count: Int) : UpdateError()
    object WifiRequired : UpdateError()
    data class InsufficientStorage(val requiredBytes: Long, val availableBytes: Long) : UpdateError()
    data class HashMismatch(val expected: String, val actual: String) : UpdateError()
    data class InvalidPackageName(val expected: String, val actual: String) : UpdateError()
    data class DowngradeAttempt(val installedVersionCode: Long, val downloadedVersionCode: Long) : UpdateError()
    data class SignatureMismatch(val installedCert: String, val downloadedCert: String) : UpdateError()
    data class FileTooLarge(val sizeBytes: Long, val limitBytes: Long) : UpdateError()
    data class InstallFailed(val statusCode: Int, val statusMessage: String?) : UpdateError()
    data class GenericError(override val message: String) : UpdateError()
}
