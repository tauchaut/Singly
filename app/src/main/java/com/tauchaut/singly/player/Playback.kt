package com.tauchaut.singly.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.PlaybackService
import com.tauchaut.singly.data.addToRecent
import com.tauchaut.singly.data.saveState
import com.tauchaut.singly.models.Track
import com.tauchaut.singly.ui.refreshActiveTrackList
import com.tauchaut.singly.ui.showFullPlayer
import com.tauchaut.singly.ui.updateFullPlayerIfOpen
import com.tauchaut.singly.ui.updateMiniPlayer
import com.tauchaut.singly.youtube.fetchStreamUrlAndExecute
import java.io.File
import kotlin.collections.ifEmpty
import kotlin.collections.set
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi

internal fun MainActivity.playTrack(
    track: Track,
    playlist: List<Track>,
    playlistName: String? = null
) {
    if (currentPlaylistName == "Recommendations") {
        lastRecommendationPlayStart = System.currentTimeMillis()
        lastRecommendationTrackId = track.id
    }
    val wasFullPlayerOpen = fullPlayerOverlay != null
    fullPlayerOverlay?.let { root.removeView(it) }
    fullPlayerOverlay = null
    val requestId = ++playbackRequestId
    isLoadingTrack = true
    mediaPlayer?.let { player ->
        try { player.stop() } catch (_: Exception) {}
        try { player.release() } catch (_: Exception) {}
    }
    mediaPlayer = null
    isPlaying = false
    currentTrack = track
    currentPlaylist = playlist as? MutableList ?: playlist.toMutableList()
    currentSourceKey = sourceKeyOf(currentPlaylist)
    currentPlaylistName = playlistName ?: detectPlaylistName(playlist)
    prefs.edit {
        putString("last_track_id", track.id)
            .putString("last_source_key", currentSourceKey)
    }
    addToRecent(track)
    updateMiniPlayer()
    if (wasFullPlayerOpen) {
        root.postDelayed({ showFullPlayer() }, 50)
    }
    val localFile = File(filesDir, "${track.id}.mp3")
    if (localFile.exists()) {
        playUrl(localFile.absolutePath, requestId, isLocal = true)
    } else if (track.streamUrl.isNotEmpty()) {
        playUrl(track.streamUrl, requestId, isLocal = false)
    } else {
        fetchStreamUrlAndExecute(track) { url ->
            if (requestId != playbackRequestId || currentTrack?.id != track.id) return@fetchStreamUrlAndExecute
            playUrl(url, requestId, isLocal = false)
        }
    }
}

@OptIn(UnstableApi::class)
internal fun MainActivity.playUrl(url: String, requestId: Int, isLocal: Boolean = false) {
    try {
        mediaPlayer?.release()
        mediaPlayer = null
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(
            this,
            httpDataSourceFactory
        )
        val trackIsLive = currentTrack?.isLive == true
        val isHls = !isLocal && (trackIsLive || url.contains(".m3u8"))
        val mediaSourceFactory: androidx.media3.exoplayer.source.MediaSource.Factory =
            if (isHls) {
                androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dataSourceFactory)
                    .setAllowChunklessPreparation(true)
            } else {
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
            }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (requestId != playbackRequestId) return
                when (state) {
                    Player.STATE_READY -> {
                        if (!player.isPlaying) player.play()
                        isLoadingTrack = false
                        isPlaying = true
                        updateMiniPlayer()
                        preloadNextTrack()
                        val tr = currentTrack
                        if (tr != null && tr.duration <= 0 && player.duration > 0) {
                            tr.duration = (player.duration / 1000L).toInt()
                            tracks.firstOrNull { it.id == tr.id }?.duration = tr.duration
                            saveState()
                            refreshActiveTrackList()
                        }
                    }
                    Player.STATE_ENDED -> {
                        onTrackFinished()
                    }
                    Player.STATE_BUFFERING -> {

                    }

                    Player.STATE_IDLE -> {

                    }
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                if (requestId != playbackRequestId) return
                isPlaying = playing
                updateMiniPlayer()
                val buffering = player.playbackState == Player.STATE_BUFFERING ||
                        player.playbackState == Player.STATE_IDLE

                if (!playing && !buffering && downloadJobs.isEmpty()) {
                    releaseLocks()
                } else {
                    acquireLocks()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                if (requestId != playbackRequestId) return
                isPlaying = false
                val tr = currentTrack
                if (tr != null && !isLocal) {
                    tr.streamUrl = ""
                    fetchStreamUrlAndExecute(tr) { fresh ->
                        if (requestId != playbackRequestId || currentTrack?.id != tr.id) return@fetchStreamUrlAndExecute
                        playUrl(fresh, requestId)
                    }
                } else {
                    isLoadingTrack = false
                    updateMiniPlayer()
                    updateFullPlayerIfOpen()
                }
            }
        })
        val item = if (isLocal) {
            MediaItem.fromUri(Uri.fromFile(File(url)))
        } else {
            MediaItem.fromUri(url.toUri())
        }
        player.setMediaItem(item)
        startPlaybackService()
        acquireLocks()
        player.prepare()
        player.playWhenReady = true
        mediaPlayer = player
    } catch (_: Exception) {

    }
}

