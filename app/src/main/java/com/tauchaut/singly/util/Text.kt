package com.tauchaut.singly.util

internal fun cleanYoutubeTitle(rawTitle: String, rawArtist: String): Pair<String, String> {
    val t = rawTitle.trim().ifBlank { "Unknown Title" }
    val a = rawArtist.trim().ifBlank { "YouTube" }
    return t to a
}

internal fun containsAsWholeWord(haystack: String, needle: String): Boolean {
    if (needle.isEmpty() || haystack.isEmpty()) return false
    val h = haystack.lowercase()
    val n = needle.lowercase()
    var idx = h.indexOf(n)
    while (idx >= 0) {
        val before = if (idx > 0) h[idx - 1] else ' '
        val afterIdx = idx + n.length
        val after = if (afterIdx < h.length) h[afterIdx] else ' '
        if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
        idx = h.indexOf(n, idx + 1)
    }
    return false
}
