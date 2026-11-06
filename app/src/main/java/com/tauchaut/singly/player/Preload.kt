package com.tauchaut.singly.player

import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.models.Track
import com.tauchaut.singly.youtube.fetchAudioStreamUrl
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal fun MainActivity.preloadTrack(track: Track?) {
    if (track == null) return
    if (track.isLive) return
    if (track.streamUrl.isNotEmpty()) return
    if (track.id in preloadingIds) return
    if (File(filesDir, "${track.id}.mp3").exists()) return
    preloadingIds.add(track.id)
    thread {
        try {
            newPipeReady.await(15, TimeUnit.SECONDS)
            val url = fetchAudioStreamUrl(track)
            if (!url.isNullOrBlank()) {
                track.streamUrl = url
            }
        } catch (_: Exception) {

        } finally {
            preloadingIds.remove(track.id)
        }
    }
}

internal fun MainActivity.preloadNextTrack() {
    val list = currentPlaylist
    if (list.isEmpty()) return
    val idx = list.indexOfFirst { it.id == currentTrack?.id }
    if (idx < 0) return
    val next = when {
        randomOrder -> list.random()
        reverseOrder -> if (idx - 1 >= 0) list[idx - 1] else if (repeatPlaylist) list.last() else null
        idx + 1 < list.size -> list[idx + 1]
        repeatPlaylist && list.isNotEmpty() -> list[0]
        else -> null
    }
    preloadTrack(next)
}
