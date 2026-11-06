package com.tauchaut.singly.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Outline
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.R
import com.tauchaut.singly.cover.loadImageIntoView
import com.tauchaut.singly.data.saveState
import com.tauchaut.singly.models.Playlist
import com.tauchaut.singly.models.RowTag
import com.tauchaut.singly.models.Track
import com.tauchaut.singly.player.playTrack
import com.tauchaut.singly.util.dp
import com.tauchaut.singly.util.formatTime
import com.tauchaut.singly.util.roundedDrawable
import androidx.core.view.isVisible
import com.tauchaut.singly.cover.persistCoverIfCached

internal fun MainActivity.imageView(track: Track): ImageView {
    val image = ImageView(this)
    image.setBackgroundColor(Color.rgb(38, 38, 38))
    image.scaleType = ImageView.ScaleType.CENTER_CROP
    loadImageIntoView(track, image)
    return image
}

internal fun MainActivity.refreshLikedSet() {
    likedIds.clear()
    likedTracks.forEach { likedIds.add(it.id) }
}

internal fun MainActivity.refreshDownloadedSet() {
    downloadedIds.clear()
    downloadedTracks.forEach { downloadedIds.add(it.id) }
    filesDir.listFiles { _, n -> n.endsWith(".mp3") }?.forEach {
        downloadedIds.add(it.name.removeSuffix(".mp3"))
    }
}

internal fun MainActivity.toggleLike(track: Track) {
    val canonical = tracks.firstOrNull { it.id == track.id } ?: track
    val wasLiked = likedTracks.any { it.id == canonical.id }
    if (wasLiked) likedTracks.removeAll { it.id == canonical.id }
    else {
        likedTracks.removeAll { it.id == canonical.id }
        likedTracks.add(0, canonical)
        persistCoverIfCached(canonical)
    }
    syncLikedFlag(canonical.id, !wasLiked)
    playlists.find { it.id == "liked" }?.let {
        if (it.tracks !== likedTracks) it.tracks = likedTracks
    }
    saveState()
    fpLikeBtn?.let { btn ->
        btn.setImageResource(
            if (canonical.liked) R.drawable.favorite_filled_24dp_e3e3e3_fill0_wght300_grad0_opsz24
            else R.drawable.favorite_24dp_e3e3e3_fill0_wght300_grad0_opsz24
        )
        btn.setColorFilter(
            if (canonical.liked) TEXT else SECONDARY,
            android.graphics.PorterDuff.Mode.SRC_IN
        )
    }
    refreshLikeIconsForTrack(canonical.id)
    if (activeTrackListPlaylist?.id == "liked" && activeTrackListContainer === content) {
        rebuildActiveTrackList()
    }
}

internal fun MainActivity.syncLikedFlag(trackId: String, liked: Boolean) {
    tracks.firstOrNull { it.id == trackId }?.liked = liked
    recentTracks.firstOrNull { it.id == trackId }?.liked = liked
    likedTracks.firstOrNull { it.id == trackId }?.liked = liked
    downloadedTracks.firstOrNull { it.id == trackId }?.liked = liked
    playlists.forEach { p ->
        p.tracks.firstOrNull { it.id == trackId }?.liked = liked
    }
}

internal fun MainActivity.refreshActiveTrackList() {
    if (lastHighlightedTrackId == currentTrack?.id && lastHighlightedSource === currentPlaylist) return
    lastHighlightedTrackId = currentTrack?.id
    lastHighlightedSource = currentPlaylist
    if (refreshingTrackList) return
    refreshingTrackList = true
    try {
        refreshTrackRowHighlights(activeTrackListContainer)
        refreshTrackRowHighlights(homeRecommendationContainer)
    } finally {
        refreshingTrackList = false
    }
}

internal fun MainActivity.refreshLikeIconsForTrack(trackId: String) {
    val liked = likedTracks.any { it.id == trackId }
    val containers = listOfNotNull(activeTrackListContainer, homeRecommendationContainer)
    for (c in containers) {
        for (i in 0 until c.childCount) {
            val row = c.getChildAt(i) as? LinearLayout ?: continue
            val tag = row.tag
            val id = when (tag) {
                is RowTag -> tag.trackId
                is String -> tag
                else -> null
            }
            if (id != trackId) continue
            if (row.childCount < 2) continue
            val textCol = row.getChildAt(1) as? LinearLayout ?: continue
            val meta = textCol.getChildAt(2) as? LinearLayout ?: continue
            for (j in 0 until meta.childCount) {
                val v = meta.getChildAt(j) as? ImageView ?: continue
                if (v.tag == "heart") {
                    val isCurrent = currentTrack?.id == trackId
                    val color = if (isCurrent) BG else TEXT
                    v.visibility = if (liked) View.VISIBLE else View.GONE
                    v.setColorFilter(color)
                }
            }
        }
    }
}

