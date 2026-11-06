package com.tauchaut.singly.data

import com.tauchaut.singly.cover.trimCoverDiskCache
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.models.Playlist
import com.tauchaut.singly.models.Track
import com.tauchaut.singly.ui.refreshLikedSet
import com.tauchaut.singly.ui.refreshDownloadedSet
import com.tauchaut.singly.util.cleanYoutubeTitle
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.concurrent.thread
import androidx.core.content.edit

internal fun MainActivity.saveState() {
    val all = LinkedHashMap<String, Track>()
    tracks.forEach { all[it.id] = it }
    recentTracks.forEach { all[it.id] = it }
    likedTracks.forEach { all[it.id] = it }
    playlists.forEach { playlist -> playlist.tracks.forEach { all[it.id] = it } }
    val tracksJson = org.json.JSONArray()
    all.values.forEach { track ->
        val likedFromSource = likedTracks.any { it.id == track.id }
        tracksJson.put(org.json.JSONObject().apply {
            put("id", track.id)
            put("title", track.title)
            put("artist", track.artist)
            put("imageUrl", track.imageUrl)
            put("streamUrl", "")
            put("duration", track.duration)
            put("liked", likedFromSource)
            put("isLive", track.isLive)
        })
    }
    val recentMapJson = org.json.JSONObject().apply {
        recentlyPlayed.forEach { (id, ts) -> put(id, ts) }
    }
    val recent = recentTracks.joinToString("|") { it.id }
    val liked = likedTracks.joinToString("|") { it.id }
    val custom = playlists
        .filter { it.id != "recent" && it.id != "liked" && it.id != "listen_later" && it.id != "downloads" }
        .joinToString(";;") { playlist ->
            playlist.name + "::" + playlist.tracks.joinToString(",") { it.id }
        }
    prefs.edit {
        putString("library_tracks", tracksJson.toString())
            .putString("recent", recent)
            .putString("liked", liked)
            .putString("playlists", custom)
            .putString(
                "listen_later",
                playlists.find { it.id == "listen_later" }?.tracks?.joinToString("|") { it.id }
                    ?: "")
            .putString("queue_name", currentPlaylistName)
            .putString("recently_played", recentMapJson.toString())
            .putString("last_track_id", currentTrack?.id ?: "")
            .putString("last_source_key", currentSourceKey)
            .putString("pinned_playlists", pinnedPlaylistIds.joinToString("|"))
    }
    requestCoverTrim()
    refreshLikedSet()
    refreshDownloadedSet()
}

