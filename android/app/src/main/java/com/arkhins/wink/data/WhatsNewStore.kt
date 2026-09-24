package com.arkhins.wink.data

import android.content.Context
import com.arkhins.wink.BuildConfig

/**
 * The "What's new" popup's memory, in SharedPreferences: which version
 * the user has seen, and the release notes of an update saved just before
 * it installs — the app is replaced during the install, so the notes must
 * outlive the process to be shown on the first open of the new version.
 *
 * A version is "seen" once the popup for it was closed, or when there
 * was nothing to show for it (a fresh install, or no notes to be found).
 */
class WhatsNewStore(context: Context) {
    private val prefs = context.getSharedPreferences("wink_whats_new", Context.MODE_PRIVATE)

    /** Keep the release about to be installed, for the first open after. */
    fun savePending(info: AppVersionInfo) {
        prefs.edit()
            .putString(PENDING_VERSION, info.version)
            .putString(PENDING_NOTES, info.notes)
            .putString(PENDING_URL, info.releaseUrl)
            .apply()
    }

    /** The release saved by [savePending], if any. */
    fun pending(): AppVersionInfo? {
        val version = prefs.getString(PENDING_VERSION, null) ?: return null
        return AppVersionInfo(
            version = version,
            releaseUrl = prefs.getString(PENDING_URL, null).orEmpty(),
            notes = prefs.getString(PENDING_NOTES, null).orEmpty(),
        )
    }

    fun clearPending() {
        prefs.edit().remove(PENDING_VERSION).remove(PENDING_NOTES).remove(PENDING_URL).apply()
    }

    /** The version running now counts as seen; the pending release, shown or not, is done with. */
    fun markSeen() {
        prefs.edit()
            .putString(LAST_SEEN, BuildConfig.VERSION_NAME)
            .remove(PENDING_VERSION)
            .remove(PENDING_NOTES)
            .remove(PENDING_URL)
            .apply()
    }

    /**
     * What to show on this start: the notes of the version that was just
     * installed, or null when there is nothing to show. That is the case on
     * a fresh install, when the version has not changed since last time,
     * and when no notes for this version can be found anywhere — the saved
     * pending release first, then whatever [updates] reports as latest. In
     * each of those the version is marked seen here, so the check is not
     * repeated on the next start.
     */
    suspend fun afterUpdate(updates: UpdateChecker): AppVersionInfo? {
        val current = BuildConfig.VERSION_NAME
        val seen = prefs.getString(LAST_SEEN, null)
        if (seen == null || sameVersion(seen, current)) {
            if (seen == null) markSeen()
            return null
        }

        val saved = pending()
        val info = saved?.takeIf { sameVersion(it.version, current) }
            ?: runCatching { (updates.latest() as? Latest.Release)?.info }.getOrNull()?.takeIf { sameVersion(it.version, current) }
        if (info == null || info.notes.isBlank()) {
            markSeen()
            return null
        }
        return info
    }

    /** Equal once padded ("1.2" is "1.2.0.0"), with any "v" prefix ignored. */
    private fun sameVersion(a: String, b: String): Boolean = !isNewerVersion(a, b) && !isNewerVersion(b, a)

    private companion object {
        const val LAST_SEEN = "lastSeenVersion"
        const val PENDING_VERSION = "pendingVersion"
        const val PENDING_NOTES = "pendingNotes"
        const val PENDING_URL = "pendingReleaseUrl"
    }
}
