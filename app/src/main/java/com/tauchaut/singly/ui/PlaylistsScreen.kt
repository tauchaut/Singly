package com.tauchaut.singly.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Outline
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.R
import com.tauchaut.singly.cover.persistCoverIfCached
import com.tauchaut.singly.data.saveState
import com.tauchaut.singly.media.systemPlaylistIcon
import com.tauchaut.singly.models.ContextSheetOption
import com.tauchaut.singly.models.Playlist
import com.tauchaut.singly.models.Track
import com.tauchaut.singly.util.dp
import com.tauchaut.singly.util.formatDurationLong
import com.tauchaut.singly.util.roundedDrawable
import com.tauchaut.singly.util.tracksWord
import androidx.core.graphics.toColorInt

@SuppressLint("SetTextI18n")
internal fun MainActivity.showPlaylistsTab() {
    inSubScreen = false
    currentPlaylistView = null
    contentPaddingDense()
    content.removeAllViews()
    activeTrackListContainer = null
    activeTrackListSource = null
    activeTrackListPlaylist = null
    activeTrackListName = null
    val pinned = playlists
        .filter { isPinned(it.id) }
        .sortedBy { pinnedPlaylistIds.indexOf(it.id) }
    val unpinned = playlists.filter { !isPinned(it.id) }
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), 0, dp(8), dp(0))
        setBackgroundColor(BG)
    }
    val title = TextView(this).apply {
        text = "Playlists"
        textSize = 20f
        setTextColor(TEXT)
        setTypeface(null, Typeface.BOLD)
    }
    header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
    val addBtn = ImageView(this).apply {
        setImageResource(R.drawable.add_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setColorFilter(TEXT)
        isClickable = true
        isFocusable = true
        setOnClickListener { createPlaylist() }
    }
    header.addView(addBtn, LinearLayout.LayoutParams(dp(42), dp(42)))
    val menu = ImageView(this).apply {
        setImageResource(R.drawable.more_vert_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setColorFilter(TEXT)
        setOnClickListener { showPlaylistsMenu() }
    }
    header.addView(menu, LinearLayout.LayoutParams(dp(42), dp(42)))
    showStickyHeader(header)
    if (playlists.isEmpty()) {
        addText("No playlists", SECONDARY, 15f)
        return
    }
    val itemsPerRow = 2
    val gap = dp(14)
    val horizontalMargins = dp(8) * 2
    val horizontalPadding = content.paddingLeft + content.paddingRight
    val screenWidth = resources.displayMetrics.widthPixels
    val cellWidth = (screenWidth - horizontalPadding - horizontalMargins - gap) / itemsPerRow
    val cardHeight = cellWidth + dp(48)
    fun addSection(label: String, list: List<Playlist>) {
        if (list.isEmpty()) return
        content.addView(TextView(this).apply {
            text = label
            textSize = 20f
            setTextColor(TEXT)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(8), dp(0), dp(8), dp(10))
        })
        for (i in list.indices step itemsPerRow) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (j in 0 until itemsPerRow) {
                val index = i + j
                val lp = LinearLayout.LayoutParams(0, cardHeight, 1f).apply {
                    if (j < itemsPerRow - 1) marginEnd = gap
                }
                if (index < list.size) {
                    row.addView(playlistGridCard(list[index], cellWidth), lp)
                } else {
                    row.addView(View(this), lp)
                }
            }
            content.addView(row, LinearLayout.LayoutParams(-1, cardHeight).apply {
                marginStart = dp(8)
                marginEnd = dp(8)
                bottomMargin = dp(18)
            })
        }
    }
    addSection("Pinned", pinned)
    addSection("All playlists", unpinned)
}

