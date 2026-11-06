package com.tauchaut.singly.ui

import android.app.AlertDialog
import android.graphics.Typeface
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.R
import com.tauchaut.singly.cover.preloadCover
import com.tauchaut.singly.data.saveState
import com.tauchaut.singly.models.ContextSheetOption
import com.tauchaut.singly.models.Playlist
import com.tauchaut.singly.models.Track
import com.tauchaut.singly.player.downloadTrack
import com.tauchaut.singly.player.playTrack
import com.tauchaut.singly.util.dp
import com.tauchaut.singly.util.formatDurationLong
import com.tauchaut.singly.util.tracksWord
import java.io.File

internal fun MainActivity.showPlaylist(playlist: Playlist) {
    inSubScreen = true
    currentPlaylistView = playlist
    content.removeAllViews()
    contentPaddingDense()
    playlist.tracks.take(8).forEach { preloadCover(it) }
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    header.setPadding(0, 0, 0, 12)
    val backBtn = ImageView(this).apply {
        setImageResource(R.drawable.arrow_back_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setColorFilter(TEXT)
        val p = dp(10)
        setPadding(p, p, p, p)
        isClickable = true
        isFocusable = true
        setOnClickListener { selectTab(TAB_PLAYLISTS) }
    }
    header.addView(backBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
    val titleColumn = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
    }
    titleColumn.addView(TextView(this).apply {
        text = playlist.name
        textSize = 19f
        setTextColor(TEXT)
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }, LinearLayout.LayoutParams(-1, -2))
    val totalSeconds = playlist.tracks.sumOf { it.duration.toLong() }
    val subtitle = buildString {
        append("${playlist.tracks.size} ")
        append(tracksWord(playlist.tracks.size))
        if (totalSeconds > 0) {
            append(" – ")
            append(formatDurationLong(totalSeconds))
        }
    }
    titleColumn.addView(TextView(this).apply {
        text = subtitle
        textSize = 12f
        setTextColor(SECONDARY)
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(0, dp(2), 0, 0)
    }, LinearLayout.LayoutParams(-1, -2))
    header.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f))
    val menuBtn = ImageView(this).apply {
        setImageResource(R.drawable.menu_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setColorFilter(TEXT)
        val p = dp(10)
        setPadding(p, p, p, p)
        isClickable = true
        isFocusable = true
        setOnClickListener { showPlaylistActionsMenu(playlist) }
    }
    header.addView(menuBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
    header.setBackgroundColor(BG)
    showStickyHeader(header)
    if (playlist.tracks.isEmpty()) {
        addText("Playlist is empty", SECONDARY, 15f)
        return
    }
    val canReorder = playlist.id != "recent"
            && playlist.id != "listen_later"
            && playlist.id != "downloads"
    val tracksContainer = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }
    content.addView(tracksContainer, LinearLayout.LayoutParams(-1, -2))
    activeTrackListContainer = tracksContainer
    activeTrackListSource    = playlist.tracks
    activeTrackListPlaylist  = playlist
    activeTrackListName      = playlist.name
    playlist.tracks.forEach { track ->
        addTrackRowTo(
            tracksContainer, track,
            showRemove = canReorder,
            playlist = playlist,
            playbackPlaylist = playlist.tracks,
            playbackPlaylistName = playlist.name
        )
    }
}

internal fun MainActivity.showPlaylistActionsMenu(playlist: Playlist) {
    val isSystem = playlist.id in setOf("recent", "liked", "listen_later", "downloads")
    val options = mutableListOf<ContextSheetOption>()
    options.add(ContextSheetOption("Open") {
        if (playlist.id == "recent") showRecent() else showPlaylist(playlist)
    })
    if (playlist.tracks.isNotEmpty()) {
        options.add(ContextSheetOption("Play all") {
            playTrack(playlist.tracks.first(), playlist.tracks, playlist.name)
        })
    }
    if (!isSystem) {
        options.add(ContextSheetOption("Rename") {
            renamePlaylistDialog(playlist)
        })
        options.add(ContextSheetOption("Delete playlist", destructive = true) {
            deletePlaylistDialog(playlist)
        })
    }
    val pinned = isPinned(playlist.id)
    options.add(ContextSheetOption(
        if (pinned) "Unpin" else "Pin"
    ) {
        if (pinned) pinnedPlaylistIds.remove(playlist.id)
        else {
            pinnedPlaylistIds.remove(playlist.id)
            pinnedPlaylistIds.add(0, playlist.id)
        }
        saveState()
        if (currentTab == TAB_HOME) showHome()
        if (currentTab == TAB_PLAYLISTS) showPlaylistsTab()
        Toast.makeText(this, if (pinned) "Unpinned" else "Pinned", Toast.LENGTH_SHORT).show()
    })
    showContextSheet(
        title = playlist.name,
        subtitle = "${playlist.tracks.size} ${tracksWord(playlist.tracks.size)}",
        options = options
    )
}