internal fun MainActivity.loadState() {
    recentTracks.clear()
    likedTracks.clear()
    downloadedTracks.clear()
    tracks.clear()
    playlists.clear()
    val savedTracks = prefs.getString("library_tracks", "") ?: ""
    if (savedTracks.isNotEmpty()) {
        try {
            val array = org.json.JSONArray(savedTracks)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val rawTitle = obj.optString("title", "Unknown Title")
                val rawArtist = obj.optString("artist", "YouTube")
                val (cleanTitle, cleanArtist) = cleanYoutubeTitle(rawTitle, rawArtist)
                val track = Track(
                    id = obj.optString("id"),
                    title = cleanTitle,
                    artist = cleanArtist,
                    imageUrl = obj.optString("imageUrl", ""),
                    streamUrl = obj.optString("streamUrl", ""),
                    duration = obj.optInt("duration", 0),
                    liked = obj.optBoolean("liked", false),
                    isLive = obj.optBoolean("isLive", false)
                )
                if (track.id.isNotEmpty()) tracks.add(track)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    filesDir.listFiles { _, name -> name.endsWith(".mp3.tmp") }?.forEach {
        it.delete()
    }
    val byId = tracks.associateBy { it.id }
    val downloadedFiles = filesDir.listFiles { _, name -> name.endsWith(".mp3") }
        ?.sortedByDescending { it.lastModified() }
    downloadedFiles?.forEach { file ->
        val id = file.name.removeSuffix(".mp3")
        val track = byId[id] ?: Track(id, "Unknown Audio", "Unknown Artist")
        if (downloadedTracks.none { it.id == track.id }) {
            downloadedTracks.add(track)
        }
        if (!tracks.any { it.id == id }) {
            tracks.add(track)
        }
    }
    val updatedById = tracks.associateBy { it.id }
    val recentIds = (prefs.getString("recent", "") ?: "").split("|").filter { it.isNotEmpty() }
    recentIds.forEach { id -> updatedById[id]?.let { recentTracks.add(it) } }
    val rpJson = prefs.getString("recently_played", "") ?: ""
    if (rpJson.isNotEmpty()) {
        try {
            val obj = org.json.JSONObject(rpJson)
            obj.keys().forEach { id ->
                recentlyPlayed[id] = obj.getLong(id)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
    val likedIds = (prefs.getString("liked", "") ?: "").split("|").filter { it.isNotEmpty() }
    likedIds.forEach { id ->
        updatedById[id]?.let {
            it.liked = true
            if (likedTracks.none { item -> item.id == it.id }) likedTracks.add(it)
        }
    }
    playlists.add(Playlist("recent", "Recent tracks", recentTracks))
    playlists.add(Playlist("liked", "Liked", likedTracks))
    val listenLater = mutableListOf<Track>()
    (prefs.getString("listen_later", "") ?: "").split("|").filter { it.isNotEmpty() }.forEach { id ->
        updatedById[id]?.let { listenLater.add(it) }
    }
    playlists.add(Playlist("listen_later", "Listen later", listenLater))
    playlists.add(Playlist("downloads", "Downloads", downloadedTracks))
    currentPlaylistName = prefs.getString("queue_name", "Queue") ?: "Queue"
    val saved = prefs.getString("playlists", "") ?: ""
    if (saved.isNotEmpty()) {
        saved.split(";;").forEach { raw ->
            val parts = raw.split("::", limit = 2)
            if (parts.size != 2 || parts[0].isBlank()) return@forEach
            val playlist = Playlist(
                "custom_${System.nanoTime()}",
                parts[0]
            )
            parts[1].split(",").filter { it.isNotEmpty() }.forEach { id ->
                updatedById[id]?.let { if (playlist.tracks.none { t -> t.id == id }) playlist.tracks.add(it) }
            }
            playlists.add(playlist)
        }
    }
    likedTracks.forEach { liked ->
        tracks.firstOrNull { it.id == liked.id }?.liked = true
        recentTracks.firstOrNull { it.id == liked.id }?.liked = true
        downloadedTracks.firstOrNull { it.id == liked.id }?.liked = true
        playlists.forEach { p ->
            p.tracks.firstOrNull { it.id == liked.id }?.liked = true
        }
    }
    tracks.forEach { t ->
        if (likedTracks.none { it.id == t.id }) t.liked = false
    }
    recentTracks.forEach { t ->
        if (likedTracks.none { it.id == t.id }) t.liked = false
    }
    playlists.forEach { p ->
        p.tracks.forEach { t ->
            if (likedTracks.none { it.id == t.id }) t.liked = false
        }
    }
    pinnedPlaylistIds.clear()
    (prefs.getString("pinned_playlists", "") ?: "")
        .split("|").filter { it.isNotEmpty() }
        .forEach { pinnedPlaylistIds.add(it) }
    refreshLikedSet()
    refreshDownloadedSet()
}

internal fun MainActivity.rememberTrack(track: Track) {
    val existing = tracks.indexOfFirst { it.id == track.id }
    if (existing >= 0) {
        val stored = tracks[existing]
        stored.title = track.title
        stored.artist = track.artist
        if (track.imageUrl.isNotEmpty()) stored.imageUrl = track.imageUrl
        if (track.streamUrl.isNotEmpty()) stored.streamUrl = track.streamUrl
        if (track.duration > 0) stored.duration = track.duration
        stored.liked = track.liked
    } else {
        tracks.add(track)
        if (tracks.size > 2000) {
            val protectedIds = mutableSetOf<String>().apply {
                likedTracks.forEach { add(it.id) }
                downloadedTracks.forEach { add(it.id) }
                playlists.forEach { p -> p.tracks.forEach { add(it.id) } }
                recentTracks.forEach { add(it.id) }
            }
            val idx = tracks.indexOfFirst { it.id !in protectedIds }
            if (idx >= 0) tracks.removeAt(idx)
        }
    }
}

internal fun MainActivity.addToRecent(track: Track) {
    val playingFromRecent = currentPlaylistName == "Recent tracks"
    if (playingFromRecent && recentTracks.contains(track)) {
        recentlyPlayed[track.id] = System.currentTimeMillis()
        return
    }
    recentlyPlayed[track.id] = System.currentTimeMillis()
    val cutoff = System.currentTimeMillis() - COOLDOWN_MS * 4
    recentlyPlayed.entries.removeAll { it.value < cutoff }
    recentTracks.removeAll { it.id == track.id }
    recentTracks.add(0, track)
    while (recentTracks.size > 50) {
        recentTracks.removeAt(recentTracks.lastIndex)
    }
    val recent = playlists.find { it.id == "recent" }
    if (recent != null && recent.tracks !== recentTracks) {
        recent.tracks = recentTracks
    }
    rememberTrack(track)
    saveState()
}

internal fun MainActivity.requestCoverTrim() {
    val last = prefs.getLong("cover_cache_trimmed_at", 0L)
    val now = System.currentTimeMillis()
    if (now - last < 30_000) return
    prefs.edit { putLong("cover_cache_trimmed_at", now) }
    thread { try { trimCoverDiskCache() } catch (_: Exception) {} }
}
