package com.tauchaut.singly.player

import android.view.View
import android.widget.Toast
import com.tauchaut.singly.R
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.cover.persistCoverIfCached
import com.tauchaut.singly.data.rememberTrack
import com.tauchaut.singly.data.saveState
import com.tauchaut.singly.models.DownloadJob
import com.tauchaut.singly.models.Track
import com.tauchaut.singly.ui.refreshActiveTrackList
import com.tauchaut.singly.ui.refreshDownloadedSet
import com.tauchaut.singly.util.formatBytes
import com.tauchaut.singly.youtube.fetchStreamUrlAndExecute
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.collections.set
import kotlin.concurrent.thread

internal fun MainActivity.downloadTrack(track: Track) {
    val localFile = File(filesDir, "${track.id}.mp3")
    if (localFile.exists()) {
        Toast.makeText(this, "Already downloaded", Toast.LENGTH_SHORT).show()
        return
    }
    if (downloadJobs.containsKey(track.id)) {
        Toast.makeText(this, "Is already downloading", Toast.LENGTH_SHORT).show()
        return
    }
    startPlaybackService()
    acquireLocks()
    val job = DownloadJob(trackId = track.id)
    downloadJobs[track.id] = job
    updateDownloadButton()
    fetchStreamUrlAndExecute(track) { streamUrl ->
        if (job.cancelled) {
            downloadJobs.remove(track.id)
            updateDownloadButton()
            return@fetchStreamUrlAndExecute
        }
        Toast.makeText(this, "Downloading: ${track.title}", Toast.LENGTH_SHORT).show()
        thread {
            val tmpFile = File(filesDir, "${track.id}.mp3.tmp")
            if (tmpFile.exists()) tmpFile.delete()
            var success = false
            try {
                val connection = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                    setRequestProperty("Accept", "*/*")
                    setRequestProperty("Accept-Encoding", "identity")
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    useCaches = false
                }
                job.connection = connection
                connection.connect()
                val totalSize = connection.contentLengthLong
                val input  = java.io.BufferedInputStream(connection.inputStream, 1 shl 20)
                val output = java.io.BufferedOutputStream(tmpFile.outputStream(), 1 shl 20)
                val buffer = ByteArray(1 shl 20)
                var bytesRead: Int
                var totalRead = 0L
                var lastPct = -1
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (job.cancelled) break
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    job.bytesRead = totalRead
                    if (totalSize > 0) {
                        val pct = ((totalRead * 100) / totalSize).toInt()
                        if (pct != lastPct) {
                            lastPct = pct
                            job.progress = pct
                            runOnUiThread { updateDownloadButton() }
                        }
                    }
                }
                output.flush()
                output.close()
                input.close()
                if (job.cancelled) tmpFile.delete() else {
                    if (tmpFile.renameTo(localFile)) success = true
                    else {
                        try {
                            tmpFile.copyTo(localFile, overwrite = true)
                            tmpFile.delete()
                            success = localFile.exists() && localFile.length() > 0
                        } catch (_: Exception) { localFile.delete() }
                    }
                }
            } catch (_: Exception) {

            } finally {
                try { job.connection?.disconnect() } catch (_: Exception) {}
                if (!success && tmpFile.exists()) tmpFile.delete()
            }
            runOnUiThread {
                downloadJobs.remove(track.id)
                if (downloadJobs.isEmpty() && !isPlaying) {
                    releaseLocks()
                }
                updateDownloadButton()
                if (success) {
                    Toast.makeText(this, "Downloaded: ${track.title}", Toast.LENGTH_SHORT).show()

                    if (downloadedTracks.none { it.id == track.id }) {
                        downloadedTracks.add(0, track)
                        persistCoverIfCached(track)
                    }
                    val downloadsPlaylist = playlists.find { it.id == "downloads" }
                    if (downloadsPlaylist != null && downloadsPlaylist.tracks.none { it.id == track.id }) {
                        downloadsPlaylist.tracks.add(0, track)
                    }
                    rememberTrack(track)
                    saveState()
                    refreshActiveTrackList()
                } else if (!job.cancelled) {
                    Toast.makeText(this, "Downloading error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    refreshDownloadedSet()
}

internal fun MainActivity.updateDownloadButton() {
    val btn = fpDownloadBtn ?: return
    val percent = fpDownloadPercent
    val track = currentTrack ?: return
    val job = downloadJobs[track.id]
    val downloaded = File(filesDir, "${track.id}.mp3").exists()
    when {
        job != null -> {
            btn.visibility = View.GONE
            percent?.visibility = View.VISIBLE
            percent?.text = when {
                job.progress > 0   -> "${job.progress}%"
                job.bytesRead > 0  -> formatBytes(job.bytesRead)
                else               -> "···"
            }
        }
        downloaded -> {
            btn.visibility = View.VISIBLE
            btn.setImageResource(R.drawable.download_done_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
            btn.setColorFilter(TEXT, android.graphics.PorterDuff.Mode.SRC_IN)
            btn.alpha = 1f
            percent?.visibility = View.GONE
        }
        else -> {
            btn.visibility = View.VISIBLE
            btn.setImageResource(R.drawable.download_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
            btn.setColorFilter(SECONDARY, android.graphics.PorterDuff.Mode.SRC_IN)
            btn.alpha = 1f
            percent?.visibility = View.GONE
        }
    }
}

internal fun MainActivity.isTrackStillInUse(trackId: String): Boolean {
    if (likedTracks.any { it.id == trackId }) return true
    if (downloadedTracks.any { it.id == trackId }) return true
    if (recentTracks.any { it.id == trackId }) return true
    if (playlists.any { p -> p.tracks.any { it.id == trackId } }) return true
    return false
}
