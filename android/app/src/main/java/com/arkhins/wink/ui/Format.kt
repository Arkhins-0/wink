package com.arkhins.wink.ui

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/* Times, as the screens show them. The server sends ISO instants. */

private val dateTime = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.getDefault())
private val timeOnly = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private val dayOnly = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

fun instant(iso: String): Instant = runCatching { Instant.parse(iso) }.getOrDefault(Instant.EPOCH)

/** In the phone's own zone. */
fun localDateTime(iso: String): String = dateTime.format(instant(iso).atZone(ZoneId.systemDefault()))
fun localTime(iso: String): String = timeOnly.format(instant(iso).atZone(ZoneId.systemDefault()))

/** In the track's zone. */
fun trackDateTime(iso: String, tz: String): String = dateTime.format(instant(iso).atZone(zone(tz)))
fun trackTime(iso: String, tz: String): String = timeOnly.format(instant(iso).atZone(zone(tz)))
fun trackDay(iso: String, tz: String): String = dayOnly.format(instant(iso).atZone(zone(tz)))

fun zone(tz: String): ZoneId = runCatching { ZoneId.of(tz) }.getOrDefault(ZoneId.of("UTC"))

/** "3m", "2h", or the day — for the corner of a message. */
fun ago(iso: String): String {
    val d = Duration.between(instant(iso), Instant.now())
    return when {
        d.toMinutes() < 1 -> "now"
        d.toMinutes() < 60 -> "${d.toMinutes()}m"
        d.toHours() < 24 -> "${d.toHours()}h"
        else -> dayOnly.format(instant(iso).atZone(ZoneId.systemDefault()))
    }
}

/** "2d 04:12:09" or "04:12:09": the countdown chip. */
fun countdown(untilMillis: Long): String {
    if (untilMillis <= 0) return "now"
    val total = untilMillis / 1000
    val days = total / 86_400
    val h = (total % 86_400) / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val hms = String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    return if (days > 0) "${days}d $hms" else hms
}

fun bytes(n: Long): String = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "${n / 1024} KB"
    else -> String.format(Locale.US, "%.1f MB", n / (1024.0 * 1024.0))
}
