package com.tauchaut.singly.qr

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.data.rememberTrack
import com.tauchaut.singly.models.AccountProfile
import com.tauchaut.singly.models.Playlist
import com.tauchaut.singly.models.PublishedPlaylist
import com.tauchaut.singly.models.PublishedProfile
import com.tauchaut.singly.models.PublishedTrack
import com.tauchaut.singly.models.Track
import com.tauchaut.singly.ui.loadAvatarBitmap
import com.tauchaut.singly.ui.showScannedProfile
import com.tauchaut.singly.util.dp
import com.tauchaut.singly.util.roundedDrawable
import com.tauchaut.singly.youtube.bestThumbnailFor
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.content.edit

internal fun MainActivity.encodeProfileToQr(p: PublishedProfile): String? {
    val b64 = compressToB64(publishedProfileToJson(p))
    return if (b64.length <= 1200) "$QR_PREFIX|$b64" else null
}

internal fun publishedProfileToJson(p: PublishedProfile): String {
    val o = org.json.JSONObject()
    o.put("v", 2)
    o.put("n", p.name)
    o.put("e", p.emoji)
    o.put("c", p.color)
    p.avatarPhotoB64?.let { o.put("b", it) }
    val plArr = org.json.JSONArray()
    p.playlists.forEach { pl ->
        val entry = org.json.JSONArray()
        entry.put(pl.name)
        val tracksArr = org.json.JSONArray()
        pl.tracks.forEach { t ->
            tracksArr.put(org.json.JSONArray().apply {
                put(t.id)
                put(t.title.take(50))
                put(t.artist.take(30))
                put(t.duration)
            })
        }
        entry.put(tracksArr)
        plArr.put(entry)
    }
    o.put("pl", plArr)
    return o.toString()
}

internal fun publishedProfileFromJson(s: String): PublishedProfile? = try {
    val o = org.json.JSONObject(s)
    val version = o.optInt("v", 1)
    if (version < 2) {
        null
    } else {
        val playlists = mutableListOf<PublishedPlaylist>()
        val plArr = o.getJSONArray("pl")
        for (i in 0 until plArr.length()) {
            val entry = plArr.getJSONArray(i)
            val name = entry.getString(0)
            val ta = entry.getJSONArray(1)
            val tracks = mutableListOf<PublishedTrack>()
            for (j in 0 until ta.length()) {
                val to = ta.getJSONArray(j)
                tracks.add(PublishedTrack(
                    id = to.getString(0),
                    title = to.optString(1, ""),
                    artist = to.optString(2, ""),
                    duration = to.optInt(3, 0)
                ))
            }
            playlists.add(PublishedPlaylist(name, tracks))
        }
        val photoB64 = o.optString("b", "").ifEmpty { null }
        var avatarBmp: Bitmap? = null
        if (photoB64 != null) {
            try {
                val bytes = android.util.Base64.decode(
                    photoB64,
                    android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE
                )
                avatarBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {}
        }
        PublishedProfile(
            name = o.getString("n"),
            emoji = o.optString("e", "🎵"),
            color = o.optInt("c", 0xFF3B5BDB.toInt()),
            playlists = playlists,
            avatarPhotoB64 = photoB64,
            avatarBitmap = avatarBmp
        )
    }
} catch (_: Exception) { null }

internal fun compressToB64(s: String): String {
    val bos = java.io.ByteArrayOutputStream()
    java.util.zip.GZIPOutputStream(bos).use { it.write(s.toByteArray(Charsets.UTF_8)) }
    return android.util.Base64.encodeToString(
        bos.toByteArray(),
        android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE
    )
}

internal fun decompressFromB64(b64: String): String? = try {
    val bytes = android.util.Base64.decode(
        b64, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE
    )
    java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(bytes))
        .use { it.readBytes().toString(Charsets.UTF_8) }
} catch (_: Exception) { null }

internal fun generateQrBitmap(content: String, sizePx: Int = 900): Bitmap? = try {
    val hints = mapOf(
        com.google.zxing.EncodeHintType.ERROR_CORRECTION
                to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L,
        com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8",
        com.google.zxing.EncodeHintType.MARGIN to 1
    )
    val matrix = com.google.zxing.qrcode.QRCodeWriter()
        .encode(content, com.google.zxing.BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val bmp = createBitmap(sizePx, sizePx)
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) for (x in 0 until sizePx) {
        pixels[y * sizePx + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
    }
    bmp.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    bmp
} catch (_: Exception) { null }

internal fun MainActivity.handleScannedQr(content: String) {
    if (!content.startsWith("$QR_PREFIX|")) {
        Toast.makeText(this, "This is not a Singly board QR-code", Toast.LENGTH_SHORT).show()
        return
    }
    val b64 = content.removePrefix("$QR_PREFIX|")
    val json = decompressFromB64(b64)
    val profile = json?.let { publishedProfileFromJson(it) }
    if (profile == null) {
        Toast.makeText(this, "Failed to read the data", Toast.LENGTH_SHORT).show()
        return
    }
    showScannedProfile(profile)
}

internal fun MainActivity.startQrScan() {
    com.google.zxing.integration.android.IntentIntegrator(this)
        .setDesiredBarcodeFormats(
            com.google.zxing.integration.android.IntentIntegrator.QR_CODE
        )
        .setPrompt("Point at board QR-code")
        .setBeepEnabled(false)
        .setOrientationLocked(false)
        .initiateScan()
}

@SuppressLint("SetTextI18n")
internal fun MainActivity.showQrSingle(content: String) {
    val overlay = FrameLayout(this).apply { setBackgroundColor(BG) }
    val qrView = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    val hint = TextView(this).apply {
        text = "Show this QR-code to scan"
        textSize = 14f; setTextColor(TEXT); gravity = Gravity.CENTER
        setPadding(0, dp(48), 0, dp(16))
    }
    val closeBtn = TextView(this).apply {
        text = "Close"; textSize = 15f; setTextColor(TEXT)
        setPadding(dp(20), dp(12), dp(20), dp(12))
        background = roundedDrawable(CARD, 12)
        gravity = Gravity.CENTER
        setOnClickListener { root.removeView(overlay) }
    }
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), 0, dp(24), dp(24))
    }
    body.addView(hint, LinearLayout.LayoutParams(-1, -2))
    body.addView(qrView, LinearLayout.LayoutParams(-1, 0, 1f).apply {
        bottomMargin = dp(16)
    })
    body.addView(closeBtn, LinearLayout.LayoutParams(-1, dp(48)))
    overlay.addView(body, FrameLayout.LayoutParams(-1, -1))
    root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
    overlay.elevation = dp(100).toFloat()
    generateQrBitmap(content)?.let { qrView.setImageBitmap(it) }
}

