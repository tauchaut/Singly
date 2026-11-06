package com.tauchaut.singly.util

internal fun tracksWord(count: Int): String {
    val mod10  = count % 10
    val mod100 = count % 100
    return when (mod10) {
        1 if mod100 != 11 -> "трек"
        in 2..4 if mod100 !in 12..14 -> "трека"
        else -> "треков"
    }
}

internal fun playlistWord(count: Int): String {
    val m10 = count % 10
    val m100 = count % 100
    return when (m10) {
        1 if m100 != 11 -> "плейлист"
        in 2..4 if m100 !in 12..14 -> "плейлиста"
        else -> "плейлистов"
    }
}
