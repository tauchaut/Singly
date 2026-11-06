package com.tauchaut.singly.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.support.v4.media.session.PlaybackStateCompat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.R
import com.tauchaut.singly.cover.applyCoverGradientToMiniPlayer
import com.tauchaut.singly.cover.canonicalCoverUrl
import com.tauchaut.singly.cover.loadImageIntoView
import com.tauchaut.singly.media.updateMediaNotification
import com.tauchaut.singly.player.nextTrack
import com.tauchaut.singly.player.togglePlayPause
import com.tauchaut.singly.util.dp
import com.tauchaut.singly.util.formatTime
import java.io.File
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
internal class TimeLabelView(context: Context, textColor: Int) : View(context) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            10f,
            context.resources.displayMetrics
        )
        color = textColor
    }
    private var timeText = ""
    private val fm = paint.fontMetrics
    private val textHeight = fm.descent - fm.ascent
    private val maxTextWidth = paint.measureText("888:88:88 / 888:88:88")

    fun setTime(text: String) {
        if (timeText == text) return
        timeText = text
        invalidate()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val desiredW = kotlin.math.ceil(maxTextWidth).toInt() + paddingLeft + paddingRight
        val desiredH = kotlin.math.ceil(textHeight).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(desiredW, widthSpec),
            resolveSize(desiredH, heightSpec)
        )
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val baseline = paddingTop +
                (height - paddingTop - paddingBottom) / 2f -
                (fm.ascent + fm.descent) / 2f
        canvas.drawText(timeText, paddingLeft.toFloat(), baseline, paint)
    }
}

internal fun applyMiniPlayerCorners(bg: GradientDrawable) {
    val r = 18f * Resources.getSystem().displayMetrics.density
    bg.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
}

internal fun MainActivity.createMiniPlayer() {
    val radius = dp(18).toFloat()
    miniPlayer = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(10), dp(0), dp(10))
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf("#1F1F1F".toColorInt(), BG)
        ).apply {
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        }
        setOnClickListener { showFullPlayer() }
    }
    val l = dp(6)
    val r = dp(2)
    val b = dp(56)
    val params = FrameLayout.LayoutParams(-1, dp(76)).apply {
        gravity = Gravity.BOTTOM
        leftMargin = l
        rightMargin = r
        bottomMargin = b
    }
    root.addView(miniPlayer, params)
    updateMiniPlayer(forceRebuild = true)
}

internal fun MainActivity.updateMiniTimeLabel() {
    val label = miniTimeLabel ?: return
    val tr = currentTrack
    val newText = when {
        tr == null -> "0:00 / 0:00"
        tr.isLive -> "LIVE"
        else -> {
            val fallbackDurMs = tr.duration.toLong() * 1000L
            try {
                val player = mediaPlayer
                val pos = player?.currentPosition ?: 0L
                val rawDur = player?.duration ?: 0L
                val dur = if (rawDur > 0) rawDur else fallbackDurMs
                "${formatTime(pos)} / ${formatTime(dur)}"
            } catch (_: Exception) {
                "${formatTime(0)} / ${formatTime(fallbackDurMs)}"
            }
        }
    }
    label.setTime(newText)
}

