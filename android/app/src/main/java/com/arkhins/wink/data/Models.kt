package com.arkhins.wink.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the update endpoint reports: the latest release of this app.
 *
 * GET <baseUrl>/api/app-version on the Wink server returns exactly this
 * shape. [apkUrl] is null when the release carries no APK, in which case the
 * app can only open [releaseUrl] in the browser.
 */
@Serializable
data class AppVersionInfo(
    val version: String,
    val releaseUrl: String,
    val apkUrl: String? = null,
    val notes: String = "",
)

/** The parts of GitHub's "latest release" response the fallback needs. */
@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val body: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
) {
    /** Prefer the release APK over a debug one when both are attached. */
    fun toVersionInfo(): AppVersionInfo {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val apk = apks.firstOrNull { !it.name.contains("debug", ignoreCase = true) } ?: apks.firstOrNull()
        return AppVersionInfo(
            version = tagName.trim().removePrefix("v"),
            releaseUrl = htmlUrl,
            apkUrl = apk?.browserDownloadUrl,
            notes = body.orEmpty(),
        )
    }
}

@Serializable
internal data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)