internal fun MainActivity.refreshTrackRowHighlights(container: LinearLayout?) {
    val c = container ?: return
    val curId = currentTrack?.id
    for (i in 0 until c.childCount) {
        val child = c.getChildAt(i) as? LinearLayout ?: continue
        val tag = child.tag
        val trackId: String
        val playbackList: List<Track>?
        when (tag) {
            is RowTag -> { trackId = tag.trackId; playbackList = tag.playbackList }
            is String -> { trackId = tag; playbackList = null }
            else -> continue
        }
        val isCurrent = curId != null && trackId == curId && playbackList === currentPlaylist
        val rowTextColor = if (isCurrent) BG else TEXT
        val rowSecondColor = if (isCurrent) BG else SECONDARY
        if (isCurrent) {
            child.clipToOutline = true
            child.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(12).toFloat())
                }
            }
            child.background = roundedDrawable(TEXT, 12)
        } else {
            child.background = null
        }
        if (child.childCount < 3) continue
        val textCol = child.getChildAt(1) as? LinearLayout ?: continue
        val menu = child.getChildAt(2) as? ImageView ?: continue
        (textCol.getChildAt(0) as? TextView)?.setTextColor(rowTextColor)
        (textCol.getChildAt(1) as? TextView)?.setTextColor(rowSecondColor)
        menu.setColorFilter(if (isCurrent) BG else SECONDARY)
        (textCol.getChildAt(2) as? LinearLayout)?.let { meta ->
            for (j in 0 until meta.childCount) {
                (meta.getChildAt(j) as? ImageView)?.setColorFilter(rowSecondColor)
                (meta.getChildAt(j) as? TextView)?.setTextColor(rowSecondColor)
            }
        }
    }
}

internal fun MainActivity.rebuildActiveTrackList() {
    val container = activeTrackListContainer ?: return
    val source = activeTrackListSource ?: return
    val playlist = activeTrackListPlaylist
    val name = activeTrackListName
    container.removeAllViews()
    source.forEach { t ->
        addTrackRowTo(
            container,
            t,
            showRemove = playlist != null &&
                    playlist.id != "recent" &&
                    playlist.id != "liked" &&
                    playlist.id != "listen_later" &&
                    playlist.id != "downloads",
            playlist = playlist,
            playbackPlaylist = source,
            playbackPlaylistName = name
        )
    }
}

