package com.tauchaut.singly.models

internal data class PublishedPlaylist(
    val name: String,
    val tracks: List<PublishedTrack>
)
