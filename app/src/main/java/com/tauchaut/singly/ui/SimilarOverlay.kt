package com.tauchaut.singly.ui

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.R
import com.tauchaut.singly.data.rememberTrack
import com.tauchaut.singly.models.Track
import com.tauchaut.singly.player.trimQueue
import com.tauchaut.singly.util.cleanYoutubeTitle
import com.tauchaut.singly.util.dp
import com.tauchaut.singly.util.extractVideoId
import com.tauchaut.singly.youtube.bestThumbnailFor
import com.tauchaut.singly.youtube.extractRelatedTracksFromYouTube
import com.tauchaut.singly.youtube.similarSearchSeeds
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@SuppressLint("SetTextI18n")
internal fun MainActivity.openSimilarPlaylistOverlay(sourceTracks: List<Track>, title: String) {
    closeFullPlayer()
    similarPlaylistOverlay?.let { root.removeView(it) }
    similarPlaylistTracks.clear()
    similarPlaylistSeenIds.clear()
    similarPlaylistExtractor = null
    similarPlaylistNextPage = null
    similarPlaylistLoading = false
    similarPlaylistTitle = "$title (similar)"
    similarPlaylistSourceArtists = sourceTracks
        .asSequence()
        .map { it.artist }
        .filter { it.isNotBlank() }
        .distinct()
        .take(10)
        .toMutableList()
    similarPlaylistSeedTrack = sourceTracks.firstOrNull()
    val overlay = FrameLayout(this).apply {
        setBackgroundColor(BG)
    }
    val scroll = ScrollView(this).apply { clipToPadding = false }
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        clipToPadding = false
        setPadding(dp(8), dp(10), dp(8), dp(200))
    }
    val back = ImageView(this).apply {
        setImageResource(R.drawable.arrow_back_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setColorFilter(TEXT)
        val p = dp(12)
        setPadding(p, p, p, p)
        isClickable = true
        isFocusable = true
        setOnClickListener { closeSimilarPlaylistOverlay() }
    }
    body.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
    body.addView(TextView(this).apply {
        text = similarPlaylistTitle
        textSize = 22f
        setTextColor(TEXT)
        setTypeface(null, android.graphics.Typeface.BOLD)
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(0, dp(4), 0, dp(12))
    })
    val tracksContainer = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }
    similarPlaylistContainer = tracksContainer
    body.addView(tracksContainer, LinearLayout.LayoutParams(-1, -2))
    val loadingView = TextView(this).apply {
        text = "Loading similar tracks..."
        textSize = 13f
        setTextColor(SECONDARY)
        gravity = Gravity.CENTER
        setPadding(0, dp(16), 0, dp(16))
    }
    body.addView(loadingView)
    scroll.addView(body)
    overlay.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    similarPlaylistOverlay = overlay
    root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
    overlay.elevation = dp(50).toFloat()
    bottomNav.bringToFront()
    bottomNav.elevation = dp(55).toFloat()
    miniPlayer.bringToFront()
    miniPlayer.elevation = dp(60).toFloat()
    loadMoreSimilarPlaylist(loadingView)
}

@SuppressLint("SetTextI18n")
internal fun MainActivity.loadMoreSimilarPlaylist(loadingView: TextView? = null) {
    if (similarPlaylistLoading) return
    similarPlaylistLoading = true
    loadingView?.visibility = View.VISIBLE
    val seed = similarPlaylistSeedTrack
    if (seed == null) {
        similarPlaylistLoading = false
        loadingView?.visibility = View.GONE
        return
    }
    thread {
        val excludeSnapshot = HashSet(similarPlaylistSeenIds)
        val fresh = extractRelatedTracksFromYouTube(seed.id, seed.artist, excludeSnapshot)
        runOnUiThread {
            val container = similarPlaylistContainer
            val take = fresh.take(20)
            take.forEach { t ->
                similarPlaylistSeenIds.add(t.id)
                rememberTrack(t)
                similarPlaylistTracks.add(t)
                trimQueue(similarPlaylistTracks)
                if (container != null) {
                    addTrackRowTo(
                        container,
                        t,
                        playbackPlaylist = similarPlaylistTracks,
                        playbackPlaylistName = similarPlaylistTitle
                    )
                }
            }
            similarPlaylistLoading = false
            loadingView?.visibility = View.GONE
            if (take.isEmpty()) {
                if (loadingView != null) {
                    loadingView.text = "No more similar tracks found"
                    loadingView.visibility = View.VISIBLE
                }
            }
        }
    }
}

