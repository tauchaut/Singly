package com.tauchaut.singly.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.R
import com.tauchaut.singly.models.Track
import java.net.URL
import kotlin.concurrent.thread

internal fun MainActivity.createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Playback control"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}

internal fun MainActivity.updateMediaNotification() {
    val track = currentTrack
    val stateCode = when {
        track == null -> -2
        isLoadingTrack -> 0
        isPlaying -> 1
        else -> 2
    }
    val now = System.currentTimeMillis()
    if (track?.id == lastNotificationTrackId &&
        stateCode == lastNotificationState &&
        now - lastNotificationTime < 300) {
        return
    }
    lastNotificationTrackId = track?.id
    lastNotificationState = stateCode
    lastNotificationTime = now
    if (track == null) {
        try { NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID) } catch (_: SecurityException) {}
        return
    }
    val session = mediaSession
    if (session == null) {
        return
    }
    val nm = NotificationManagerCompat.from(this)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            return
        }
    }
    if (!nm.areNotificationsEnabled()) {
        return
    }
    val metadataBuilder = MediaMetadataCompat.Builder()
        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.duration.toLong() * 1000L)
    val hiRes = notificationIconCache.get(track.id)
    if (hiRes != null) {
        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, hiRes)
        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, hiRes)
        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, hiRes)
    } else {
        loadNotificationIcon(track, onReady = { })
    }
    session.setMetadata(metadataBuilder.build())
    val state = when {
        isLoadingTrack -> PlaybackStateCompat.STATE_BUFFERING
        isPlaying      -> PlaybackStateCompat.STATE_PLAYING
        else           -> PlaybackStateCompat.STATE_PAUSED
    }
    val actions = (PlaybackStateCompat.ACTION_PLAY
            or PlaybackStateCompat.ACTION_PAUSE
            or PlaybackStateCompat.ACTION_PLAY_PAUSE
            or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            or PlaybackStateCompat.ACTION_SEEK_TO)
    session.setPlaybackState(
        PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, mediaPlayer?.currentPosition ?: 0L, if (isPlaying) 1f else 0f)
            .build()
    )
    val prevPI = PendingIntent.getBroadcast(
        this, 1,
        Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT,
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val playPI = PendingIntent.getBroadcast(
        this, 2,
        Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT,
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val nextPI = PendingIntent.getBroadcast(
        this, 3,
        Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT,
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val contentIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val builder = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.play_arrow_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
        .setContentTitle(track.title)
        .setContentText(track.artist)
        .setContentIntent(contentIntent)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setOngoing(true)
        .addAction(R.drawable.skip_previous_24dp_e3e3e3_fill0_wght300_grad0_opsz24, "Previous", prevPI)
        .addAction(
            if (isPlaying) R.drawable.pause_24dp_e3e3e3_fill0_wght300_grad0_opsz24
            else R.drawable.play_arrow_24dp_e3e3e3_fill0_wght300_grad0_opsz24,
            if (isPlaying) "Pause" else "Play", playPI
        )
        .addAction(R.drawable.skip_next_24dp_e3e3e3_fill0_wght300_grad0_opsz24, "Next", nextPI)
        .setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
        )
    val cachedHiRes = notificationIconCache.get(track.id)
    if (cachedHiRes != null) {
        builder.setLargeIcon(cachedHiRes)
    } else {
        loadNotificationIcon(track) { }
    }
    try {
        nm.notify(NOTIFICATION_ID, builder.build())
    } catch (_: SecurityException) {
    } catch (_: Exception) {
    }
}

internal fun MainActivity.loadNotificationIcon(track: Track, onReady: (Bitmap?) -> Unit) {
    val key = track.id
    notificationIconCache.get(key)?.let {
        onReady(it); return
    }
    if (key in notificationIconLoading) {
        onReady(null); return
    }
    notificationIconLoading.add(key)
    thread {
        var best: Bitmap? = null

        val candidates = listOf(
            "https://i.ytimg.com/vi/$key/maxresdefault.jpg",
            "https://i.ytimg.com/vi/$key/sddefault.jpg",
            "https://i.ytimg.com/vi/$key/hqdefault.jpg"
        )
        try {
            for (url in candidates) {
                val conn = try {
                    (URL(url).openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 8000
                        readTimeout = 8000
                        instanceFollowRedirects = true
                    }
                } catch (_: Exception) {
                    continue
                }
                val code = try {
                    conn.responseCode
                } catch (_: Exception) {
                    -1
                }
                if (code !in 200..299) {
                    continue
                }
                val bytes = try {
                    conn.inputStream.use { it.readBytes() }
                } catch (_: Exception) {
                    continue
                }
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                if (bmp != null && (best == null || bmp.width > best.width)) {
                    best = bmp
                }
                if (bmp != null && bmp.width >= 1000) break
            }
            best?.let {
                notificationIconCache.put(key, it)
            }
        } catch (_: Exception) {
        } finally {
            notificationIconLoading.remove(key)
        }
        runOnUiThread {
            onReady(best)
            if (best != null && currentTrack?.id == track.id) {
                updateMediaNotification()
            }
        }
    }
}

internal fun systemPlaylistIcon(id: String): Int = when (id) {
    "recent"       -> R.drawable.music_history_24dp_e3e3e3_fill0_wght300_grad0_opsz24
    "liked"        -> R.drawable.favorite_filled_24dp_e3e3e3_fill0_wght300_grad0_opsz24
    "listen_later" -> R.drawable.schedule_24dp_e3e3e3_fill0_wght300_grad0_opsz24
    "downloads"    -> R.drawable.download_done_24dp_e3e3e3_fill0_wght300_grad0_opsz24
    else           -> 0
}
