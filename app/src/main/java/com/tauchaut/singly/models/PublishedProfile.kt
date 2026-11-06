package com.tauchaut.singly.models

import android.graphics.Bitmap

internal data class PublishedProfile(
    val name: String,
    val emoji: String,
    val color: Int,
    val playlists: List<PublishedPlaylist>,
    val avatarPhotoB64: String? = null,
    var avatarBitmap: Bitmap? = null
)
