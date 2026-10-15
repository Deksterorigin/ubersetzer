package app.deutschlern.update.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name")
    val tagName: String = "",
    @SerialName("name")
    val name: String? = null,
    @SerialName("body")
    val body: String? = null,
    @SerialName("draft")
    val draft: Boolean = false,
    @SerialName("prerelease")
    val prerelease: Boolean = false,
    @SerialName("assets")
    val assets: List<GitHubAssetDto> = emptyList()
)

@Serializable
data class GitHubAssetDto(
    @SerialName("name")
    val name: String = "",
    @SerialName("size")
    val size: Long = 0L,
    @SerialName("browser_download_url")
    val downloadUrl: String = "",
    @SerialName("content_type")
    val contentType: String? = null,
    @SerialName("digest")
    val digest: String? = null
)