internal fun MainActivity.closeSimilarPlaylistOverlay() {
    similarPlaylistOverlay?.let { root.removeView(it) }
    similarPlaylistOverlay = null
    similarPlaylistContainer = null
    if (fullPlayerOverlay != null) {
        fullPlayerOverlay?.bringToFront()
        fullPlayerOverlay?.elevation = dp(30).toFloat()
    }
    miniPlayer.elevation = dp(8).toFloat()
    bottomNav.elevation = dp(8).toFloat()
}

internal fun MainActivity.loadMoreSimilarTracks(track: Track) {
    if (similarTracksLoadingState) return
    similarTracksLoadingState = true
    similarTracksLoading?.visibility = View.VISIBLE
    val seeds = similarSearchSeeds(track)
    thread {
        val resultList = mutableListOf<Track>()
        var nextPage: Page? = null
        var loaded = false
        try {
            newPipeReady.await(15, TimeUnit.SECONDS)
            var extractor = similarTracksExtractor
            var pageToLoad = similarTracksNextPage
            if (extractor == null) {
                for (seed in seeds) {
                    try {
                        val candidate = ServiceList.YouTube.getSearchExtractor(seed)
                        candidate.fetchPage()
                        extractor = candidate
                        similarTracksExtractor = candidate
                        pageToLoad = null
                        break
                    } catch (e: Exception) { e.printStackTrace() }
                }
            } else if (pageToLoad == null) {
                runOnUiThread {
                    similarTracksLoadingState = false
                    similarTracksLoading?.visibility = View.GONE
                }
                return@thread
            }
            if (extractor != null) {
                val page = if (pageToLoad == null) {
                    extractor.initialPage
                } else {
                    extractor.getPage(pageToLoad)
                }
                nextPage = page.nextPage
                page.items.forEach { item ->
                    if (item is StreamInfoItem) {
                        val videoId = extractVideoId(item.url)
                        if (videoId.isNotEmpty() && resultList.none { it.id == videoId }) {
                            val (cleanTitle, cleanArtist) = cleanYoutubeTitle(
                                rawTitle = item.name ?: "Unknown Title",
                                rawArtist = item.uploaderName ?: "YouTube"
                            )
                            resultList.add(Track(
                                id = videoId,
                                title = cleanTitle,
                                artist = cleanArtist,
                                imageUrl = bestThumbnailFor(videoId),
                                duration = item.duration.toInt()
                            ))
                        }
                    }
                }
                loaded = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        runOnUiThread {
            if (loaded) similarTracksNextPage = nextPage
            val fresh = resultList
                .filter { it.id != track.id && it.id !in similarTracksSeenIds }
                .take(10)
            similarTracksContainer?.let { target ->
                fresh.forEach { similar ->
                    similarTracksSeenIds.add(similar.id)
                    rememberTrack(similar)
                    similarPlaybackTracks.add(similar)
                    trimQueue(similarPlaybackTracks)
                    addTrackRowTo(
                        target,
                        similar,
                        playbackPlaylist = similarPlaybackTracks,
                        playbackPlaylistName = "${similar.title} (similar)"
                    )
                }
            }
            similarTracksLoadingState = false
            similarTracksLoading?.visibility = View.GONE
            if (fresh.isEmpty() && similarTracksNextPage != null) {
                loadMoreSimilarTracks(track)
            } else if (fresh.isEmpty() && !loaded) {
                android.widget.Toast.makeText(this, "Failed to load similar tracks", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
