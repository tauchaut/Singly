package com.tauchaut.singly.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.data.loadPlaybackFlags
import com.tauchaut.singly.data.loadState
import com.tauchaut.singly.data.persistSearchHistory
import com.tauchaut.singly.data.savePlaybackFlags
import com.tauchaut.singly.data.saveState
import com.tauchaut.singly.util.dp
import com.tauchaut.singly.util.formatBytes
import org.json.JSONObject
import java.io.File
import androidx.core.net.toUri
import androidx.core.content.edit

@SuppressLint("BatteryLife", "SetTextI18n")
internal fun MainActivity.showSettings() {
    inSubScreen = true
    content.removeAllViews()
    hideStickyHeader()

    addBackButton { showProfile() }
    addTitle("Settings")

    sectionLabel("PAYBACK")
    settingSwitch("Track repeat", repeatTrack) {
        repeatTrack = it; if (it) repeatPlaylist = false; savePlaybackFlags()
    }
    settingSwitch("Playlist repeat", repeatPlaylist) {
        repeatPlaylist = it; if (it) repeatTrack = false; savePlaybackFlags()
    }
    settingSwitch("Random order", randomOrder) {
        randomOrder = it; savePlaybackFlags()
    }
    settingSwitch("Reversed order", reverseOrder) {
        reverseOrder = it; savePlaybackFlags()
    }

    addSpace(20)
    sectionLabel("STORAGE")

    val coversSize = coverDiskDir.listFiles()?.sumOf { it.length() } ?: 0L
    val dlSize = downloadedTracks.sumOf { File(filesDir, "${it.id}.mp3").length() }

    button("Covers cache — ${formatBytes(coversSize)}") {
        android.app.AlertDialog.Builder(this)
            .setTitle("Clear covers cache?")
            .setMessage("The covers will be downloaded again after next time you open it.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                coverDiskDir.listFiles()?.forEach { it.delete() }
                imageCache.evictAll()
                notificationIconCache.evictAll()
                Toast.makeText(this, "Cleared the cache", Toast.LENGTH_SHORT).show()
                showSettings()
            }
            .show()
    }

    button("Downloads — ${formatBytes(dlSize)}") {
        if (downloadedTracks.isEmpty()) {
            Toast.makeText(this, "No downloaded tracks", Toast.LENGTH_SHORT).show()
            return@button
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Remove all downloaded tracks?")
            .setMessage("The files will be removed from the device forever.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                downloadedTracks.forEach { File(filesDir, "${it.id}.mp3").delete() }
                downloadedTracks.clear()
                playlists.find { it.id == "downloads" }?.tracks?.clear()
                saveState()
                refreshDownloadedSet()
                showSettings()
            }
            .show()
    }

    addSpace(20)
    sectionLabel("PRIVACY")

    button("Clear search history") {
        android.app.AlertDialog.Builder(this)
            .setTitle("Clear search history?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                persistSearchHistory(emptyList())
                Toast.makeText(this, "Cleared search history", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    button("Clear recent tracks") {
        android.app.AlertDialog.Builder(this)
            .setTitle("Clear recent?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                recentTracks.clear()
                playlists.find { it.id == "recent" }?.tracks?.clear()
                saveState()
                Toast.makeText(this, "Cleared listening history", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    addSpace(20)
    sectionLabel("SYSTEM")

    button("Do not optimize the battery (recommended)") {
        val pm = getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "Already disabled", Toast.LENGTH_SHORT).show()
        } else {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        "package:$packageName".toUri()
                    )
                )
            } catch (_: Exception) {
                Toast.makeText(this, "Failed to open settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    addSpace(20)
    sectionLabel("ABOUT APPLICATION")

    content.addView(TextView(this).apply {
        text = "Singly\nLocal music player\nVersion 0.1"
        textSize = 13f
        setTextColor(SECONDARY)
        setLineSpacing(0f, 1.3f)
        setPadding(dp(4), dp(8), dp(4), dp(8))
    })
}

internal fun MainActivity.exportData() {
    val acc = currentAccount ?: return
    val safeName = acc.name.replace(Regex("[^a-zA-Z0-9а-яА-Я_-]"), "_")
    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/json"
        putExtra(
            Intent.EXTRA_TITLE,
            "tauchaut_${safeName}_${System.currentTimeMillis()}.json"
        )
    }
    @Suppress("DEPRECATION")
    startActivityForResult(intent, EXPORT_REQUEST)
}

internal fun MainActivity.importData() {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/json"
    }
    @Suppress("DEPRECATION")
    startActivityForResult(intent, IMPORT_REQUEST)
}

internal fun MainActivity.exportStateToJson(): String {
    saveState()
    val o = JSONObject()
    o.put("version", 1)
    o.put("account_name", currentAccount?.name ?: "")
    o.put("library_tracks", prefs.getString("library_tracks", "[]"))
    o.put("recent", prefs.getString("recent", ""))
    o.put("liked", prefs.getString("liked", ""))
    o.put("playlists", prefs.getString("playlists", ""))
    o.put("listen_later", prefs.getString("listen_later", ""))
    o.put("recently_played", prefs.getString("recently_played", "{}"))
    o.put("pinned_playlists", prefs.getString("pinned_playlists", ""))
    o.put("repeatTrack", prefs.getBoolean("repeatTrack", false))
    o.put("repeatPlaylist", prefs.getBoolean("repeatPlaylist", false))
    o.put("randomOrder", prefs.getBoolean("randomOrder", false))
    o.put("reverseOrder", prefs.getBoolean("reverseOrder", false))
    o.put("repeatSimilar", prefs.getBoolean("repeatSimilar", false))
    o.put("search_history_v2", prefs.getString("search_history_v2", "[]"))
    return o.toString(2)
}

internal fun MainActivity.importStateFromJson(json: String): Boolean {
    return try {
        val o = JSONObject(json)
        prefs.edit {
            putString("library_tracks", o.optString("library_tracks", "[]"))
            putString("recent", o.optString("recent", ""))
            putString("liked", o.optString("liked", ""))
            putString("playlists", o.optString("playlists", ""))
            putString("listen_later", o.optString("listen_later", ""))
            putString("recently_played", o.optString("recently_played", "{}"))
            putString("pinned_playlists", o.optString("pinned_playlists", ""))
            putBoolean("repeatTrack", o.optBoolean("repeatTrack", false))
            putBoolean("repeatPlaylist", o.optBoolean("repeatPlaylist", false))
            putBoolean("randomOrder", o.optBoolean("randomOrder", false))
            putBoolean("reverseOrder", o.optBoolean("reverseOrder", false))
            putBoolean("repeatSimilar", o.optBoolean("repeatSimilar", false))
            putString("search_history_v2", o.optString("search_history_v2", "[]"))
        }
        loadState()
        loadPlaybackFlags()
        true
    } catch (_: Exception) {
        false
    }
}
