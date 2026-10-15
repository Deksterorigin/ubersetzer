package app.deutschlern.update.data

import app.deutschlern.update.data.dto.GitHubReleaseDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Test
    fun parse_validReleaseJson_parsesFieldsCorrectly() {
        val jsonString = """
            {
              "tag_name": "v1.2.0",
              "name": "DeutschLern v1.2.0",
              "body": "Fixed bugs and improved performance",
              "assets": [
                {
                  "name": "DeutschLern-v1.2.0.apk",
                  "size": 125829120,
                  "browser_download_url": "https://github.com/Deksterorigin/ubersetzer/releases/download/v1.2.0/DeutschLern-v1.2.0.apk",
                  "digest": "sha256:052af4d7fcbe68524a9eb02877c1ba46c0b40027264c44338ddae6b8bc4aed32"
                }
              ]
            }
        """.trimIndent()

        val dto = json.decodeFromString<GitHubReleaseDto>(jsonString)

        assertEquals("v1.2.0", dto.tagName)
        assertEquals("DeutschLern v1.2.0", dto.name)
        assertEquals("Fixed bugs and improved performance", dto.body)
        assertEquals(1, dto.assets.size)

        val asset = dto.assets.first()
        assertEquals("DeutschLern-v1.2.0.apk", asset.name)
        assertEquals(125829120L, asset.size)
        assertEquals("sha256:052af4d7fcbe68524a9eb02877c1ba46c0b40027264c44338ddae6b8bc4aed32", asset.digest)
    }

    @Test
    fun parse_missingOptionalFields_handlesDefaultsSafely() {
        val jsonString = """
            {
              "tag_name": "v1.0.0"
            }
        """.trimIndent()

        val dto = json.decodeFromString<GitHubReleaseDto>(jsonString)

        assertEquals("v1.0.0", dto.tagName)
        assertNull(dto.name)
        assertNull(dto.body)
        assertTrue(dto.assets.isEmpty())
    }

    @Test
    fun parse_hostileBodyContent_parsedSafelyAsRawString() {
        val hostilePayload = "<script>window.location='http://evil.com'</script><img src=x onerror=alert(1)>'; DROP TABLE words; --"
        val jsonString = """
            {
              "tag_name": "v2.0.0",
              "body": "$hostilePayload",
              "assets": []
            }
        """.trimIndent()

        val dto = json.decodeFromString<GitHubReleaseDto>(jsonString)

        assertNotNull(dto.body)
        assertEquals(hostilePayload, dto.body)
    }

    @Test
    fun filterApkAssets_distinguishesZeroSingleAndMultipleApks() {
        val emptyDto = GitHubReleaseDto(tagName = "v1.0", assets = emptyList())
        val singleDto = GitHubReleaseDto(
            tagName = "v1.0",
            assets = listOf(
                app.deutschlern.update.data.dto.GitHubAssetDto(name = "DeutschLern.apk"),
                app.deutschlern.update.data.dto.GitHubAssetDto(name = "mapping.txt")
            )
        )
        val multipleDto = GitHubReleaseDto(
            tagName = "v1.0",
            assets = listOf(
                app.deutschlern.update.data.dto.GitHubAssetDto(name = "app-arm64.apk"),
                app.deutschlern.update.data.dto.GitHubAssetDto(name = "app-x86_64.apk")
            )
        )

        assertEquals(0, emptyDto.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }.size)
        assertEquals(1, singleDto.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }.size)
        assertEquals(2, multipleDto.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }.size)
    }
}