internal fun MainActivity.updateMiniPlayer(forceRebuild: Boolean = false) {
    val track = currentTrack ?: run {
        miniProgressHandler.removeCallbacksAndMessages(null)
        miniPlayer.removeAllViews()
        miniCover = null
        miniTitle = null
        miniArtist = null
        miniPlayer.visibility = View.GONE
        return
    }
    miniPlayer.visibility = View.VISIBLE
    if (forceRebuild || miniCover == null) {
        miniPlayer.removeAllViews()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setOnClickListener { showFullPlayer() }
        }
        val coverCorner = dp(10).toFloat()
        val cover = imageView(track).apply {
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, coverCorner)
                }
            }
        }
        miniCover = cover
        row.addView(cover, LinearLayout.LayoutParams(dp(56), dp(56)))
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setOnClickListener { showFullPlayer() }
        }
        val title = TextView(this).apply {
            setTextColor(TEXT)
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
        }
        val artist = TextView(this).apply {
            setTextColor(TEXT)
            textSize = 12f
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
        }
        miniTitle = title
        miniArtist = artist
        text.addView(title)
        text.addView(artist)
        val metaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val timeLabel = TimeLabelView(this, TEXT)
        miniTimeLabel = timeLabel
        metaRow.addView(timeLabel)
        val metaTop = dp(2)
        text.addView(metaRow, LinearLayout.LayoutParams(-2, -2).apply { topMargin = metaTop })
        val textStart = dp(12)
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = textStart
            topMargin = 0
        })
        val ICON_NEXT = R.drawable.skip_next_24dp_e3e3e3_fill0_wght300_grad0_opsz24
        val ICON_PLAY = R.drawable.play_arrow_24dp_e3e3e3_fill0_wght300_grad0_opsz24
        val play = iconButton(ICON_PLAY, viewSizeDp = 38, iconSizeDp = 26)
        play.tag = "mini_play"
        miniPlayButton = play
        play.setOnClickListener { if (!isLoadingTrack) togglePlayPause() }
        row.addView(play, LinearLayout.LayoutParams(dp(55), dp(55)))
        val next = iconButton(ICON_NEXT, viewSizeDp = 42, iconSizeDp = 22)
        next.setOnClickListener { nextTrack() }
        row.addView(next, LinearLayout.LayoutParams(dp(45), dp(45)))
        miniPlayer.addView(row)
    }
    miniTitle?.let { tv ->
        if (tv.text.toString() != track.title) tv.text = track.title
    }
    miniArtist?.let { tv ->
        if (tv.text.toString() != track.artist) tv.text = track.artist
    }
    val isLiked = likedTracks.any { it.id == track.id }
    miniLikedIcon?.visibility = if (isLiked) View.VISIBLE else View.GONE
    val isDownloaded = File(filesDir, "${track.id}.mp3").exists()
    miniDownloadedIcon?.visibility = if (isDownloaded) View.VISIBLE else View.GONE
    miniTimeHandler.removeCallbacks(miniTimeRunnable)
    updateMiniTimeLabel()
    miniTimeHandler.postDelayed(miniTimeRunnable, 500)
    miniPlayButton?.let { button ->
        if (isLoadingTrack) {
            button.setImageResource(R.drawable.play_arrow_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
            button.alpha = 0.3f
        } else {
            button.setImageResource(
                if (isPlaying) R.drawable.pause_24dp_e3e3e3_fill0_wght300_grad0_opsz24
                else R.drawable.play_arrow_24dp_e3e3e3_fill0_wght300_grad0_opsz24
            )
            button.alpha = 1f
        }
        button.isClickable = !isLoadingTrack
    }
    loadImageIntoView(track, miniCover)
    updateFullPlayerIfOpen()
    updateMediaNotification()
    applyCoverGradientToMiniPlayer(track, imageCache.get(canonicalCoverUrl(track)))
    refreshActiveTrackList()
}

internal fun MainActivity.updateMiniProgress() {
    val player = mediaPlayer
    val progress = miniProgress
    if (player != null && progress != null) {
        try {
            val duration = player.duration
            val position = player.currentPosition
            progress.progress = if (duration > 0) {
                (position.toFloat() / duration * 100).roundToInt()
            } else 0
        } catch (_: Exception) { }
    }
    miniProgressHandler.removeCallbacksAndMessages(null)
    if (player != null && isPlaying) {
        miniProgressHandler.postDelayed({ updateMiniProgress() }, 500)
    }
    mediaSession?.setPlaybackState(
        PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING
                else PlaybackStateCompat.STATE_PAUSED,
                mediaPlayer?.currentPosition ?: 0L,
                if (isPlaying) 1f else 0f
            )
            .build()
    )
}
