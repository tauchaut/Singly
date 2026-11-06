package com.tauchaut.singly

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class PlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "Singly_playback"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.tauchaut.singly.START"
        const val ACTION_STOP  = "com.tauchaut.singly.STOP"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildPlaceholder())
            }
        }
        return START_STICKY
    }
    override fun onTaskRemoved(rootIntent: Intent?) {

    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Playback",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Playback control"
                        setShowBadge(false)
                    }
                )
            }
        }
    }

    private fun buildPlaceholder(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.play_arrow_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
            .setContentTitle("Singly")
            .setContentText("Ready to play")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
}