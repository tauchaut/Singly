package com.tauchaut.singly.models

internal data class Playlist(
    val id: String,
    var name: String,
    var tracks: MutableList<Track> = mutableListOf()
)

