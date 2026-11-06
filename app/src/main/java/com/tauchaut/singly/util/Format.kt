package com.tauchaut.singly.util

internal fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}

internal fun formatDurationLong(seconds: Long): String {
    if (seconds <= 0) return "0:00"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}

internal fun formatSearchTime(ts: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ts
    return when {
        diff < 60_000              -> "just now"
        diff < 3_600_000           -> "${diff / 60_000} m ago"
        diff < 86_400_000          -> "${diff / 3_600_000} h ago"
        diff < 7 * 86_400_000      -> "${diff / 86_400_000} d ago"
        else -> java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(ts))
    }
}

internal fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}K"
        else -> "%.1fM".format(bytes / 1024.0 / 1024.0)
    }
}
