package com.tauchaut.singly.cover

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.core.content.edit
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.media.loadNotificationIcon
import com.tauchaut.singly.media.updateMediaNotification
import com.tauchaut.singly.models.Track
import com.tauchaut.singly.player.isTrackStillInUse
import com.tauchaut.singly.ui.applyMiniPlayerCorners
import com.tauchaut.singly.util.blendWithBg
import com.tauchaut.singly.util.cropToSquare
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlin.collections.set
import kotlin.concurrent.thread

private fun saveCoverJpeg(bitmap: Bitmap, file: File) {
    runCatching {
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }
}

private fun colorLuminance(color: Int): Float {
    val r = Color.red(color) / 255f
    val g = Color.green(color) / 255f
    val b = Color.blue(color) / 255f
    return 0.299f * r + 0.587f * g + 0.114f * b
}

private fun colorSaturation(color: Int): Float {
    val r = Color.red(color) / 255f
    val g = Color.green(color) / 255f
    val b = Color.blue(color) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    return if (max == 0f) 0f else (max - min) / max
}

private fun MainActivity.resolveCoverBitmap(track: Track): Bitmap? {
    val id = track.id
    val diskFile = File(coverDiskDir, "$id.jpg")
    var fromDisk = false

    var bmp: Bitmap? = notificationIconCache.get(id)?.let {
        if (it.width == it.height) it
        else cropToSquare(it.copy(Bitmap.Config.ARGB_8888, false))
    }
    if (bmp != null && shouldPersistCover(id) && !diskFile.exists()) {
        saveCoverJpeg(bmp, diskFile)
    }

    if (bmp == null && diskFile.exists()) {
        val raw = BitmapFactory.decodeFile(diskFile.absolutePath)
        if (raw != null && raw.width >= 640) {
            bmp = raw
            fromDisk = true
        } else {
            raw?.recycle()
            diskFile.delete()
        }
    }

    if (bmp == null) bmp = downloadCoverParallel(id)

    val wasNormalized = bmp?.let { it.width == it.height } ?: true
    if (bmp != null) {
        bmp = cropToSquare(bmp)
        if (!wasNormalized) fromDisk = false
    }

    if (bmp != null && shouldPersistCover(id) && !fromDisk) {
        saveCoverJpeg(bmp, diskFile)
    }
    return bmp
}

internal fun MainActivity.loadImageIntoView(track: Track, target: ImageView?) {
    if (target == null) return
    val key = canonicalCoverUrl(track)
    val id = track.id
    imageCache.get(key)?.let { target.setImageBitmap(it); return }
    if (!ViewCompat.isAttachedToWindow(target) || !target.isShown) {
        target.tag = key
        target.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                v.post { if (target.tag == key) loadImageIntoView(track, target) }
            }
            override fun onViewDetachedFromWindow(v: View) = Unit
        })
        return
    }
    target.tag = key
    val onLoaded: (Bitmap?) -> Unit = { bmp ->
        if (bmp != null && target.tag == key) {
            target.setImageBitmap(bmp)
            if (target === miniCover) applyCoverGradientToMiniPlayer(currentTrack, bmp)
        }
    }
    inflightCoverRequests[id]?.let { it.add(onLoaded); return }
    inflightCoverRequests[id] = mutableListOf(onLoaded)
    coverExecutor.execute {
        val finalBmp = try {
            resolveCoverBitmap(track)
        } catch (e: Exception) {
            Log.e("Cover", "load failed for $id", e)
            null
        }
        finalBmp?.let { imageCache.put(key, it) }
        runOnUiThread {
            val callbacks = inflightCoverRequests.remove(id) ?: emptyList()
            callbacks.forEach { it(finalBmp) }
            if (target === miniCover) {
                applyCoverGradientToMiniPlayer(currentTrack, finalBmp)
                updateMediaNotification()
            }
        }
    }
}

internal fun downloadCoverParallel(id: String): Bitmap? {
    tryFetchBitmap("https://i.ytimg.com/vi/$id/maxresdefault.jpg", 1500)?.let {
        if (it.width >= 1000) return it
    }
    tryFetchBitmap("https://i.ytimg.com/vi/$id/sddefault.jpg", 2000)?.let {
        if (it.width >= 640) return it
    }
    return tryFetchBitmap("https://i.ytimg.com/vi/$id/hqdefault.jpg", 2000)
}

internal fun tryFetchBitmap(url: String, timeoutMs: Int): Bitmap? {
    return try {
        val conn = URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.instanceFollowRedirects = true
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            return null
        }
        val bytes = conn.inputStream.use { it.readBytes() }
        conn.disconnect()
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = 1
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    } catch (_: Exception) {
        null
    }
}

