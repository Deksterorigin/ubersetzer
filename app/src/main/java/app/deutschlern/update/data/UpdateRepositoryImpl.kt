package app.deutschlern.update.data

import android.content.Context
import app.deutschlern.BuildConfig
import app.deutschlern.update.data.dto.GitHubReleaseDto
import app.deutschlern.update.model.AppVersion
import app.deutschlern.update.model.ReleaseInfo
import app.deutschlern.update.model.UpdateError
import app.deutschlern.update.receiver.InstallEvent
import app.deutschlern.update.receiver.InstallStatusNotifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import app.deutschlern.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepositoryImpl(
    private val context: Context,
    private val json: Json,
    private val apkVerifier: ApkVerifier,
    private val packageInstallerWrapper: PackageInstallerWrapper,
    private val connectivityProvider: NetworkConnectivityProvider,
    private val statusNotifier: InstallStatusNotifier,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val releasesApiUrl: String = RELEASES_API_URL
) : UpdateRepository {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        json: Json,
        apkVerifier: ApkVerifier,
        packageInstallerWrapper: PackageInstallerWrapper,
        connectivityProvider: NetworkConnectivityProvider,
        statusNotifier: InstallStatusNotifier,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) : this(
        context = context,
        json = json,
        apkVerifier = apkVerifier,
        packageInstallerWrapper = packageInstallerWrapper,
        connectivityProvider = connectivityProvider,
        statusNotifier = statusNotifier,
        ioDispatcher = ioDispatcher,
        releasesApiUrl = RELEASES_API_URL
    )

    override val installEvents: SharedFlow<InstallEvent> = statusNotifier.events

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private var activeCall: Call? = null
    private val downloadLock = Any()

    override fun getCurrentVersionName(): String = BuildConfig.VERSION_NAME
    override fun getCurrentVersionCode(): Long = BuildConfig.VERSION_CODE.toLong()

    override suspend fun checkForUpdates(): Result<ReleaseInfo?> = withContext(ioDispatcher) {
        if (!connectivityProvider.isConnected()) {
            return@withContext Result.failure(UpdateError.NetworkError)
        }

        val request = Request.Builder()
            .url(releasesApiUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/vnd.github.v3+json")
            .get()
            .build()

        try {
            downloadClient.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val body = response.body?.string() ?: return@withContext Result.failure(UpdateError.NetworkError)
                        val releaseDto = try {
                            json.decodeFromString<GitHubReleaseDto>(body)
                        } catch (e: Exception) {
                            return@withContext Result.failure(UpdateError.GenericError("Failed to parse release JSON: ${e.message}"))
                        }

                        val rawTag = releaseDto.tagName
                        val releaseVersion = AppVersion.parseOrNull(rawTag)
                            ?: return@withContext Result.failure(UpdateError.ParseVersionError(rawTag))

                        // Filter apk assets
                        val apkAssets = releaseDto.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
                        if (apkAssets.isEmpty()) {
                            return@withContext Result.failure(UpdateError.NoApkAsset)
                        }
                        if (apkAssets.size > 1) {
                            return@withContext Result.failure(UpdateError.MultipleApkAssets(apkAssets.size))
                        }

                        val apkAsset = apkAssets.first()
                        val currentVersionName = getCurrentVersionName()
                        val currentVersion = AppVersion.parseOrNull(currentVersionName)
                            ?: AppVersion(currentVersionName, listOf(BuildConfig.VERSION_CODE, 0))

                        if (releaseVersion > currentVersion) {
                            val releaseInfo = ReleaseInfo(
                                tagName = releaseDto.tagName,
                                version = releaseVersion,
                                releaseNotes = releaseDto.body.orEmpty(),
                                apkFileName = apkAsset.name,
                                downloadUrl = apkAsset.downloadUrl,
                                apkSizeBytes = apkAsset.size,
                                expectedDigest = apkAsset.digest
                            )
                            Result.success(releaseInfo)
                        } else {
                            // Up to date
                            Result.success(null)
                        }
                    }

                    403, 429 -> Result.failure(UpdateError.RateLimitExceeded)
                    404 -> Result.failure(UpdateError.NoReleasesFound)
                    else -> Result.failure(UpdateError.GenericError("GitHub API returned HTTP ${response.code}"))
                }
            }
        } catch (_: IOException) {
            Result.failure(UpdateError.NetworkError)
        } catch (e: Exception) {
            Result.failure(UpdateError.GenericError(e.message ?: "Unknown error"))
        }
    }

    override suspend fun downloadApk(
        release: ReleaseInfo,
        requireWifi: Boolean,
        onProgress: (progressPercent: Int, bytesRead: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(ioDispatcher) {
        if (!connectivityProvider.isConnected()) {
            return@withContext Result.failure(UpdateError.NetworkError)
        }

        if (requireWifi && !connectivityProvider.isWifiConnected()) {
            return@withContext Result.failure(UpdateError.WifiRequired)
        }

        val updatesDir = getUpdatesDir()
        val requiredSpace = release.apkSizeBytes + MIN_FREE_SPACE_BUFFER_BYTES
        if (updatesDir.usableSpace < requiredSpace) {
            return@withContext Result.failure(
                UpdateError.InsufficientStorage(
                    requiredBytes = requiredSpace,
                    availableBytes = updatesDir.usableSpace
                )
            )
        }

        // Clean any old downloads
        cleanupTempFiles()

        val targetFile = File(updatesDir, "update_${release.version.raw.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.apk")
        val partFile = File(updatesDir, "${targetFile.name}.part")

        val request = Request.Builder()
            .url(release.downloadUrl)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        val call: Call
        synchronized(downloadLock) {
            activeCall?.cancel()
            call = downloadClient.newCall(request)
            activeCall = call
        }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    partFile.delete()
                    return@withContext Result.failure(UpdateError.GenericError("Download failed with HTTP ${response.code}"))
                }

                val body = response.body ?: run {
                    partFile.delete()
                    return@withContext Result.failure(UpdateError.NetworkError)
                }

                val totalBytes = if (body.contentLength() > 0) body.contentLength() else release.apkSizeBytes
                var bytesRead = 0L
                var lastReportedPercent = -1

                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            if (!isActive || call.isCanceled()) {
                                partFile.delete()
                                return@withContext Result.failure(UpdateError.GenericError("Download cancelled"))
                            }
                            output.write(buffer, 0, read)
                            bytesRead += read

                            val percent = if (totalBytes > 0) ((bytesRead * 100) / totalBytes).toInt() else 0
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                onProgress(percent, bytesRead, totalBytes)
                            }
                        }
                        output.flush()
                    }
                }

                if (partFile.exists()) {
                    targetFile.delete()
                    if (partFile.renameTo(targetFile)) {
                        Result.success(targetFile)
                    } else {
                        partFile.delete()
                        Result.failure(UpdateError.GenericError("Failed to rename temporary APK file"))
                    }
                } else {
                    Result.failure(UpdateError.GenericError("Downloaded file missing"))
                }
            }
        } catch (e: IOException) {
            partFile.delete()
            targetFile.delete()
            if (call.isCanceled()) {
                Result.failure(UpdateError.GenericError("Download cancelled"))
            } else {
                Result.failure(UpdateError.NetworkError)
            }
        } catch (e: Exception) {
            partFile.delete()
            targetFile.delete()
            Result.failure(UpdateError.GenericError(e.message ?: "Download failed"))
        } finally {
            synchronized(downloadLock) {
                if (activeCall == call) {
                    activeCall = null
                }
            }
        }
    }

    override fun cancelDownload() {
        synchronized(downloadLock) {
            activeCall?.cancel()
            activeCall = null
        }
        cleanupTempFiles()
    }

    override fun verifyApk(file: File, expectedDigest: String?): Result<Unit> {
        return apkVerifier.verify(file, expectedDigest)
    }

    override fun installApk(file: File): Result<Unit> {
        return packageInstallerWrapper.install(file)
    }

    override fun canRequestPackageInstalls(): Boolean {
        return packageInstallerWrapper.canRequestPackageInstalls()
    }

    override fun cleanupTempFiles() {
        try {
            val dir = getUpdatesDir()
            dir.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    private fun getUpdatesDir(): File {
        val dir = File(context.cacheDir, "updates")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    companion object {
        const val GITHUB_OWNER = "Deksterorigin"
        const val GITHUB_REPO = "ubersetzer"
        const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
        const val USER_AGENT = "DeutschLern-App"
        private const val MIN_FREE_SPACE_BUFFER_BYTES = 20L * 1024L * 1024L // 20 MB buffer
    }
}
