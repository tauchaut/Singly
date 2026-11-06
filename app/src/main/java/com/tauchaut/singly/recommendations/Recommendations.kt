package com.tauchaut.singly.recommendations

import android.annotation.SuppressLint
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.data.rememberTrack
import com.tauchaut.singly.models.ContextSheetOption
import com.tauchaut.singly.models.Track
import com.tauchaut.singly.player.trimQueue
import com.tauchaut.singly.ui.addTrackRowTo
import com.tauchaut.singly.ui.showContextSheet
import com.tauchaut.singly.ui.showHome
import com.tauchaut.singly.util.containsAsWholeWord
import com.tauchaut.singly.util.dp
import com.tauchaut.singly.youtube.extractRelatedTracksFromYouTube
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import androidx.core.view.isEmpty
import androidx.core.content.edit

internal fun MainActivity.setupHomeRecommendations() {
    recommendationGeneration++
    recommendationSeenIds.clear()
    recommendationSeedIndex = 0
    recommendationExtractor = null
    recommendationNextPage = null
    recommendationPlaybackTracks.clear()
    recommendationLoading = false
    recommendationEmptyStreak = 0
    homeRecommendationContainer?.removeAllViews()
    homeRecommendationScrollLoading?.visibility = View.VISIBLE
    homeRecommendationRefreshBtn?.visibility = View.GONE
    loadMoreHomeRecommendations(initial = true)
}