internal fun MainActivity.showPlaylistMenu(playlist: Playlist) = showPlaylistActionsMenu(playlist)

internal fun MainActivity.renamePlaylistDialog(playlist: Playlist) {
    val input = EditText(this).apply {
        setText(playlist.name)
        setTextColor(TEXT)
        setHintTextColor(SECONDARY)
    }
    AlertDialog.Builder(this)
        .setTitle("Rename playlist")
        .setView(input)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Save") { _, _ ->
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty()) {
                playlist.name = newName
                saveState()
                if (currentTab == TAB_PLAYLISTS) showPlaylistsTab()
            }
        }
        .show()
}

internal fun MainActivity.deletePlaylistDialog(playlist: Playlist) {
    AlertDialog.Builder(this)
        .setTitle("Delete playlist")
        .setMessage("Are you sure you want to remove \"${playlist.name}\"?")
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Delete") { _, _ ->
            playlists.removeAll { it.id == playlist.id }
            pinnedPlaylistIds.remove(playlist.id)
            saveState()
            if (currentTab == TAB_PLAYLISTS) showPlaylistsTab()
            Toast.makeText(this, "Playlist deleted", Toast.LENGTH_SHORT).show()
        }
        .show()
}

internal fun MainActivity.showTrackMenu(track: Track, playlist: Playlist?) {
    val options = mutableListOf<ContextSheetOption>()
    options.add(ContextSheetOption("Play") {
        playTrack(track, playlist?.tracks ?: tracks)
    })
    val isLiked = likedTracks.any { it.id == track.id }
    options.add(ContextSheetOption(
        if (isLiked) "Remove from liked" else "Add to liked"
    ) { toggleLike(track) })
    options.add(ContextSheetOption("Add to playlist") {
        showPlaylistPicker(track)
    })
    options.add(ContextSheetOption("Listen later") {
        val later = playlists.find { it.id == "listen_later" }
        if (later != null && later.tracks.none { it.id == track.id }) {
            later.tracks.add(0, track)
            saveState()
            Toast.makeText(this, "Added to «Listen later»", Toast.LENGTH_SHORT).show()
        }
    })
    if (File(filesDir, "${track.id}.mp3").exists()) {
        options.add(ContextSheetOption("Remove from downloads", destructive = true) {
            File(filesDir, "${track.id}.mp3").delete()
            downloadedTracks.removeAll { it.id == track.id }
            playlists.find { it.id == "downloads" }?.tracks?.removeAll { it.id == track.id }
            saveState()
            refreshActiveTrackList()
            Toast.makeText(this, "File removed", Toast.LENGTH_SHORT).show()
        })
    } else {
        options.add(ContextSheetOption("Download") {
            downloadTrack(track)
        })
    }
    if (playlist != null && playlist.id != "recent") {
        options.add(ContextSheetOption(
            if (playlist.id == "liked") "Remove from liked" else "Remove from playlist",
            destructive = true
        ) {
            if (playlist.id == "liked") {
                likedTracks.removeAll { it.id == track.id }
                track.liked = false
            } else {
                playlist.tracks.removeAll { it.id == track.id }
            }
            saveState()
            if (currentPlaylistView?.id == playlist.id) showPlaylist(playlist)
        })
    }
    showContextSheet(track.title, track.artist, options)
}

internal fun MainActivity.showRecent() {
    val recent = playlists.find { it.id == "recent" } ?: return
    showPlaylist(recent)
}
