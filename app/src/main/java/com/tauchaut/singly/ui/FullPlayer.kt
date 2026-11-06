package com.tauchaut.singly.ui

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.R
import com.tauchaut.singly.cover.loadRawCoverIntoView
import com.tauchaut.singly.data.savePlaybackFlags
import com.tauchaut.singly.data.saveState
import com.tauchaut.singly.models.Playlist
import com.tauchaut.singly.models.Track
import com.tauchaut.singly.player.downloadTrack
import com.tauchaut.singly.player.nextTrack
import com.tauchaut.singly.player.previousTrack
import com.tauchaut.singly.player.togglePlayPause
import com.tauchaut.singly.player.updateDownloadButton
import com.tauchaut.singly.util.dp
import com.tauchaut.singly.util.formatTime
import com.tauchaut.singly.util.roundedDrawable
import java.io.File
import androidx.core.graphics.toColorInt
import androidx.core.graphics.drawable.toDrawable

internal fun MainActivity.closeFullPlayer() {
    fullPlayerOverlay?.let { root.removeView(it) }
    fullPlayerOverlay = null
    fpPlayButton = null
    fpPlayCircle = null
    fpPlayIcon = null
    fpLoadingSpinner = null
    fpDownloadBtn = null
    fpDownloadPercent = null
    fpLikeBtn = null
}

