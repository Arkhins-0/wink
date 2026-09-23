package com.arkhins.wink.data

/**
 * Whether [remote] is a newer version than [local].
 *
 * Versions are dotted numeric segments of any length ("0.0.0.0", "1.2",
 * "v2.0.1.7"); a missing segment counts as 0 and a non-numeric suffix on a
 * segment ("3-beta") is ignored.
 */
fun isNewerVersion(remote: String, local: String): Boolean {
    val r = versionSegments(remote)
    val l = versionSegments(local)
    for (i in 0 until maxOf(r.size, l.size)) {
        val rv = r.getOrElse(i) { 0 }
        val lv = l.getOrElse(i) { 0 }
        if (rv != lv) return rv > lv
    }
    return false
}

fun versionSegments(version: String): List<Int> =
    version.trim().removePrefix("v").removePrefix("V").split(".")
        .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