internal fun MainActivity.togglePlayPause() {
    val player = mediaPlayer
    if (player == null) {
        currentTrack?.let { playTrack(it, currentPlaylist.ifEmpty { tracks }) }
        return
    }
    if (player.isPlaying) {
        player.pause()
        isPlaying = false
    } else {
        player.play()
        isPlaying = true
    }
    updateMiniPlayer()
}

internal fun MainActivity.nextTrack() {
    val order = orderedPlaylist()
    if (order.isEmpty()) return
    val idx = order.indexOfFirst { it.id == currentTrack?.id }
    if (idx < 0) {
        order.firstOrNull()?.let { playTrack(it, currentPlaylist) }
        return
    }
    var next = idx + 1
    if (next >= order.size) {
        when {
            repeatSimilar && similarPlaybackTracks.isNotEmpty() -> {
                currentPlaylist = similarPlaybackTracks
                currentPlaylistName = "Similar Tracks"
                playTrack(similarPlaybackTracks[0], currentPlaylist)
                return
            }
            repeatPlaylist -> next = 0
            else -> {
                mediaPlayer?.pause()
                isPlaying = false
                updateMiniPlayer()
                return
            }
        }
    }
    playTrack(order[next], currentPlaylist)
}

internal fun MainActivity.previousTrack() {
    if (currentPlaylist.isEmpty()) return
    val player = mediaPlayer
    if (player != null && player.currentPosition > 3000) {
        try { player.seekTo(0) } catch (_: Exception) {}
        return
    }
    val order = orderedPlaylist()
    val idx = order.indexOfFirst { it.id == currentTrack?.id }
    if (idx < 0) {
        try { player?.seekTo(0) } catch (_: Exception) {}
        return
    }
    var prev = idx - 1
    if (prev < 0) {
        if (repeatPlaylist) prev = order.size - 1
        else {
            try { player?.seekTo(0) } catch (_: Exception) {}
            return
        }
    }
    playTrack(order[prev], currentPlaylist)
}

internal fun MainActivity.onTrackFinished() {
    if (repeatTrack) {
        currentTrack?.let {
            playTrack(
                it,
                currentPlaylist
            )
        }
    } else {
        val playDuration = System.currentTimeMillis() - lastRecommendationPlayStart
        if (currentPlaylistName == "Recommendations" &&
            currentTrack?.id == lastRecommendationTrackId &&
            playDuration > 60_000) {
            val artist = currentTrack?.artist?.lowercase()?.trim() ?: ""
            if (artist.isNotEmpty()) {
                recommendationHits[artist] = (recommendationHits[artist] ?: 0) + 1
            }
        }
        nextTrack()
    }
}

internal fun MainActivity.orderedPlaylist(): List<Track> {
    return when {
        randomOrder && shuffledOrder != null -> shuffledOrder!!
        reverseOrder -> currentPlaylist.reversed()
        else -> currentPlaylist
    }
}