internal fun MainActivity.updateFullPlayerIfOpen() {
    val circle = fpPlayCircle
    val icon = fpPlayIcon
    val button = fpPlayButton
    if (circle == null || icon == null || button == null) return
    when {
        isLoadingTrack -> {
            icon.setImageResource(R.drawable.play_arrow_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
            icon.setColorFilter(BG)
            icon.visibility = View.VISIBLE
            circle.setColor(SECONDARY)
            button.alpha = 1f
            button.isClickable = false
        }
        isPlaying -> {
            icon.setImageResource(R.drawable.pause_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
            icon.setColorFilter(BG)
            icon.visibility = View.VISIBLE
            circle.setColor(TEXT)
            button.alpha = 1f
            button.isClickable = true
        }
        else -> {
            icon.setImageResource(R.drawable.play_arrow_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
            icon.setColorFilter(BG)
            icon.visibility = View.VISIBLE
            circle.setColor(TEXT)
            button.alpha = 1f
            button.isClickable = true
        }
    }
    fpLoadingSpinner?.visibility = if (isLoadingTrack) View.VISIBLE else View.GONE
}

internal fun MainActivity.scrollToCurrentTrackInActiveList() {
    val container = activeTrackListContainer ?: return
    val curId = currentTrack?.id ?: return
    var targetTop = -1
    for (i in 0 until container.childCount) {
        val child = container.getChildAt(i)
        val id = when (val tag = child.tag) {
            is com.tauchaut.singly.models.RowTag -> tag.trackId
            is String -> tag
            else -> null
        }
        if (id == curId) { targetTop = child.top; break }
    }
    if (targetTop < 0) return
    val finalTop = (targetTop - dp(80)).coerceAtLeast(0)
    mainScroll.smoothScrollTo(0, finalTop)
}

internal fun MainActivity.openCurrentPlaylistFromPlayer() {
    fullPlayerOverlay?.let { root.removeView(it) }
    fullPlayerOverlay = null
    fpPlayButton = null
    fpPlayCircle = null
    fpPlayIcon = null
    fpLoadingSpinner = null
    fpDownloadBtn = null
    fpDownloadPercent = null
    val tracksList = currentPlaylist
    if (tracksList.isEmpty()) {
        Toast.makeText(this, "Queue is empty", Toast.LENGTH_SHORT).show()
        return
    }
    val existing = playlists.firstOrNull { p ->
        p.tracks === tracksList ||
                (p.name == currentPlaylistName &&
                        p.tracks.size == tracksList.size &&
                        p.tracks.map { it.id } == tracksList.map { it.id })
    }
    val sharedList: MutableList<Track>? = when {
        tracksList === recommendationPlaybackTracks -> recommendationPlaybackTracks
        tracksList === similarPlaybackTracks -> similarPlaybackTracks
        tracksList === similarPlaylistTracks -> similarPlaylistTracks
        tracksList === recentTracks -> recentTracks
        tracksList === likedTracks -> likedTracks
        tracksList === downloadedTracks -> downloadedTracks
        tracksList === tracks -> tracks
        else -> null
    }
    val playlist = existing ?: Playlist(
        id = "current_${System.currentTimeMillis()}",
        name = currentPlaylistName.ifBlank { "Queue" },
        tracks = sharedList ?: tracksList.toMutableList()
    )
    currentTab = TAB_PLAYLISTS
    updateTabHighlight()
    swipeRefresh?.isEnabled = false
    showPlaylist(playlist)
    mainScroll.post { scrollToCurrentTrackInActiveList() }
}

@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
internal fun MainActivity.showFullPlayer() {
    val track = currentTrack ?: return
    if (similarPlaylistOverlay != null) closeSimilarPlaylistOverlay()
    fullPlayerOverlay?.let { root.removeView(it) }
    fullPlayerOverlay = null
    fpPlayButton = null
    fpPlayCircle = null
    fpPlayIcon = null
    fpLoadingSpinner = null
    fpDownloadBtn = null
    fpDownloadPercent = null
    val overlay = FrameLayout(this).apply {
        setBackgroundColor(BG)
    }
    val scroll = ScrollView(this).apply {
        clipToPadding = false
    }
    var playerSwipeStartY = 0f
    scroll.setOnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                playerSwipeStartY = event.rawY
                false
            }
            MotionEvent.ACTION_UP -> {
                val distance = event.rawY - playerSwipeStartY
                if (scroll.scrollY <= 0 && distance > dp(120)) {
                    root.removeView(overlay)
                    if (fullPlayerOverlay === overlay) fullPlayerOverlay = null
                    true
                } else false
            }
            else -> false
        }
    }
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipToPadding = false
        setPadding(0, dp(0), 0, dp(180))
    }
    scroll.addView(body)
    overlay.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    val bottomPanel = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(BG)
        setPadding(0, dp(4), 0, dp(36))
    }
    val bottomParams = FrameLayout.LayoutParams(-1, -2).apply {
        gravity = Gravity.BOTTOM
    }
    overlay.addView(bottomPanel, bottomParams)
    val screenWidth = resources.displayMetrics.widthPixels
    val coverWidth = screenWidth - dp(40)
    val coverHeight = (coverWidth * 9f / 16f).toInt()
    val glowPadding = dp(60)
    val visibleWidth = coverWidth + glowPadding * 2
    val visibleHeight = coverHeight + glowPadding * 2
    val oldSquareVisualHeight = dp(300) + dp(35) * 2
    val topMargin = ((oldSquareVisualHeight - visibleHeight) / 2).coerceAtLeast(dp(8))
    val coverWrap = FrameLayout(this)
    val blurBack = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundColor(Color.rgb(38, 38, 38))
        loadRawCoverIntoView(track, this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRenderEffect(
                android.graphics.RenderEffect.createBlurEffect(
                    dp(60).toFloat(), dp(60).toFloat(),
                    android.graphics.Shader.TileMode.CLAMP
                )
            )
        }
    }
    coverWrap.addView(blurBack, FrameLayout.LayoutParams(visibleWidth, visibleHeight, Gravity.CENTER))
    val glowLayer = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(2f) })
        loadRawCoverIntoView(track, this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRenderEffect(
                android.graphics.RenderEffect.createBlurEffect(
                    60f, 60f, android.graphics.Shader.TileMode.CLAMP
                )
            )
        }
    }
    coverWrap.addView(glowLayer, FrameLayout.LayoutParams(visibleWidth, visibleHeight, Gravity.CENTER))
    val blurDarken = View(this).apply {
        setBackgroundColor("#99000000".toColorInt())
    }
    coverWrap.addView(blurDarken, FrameLayout.LayoutParams(visibleWidth, visibleHeight, Gravity.CENTER))
    val fade = View(this).apply {
        background = glowFadeDrawableRect(visibleWidth, visibleHeight)
    }
    coverWrap.addView(fade, FrameLayout.LayoutParams(visibleWidth, visibleHeight, Gravity.CENTER))
    val cover = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundColor(Color.rgb(38, 38, 38))
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(16).toFloat())
            }
        }
        loadRawCoverIntoView(track, this)
    }
    coverWrap.addView(cover, FrameLayout.LayoutParams(coverWidth, coverHeight, Gravity.CENTER))
    val loadingSpinner = android.widget.ProgressBar(
        this, null, android.R.attr.progressBarStyleSmall
    ).apply {
        isIndeterminate = true
        indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
        visibility = View.GONE
    }
    coverWrap.addView(loadingSpinner, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
    fpLoadingSpinner = loadingSpinner
    body.addView(coverWrap, LinearLayout.LayoutParams(visibleWidth, visibleHeight).apply {
        this.topMargin = topMargin
        bottomMargin = dp(48)
        gravity = Gravity.CENTER_HORIZONTAL
    })
    val ICON_INTERESTS = R.drawable.interests_24dp_e3e3e3_fill0_wght300_grad0_opsz24
    val ICON_PLAYLIST_ADD = R.drawable.playlist_add_24dp_e3e3e3_fill0_wght300_grad0_opsz24
    val ICON_HEART_OUTLINE = R.drawable.favorite_24dp_e3e3e3_fill0_wght300_grad0_opsz24
    val ICON_HEART_FILLED = R.drawable.favorite_filled_24dp_e3e3e3_fill0_wght300_grad0_opsz24
    val actionsRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
    }
    body.addView(actionsRow, LinearLayout.LayoutParams(-1, dp(48)).apply {
        this.topMargin = dp(0)
        marginStart = dp(12); marginEnd = dp(12)
    })
    val playlistLabel = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = roundedDrawable("#202020".toColorInt(), 14)
        setPadding(dp(10), 0, dp(12), 0)
        isClickable = true
        isFocusable = true
        setOnClickListener { openCurrentPlaylistFromPlayer() }
    }
    val pillPlayIcon = ImageView(this).apply {
        setImageResource(R.drawable.play_arrow_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setColorFilter(TEXT)
    }
    playlistLabel.addView(pillPlayIcon, LinearLayout.LayoutParams(dp(14), dp(14)))
    val pillTitle = TextView(this).apply {
        text = currentPlaylistName.ifBlank { "Queue" }
        textSize = 12f
        setTextColor(TEXT)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(dp(4), 0, 4, 0)
    }
    playlistLabel.addView(pillTitle, LinearLayout.LayoutParams(0, -2, 1f))
    val similarToggle = ImageView(this).apply {
        setImageResource(ICON_INTERESTS)
        scaleType = ImageView.ScaleType.FIT_XY
        setColorFilter(SECONDARY, android.graphics.PorterDuff.Mode.SRC_IN)
        val p = dp(10)
        setPadding(p, p, p, p)
    }
    actionsRow.addView(similarToggle, LinearLayout.LayoutParams(dp(40), dp(40)).apply {
        marginEnd = dp(4)
    })
    val addToPlaylistBtn = ImageView(this).apply {
        setImageResource(ICON_PLAYLIST_ADD)
        scaleType = ImageView.ScaleType.FIT_XY
        setColorFilter(SECONDARY, android.graphics.PorterDuff.Mode.SRC_IN)
        val p = dp(10)
        setPadding(p, p, p, p)
        setOnClickListener { showPlaylistPicker(track) }
    }
    actionsRow.addView(addToPlaylistBtn, LinearLayout.LayoutParams(dp(40), dp(40)).apply {
        marginEnd = dp(4)
    })
    val downloadContainer = FrameLayout(this)
    val downloadBtn = ImageView(this).apply {
        setImageResource(R.drawable.download_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
        scaleType = ImageView.ScaleType.FIT_XY
        setColorFilter(SECONDARY, android.graphics.PorterDuff.Mode.SRC_IN)
        val p = dp(10)
        setPadding(p, p, p, p)
    }
    fpDownloadBtn = downloadBtn
    downloadContainer.addView(downloadBtn, FrameLayout.LayoutParams(-1, -1))
    val downloadPercent = TextView(this).apply {
        textSize = 11f
        setTextColor(TEXT)
        gravity = Gravity.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        visibility = View.GONE
    }
    fpDownloadPercent = downloadPercent
    downloadContainer.addView(downloadPercent, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
    downloadContainer.setOnClickListener {
        val job = downloadJobs[track.id]
        val localFile = File(filesDir, "${track.id}.mp3")
        when {
            job != null -> {
                job.cancelled = true
                try { job.connection?.disconnect() } catch (_: Exception) {}
                Toast.makeText(this@showFullPlayer, "Cancelled download", Toast.LENGTH_SHORT).show()
            }
            localFile.exists() -> {
                val dialog = android.app.Dialog(this@showFullPlayer)
                val box = LinearLayout(this@showFullPlayer).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(22), dp(20), dp(22), dp(20))
                    setBackgroundColor(CARD)
                }
                addDialogButton(box, "Delete downloaded track") {
                    localFile.delete()
                    downloadedTracks.removeAll { it.id == track.id }
                    playlists.find { it.id == "downloads" }?.tracks?.removeAll { it.id == track.id }
                    saveState()
                    updateDownloadButton()
                    refreshActiveTrackList()
                    dialog.dismiss()
                    Toast.makeText(this@showFullPlayer, "File deleted", Toast.LENGTH_SHORT).show()
                }
                dialog.setContentView(box)
                dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
                dialog.show()
            }
            else -> downloadTrack(track)
        }
    }
    actionsRow.addView(downloadContainer, LinearLayout.LayoutParams(dp(40), dp(40)).apply {
        marginEnd = dp(4)
    })
    val likeBtn = ImageView(this).apply {
        setImageResource(if (track.liked) ICON_HEART_FILLED else ICON_HEART_OUTLINE)
        scaleType = ImageView.ScaleType.FIT_XY
        setColorFilter(
            if (track.liked) TEXT else SECONDARY,
            android.graphics.PorterDuff.Mode.SRC_IN
        )
        val p = dp(10)
        setPadding(p, p, p, p)
        setOnClickListener {
            toggleLike(track)
            setImageResource(if (track.liked) ICON_HEART_FILLED else ICON_HEART_OUTLINE)
            setColorFilter(
                if (track.liked) TEXT else SECONDARY,
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        }
    }
    fpLikeBtn = likeBtn
    actionsRow.addView(likeBtn, LinearLayout.LayoutParams(dp(40), dp(40)))
    body.addView(
        playlistLabel,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dp(28)
        ).apply {
            gravity = Gravity.START
            marginStart = dp(23)
            marginEnd = dp(16)
            this.topMargin = dp(4)
            bottomMargin = dp(6)
        }
    )
    val textColumn = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }
    body.addView(textColumn, LinearLayout.LayoutParams(-1, -2).apply {
        marginStart = dp(23); marginEnd = dp(16)
    })
    val title = TextView(this).apply {
        text = track.title
        textSize = 22f
        setTextColor(TEXT)
        setTypeface(null, android.graphics.Typeface.BOLD)
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    textColumn.addView(title, LinearLayout.LayoutParams(-1, -2))
    val artist = TextView(this).apply {
        text = track.artist
        textSize = 15f
        setTextColor(SECONDARY)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    textColumn.addView(artist, LinearLayout.LayoutParams(-1, -2))
    val timeRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
    }
    bottomPanel.addView(timeRow, LinearLayout.LayoutParams(-1, -2).apply {
        this.topMargin = dp(4)
        marginStart = dp(25); marginEnd = dp(25)
    })
    val currentTimeLabel = TextView(this).apply {
        text = "0:00"
        textSize = 12f
        setTextColor(SECONDARY)
    }
    timeRow.addView(currentTimeLabel, LinearLayout.LayoutParams(0, -2, 1f))
    val totalTimeLabel = TextView(this).apply {
        text = "0:00"
        textSize = 12f
        setTextColor(SECONDARY)
        gravity = Gravity.END
    }
    timeRow.addView(totalTimeLabel, LinearLayout.LayoutParams(0, -2, 1f))
    val seek = SeekBar(this)
    seek.max = 1000
    seek.progressTintList = ColorStateList.valueOf(Color.WHITE)
    seek.progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(90, 90, 90))
    seek.thumbTintList = ColorStateList.valueOf(Color.WHITE)
    seek.setPadding(dp(12), 0, dp(12), 0)
    bottomPanel.addView(seek, LinearLayout.LayoutParams(-1, dp(36)).apply {
        marginStart = dp(14); marginEnd = dp(14)
    })
    val syncSeek = object : Runnable {
        override fun run() {
            val tr = currentTrack
            if (tr?.isLive == true) {
                if (overlay.parent != null) seek.postDelayed(this, 1000)
                return
            }
            mediaPlayer?.let { p ->
                try {
                    if (p.duration > 0) {
                        val pos = p.currentPosition
                        val dur = p.duration
                        seek.progress = (pos * 1000L / dur).toInt()
                        currentTimeLabel.text = formatTime(pos)
                        totalTimeLabel.text = formatTime(dur)
                    }
                } catch (_: Exception) {}
            }
            if (overlay.parent != null) seek.postDelayed(this, 500)
        }
    }
    seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
            mediaPlayer?.let { p ->
                val dur = p.duration
                if (dur > 0) {
                    val previewMs = dur * progress / 1000L
                    if (fromUser) {
                        currentTimeLabel.text = formatTime(previewMs)
                        try { p.seekTo(previewMs) } catch (_: Exception) {}
                    }
                }
            }
        }
        override fun onStartTrackingTouch(sb: SeekBar?) = Unit
        override fun onStopTrackingTouch(sb: SeekBar?) = Unit
    })
    seek.post(syncSeek)
    val isLive = track.isLive
    if (isLive) {
        currentTimeLabel.text = "LIVE"
        currentTimeLabel.setTextColor("#FF5252".toColorInt())
        totalTimeLabel.visibility = View.GONE
        seek.isEnabled = false
        seek.alpha = 0.35f
    }
    val controls = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }
    controls.setPadding(2, 0, 2, 0)
    val ICON_SHUFFLE = R.drawable.shuffle_24dp_e3e3e3_fill0_wght300_grad0_opsz24
    val ICON_REPEAT = R.drawable.repeat_24dp_e3e3e3_fill0_wght300_grad0_opsz24
    val ICON_REPEAT_ONE = R.drawable.repeat_one_24dp_e3e3e3_fill0_wght300_grad0_opsz24
    val ICON_PREV = R.drawable.skip_previous_24dp_e3e3e3_fill0_wght300_grad0_opsz24
    val ICON_NEXT = R.drawable.skip_next_24dp_e3e3e3_fill0_wght300_grad0_opsz24
    val shuffleBtn = iconButton(ICON_SHUFFLE, viewSizeDp = 64, iconSizeDp = 34).apply {
        setColorFilter(if (randomOrder) TEXT else SECONDARY)
        alpha = if (randomOrder) 1f else 0.55f
        setOnClickListener {
            randomOrder = !randomOrder
            savePlaybackFlags()
            shuffledOrder = if (randomOrder) {
                val rest = currentPlaylist.filter { it.id != currentTrack?.id }.shuffled()
                val cur = currentTrack
                (listOfNotNull(cur) + rest).toMutableList()
            } else null
            setColorFilter(if (randomOrder) TEXT else SECONDARY)
            alpha = if (randomOrder) 1f else 0.55f
        }
    }
    controls.addView(shuffleBtn, LinearLayout.LayoutParams(dp(62), dp(62)))
    val previousButton = iconButton(ICON_PREV, viewSizeDp = 72, iconSizeDp = 48)
    previousButton.setColorFilter(TEXT)
    previousButton.setOnClickListener {
        if (!previousButton.isEnabled) return@setOnClickListener
        root.removeView(overlay)
        if (fullPlayerOverlay === overlay) fullPlayerOverlay = null
        previousTrack()
        showFullPlayer()
    }
    controls.addView(previousButton, LinearLayout.LayoutParams(dp(72), dp(72)).apply {
        marginEnd = dp(6)
    })
    val PLAY_SIZE = 64
    val PLAY_ICON = 48
    val bigPlay = FrameLayout(this)
    val initialCircleColor = if (isLoadingTrack) SECONDARY else TEXT
    val bigPlayCircle = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(initialCircleColor)
    }
    bigPlay.background = bigPlayCircle
    val playIcon = ImageView(this).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        setColorFilter(BG)
        setImageResource(
            when {
                isLoadingTrack -> R.drawable.play_arrow_24dp_e3e3e3_fill0_wght300_grad0_opsz24
                isPlaying -> R.drawable.pause_24dp_e3e3e3_fill0_wght300_grad0_opsz24
                else -> R.drawable.play_arrow_24dp_e3e3e3_fill0_wght300_grad0_opsz24
            }
        )
        visibility = View.VISIBLE
    }
    bigPlay.addView(playIcon, FrameLayout.LayoutParams(dp(PLAY_ICON), dp(PLAY_ICON), Gravity.CENTER))
    bigPlay.isClickable = !isLoadingTrack
    bigPlay.alpha = 1f
    fpPlayButton = bigPlay
    fpPlayCircle = bigPlayCircle
    fpPlayIcon = playIcon
    bigPlay.setOnClickListener {
        if (isLoadingTrack) return@setOnClickListener
        togglePlayPause()
        updateFullPlayerIfOpen()
    }
    controls.addView(bigPlay, LinearLayout.LayoutParams(dp(PLAY_SIZE), dp(PLAY_SIZE)).apply {
        marginStart = dp(4)
        marginEnd = dp(4)
    })
    val nextButton = iconButton(ICON_NEXT, viewSizeDp = 72, iconSizeDp = 48).apply {
        setColorFilter(TEXT)
    }
    nextButton.setOnClickListener {
        if (!nextButton.isEnabled) return@setOnClickListener
        root.removeView(overlay)
        if (fullPlayerOverlay === overlay) fullPlayerOverlay = null
        closeFullPlayer()
        nextTrack()
        showFullPlayer()
    }
    controls.addView(nextButton, LinearLayout.LayoutParams(dp(72), dp(72)).apply {
        marginStart = dp(6)
    })
    val repeatBtn = iconButton(
        if (repeatTrack) ICON_REPEAT_ONE else ICON_REPEAT,
        viewSizeDp = 64,
        iconSizeDp = 34
    ).apply {
        setColorFilter(if (repeatTrack || repeatPlaylist) TEXT else SECONDARY)
        alpha = if (repeatTrack || repeatPlaylist) 1f else 0.55f
        setOnClickListener {
            when {
                !repeatTrack && !repeatPlaylist -> { repeatPlaylist = true; repeatTrack = false }
                repeatPlaylist -> { repeatPlaylist = false; repeatTrack = true }
                else -> { repeatTrack = false; repeatPlaylist = false }
            }
            setImageResource(if (repeatTrack) ICON_REPEAT_ONE else ICON_REPEAT)
            setColorFilter(if (repeatTrack || repeatPlaylist) TEXT else SECONDARY)
            alpha = if (repeatTrack || repeatPlaylist) 1f else 0.55f
        }
    }
    controls.addView(repeatBtn, LinearLayout.LayoutParams(dp(62), dp(62)))
    previousButton.isEnabled = true
    nextButton.isEnabled = true
    previousButton.alpha = 1f
    nextButton.alpha = 1f
    bottomPanel.addView(controls, LinearLayout.LayoutParams(-1, dp(PLAY_SIZE + 12)).apply {
        this.topMargin = dp(8)
        marginStart = dp(16); marginEnd = dp(16)
    })
    similarToggle.setOnClickListener {
        openSimilarPlaylistOverlay(
            sourceTracks = listOf(track),
            title = track.title
        )
    }
    updateFullPlayerIfOpen()
    updateDownloadButton()
    fullPlayerOverlay = overlay
    root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
    overlay.elevation = dp(30).toFloat()
    overlay.bringToFront()
}
