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
        val parts = p.trim('/').split('/')
        return when {
            parts.size == 2 && parts[0] == "invite" -> "setpassword/invite/${parts[1]}"
            parts.size == 2 && parts[0] == "reset" -> "setpassword/reset/${parts[1]}"
            parts.size == 2 && parts[0] == "v" -> "verify/${parts[1]}"
            parts.size == 2 && parts[0] == "chats" -> "chat/${parts[1]}"
            parts.size == 2 && parts[0] == "w" -> "weekend/${parts[1]}"
            parts.firstOrNull() == "home" -> "home" + (query?.substringAfter("m=", "")?.takeIf { it.isNotBlank() }?.let { "?m=$it" } ?: "")
            else -> null
        }
    }
}
