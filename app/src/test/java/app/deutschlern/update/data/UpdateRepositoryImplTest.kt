package app.deutschlern.update.data

import android.content.Context
import app.deutschlern.update.model.AppVersion
import app.deutschlern.update.model.ReleaseInfo
import app.deutschlern.update.model.UpdateError
import app.deutschlern.update.receiver.InstallStatusNotifier
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockWebServer: MockWebServer
    private val testDispatcher = StandardTestDispatcher()

    private val context = mockk<Context>(relaxed = true)
    private val apkVerifier = mockk<ApkVerifier>(relaxed = true)
    private val packageInstallerWrapper = mockk<PackageInstallerWrapper>(relaxed = true)
    private val statusNotifier = InstallStatusNotifier()

    private class FakeConnectivityProvider : NetworkConnectivityProvider {
        var connected = true
        var wifi = true
        override fun isConnected(): Boolean = connected
        override fun isWifiConnected(): Boolean = wifi
    }

    private val connectivityProvider = FakeConnectivityProvider()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private lateinit var repository: UpdateRepositoryImpl

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        every { context.cacheDir } returns tempFolder.root

        repository = UpdateRepositoryImpl(
            context = context,
            json = json,
            apkVerifier = apkVerifier,
            packageInstallerWrapper = packageInstallerWrapper,
            connectivityProvider = connectivityProvider,
            statusNotifier = statusNotifier,
            ioDispatcher = testDispatcher,
            releasesApiUrl = mockWebServer.url("/releases/latest").toString()
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun checkForUpdates_offline_returnsNetworkError() = runTest(testDispatcher) {
        connectivityProvider.connected = false

        val result = repository.checkForUpdates()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UpdateError.NetworkError)
    }

    @Test
    fun checkForUpdates_rateLimit403_returnsRateLimitExceeded() = runTest(testDispatcher) {
        mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody("rate limit exceeded"))

        val result = repository.checkForUpdates()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UpdateError.RateLimitExceeded)
    }

    @Test
    fun checkForUpdates_rateLimit429_returnsRateLimitExceeded() = runTest(testDispatcher) {
        mockWebServer.enqueue(MockResponse().setResponseCode(429).setBody("too many requests"))

        val result = repository.checkForUpdates()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UpdateError.RateLimitExceeded)
    }

    @Test
    fun checkForUpdates_notFound404_returnsNoReleasesFound() = runTest(testDispatcher) {
        mockWebServer.enqueue(MockResponse().setResponseCode(404).setBody("Not found"))

        val result = repository.checkForUpdates()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UpdateError.NoReleasesFound)
    }

    @Test
    fun checkForUpdates_newerVersionAvailable_returnsReleaseInfo() = runTest(testDispatcher) {
        val releaseJson = """
            {
              "tag_name": "v99.0.0",
              "body": "Massive update",
              "assets": [
                {
                  "name": "DeutschLern-v99.0.0.apk",
                  "size": 50000000,
                  "browser_download_url": "https://github.com/Deksterorigin/ubersetzer/releases/download/v99.0.0/app.apk",
                  "digest": "sha256:1234567890abcdef"
                }
              ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(releaseJson))

        val result = repository.checkForUpdates()

        assertTrue(result.isSuccess)
        val release = result.getOrNull()
        assertNotNull(release)
        assertEquals("v99.0.0", release?.tagName)
        assertEquals("DeutschLern-v99.0.0.apk", release?.apkFileName)
        assertEquals(50000000L, release?.apkSizeBytes)
        assertEquals("sha256:1234567890abcdef", release?.expectedDigest)
    }

    @Test
    fun checkForUpdates_currentOrOlderVersion_returnsNullUpToDate() = runTest(testDispatcher) {
        val releaseJson = """
            {
              "tag_name": "v0.1.0",
              "assets": [
                {
                  "name": "DeutschLern-v0.1.0.apk",
                  "size": 50000000,
                  "browser_download_url": "https://github.com/Deksterorigin/ubersetzer/download/app.apk"
                }
              ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(releaseJson))

        val result = repository.checkForUpdates()

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull()) // null signifies already up to date
    }

    @Test
    fun checkForUpdates_noApkAsset_returnsNoApkAssetError() = runTest(testDispatcher) {
        val releaseJson = """
            {
              "tag_name": "v99.0.0",
              "assets": [
                {
                  "name": "source.tar.gz",
                  "size": 1000,
                  "browser_download_url": "https://github.com/app/source.tar.gz"
                }
              ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(releaseJson))

        val result = repository.checkForUpdates()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UpdateError.NoApkAsset)
    }

    @Test
    fun checkForUpdates_multipleApkAssets_returnsMultipleApkAssetsError() = runTest(testDispatcher) {
        val releaseJson = """
            {
              "tag_name": "v99.0.0",
              "assets": [
                {
                  "name": "app-arm64.apk",
                  "size": 1000,
                  "browser_download_url": "https://github.com/app/app-arm64.apk"
                },
                {
                  "name": "app-x86.apk",
                  "size": 1000,
                  "browser_download_url": "https://github.com/app/app-x86.apk"
                }
              ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(releaseJson))

        val result = repository.checkForUpdates()

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is UpdateError.MultipleApkAssets)
        assertEquals(2, (error as UpdateError.MultipleApkAssets).count)
    }

    @Test
    fun downloadApk_wifiRequiredWhenNotOnWifi_returnsWifiRequiredError() = runTest(testDispatcher) {
        connectivityProvider.wifi = false

        val release = ReleaseInfo(
            tagName = "v2.0",
            version = AppVersion.parse("2.0"),
            releaseNotes = "Notes",
            apkFileName = "app.apk",
            downloadUrl = mockWebServer.url("/download/app.apk").toString(),
            apkSizeBytes = 1024L,
            expectedDigest = null
        )

        val result = repository.downloadApk(release = release, requireWifi = true) { _, _, _ -> }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UpdateError.WifiRequired)
    }

    @Test
    fun downloadApk_successfulDownloadWithRedirect_downloadsFileAndTracksProgress() = runTest(testDispatcher) {
        val apkData = ByteArray(1024 * 50) { 0x33 } // 50 KB

        // 1st request: 302 redirect
        val redirectUrl = mockWebServer.url("/actual/download/app.apk")
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", redirectUrl.toString())
        )

        // 2nd request: actual binary
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(apkData))
        )

        val release = ReleaseInfo(
            tagName = "v2.0",
            version = AppVersion.parse("2.0"),
            releaseNotes = "Notes",
            apkFileName = "app.apk",
            downloadUrl = mockWebServer.url("/download/app.apk").toString(),
            apkSizeBytes = apkData.size.toLong(),
            expectedDigest = null
        )

        val progressUpdates = mutableListOf<Int>()
        val result = repository.downloadApk(release = release, requireWifi = false) { percent, _, _ ->
            progressUpdates.add(percent)
        }

        assertTrue(result.isSuccess)
        val downloadedFile = result.getOrNull()
        assertNotNull(downloadedFile)
        assertTrue(downloadedFile!!.exists())
        assertEquals(apkData.size.toLong(), downloadedFile.length())
        assertTrue(progressUpdates.isNotEmpty())
        assertEquals(100, progressUpdates.last())
    }

    @Test
    fun cleanupTempFiles_deletesAllApkFilesInUpdatesDir() = runTest(testDispatcher) {
        val updatesDir = File(tempFolder.root, "updates").apply { mkdirs() }
        val f1 = File(updatesDir, "test1.apk").apply { writeText("dummy1") }
        val f2 = File(updatesDir, "test2.apk").apply { writeText("dummy2") }

        assertTrue(f1.exists())
        assertTrue(f2.exists())

        repository.cleanupTempFiles()

        assertTrue(!f1.exists())
        assertTrue(!f2.exists())
    }
}
