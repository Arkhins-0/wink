package com.arkhins.wink.ui

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Where the app should go next, as a site path ("/chats/<id>",
 * "/invite/<token>"): set by a tapped notification or an App Link, read
 * and cleared by the navigation once it is ready.
 */
object Links {
    val pending = MutableStateFlow<String?>(null)

    /** A site path as a navigation route, or null for paths the app has no screen for. */
    fun route(path: String): String? {
        val (p, query) = path.split("?", limit = 2).let { it[0] to it.getOrNull(1) }
        val parts = p.trim('/').split('/').filter { it.isNotBlank() }
        val head = parts.firstOrNull() ?: return null
        val second = parts.getOrNull(1)
        return when (head) {
            "invite" -> second?.let { "setpassword/invite/$it" }
            "reset" -> second?.let { "setpassword/reset/$it" }
            "v" -> second?.let { "verify/$it" }
            "home" -> "home" + (query?.substringAfter("m=", "")?.substringBefore('&')?.takeIf { it.isNotBlank() }?.let { "?m=$it" } ?: "")
            "schedule" -> "schedule"
            "w" -> second?.let { "weekend/$it" }
            "chats" -> if (second == null) "chats" else if (second == "new") "newchat" else "chat/$second"
            "people" -> when (second) {
                null -> "people"
                "new" -> "newperson"
                "email" -> "email" + (query?.substringAfter("group=", "")?.substringBefore('&')?.takeIf { it.isNotBlank() }?.let { "?group=$it" } ?: "")
                else -> "person/$second"
            }
            "account" -> "account"
            "archive" -> if (second == null) "archive" else "archive/$second"
            else -> null
        }
    }
}
