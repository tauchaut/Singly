package com.tauchaut.singly.util

internal fun extractVideoId(url: String): String {
    return when {
        url.contains("watch?v=") -> url.substringAfter("watch?v=").substringBefore("&")
        url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
        else -> url.substringAfterLast('/').substringBefore('?').substringBefore('&')
    }
}
