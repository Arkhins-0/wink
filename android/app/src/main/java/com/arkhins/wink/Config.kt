package com.arkhins.wink

/**
 * The few constants the whole app shares. The values come from the build
 * (see app/build.gradle.kts): an environment variable, the root .env file,
 * local.properties or gradle.properties, in that order.
 */
object Config {
    const val APP_NAME = "Wink"

    /** Where the Wink server lives, no trailing slash. API keys and data fetches go through it. */
    val BASE_URL: String = BuildConfig.BASE_URL.trimEnd('/')

    /** Reports the latest release as JSON: { version, releaseUrl, apkUrl?, notes? }. */
    val UPDATE_URL: String = BuildConfig.UPDATE_URL.ifBlank { "$BASE_URL/api/app-version" }

    /**
     * GitHub "owner/name" whose Releases carry the APKs. Asked directly when
     * the server cannot answer, so updates work before the server exists.
     * Blank disables the fallback.
     */
    val GITHUB_REPO: String = BuildConfig.GITHUB_REPO.trim().trim('/')

    const val POWERED_BY_NAME = "arkhins.com"
    const val POWERED_BY_URL = "https://arkhins.com"
}