@SuppressLint("SetTextI18n")
internal fun MainActivity.loadMoreHomeRecommendations(initial: Boolean = false) {
    if (recommendationLoading) return
    val existing = homeRecommendationContainer?.childCount ?: 0
    if (!initial && existing >= 40) {
        homeRecommendationScrollLoading?.visibility = View.GONE
        homeRecommendationRefreshBtn?.visibility = View.VISIBLE
        swipeRefresh?.isRefreshing = false
        return
    }
    val generation = recommendationGeneration
    recommendationLoading = true
    homeRecommendationScrollLoading?.visibility = View.VISIBLE
    homeRecommendationRefreshBtn?.visibility = View.GONE
    val seeds = mutableListOf<Track>()
    repeat(3) {
        val t = pickRecommendationSeedTrack()
        if (t != null && seeds.none { it.id == t.id }) {
            seeds.add(t)
            recommendationUsedSeedIds.add(t.id)
        }
    }
    if (seeds.isEmpty()) {
        Log.w("Rec", "No seeds: liked=${likedTracks.size} recent=${recentTracks.size} dl=${downloadedTracks.size}")
        recommendationLoading = false
        homeRecommendationScrollLoading?.visibility = View.GONE
        swipeRefresh?.isRefreshing = false
        val container = homeRecommendationContainer
        if (container != null && container.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "No liked tracks yet.\nLike anything and recommendations will appear."
                textSize = 14f
                setTextColor(SECONDARY)
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(40), dp(20), dp(40))
            })
        }
        return
    }
    val filterModeSnap = recommendationFilterMode
    val knownArtistsSnap = if (filterModeSnap == "new_artists")
        knownArtistNames().filter { it.length >= 3 }.toSet() else emptySet()
    val knownTitlesSnap = if (filterModeSnap == "new_tracks")
        knownTrackTitles().filter { it.length >= 4 }.toSet() else emptySet()
    val seenIdsSnap = HashSet(recommendationSeenIds)
    val recentlyPlayedSnap = HashMap(recentlyPlayed)
    val currentTrackIdSnap = currentTrack?.id
    thread {
        val collected = java.util.Collections.synchronizedList(mutableListOf<Track>())
        val seenInBatch = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        try {
            newPipeReady.await(15, TimeUnit.SECONDS)
            val executor = java.util.concurrent.Executors.newFixedThreadPool(seeds.size)
            val latch = java.util.concurrent.CountDownLatch(seeds.size)
            for (seed in seeds) {
                executor.submit {
                    try {
                        val batch =
                            extractRelatedTracksFromYouTube(seed.id, seed.artist, seenIdsSnap)
                        for (t in batch) {
                            if (!seenInBatch.add(t.id)) continue
                            if (t.id == currentTrackIdSnap) continue
                            val last = recentlyPlayedSnap[t.id]
                            if (last != null && System.currentTimeMillis() - last < COOLDOWN_MS) continue
                            when (filterModeSnap) {
                                "new_artists" -> {
                                    val ca = t.artist.lowercase().trim()
                                    val ct = t.title.lowercase().trim()
                                    val known = knownArtistsSnap.any {
                                        containsAsWholeWord(ca, it) || containsAsWholeWord(ct, it)
                                    }
                                    if (known) continue
                                }
                                "new_tracks" -> {
                                    val ct = t.title.lowercase().trim()
                                    if (knownTitlesSnap.any { ct.contains(it) }) continue
                                }
                            }
                            collected.add(t)
                        }
                    } catch (e: Exception) {
                        Log.e("Rec", "seed ${seed.id} failed", e)
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await(12, TimeUnit.SECONDS)
            executor.shutdownNow()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val resultList = synchronized(collected) { collected.toList() }
        runOnUiThread {
            if (generation != recommendationGeneration) return@runOnUiThread
            val container = homeRecommendationContainer
            val fresh = diversify(resultList.shuffled(), maxPerArtist = 2).toMutableList()
            if (container != null && fresh.isNotEmpty()) {
                val roomLeft = 40 - container.childCount
                fresh.take(minOf(15, roomLeft.coerceAtLeast(0))).forEach { track ->
                    recommendationSeenIds.add(track.id)
                    rememberTrack(track)
                    recommendationPlaybackTracks.add(track)
                    addTrackRowTo(
                        container,
                        track,
                        playbackPlaylist = recommendationPlaybackTracks,
                        playbackPlaylistName = "Recommendations"
                    )
                }
            }
            trimQueue(recommendationPlaybackTracks)
            recommendationLoading = false
            homeRecommendationScrollLoading?.visibility = View.GONE
            swipeRefresh?.isRefreshing = false
            if (fresh.isEmpty()) {
                recommendationEmptyStreak++
                if (recommendationEmptyStreak <= MAX_EMPTY_STREAK) {
                    mainScroll.postDelayed({
                        if (generation == recommendationGeneration) loadMoreHomeRecommendations()
                    }, 300)
                } else {
                    if (container != null && container.isEmpty()) {
                        val empty = TextView(this).apply {
                            text = "No recommendations"
                            textSize = 14f
                            setTextColor(SECONDARY)
                            gravity = Gravity.CENTER
                            setPadding(0, dp(40), 0, dp(40))
                        }
                        container.addView(empty)
                    }
                }
            } else {
                recommendationEmptyStreak = 0
                if (fresh.size < 3) {
                    mainScroll.postDelayed({
                        if (generation == recommendationGeneration && !recommendationLoading) {
                            loadMoreHomeRecommendations()
                        }
                    }, 100)
                }
            }
        }
    }
}

internal fun MainActivity.pickRecommendationSeedTrack(): Track? {
    val pool = (likedTracks + recentTracks + downloadedTracks)
        .filter { it.id !in recommendationUsedSeedIds && !it.isLive && it.id.isNotBlank() }
        .distinctBy { it.id }
    if (pool.isEmpty()) {
        recommendationUsedSeedIds.clear()
        return (likedTracks + recentTracks + downloadedTracks)
            .filter { !it.isLive && it.id.isNotBlank() }
            .shuffled()
            .firstOrNull()
    }
    return pool.shuffled().firstOrNull()
}

internal fun diversify(tracks: List<Track>, maxPerArtist: Int = 1): List<Track> {
    val result = mutableListOf<Track>()
    val artistCount = mutableMapOf<String, Int>()
    for (track in tracks) {
        val key = track.artist.lowercase().trim()
        val count = artistCount[key] ?: 0
        if (count < maxPerArtist) {
            result.add(track)
            artistCount[key] = count + 1
        }
    }
    return result
}

internal fun MainActivity.knownArtistNames(): Set<String> {
    val set = mutableSetOf<String>()
    fun add(list: List<Track>) = list.forEach {
        val key = it.artist.lowercase().trim()
        if (key.isNotEmpty()) set.add(key)
    }
    add(tracks); add(recentTracks); add(likedTracks); add(downloadedTracks)
    playlists.forEach { add(it.tracks) }
    return set
}

internal fun MainActivity.knownTrackTitles(): Set<String> {
    val set = mutableSetOf<String>()
    fun add(list: List<Track>) = list.forEach {
        val key = it.title.lowercase().trim()
        if (key.isNotEmpty()) set.add(key)
    }
    add(tracks); add(recentTracks); add(likedTracks); add(downloadedTracks)
    playlists.forEach { add(it.tracks) }
    return set
}

internal fun MainActivity.showRecommendationFilterMenu() {
    fun resetFeed() {
        homeRecommendationContainer?.let {
            (it.parent as? android.view.ViewGroup)?.removeView(it)
        }
        homeRecommendationContainer = null
        recommendationGeneration++
        recommendationSeenIds.clear()
        recommendationSeedIndex = 0
        recommendationExtractor = null
        recommendationNextPage = null
        recommendationPlaybackTracks.clear()
        recommendationLoading = false
        showHome()
        mainScroll.post { mainScroll.scrollTo(0, 0) }
    }
    fun pick(mode: String) {
        if (recommendationFilterMode == mode) return
        recommendationFilterMode = mode
        prefs.edit { putString("rec_filter_mode", mode) }
        resetFeed()
    }
    showContextSheet(
        title = "Recommendations",
        subtitle = "What to show",
        options = listOf(
            ContextSheetOption("All", trailingCheck = recommendationFilterMode == "all") {
                pick("all")
            },
            ContextSheetOption("Only new artists",
                trailingCheck = recommendationFilterMode == "new_artists") {
                pick("new_artists")
            },
            ContextSheetOption("Only new tracks",
                trailingCheck = recommendationFilterMode == "new_tracks") {
                pick("new_tracks")
            }
        )
    )
}
