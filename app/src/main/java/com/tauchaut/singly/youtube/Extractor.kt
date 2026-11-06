package com.tauchaut.singly.youtube

import com.tauchaut.singly.MainActivity
import android.widget.Toast
import com.tauchaut.singly.models.Track
import com.tauchaut.singly.ui.updateFullPlayerIfOpen
import com.tauchaut.singly.ui.updateMiniPlayer
import org.schabi.newpipe.extractor.ServiceList
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal fun MainActivity.fetchAudioStreamUrl(track: Track): String? {
    if (!newPipeReady.await(15, TimeUnit.SECONDS)) {
        throw IllegalStateException("YouTube extractor is not initialized")
    }
    val videoUrl = "https://www.youtube.com/watch?v=${track.id}"
    val extractor = ServiceList.YouTube.getStreamExtractor(videoUrl)
    extractor.fetchPage()
    val live = try {
        val st = extractor.streamType
        st == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM ||
                st == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM
    } catch (_: Exception) { false }
    track.isLive = live
    if (live) {
        val hls = try { extractor.hlsUrl } catch (_: Exception) { null }
        if (!hls.isNullOrBlank()) {
            return hls
        }
        val muxedHls = extractor.videoStreams
            .filter {
                it.content.isNotBlank() &&
                        it.deliveryMethod == org.schabi.newpipe.extractor.stream.DeliveryMethod.HLS
            }
            .maxByOrNull { it.bitrate }
        if (muxedHls != null) {
            return muxedHls.content
        }
        val anyMuxed = extractor.videoStreams
            .filter { it.content.isNotBlank() }
            .maxByOrNull { it.bitrate }
        if (anyMuxed != null) return anyMuxed.content
        return null
    }
    val stream = extractor.audioStreams
        .filter { it.content.isNotBlank() }.maxByOrNull { it.bitrate }
    return stream?.content
}

internal fun MainActivity.fetchStreamUrlAndExecute(track: Track, onReady: (String) -> Unit) {
    if (track.streamUrl.isNotEmpty()) {
        onReady(track.streamUrl)
        return
    }
    thread {
        var attempt = 0
        val maxAttempts = 15
        while (currentTrack?.id == track.id && attempt < maxAttempts) {
            attempt++
            try {
                val audioUrl = fetchAudioStreamUrl(track)
                if (!audioUrl.isNullOrBlank()) {
                    track.streamUrl = audioUrl
                    runOnUiThread {
                        if (currentTrack?.id == track.id) onReady(audioUrl)
                    }
                    return@thread
                }
            } catch (_: Exception) {
            }
            Thread.sleep(1000)
        }
        runOnUiThread {
            if (currentTrack?.id == track.id) {
                isLoadingTrack = false
                isPlaying = false
                updateMiniPlayer()
                updateFullPlayerIfOpen()
                Toast.makeText(
                    this,
                    "Failed to load the track",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

internal fun bestThumbnailFor(videoId: String): String {
    return "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
}

