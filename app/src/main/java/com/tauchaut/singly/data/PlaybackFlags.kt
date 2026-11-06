package com.tauchaut.singly.data

import com.tauchaut.singly.MainActivity
import androidx.core.content.edit

internal fun MainActivity.savePlaybackFlags() {
    prefs.edit {
        putBoolean("repeatTrack", repeatTrack)
            .putBoolean("repeatPlaylist", repeatPlaylist)
            .putBoolean("randomOrder", randomOrder)
            .putBoolean("reverseOrder", reverseOrder)
            .putBoolean("repeatSimilar", repeatSimilar)
    }
}

internal fun MainActivity.loadPlaybackFlags() {
    repeatTrack = prefs.getBoolean("repeatTrack", false)
    repeatPlaylist = prefs.getBoolean("repeatPlaylist", false)
    randomOrder = prefs.getBoolean("randomOrder", false)
    reverseOrder = prefs.getBoolean("reverseOrder", false)
    repeatSimilar = prefs.getBoolean("repeatSimilar", false)
    recommendationFilterMode = prefs.getString("rec_filter_mode", "all") ?: "all"
}