@SuppressLint("ClickableViewAccessibility")
internal fun MainActivity.addTrackRowTo(
    container: LinearLayout,
    track: Track,
    showRemove: Boolean = false,
    playlist: Playlist? = null,
    playbackPlaylist: List<Track>? = null,
    playbackPlaylistName: String? = null
): View {
    val isCurrent = currentTrack?.id == track.id && playbackPlaylist === currentPlaylist
    val rowTextColor = if (isCurrent) BG else TEXT
    val rowSecondColor = if (isCurrent) BG else SECONDARY
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(6), dp(4), dp(6))
        tag = RowTag(track.id, playbackPlaylist)
        if (isCurrent) {
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(12).toFloat())
                }
            }
            background = roundedDrawable(TEXT, 12)
        }
    }
    val cover = imageView(track).apply {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(10).toFloat())
            }
        }
    }
    row.addView(cover, LinearLayout.LayoutParams(dp(56), dp(56)))
    val text = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val title = TextView(this).apply {
        this.text = track.title
        textSize = 15f
        setTextColor(rowTextColor)
        setTypeface(null, android.graphics.Typeface.BOLD)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    text.addView(title, LinearLayout.LayoutParams(-1, -2))
    val artist = TextView(this).apply {
        this.text = track.artist
        textSize = 13f
        setTextColor(rowSecondColor)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    text.addView(artist, LinearLayout.LayoutParams(-1, -2))
    val meta = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    if (track.duration > 0) {
        meta.addView(TextView(this).apply {
            this.text = formatTime(track.duration.toLong() * 1000L)
            textSize = 12f
            setTextColor(rowSecondColor)
        }, LinearLayout.LayoutParams(-2, -2).apply {
            marginEnd = if (track.id in likedIds || track.id in downloadedIds) dp(8) else 0
        })
    }
    val metaIconColor = if (isCurrent) BG else TEXT
    if (track.id in likedIds) {
        meta.addView(ImageView(this).apply {
            tag = "heart"
            setImageResource(R.drawable.favorite_filled_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(metaIconColor)
        }, LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(6) })
    }
    if (track.id in downloadedIds) {
        meta.addView(ImageView(this).apply {
            setImageResource(R.drawable.download_done_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(metaIconColor)
        }, LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(6) })
    }
    text.addView(meta, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
    row.addView(text, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
    val menu = ImageView(this).apply {
        setImageResource(R.drawable.more_vert_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setColorFilter(if (isCurrent) BG else SECONDARY)
        setOnClickListener { showTrackMenu(track, playlist) }
    }
    row.addView(menu, LinearLayout.LayoutParams(dp(36), dp(56)))
    if (showRemove && playlist != null) {
        val handle = ImageView(this).apply {
            setImageResource(R.drawable.menu_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(SECONDARY)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            tag = "drag_handle"
            contentDescription = "Drag"
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        row.parent?.requestDisallowInterceptTouchEvent(true)
                        beginReorder(row, container, playlist, event.rawY)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        updateReorder(event.rawY)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        row.parent?.requestDisallowInterceptTouchEvent(false)
                        finishReorder(apply = true)
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        row.parent?.requestDisallowInterceptTouchEvent(false)
                        finishReorder(apply = false)
                        true
                    }
                    else -> false
                }
            }
        }
        row.addView(handle, LinearLayout.LayoutParams(dp(32), dp(56)))
    }
    row.setOnClickListener {
        val sourceList = playbackPlaylist ?: playlist?.tracks ?: mutableListOf(track)
        val sourceName = when {
            playlist != null -> playlist.name
            playbackPlaylist === recommendationPlaybackTracks -> "Recommendations"
            playbackPlaylist === similarPlaybackTracks -> "Similar Tracks"
            playbackPlaylistName != null -> playbackPlaylistName
            else -> "Single track"
        }
        playTrack(track, sourceList, sourceName)
        if (fullPlayerOverlay != null) {
            val oldOverlay = fullPlayerOverlay
            fullPlayerOverlay = null
            root.removeView(oldOverlay)
            showFullPlayer()
        }
    }
    row.setOnLongClickListener {
        showTrackMenu(track, playlist)
        true
    }
    container.addView(row)
    return row
}

internal fun MainActivity.beginReorder(
    row: View,
    container: LinearLayout,
    playlist: Playlist,
    startRawY: Float
) {
    val srcIndex = container.indexOfChild(row)
    if (srcIndex < 0) return
    stopAutoScroll()
    reorderActive = true
    reorderRow = row
    reorderContainer = container
    reorderPlaylist = playlist
    reorderSourceIndex = srcIndex
    reorderTargetIndex = srcIndex
    reorderStartRawY = startRawY
    reorderLastRawY = startRawY
    reorderStartScrollY = mainScroll.scrollY
    reorderRowHeight = row.height
    val n = container.childCount
    reorderRows = (0 until n).map { container.getChildAt(it) }
    reorderOriginalTops = FloatArray(n) { reorderRows[it].top.toFloat() }
    val scrollLoc = IntArray(2)
    mainScroll.getLocationOnScreen(scrollLoc)
    reorderTopLimitScreen = (scrollLoc[1] + stickyTopOffset).toFloat()
    reorderBottomLimitScreen = if (miniPlayer.isVisible) {
        val mpLoc = IntArray(2)
        miniPlayer.getLocationOnScreen(mpLoc)
        mpLoc[1].toFloat()
    } else {
        (scrollLoc[1] + mainScroll.height).toFloat()
    }
    val containerLoc = IntArray(2)
    container.getLocationOnScreen(containerLoc)
    reorderScreenTopAtStart = containerLoc[1].toFloat() + reorderOriginalTops[srcIndex]
    reorderRows.forEach {
        it.animate().cancel()
        it.translationY = 0f
    }
    row.animate()
        .scaleX(1.02f).scaleY(1.02f)
        .setDuration(120)
        .start()
    row.alpha = 0.95f
}

internal fun MainActivity.updateReorder(rawY: Float) {
    if (!reorderActive) return
    val row = reorderRow ?: return
    reorderLastRawY = rawY
    val rawDy = rawY - reorderStartRawY
    val scrollDy = mainScroll.scrollY - reorderStartScrollY
    val effectiveDy = rawDy + scrollDy
    val screenTop = reorderScreenTopAtStart + rawDy
    val screenBottom = screenTop + reorderRowHeight
    val adjustedDy = when {
        screenTop < reorderTopLimitScreen ->
            effectiveDy + (reorderTopLimitScreen - screenTop)
        screenBottom > reorderBottomLimitScreen ->
            effectiveDy - (screenBottom - reorderBottomLimitScreen)
        else -> effectiveDy
    }
    row.translationY = adjustedDy
    val myCenter =
        reorderOriginalTops[reorderSourceIndex] + adjustedDy + reorderRowHeight / 2f
    var newIndex = reorderSourceIndex
    while (newIndex > 0) {
        val aboveCenter =
            reorderOriginalTops[newIndex - 1] + reorderRows[newIndex - 1].height / 2f
        if (myCenter < aboveCenter) newIndex-- else break
    }
    while (newIndex < reorderRows.size - 1) {
        val belowCenter =
            reorderOriginalTops[newIndex + 1] + reorderRows[newIndex + 1].height / 2f
        if (myCenter > belowCenter) newIndex++ else break
    }
    if (newIndex != reorderTargetIndex) {
        reorderTargetIndex = newIndex
        shiftNeighbors()
    }
    maybeStartAutoScroll(rawY)
}

internal fun MainActivity.maybeStartAutoScroll(rawY: Float) {
    if (!reorderActive) { stopAutoScroll(); return }
    val inTopZone = rawY < reorderTopLimitScreen + reorderEdgeZonePx
    val inBottomZone = rawY > reorderBottomLimitScreen - reorderEdgeZonePx
    if (!inTopZone && !inBottomZone) { stopAutoScroll(); return }
    if (reorderAutoScrollRunnable != null) return
    val runnable = object : Runnable {
        override fun run() {
            if (!reorderActive) { stopAutoScroll(); return }
            val y = reorderLastRawY
            val step: Int = when {
                y < reorderTopLimitScreen + reorderEdgeZonePx -> {
                    val depth = ((reorderTopLimitScreen + reorderEdgeZonePx) - y) / reorderEdgeZonePx
                    (-reorderMaxStepPx * depth).toInt().coerceAtMost(-2)
                }
                y > reorderBottomLimitScreen - reorderEdgeZonePx -> {
                    val depth = (y - (reorderBottomLimitScreen - reorderEdgeZonePx)) / reorderEdgeZonePx
                    (reorderMaxStepPx * depth).toInt().coerceAtLeast(2)
                }
                else -> 0
            }
            if (step == 0) { stopAutoScroll(); return }
            val child = mainScroll.getChildAt(0)
            val maxScroll = ((child?.height ?: 0) - mainScroll.height).coerceAtLeast(0)
            val oldScroll = mainScroll.scrollY
            val newScroll = (oldScroll + step).coerceIn(0, maxScroll)
            if (newScroll == oldScroll) { stopAutoScroll(); return }
            mainScroll.scrollTo(0, newScroll)
            updateReorder(reorderLastRawY)
            mainScroll.postDelayed(this, 16L)
        }
    }
    reorderAutoScrollRunnable = runnable
    mainScroll.postDelayed(runnable, 16L)
}

internal fun MainActivity.stopAutoScroll() {
    reorderAutoScrollRunnable?.let { mainScroll.removeCallbacks(it) }
    reorderAutoScrollRunnable = null
}

internal fun MainActivity.shiftNeighbors() {
    for (i in reorderRows.indices) {
        val v = reorderRows[i]
        if (i == reorderSourceIndex) continue
        val targetTop: Float = when {
            reorderSourceIndex < reorderTargetIndex &&
                    i in (reorderSourceIndex + 1)..reorderTargetIndex ->
                reorderOriginalTops[i - 1]
            reorderSourceIndex > reorderTargetIndex &&
                    i in reorderTargetIndex until reorderSourceIndex ->
                reorderOriginalTops[i + 1]
            else -> reorderOriginalTops[i]
        }
        val shift = targetTop - reorderOriginalTops[i]
        v.animate()
            .translationY(shift)
            .setDuration(160)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }
}

internal fun MainActivity.finishReorder(apply: Boolean) {
    stopAutoScroll()
    if (!reorderActive) return
    val row = reorderRow
    val container = reorderContainer
    val playlist = reorderPlaylist
    val fromIdx = reorderSourceIndex
    val toIdx = reorderTargetIndex
    reorderActive = false
    reorderRow = null
    reorderContainer = null
    reorderPlaylist = null
    if (apply && row != null && container != null && playlist != null && fromIdx != toIdx
        && fromIdx in playlist.tracks.indices && toIdx in playlist.tracks.indices
    ) {
        val moved = playlist.tracks.removeAt(fromIdx)
        playlist.tracks.add(toIdx, moved)
        saveState()
        val scrollY = mainScroll.scrollY
        showPlaylist(playlist)
        mainScroll.post { mainScroll.scrollTo(0, scrollY) }
    } else {
        reorderRows.forEach {
            it.animate()
                .translationY(0f)
                .setDuration(160)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
        row?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(160)?.start()
    }
    reorderRows = emptyList()
    reorderOriginalTops = FloatArray(0)
}
