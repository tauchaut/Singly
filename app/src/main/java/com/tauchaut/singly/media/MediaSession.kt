package com.tauchaut.singly.media

import com.tauchaut.singly.MainActivity
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import com.tauchaut.singly.player.nextTrack
import com.tauchaut.singly.player.previousTrack
import com.tauchaut.singly.player.togglePlayPause
import com.tauchaut.singly.ui.updateMiniPlayer

internal fun MainActivity.setupMediaSession() {
    Log.d("Cover", "density=${resources.displayMetrics.density}, " +
            "densityDpi=${resources.displayMetrics.densityDpi}, " +
            "screen=${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels}")
    createNotificationChannel()
    mediaSession = MediaSessionCompat(this, "SinglySession").apply {
        setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                if (!isPlaying) togglePlayPause()
            }
            override fun onPause() {
                if (isPlaying) togglePlayPause()
            }
            override fun onSkipToNext() {
                nextTrack()
            }
            override fun onSkipToPrevious() {
                previousTrack()
            }
            override fun onSeekTo(pos: Long) {
                mediaPlayer?.seekTo(pos)
            }
            override fun onStop() {
                mediaPlayer?.pause()
                isPlaying = false
                updateMiniPlayer()
            }
        })
        isActive = true
    }
}