internal fun MainActivity.playlistGridCard(playlist: Playlist, coverSize: Int): View {
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        isClickable = true
        isFocusable = true
    }
    val cover = FrameLayout(this).apply {
        background = roundedDrawable(CARD, 20)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(20).toFloat())
            }
        }
    }
    val coverTracks = playlist.tracks.take(4)
    val sysIcon = systemPlaylistIcon(playlist.id)
    if (coverTracks.isEmpty()) {
        if (sysIcon == 0) {
            val noteIcon = ImageView(this).apply {
                setImageResource(R.drawable.music_note_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
                scaleType = ImageView.ScaleType.FIT_XY
                setColorFilter(SECONDARY)
            }
            val iconSize = (coverSize * 0.36f).toInt().coerceAtLeast(dp(28))
            cover.addView(
                noteIcon,
                FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
            )
        }
    } else {
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (r in 0 until 2) {
            val gridRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0 until 2) {
                val idx = r * 2 + c
                if (idx < coverTracks.size) {
                    gridRow.addView(
                        imageView(coverTracks[idx]),
                        LinearLayout.LayoutParams(0, -1, 1f)
                    )
                } else {
                    gridRow.addView(
                        View(this).apply { setBackgroundColor(CARD_ACTIVE) },
                        LinearLayout.LayoutParams(0, -1, 1f)
                    )
                }
            }
            grid.addView(gridRow, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        cover.addView(grid, FrameLayout.LayoutParams(-1, -1))
    }
    if (sysIcon != 0) {
        val darken = View(this).apply {
            setBackgroundColor("#99000000".toColorInt())
        }
        cover.addView(darken, FrameLayout.LayoutParams(-1, -1))
        val icon = ImageView(this).apply {
            setImageResource(sysIcon)
            scaleType = ImageView.ScaleType.FIT_XY
            setColorFilter(TEXT)
        }
        val iconSize = (coverSize * 0.36f).toInt().coerceAtLeast(dp(28))
        cover.addView(
            icon,
            FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
        )
    }
    box.addView(cover, LinearLayout.LayoutParams(coverSize, coverSize))
    box.addView(TextView(this).apply {
        text = playlist.name
        textSize = 14f
        setTextColor(TEXT)
        setTypeface(null, Typeface.BOLD)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(0, dp(8), 0, 0)
    })
    box.addView(TextView(this).apply {
        text = playlistSubtitleText(playlist)
        textSize = 11f
        setTextColor(SECONDARY)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    })
    box.setOnClickListener {
        if (playlist.id == "recent") showRecent() else showPlaylist(playlist)
    }
    box.setOnLongClickListener {
        showPlaylistMenu(playlist)
        true
    }
    return box
}

internal fun MainActivity.showPlaylistsMenu() {
    showContextSheet(
        title = "Playlists",
        options = listOf(
            ContextSheetOption("Create a playlist") { createPlaylist() },
            ContextSheetOption("Show all tracks") { showRecent() },
            ContextSheetOption("Liked") {
                playlists.find { it.id == "liked" }?.let { showPlaylist(it) }
            }
        )
    )
}

internal fun playlistSubtitleText(playlist: Playlist): String {
    val count = playlist.tracks.size
    val base = "$count ${tracksWord(count)}"
    val totalSec = playlist.tracks.sumOf { it.duration.toLong() }
    return if (totalSec > 0) "$base – ${formatDurationLong(totalSec)}" else base
}

internal fun MainActivity.createPlaylist(track: Track? = null) {
    val input = EditText(this)
    input.hint = "Playlist name"
    input.setTextColor(TEXT)
    input.setHintTextColor(SECONDARY)
    AlertDialog.Builder(this)
        .setTitle("Create a playlist")
        .setView(input)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Create") { _, _ ->
            val rawName = input.text.toString().trim()
            if (rawName.isEmpty()) return@setPositiveButton
            val name = makeUniquePlaylistName(rawName)
            val wasRenamed = name != rawName
            val playlist = Playlist("custom_${System.nanoTime()}", name)
            if (track != null) playlist.tracks.add(track)
            playlists.add(playlist)
            saveState()
            Toast.makeText(
                this,
                if (wasRenamed) "Created as «$name»"
                else "Playlist created",
                Toast.LENGTH_SHORT
            ).show()
            if (currentTab == TAB_PLAYLISTS) showPlaylistsTab()
        }
        .show()
}

internal fun MainActivity.showPlaylistPicker(track: Track) {
    val options = mutableListOf<ContextSheetOption>()
    playlists.filter { it.id != "recent" }.forEach { playlist ->
        options.add(ContextSheetOption(playlist.name) {
            if (playlist.id == "liked") {
                if (likedTracks.none { it.id == track.id }) {
                    likedTracks.add(0, track)
                    syncLikedFlag(track.id, true)
                    persistCoverIfCached(track)
                }
            } else {
                if (playlist.tracks.none { it.id == track.id }) {
                    playlist.tracks.add(0, track)
                    persistCoverIfCached(track)
                }
            }
            saveState()
            Toast.makeText(this, "Added to ${playlist.name}", Toast.LENGTH_SHORT).show()
        })
    }
    options.add(ContextSheetOption("+ Create playlist") { createPlaylist(track) })
    showContextSheet("Add to playlist", track.title, options)
}