internal fun MainActivity.restoreLastTrackForMiniPlayer() {
    val lastId = prefs.getString("last_track_id", "") ?: ""
    if (lastId.isEmpty()) return
    val restored = tracks.firstOrNull { it.id == lastId }
        ?: recentTracks.firstOrNull { it.id == lastId }
        ?: likedTracks.firstOrNull { it.id == lastId }
        ?: downloadedTracks.firstOrNull { it.id == lastId }
        ?: return
    currentTrack = restored
    val sourceKey = prefs.getString("last_source_key", "") ?: ""
    val sourceList: MutableList<Track>? = when {
        sourceKey == "recent"           -> recentTracks
        sourceKey == "liked"            -> likedTracks
        sourceKey == "downloads"        -> downloadedTracks
        sourceKey == "recommendations"  -> recommendationPlaybackTracks
        sourceKey == "similar"          -> similarPlaybackTracks
        sourceKey == "similar_playlist" -> similarPlaylistTracks
        sourceKey == "library"          -> tracks
        sourceKey.startsWith("playlist:") -> {
            val plId = sourceKey.removePrefix("playlist:")
            playlists.find { it.id == plId }?.tracks
        }
        else -> null
    }
    val validSource = sourceList?.takeIf { list -> list.any { it.id == lastId } }
    if (validSource != null) {
        currentPlaylist = validSource
        currentSourceKey = sourceKey
        currentPlaylistName = when (sourceKey) {
            "recent"           -> "Recent tracks"
            "liked"            -> "Liked"
            "downloads"        -> "Downloads"
            "recommendations"  -> "Recommendations"
            "similar",
            "similar_playlist" -> "Similar Tracks"
            "library"          -> "Queue"
            else -> if (sourceKey.startsWith("playlist:")) {
                val plId = sourceKey.removePrefix("playlist:")
                playlists.find { it.id == plId }?.name ?: "Queue"
            } else "Queue"
        }
    } else {
        currentPlaylist = mutableListOf(restored)
        currentPlaylistName = "Queue"
    }
    isPlaying = false
    isLoadingTrack = false
    updateMiniPlayer(forceRebuild = true)
}

internal fun trimQueue(list: MutableList<Track>, maxSize: Int = 200) {
    while (list.size > maxSize) list.removeAt(0)
}

internal fun MainActivity.detectPlaylistName(playlist: List<Track>): String = when {
    playlist === recentTracks -> "Recent tracks"
    playlist === likedTracks -> "Liked"
    playlist === downloadedTracks -> "Downloads"
    playlist === recommendationPlaybackTracks -> "Recommendations"
    playlist === similarPlaybackTracks -> "Similar Tracks"
    playlist === similarPlaylistTracks -> "Similar Tracks"
    playlist.size == 1 -> "Single track"
    else -> playlists.firstOrNull { it.tracks === playlist }?.name ?: ""
}

internal fun MainActivity.sourceKeyOf(list: List<Track>?): String {
    if (list == null) return ""
    return when {
        list === recentTracks               -> "recent"
        list === likedTracks                -> "liked"
        list === downloadedTracks           -> "downloads"
        list === recommendationPlaybackTracks -> "recommendations"
        list === similarPlaybackTracks      -> "similar"
        list === similarPlaylistTracks      -> "similar_playlist"
        list === tracks                     -> "library"
        else -> {
            val pl = playlists.firstOrNull { it.tracks === list }
            if (pl != null) "playlist:${pl.id}"
            else "list:${System.identityHashCode(list)}"
        }
    }
}

internal fun MainActivity.attachPlayerListener(player: ExoPlayer) {
    player.addListener(object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> {
                    if (!player.isPlaying) player.play()
                    isLoadingTrack = false
                    isPlaying = true
                    updateMiniPlayer()
                    preloadNextTrack()
                }
                Player.STATE_ENDED -> onTrackFinished()
                Player.STATE_BUFFERING -> {

                }

                Player.STATE_IDLE -> {

                }
            }
        }
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            updateMiniPlayer()
            val buffering = player.playbackState == Player.STATE_BUFFERING ||
                    player.playbackState == Player.STATE_IDLE
            if (!playing && !buffering && downloadJobs.isEmpty()) releaseLocks() else acquireLocks()
        }
        override fun onPlayerError(error: PlaybackException) {
            isPlaying = false
            val tr = currentTrack ?: return
            if (!File(filesDir, "${tr.id}.mp3").exists()) {
                tr.streamUrl = ""
                fetchStreamUrlAndExecute(tr) { fresh -> playUrl(fresh, ++playbackRequestId) }
            }
        }
    })
}

internal fun MainActivity.acquireLocks() {
    if (wakeLock == null) {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Singly::Playback"
        ).apply { setReferenceCounted(false) }
    }
    if (wakeLock?.isHeld != true) {
        wakeLock?.acquire(6 * 60 * 60 * 1000L)
    }
    if (wifiLock == null) {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE)
                as android.net.wifi.WifiManager
        wifiLock = wm.createWifiLock(
            android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "Singly::Wifi"
        ).apply { setReferenceCounted(false) }
    }
    if (wifiLock?.isHeld != true) {
        wifiLock?.acquire()
    }
}

internal fun MainActivity.releaseLocks() {
    try { wifiLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
}

internal fun MainActivity.startPlaybackService() {
    if (playbackServiceStarted) return
    val i = Intent(this, PlaybackService::class.java).apply {
        action = PlaybackService.ACTION_START
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(i)
    } else {
        startService(i)
    }
    playbackServiceStarted = true
}