internal fun MainActivity.showShareBoardQr() {
    val acc = currentAccount ?: return
    val published = loadPublishedIds()
    val chosen = playlists
        .filter { it.id in published }
        .map { pl -> PublishedPlaylist(
            name = pl.name,
            tracks = pl.tracks.map { PublishedTrack(it.id, it.title, it.artist, it.duration) }
        ) }
    if (chosen.isEmpty()) {
        Toast.makeText(this, "Select at least one playlist", Toast.LENGTH_SHORT).show()
        return
    }
    val profile = PublishedProfile(
        name = acc.name,
        emoji = acc.avatarEmoji,
        color = acc.avatarColor,
        playlists = chosen,
        avatarPhotoB64 = encodeAvatarThumbnail(acc)
    )
    val content = encodeProfileToQr(profile)
    if (content != null) {
        showQrSingle(content)
        return
    }
    val totalTracks = chosen.sumOf { it.tracks.size }
    AlertDialog.Builder(this)
        .setTitle("Too much for QR-code")
        .setMessage(
            "Tracks: $totalTracks. ~20 fits into 1 QR-code.\n\n" +
                    "Select, what to do:"
        )
        .setNeutralButton("Export as file") { _, _ -> exportBoardToFile(profile) }
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Cut to 20") { _, _ ->
            val trimmed = trimProfileToTracks(profile, 20)
            val tContent = encodeProfileToQr(trimmed)
            if (tContent != null) showQrSingle(tContent)
        }
        .show()
}

internal fun trimProfileToTracks(p: PublishedProfile, maxTracks: Int): PublishedProfile {
    var left = maxTracks
    val trimmedPlaylists = mutableListOf<PublishedPlaylist>()
    for (pl in p.playlists) {
        if (left <= 0) break
        val take = minOf(left, pl.tracks.size)
        trimmedPlaylists.add(PublishedPlaylist(pl.name, pl.tracks.take(take)))
        left -= take
    }
    return p.copy(playlists = trimmedPlaylists)
}

internal fun MainActivity.exportBoardToFile(profile: PublishedProfile) {
    try {
        val json = publishedProfileToJson(profile)
        val b64 = compressToB64(json)
        val payload = "SINGLY|$b64"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TITLE,
                "board_${profile.name.replace(" ", "_")}.singly"
            )
        }
        pendingBoardExport = payload
        @Suppress("DEPRECATION")
        startActivityForResult(intent, BOARD_EXPORT_REQUEST)
    } catch (e: Exception) {
        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

internal fun MainActivity.loadPublishedIds(): Set<String> =
    (prefs.getString("published_playlist_ids", "") ?: "")
        .split("|").filter { it.isNotEmpty() }.toSet()

internal fun MainActivity.savePublishedIds(ids: Set<String>) {
    prefs.edit { putString("published_playlist_ids", ids.joinToString("|")) }
}

internal fun MainActivity.encodeAvatarThumbnail(account: AccountProfile): String? {
    val bmp = loadAvatarBitmap(account, 256) ?: return null
    val scaled = if (bmp.width > 96) bmp.scale(96, 96) else bmp
    val bos = java.io.ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 55, bos)
    if (scaled !== bmp) scaled.recycle()
    bmp.recycle()
    return android.util.Base64.encodeToString(
        bos.toByteArray(),
        android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE
    )
}

internal fun MainActivity.importPublishedPlaylist(pl: PublishedPlaylist, authorName: String) {
    val baseName = if (authorName.isNotBlank())
        "${pl.name}@$authorName"
    else
        pl.name
    val uniqueName = makeUniquePlaylistName(baseName)
    val target = Playlist("custom_${System.nanoTime()}", uniqueName)
    playlists.add(target)
    pl.tracks.forEach { pub ->
        val track = tracks.firstOrNull { it.id == pub.id }
            ?: Track(
                id = pub.id,
                title = pub.title,
                artist = pub.artist,
                imageUrl = bestThumbnailFor(pub.id),
                duration = pub.duration
            ).also { rememberTrack(it) }
        if (target.tracks.none { it.id == track.id }) target.tracks.add(track)
    }
}