internal fun canonicalCoverUrl(track: Track): String =
    "https://i.ytimg.com/vi/${track.id}/maxresdefault.jpg"

internal fun MainActivity.preloadCover(track: Track) {
    val key = canonicalCoverUrl(track)
    if (imageCache.get(key) != null) return
    if (inflightCoverRequests.containsKey(track.id)) return
    inflightCoverRequests[track.id] = mutableListOf()
    coverExecutor.execute {
        val bmp = resolveCoverBitmap(track)
        if (bmp != null) imageCache.put(key, bmp)
        runOnUiThread { inflightCoverRequests.remove(track.id) }
    }
}

internal fun MainActivity.persistCoverIfCached(track: Track) {
    val key = canonicalCoverUrl(track)
    val bmp = imageCache.get(key) ?: return
    val diskFile = File(coverDiskDir, "${track.id}.jpg")
    if (diskFile.exists()) return
    thread { saveCoverJpeg(bmp, diskFile) }
}

internal fun MainActivity.shouldPersistCover(trackId: String): Boolean {
    if (likedTracks.any { it.id == trackId }) return true
    if (downloadedTracks.any { it.id == trackId }) return true
    return playlists.any { p -> p.tracks.any { it.id == trackId } }
}

internal fun MainActivity.maybeTrimCoverDiskCache() {
    val last = prefs.getLong("cover_cache_trimmed_at", 0L)
    val now = System.currentTimeMillis()
    val dayMs = 24L * 60 * 60 * 1000
    if (now - last < dayMs) return
    prefs.edit { putLong("cover_cache_trimmed_at", now) }
    thread {
        try {
            trimCoverDiskCache(maxBytes = 50L * 1024 * 1024)
        } catch (e: Exception) {
            Log.w("Cover", "trim failed: ${e.message}")
        }
    }
}

internal fun MainActivity.trimCoverDiskCache(maxBytes: Long = 50L * 1024 * 1024) {
    val files = coverDiskDir.listFiles() ?: return
    files.forEach { f ->
        val id = f.name.removeSuffix(".jpg")
        if (!isTrackStillInUse(id)) {
            f.delete()
        }
    }
    val remaining = coverDiskDir.listFiles() ?: return
    var total = remaining.sumOf { it.length() }
    if (total <= maxBytes) return

    remaining
        .sortedBy { it.lastModified() }
        .forEach { f ->
            if (total <= maxBytes) return
            total -= f.length()
            f.delete()
        }
}

internal fun MainActivity.applyCoverGradientToMiniPlayer(track: Track?, bitmap: Bitmap?) {
    val bg = miniPlayer.background as? GradientDrawable ?: return
    fun applyColors(colors: IntArray) {
        bg.colors = colors
        bg.gradientType = GradientDrawable.LINEAR_GRADIENT
        bg.orientation = GradientDrawable.Orientation.TOP_BOTTOM
        applyMiniPlayerCorners(bg)
    }
    if (track == null || bitmap == null) {
        applyColors(intArrayOf("#1F1F1F".toColorInt(), BG))
        return
    }
    coverGradientCache[track.id]?.let { applyColors(it); return }

    val small = bitmap.scale(32, 32, false)
    val palette = androidx.palette.graphics.Palette.from(small).generate()
    val dominant = palette.swatches
        .filter { sw -> colorLuminance(sw.rgb) > 0.35f && colorSaturation(sw.rgb) > 0.15f }
        .maxByOrNull { sw ->
            val sat = colorSaturation(sw.rgb)
            val lum = colorLuminance(sw.rgb)
            sw.population.toFloat() * (0.5f + sat) * (0.5f + lum)
        }
        ?.rgb
        ?: palette.getVibrantColor(0).takeIf { it != 0 && colorLuminance(it) > 0.35f }
        ?: palette.getLightVibrantColor(0).takeIf { it != 0 }
        ?: palette.getDominantColor(0).takeIf { it != 0 }
        ?: BG

    val colors = intArrayOf(blendWithBg(dominant, BG, ratio = 0.30f), BG)
    coverGradientCache[track.id] = colors
    if (coverGradientCache.size > 50) {
        coverGradientCache.keys.firstOrNull()?.let { coverGradientCache.remove(it) }
    }
    applyColors(colors)
}

internal fun MainActivity.loadRawCoverIntoView(track: Track, target: ImageView?) {
    if (target == null) return
    val id = track.id
    target.tag = "raw:$id"
    notificationIconCache.get(id)?.let {
        target.setImageBitmap(it)
        return
    }
    loadNotificationIcon(track) { bmp ->
        if (bmp != null && target.tag == "raw:$id") {
            target.setImageBitmap(bmp)
        }
    }
}