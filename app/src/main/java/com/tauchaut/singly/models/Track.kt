package com.tauchaut.singly.models

internal data class Track(
    val id: String,
    var title: String,
    var artist: String,
    var imageUrl: String = "",
    var streamUrl: String = "",
    var duration: Int = 0,
    var liked: Boolean = false,
    var isLive: Boolean = false
)


