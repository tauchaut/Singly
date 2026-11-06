package com.tauchaut.singly.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Outline
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.R
import com.tauchaut.singly.accounts.deleteAccountById
import com.tauchaut.singly.accounts.loadAccounts
import com.tauchaut.singly.accounts.saveAccounts
import com.tauchaut.singly.accounts.switchToAccount
import com.tauchaut.singly.cover.loadImageIntoView
import com.tauchaut.singly.data.saveState
import com.tauchaut.singly.models.AccountProfile
import com.tauchaut.singly.models.ContextSheetOption
import com.tauchaut.singly.models.Playlist
import com.tauchaut.singly.models.PublishedPlaylist
import com.tauchaut.singly.models.PublishedProfile
import com.tauchaut.singly.models.Track
import com.tauchaut.singly.player.playTrack
import com.tauchaut.singly.qr.importPublishedPlaylist
import com.tauchaut.singly.qr.loadPublishedIds
import com.tauchaut.singly.qr.savePublishedIds
import com.tauchaut.singly.qr.showShareBoardQr
import com.tauchaut.singly.qr.startQrScan
import com.tauchaut.singly.util.dp
import com.tauchaut.singly.util.formatDurationLong
import com.tauchaut.singly.util.formatTime
import com.tauchaut.singly.util.playlistWord
import com.tauchaut.singly.util.roundedDrawable
import com.tauchaut.singly.util.tracksWord
import com.tauchaut.singly.youtube.bestThumbnailFor
import java.io.File
import java.io.FileOutputStream
import androidx.core.graphics.toColorInt
import androidx.core.graphics.scale

@SuppressLint("SetTextI18n")
internal fun MainActivity.showProfile() {
    currentScannedProfile = null
    inScannedPlaylistPreview = false
    inSubScreen = false
    currentPlaylistView = null
    content.removeAllViews()
    contentPaddingDense()
    hideStickyHeader()

    activeTrackListContainer = null
    activeTrackListSource = null
    activeTrackListPlaylist = null
    activeTrackListName = null

    val acc = currentAccount ?: return

    val avatarWrap = FrameLayout(this)
    avatarWrap.addView(
        buildAvatarView(acc, dp(110)),
        FrameLayout.LayoutParams(dp(110), dp(110), Gravity.CENTER)
    )
    content.addView(avatarWrap, LinearLayout.LayoutParams(-1, dp(140)).apply {
        topMargin = dp(20)
    })

    content.addView(TextView(this).apply {
        text = acc.name
        textSize = 26f
        setTextColor(TEXT)
        setTypeface(null, android.graphics.Typeface.BOLD)
        gravity = Gravity.CENTER
    }, LinearLayout.LayoutParams(-1, -2))

    val customPlaylistsCount = playlists.count {
        it.id !in setOf("recent", "liked", "listen_later", "downloads")
    }
    content.addView(TextView(this).apply {
        text = "${likedTracks.size} ♥ · $customPlaylistsCount playlists · ${downloadedTracks.size} ⤓"
        textSize = 13f
        setTextColor(SECONDARY)
        gravity = Gravity.CENTER
        setPadding(0, dp(6), 0, 0)
    }, LinearLayout.LayoutParams(-1, -2))

    addSpace(24)

    button("Playlists board") { showPlaylistBoard() }
    button("Edit profile") { showEditProfile() }
    button("Settings") { showSettings() }

    addSpace(20)
    sectionLabel("ACCOUNTS")

    val accounts = loadAccounts()
    accounts.forEach { a ->
        content.addView(buildAccountRow(a, a.id == acc.id))
    }
    content.addView(buildAddAccountRow())

    addSpace(20)
    sectionLabel("DATA")

    button("Data export") { exportData() }
    button("Data import") { importData() }

    if (accounts.size > 1) {
        content.addView(TextView(this).apply {
            text = "Delete this account"
            textSize = 15f
            setTextColor("#FF6B6B".toColorInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
            background = roundedDrawable(CARD, 12)
            setOnClickListener {
                android.app.AlertDialog.Builder(this@showProfile)
                    .setTitle("Delete «${acc.name}»?")
                    .setMessage("All the account data (playlists, likes, history) will be deleted.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ -> deleteAccountById(acc.id) }
                    .show()
            }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(4)
        })
    }
}

@SuppressLint("SetTextI18n")
internal fun MainActivity.buildAccountRow(account: AccountProfile, isActive: Boolean): View {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = roundedDrawable(if (isActive) CARD_ACTIVE else CARD, 12)
        isClickable = true
        isFocusable = true
        setOnClickListener { if (!isActive) switchToAccount(account.id) }
    }

    row.addView(
        buildAvatarView(account, dp(42)),
        LinearLayout.LayoutParams(dp(42), dp(42))
    )
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), 0, 0, 0)
    }
    col.addView(TextView(this).apply {
        text = account.name
        textSize = 16f
        setTextColor(TEXT)
        setTypeface(null, android.graphics.Typeface.BOLD)
    })
    if (isActive) {
        col.addView(TextView(this).apply {
            text = "active"
            textSize = 11f
            setTextColor(SECONDARY)
        })
    }
    row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))

    if (isActive) {
        row.addView(TextView(this).apply {
            text = "✓"
            textSize = 18f
            setTextColor(TEXT)
        }, LinearLayout.LayoutParams(dp(24), -2))
    }

    row.setOnLongClickListener {
        if (!isActive) {
            android.app.AlertDialog.Builder(this@buildAccountRow)
                .setTitle("Delete «${account.name}»?")
                .setMessage("All the account data will be deleted.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ -> deleteAccountById(account.id) }
                .show()
        }
        true
    }

    return row.also {
        it.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
    }
}

@SuppressLint("SetTextI18n")
internal fun MainActivity.showEditProfile() {
    inSubScreen = true
    content.removeAllViews()
    hideStickyHeader()
    contentPaddingDense()

    val acc = currentAccount ?: return

    if (editProfileName == null) editProfileName = acc.name
    if (editProfileEmoji == null) editProfileEmoji = acc.avatarEmoji
    if (editProfileColor == null) editProfileColor = acc.avatarColor

    val currentName = editProfileName!!
    val currentEmoji = editProfileEmoji!!
    val currentColor = editProfileColor!!
    val hasPhoto = acc.avatarPhotoPath != null

    addBackButton {
        editProfileName = null
        editProfileEmoji = null
        editProfileColor = null
        showProfile()
    }
    addTitle("Edit profile")

    val previewAcc = acc.copy(
        avatarEmoji = currentEmoji,
        avatarColor = currentColor
    )
    val previewWrap = FrameLayout(this)
    previewWrap.addView(
        buildAvatarView(previewAcc, dp(110)),
        FrameLayout.LayoutParams(dp(110), dp(110), Gravity.CENTER)
    )
    content.addView(previewWrap, LinearLayout.LayoutParams(-1, dp(130)).apply {
        topMargin = dp(8)
    })

    val photoRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    photoRow.addView(TextView(this).apply {
        text = if (hasPhoto) "Replace photo" else "Load photo"
        textSize = 14f
        setTextColor(TEXT)
        setTypeface(null, android.graphics.Typeface.BOLD)
        background = roundedDrawable(CARD, 12)
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        setOnClickListener { pickAvatarPhoto() }
    }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
        if (hasPhoto) marginEnd = dp(8)
    })

    if (hasPhoto) {
        photoRow.addView(TextView(this).apply {
            text = "Remove photo"
            textSize = 14f
            setTextColor("#FF6B6B".toColorInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = roundedDrawable(CARD, 12)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { deleteAvatarPhoto() }
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
    }

    content.addView(photoRow, LinearLayout.LayoutParams(-1, dp(44)).apply {
        topMargin = dp(12)
    })

    content.addView(TextView(this).apply {
        text = "Name"
        textSize = 13f
        setTextColor(SECONDARY)
        setPadding(dp(4), dp(20), dp(4), dp(8))
    })

    val nameInput = android.widget.EditText(this).apply {
        setText(currentName)
        setSelection(currentName.length)
        setTextColor(TEXT)
        setHintTextColor(SECONDARY)
        hint = "Profile name"
        textSize = 15f
        isSingleLine = true
        setPadding(dp(16), 0, dp(16), 0)
        background = roundedDrawable(CARD, 12)
        addTextChangedListener(SimpleTextWatcher { value ->
            editProfileName = value
        })
    }
    content.addView(nameInput, LinearLayout.LayoutParams(-1, dp(55)))

    if (!hasPhoto) {
        content.addView(TextView(this).apply {
            text = "Avatar (emoji)"
            textSize = 13f
            setTextColor(SECONDARY)
            setPadding(dp(4), dp(20), dp(4), dp(8))
        })

        val emojiRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        avatarEmojis.forEach { emoji ->
            val cell = FrameLayout(this).apply {
                background = roundedDrawable(
                    if (emoji == currentEmoji) CARD_ACTIVE else CARD, 12
                )
                isClickable = true
                isFocusable = true
                tag = emoji
                addView(TextView(this@showEditProfile).apply {
                    text = emoji
                    textSize = 22f
                    gravity = Gravity.CENTER
                }, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))

                setOnClickListener {
                    editProfileEmoji = emoji
                    val updatedPreview = acc.copy(
                        avatarEmoji = emoji,
                        avatarColor = editProfileColor ?: acc.avatarColor
                    )
                    (previewWrap.getChildAt(0)?.parent as? FrameLayout)?.let {
                        previewWrap.removeAllViews()
                        previewWrap.addView(
                            buildAvatarView(updatedPreview, dp(110)),
                            FrameLayout.LayoutParams(dp(110), dp(110), Gravity.CENTER)
                        )
                    }
                    for (i in 0 until emojiRow.childCount) {
                        val c = emojiRow.getChildAt(i) as FrameLayout
                        c.background = roundedDrawable(
                            if (c.tag == emoji) CARD_ACTIVE else CARD, 12
                        )
                    }
                }
            }
            emojiRow.addView(cell, LinearLayout.LayoutParams(dp(50), dp(50)).apply {
                marginEnd = dp(6)
            })
        }

        val emojiScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(emojiRow)
        }
        content.addView(emojiScroll, LinearLayout.LayoutParams(-1, dp(50)))

        content.addView(TextView(this).apply {
            text = "Background color (if no photo provided)"
            textSize = 13f
            setTextColor(SECONDARY)
            setPadding(dp(4), dp(20), dp(4), dp(8))
        })

        val colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        avatarColors.forEach { color ->
            val cell = FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
                isClickable = true
                isFocusable = true
                tag = color

                val check = TextView(this@showEditProfile).apply {
                    text = if (color == currentColor) "✓" else ""
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    tag = "check"
                }
                addView(check, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))

                setOnClickListener {
                    editProfileColor = color
                    val updatedPreview = acc.copy(
                        avatarEmoji = editProfileEmoji ?: acc.avatarEmoji,
                        avatarColor = color
                    )
                    previewWrap.removeAllViews()
                    previewWrap.addView(
                        buildAvatarView(updatedPreview, dp(110)),
                        FrameLayout.LayoutParams(dp(110), dp(110), Gravity.CENTER)
                    )
                    for (i in 0 until colorRow.childCount) {
                        val c = colorRow.getChildAt(i) as FrameLayout
                        c.findViewWithTag<TextView>("check")?.text =
                            if (c.tag == color) "✓" else ""
                    }
                }
            }
            colorRow.addView(cell, LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                marginEnd = dp(8)
            })
        }
        content.addView(colorRow, LinearLayout.LayoutParams(-1, dp(34)))
    } else {
        content.addView(TextView(this).apply {
            text = "If you remove the photo, emoji-avatar appears."
            textSize = 12f
            setTextColor(SECONDARY)
            setPadding(dp(4), dp(16), dp(4), 0)
        })
    }

    addSpace(24)

    button("Save") {
        val freshAcc = currentAccount ?: return@button
        val newName = (editProfileName ?: freshAcc.name).trim().ifBlank { freshAcc.name }
        val newEmoji = editProfileEmoji ?: freshAcc.avatarEmoji
        val newColor = editProfileColor ?: freshAcc.avatarColor

        val updated = freshAcc.copy(
            name = newName,
            avatarEmoji = newEmoji,
            avatarColor = newColor
        )

        val list = loadAccounts().toMutableList()
        val idx = list.indexOfFirst { it.id == freshAcc.id }
        if (idx >= 0) {
            list[idx] = updated
            saveAccounts(list)
            currentAccount = updated
        }

        editProfileName = null
        editProfileEmoji = null
        editProfileColor = null

        showProfile()
        Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show()
    }
}

@SuppressLint("SetTextI18n")
internal fun MainActivity.buildAddAccountRow(): View {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(14), dp(12), dp(14))
        background = roundedDrawable(CARD, 12)
        isClickable = true
        isFocusable = true
        setOnClickListener { showCreateAccountDialog() }
    }
    row.addView(TextView(this).apply {
        text = "+"
        textSize = 22f
        setTextColor(TEXT)
        gravity = Gravity.CENTER
    }, LinearLayout.LayoutParams(dp(42), dp(42)))
    row.addView(TextView(this).apply {
        text = "Create an account"
        textSize = 16f
        setTextColor(TEXT)
        setPadding(dp(12), 0, 0, 0)
    }, LinearLayout.LayoutParams(0, -2, 1f))
    return row
}

internal fun MainActivity.showCreateAccountDialog() {
    val nameInput = android.widget.EditText(this).apply {
        hint = "Account name"
        setTextColor(TEXT)
        setHintTextColor(SECONDARY)
        setPadding(dp(16), 0, dp(16), 0)
        background = roundedDrawable(CARD, 12)
    }

    android.app.AlertDialog.Builder(this)
        .setTitle("New account")
        .setView(nameInput)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Create") { _, _ ->
            val name = nameInput.text.toString().trim().ifBlank { "Account" }
            val newId = java.util.UUID.randomUUID().toString()
            val newAcc = AccountProfile(
                id = newId,
                name = name,
                avatarEmoji = avatarEmojis.random(),
                avatarColor = avatarColors.random(),
                createdAt = System.currentTimeMillis()
            )
            val list = loadAccounts().toMutableList()
            list.add(newAcc)
            saveAccounts(list)
            switchToAccount(newId)
        }
        .show()
}

internal fun MainActivity.buildAvatarView(account: AccountProfile, sizePx: Int): View {
    val wrap = FrameLayout(this).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(account.avatarColor)
        }
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
    }
    val bmp = loadAvatarBitmap(account, sizePx)
    if (bmp != null) {
        wrap.addView(ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bmp)
        }, FrameLayout.LayoutParams(-1, -1))
    } else {
        wrap.addView(TextView(this).apply {
            text = account.avatarEmoji
            textSize = (sizePx * 0.45f) / resources.displayMetrics.scaledDensity
            gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(-1, -1))
    }
    return wrap
}

internal fun MainActivity.buildPublishedAvatarView(profile: PublishedProfile, sizePx: Int): View {
    val wrap = FrameLayout(this).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(profile.color)
        }
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
    }
    val bmp = profile.avatarBitmap
    if (bmp != null) {
        wrap.addView(ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bmp)
        }, FrameLayout.LayoutParams(-1, -1))
    } else {
        wrap.addView(TextView(this).apply {
            text = profile.emoji
            textSize = (sizePx * 0.45f) / resources.displayMetrics.scaledDensity
            gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(-1, -1))
    }
    return wrap
}

internal fun MainActivity.pickAvatarPhoto() {
    showContextSheet(
        title = "Photo source",
        options = listOf(
            ContextSheetOption("Files / Gallery / Google Photos") {
                pickAvatarFromGallery()
            },
            ContextSheetOption("Camera") {
                pickAvatarFromCamera()
            }
        )
    )
}

internal fun MainActivity.applyAvatarFromUri(uri: Uri) {
    val acc = currentAccount ?: return
    if (saveAvatarPhoto(acc.id, uri)) {
        pendingCameraUri?.let { camUri ->
            try {
                val path = camUri.path?.substringAfter("/files/")
                if (path != null) File(filesDir, path).takeIf { it.exists() }?.delete()
            } catch (_: Exception) {}
        }
        pendingCameraUri = null

        val updated = acc.copy(avatarPhotoPath = "avatars/${acc.id}.jpg")
        val list = loadAccounts().toMutableList()
        val idx = list.indexOfFirst { it.id == acc.id }
        if (idx >= 0) {
            list[idx] = updated
            saveAccounts(list)
            currentAccount = updated
        }
        showEditProfile()
        Toast.makeText(this, "Updated the photo", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(this, "Failed to load the photo", Toast.LENGTH_SHORT).show()
    }
}

internal fun MainActivity.pickAvatarFromGallery() {
    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
        type = "image/*"
        addCategory(Intent.CATEGORY_OPENABLE)
    }
    try {
        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent.createChooser(intent, "Select a photo"),
            AVATAR_PICK_REQUEST
        )
    } catch (_: Exception) {
        Toast.makeText(this, "No application to select a photo", Toast.LENGTH_SHORT).show()
    }
}

internal fun MainActivity.pickAvatarFromCamera() {
    if (checkSelfPermission(android.Manifest.permission.CAMERA) !=
    android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        requestPermissions(
            arrayOf(android.Manifest.permission.CAMERA),
            AVATAR_CAMERA_PERMISSION_REQUEST
        )
        return
    }
    launchCamera()
}

@SuppressLint("QueryPermissionsNeeded")
internal fun MainActivity.launchCamera() {
    val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
    if (intent.resolveActivity(packageManager) == null) {
        Toast.makeText(this, "Camera is unavailable", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val tmpFile = File(filesDir, "avatars/tmp_camera_${System.currentTimeMillis()}.jpg")
        tmpFile.parentFile?.mkdirs()
        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            tmpFile
        )
        pendingCameraUri = uri
        intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, AVATAR_CAMERA_REQUEST)
    } catch (e: Exception) {
        Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
        pendingCameraUri = null
    }
}

internal fun MainActivity.deleteAvatarPhoto() {
    val acc = currentAccount ?: return
    avatarFile(acc.id).takeIf { it.exists() }?.delete()

    val updated = acc.copy(avatarPhotoPath = null)
    val list = loadAccounts().toMutableList()
    val idx = list.indexOfFirst { it.id == acc.id }
    if (idx >= 0) {
        list[idx] = updated
        saveAccounts(list)
        currentAccount = updated
    }
    showEditProfile()
    Toast.makeText(this, "Removed the photo", Toast.LENGTH_SHORT).show()
}

internal fun MainActivity.avatarFile(accountId: String): File =
    File(filesDir, "avatars/$accountId.jpg").also { it.parentFile?.mkdirs() }

internal fun MainActivity.loadAvatarBitmap(account: AccountProfile, targetSizePx: Int = 512): Bitmap? {
    val path = account.avatarPhotoPath ?: return null
    val f = File(filesDir, path)
    if (!f.exists()) return null
    return try {
        val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, probe)
        var sample = 1
        while (probe.outWidth / sample > targetSizePx * 2 ||
            probe.outHeight / sample > targetSizePx * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(f.absolutePath, opts)
    } catch (_: Exception) { null }
}

internal fun MainActivity.saveAvatarPhoto(accountId: String, uri: Uri): Boolean {
    return try {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
        val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, probe)
        var sample = 1
        while (probe.outWidth / sample > 1024 || probe.outHeight / sample > 1024) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return false

        if (bmp.width != bmp.height) {
            val side = minOf(bmp.width, bmp.height)
            val x = (bmp.width - side) / 2
            val y = (bmp.height - side) / 2
            val cropped = Bitmap.createBitmap(bmp, x, y, side, side)
            if (cropped !== bmp) { bmp.recycle(); bmp = cropped }
        }
        if (bmp.width > 512) {
            val scaled = bmp.scale(512, 512)
            if (scaled !== bmp) { bmp.recycle(); bmp = scaled }
        }
        FileOutputStream(avatarFile(accountId)).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        bmp.recycle()
        true
    } catch (e: Exception) {
        Log.e("Avatar", "save failed", e)
        false
    }
}

@SuppressLint("SetTextI18n")
internal fun MainActivity.showPlaylistBoard() {
    inSubScreen = true
    content.removeAllViews()
    hideStickyHeader()
    addBackButton { showProfile() }
    addTitle("Playlists board")

    content.addView(TextView(this).apply {
        text = "Mark the playlists you want to show others. " +
                "When someone scans your QR-code, he sees your shared board."
        textSize = 13f
        setTextColor(SECONDARY)
        setPadding(0, 0, 0, dp(16))
    })

    val actionRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    actionRow.addView(buildBoardActionButton(
        icon = R.drawable.share_24dp_e3e3e3_fill0_wght300_grad0_opsz24,
        label = "Share",
        onClick = { showShareBoardQr() }
    ), LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
    actionRow.addView(buildBoardActionButton(
        icon = R.drawable.file_open_24dp_e3e3e3_fill0_wght300_grad0_opsz24,
        label = "File",
        onClick = {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, BOARD_IMPORT_REQUEST)
        }
    ), LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(8) })
    actionRow.addView(buildBoardActionButton(
        icon = R.drawable.qr_code_scanner_24dp_e3e3e3_fill0_wght300_grad0_opsz24,
        label = "Scan",
        onClick = { startQrScan() }
    ), LinearLayout.LayoutParams(0, dp(48), 1f))
    content.addView(actionRow, LinearLayout.LayoutParams(-1, dp(48)))

    addSpace(24)
    sectionLabel("ALL PLAYLISTS")

    if (playlists.isEmpty()) {
        content.addView(TextView(this).apply {
            text = "No playlists yet"
            textSize = 13f
            setTextColor(SECONDARY)
            setPadding(0, dp(16), 0, dp(16))
        })
        return
    }

    val published = loadPublishedIds().toMutableSet()

    val systemOrder = listOf("recent", "liked", "listen_later", "downloads")
    val ordered = mutableListOf<Playlist>()
    systemOrder.forEach { id -> playlists.firstOrNull { it.id == id }?.let { ordered.add(it) } }
    playlists.filter { it.id !in systemOrder }.forEach { ordered.add(it) }

    ordered.forEach { pl ->
        content.addView(buildBoardPlaylistRow(pl, published))
    }

    addSpace(12)
    content.addView(TextView(this).apply {
        text = "Published: ${published.count { id -> playlists.any { it.id == id } }} из ${playlists.size}"
        textSize = 12f
        setTextColor(SECONDARY)
        setPadding(dp(4), 0, 0, 0)
    })
}

internal fun MainActivity.buildBoardPlaylistRow(pl: Playlist, publishedSet: MutableSet<String>): View {
    val isPublished = pl.id in publishedSet
    val isSystem = pl.id in setOf("recent", "liked", "listen_later", "downloads")
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = roundedDrawable(if (isPublished) CARD_ACTIVE else CARD, 12)
    }

    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }
    col.addView(TextView(this).apply {
        text = pl.name
        textSize = 15f
        setTextColor(TEXT)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    })
    col.addView(TextView(this).apply {
        text = if (isSystem) {
            "system – ${pl.tracks.size} ${tracksWord(pl.tracks.size)}"
        } else {
            "${pl.tracks.size} ${tracksWord(pl.tracks.size)}"
        }
        textSize = 11f
        setTextColor(SECONDARY)
        setPadding(0, dp(2), 0, 0)
    })
    row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))

    row.addView(Switch(this).apply {
        isChecked = isPublished
        setOnCheckedChangeListener { _, checked ->
            if (checked) publishedSet.add(pl.id) else publishedSet.remove(pl.id)
            savePublishedIds(publishedSet)
            row.background = roundedDrawable(if (checked) CARD_ACTIVE else CARD, 12)
        }
    })

    return row.also {
        it.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
    }
}

internal fun MainActivity.buildBoardActionButton(icon: Int, label: String, onClick: () -> Unit): View =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        background = roundedDrawable(CARD, 12)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }

        addView(ImageView(this@buildBoardActionButton).apply {
            setImageResource(icon)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(TEXT)
            val p = dp(8); setPadding(p, p, p, p)
        }, LinearLayout.LayoutParams(dp(32), dp(32)))
        addView(TextView(this@buildBoardActionButton).apply {
            text = label
            textSize = 14f
            setTextColor(TEXT)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(4), 0, 0, 0)
        })
    }

@SuppressLint("SetTextI18n")
internal fun MainActivity.showScannedProfile(profile: PublishedProfile) {
    currentScannedProfile = profile
    inScannedPlaylistPreview = false
    inSubScreen = true
    currentPlaylistView = null
    content.removeAllViews()
    contentPaddingDense()

    activeTrackListContainer = null
    activeTrackListSource = null
    activeTrackListPlaylist = null
    activeTrackListName = null

    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
        setBackgroundColor(BG)
    }

    val backBtn = ImageView(this).apply {
        setImageResource(R.drawable.arrow_back_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setColorFilter(TEXT)
        val p = dp(10); setPadding(p, p, p, p)
        setOnClickListener { showProfile() }
    }
    header.addView(backBtn, LinearLayout.LayoutParams(dp(44), dp(44)))

    header.addView(TextView(this).apply {
        text = "Board: ${profile.name}"
        textSize = 19f
        setTextColor(TEXT)
        setTypeface(null, android.graphics.Typeface.BOLD)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }, LinearLayout.LayoutParams(0, -2, 1f))

    val saveAllBtn = ImageView(this).apply {
        setImageResource(R.drawable.save_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setColorFilter(TEXT)
        val p = dp(10); setPadding(p, p, p, p)
        setOnClickListener {
            profile.playlists.forEach { importPublishedPlaylist(it, profile.name) }
            saveState()
            if (currentTab == TAB_PLAYLISTS) showPlaylistsTab()
            Toast.makeText(
                this@showScannedProfile,
                "Playlists saved: ${profile.playlists.size}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    header.addView(saveAllBtn, LinearLayout.LayoutParams(dp(44), dp(44)))

    showStickyHeader(header)

    val avatarRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(8), dp(16), dp(8))
    }
    avatarRow.addView(
        buildPublishedAvatarView(profile, dp(48)),
        LinearLayout.LayoutParams(dp(48), dp(48))
    )

    val infoCol = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), 0, 0, 0)
    }
    infoCol.addView(TextView(this).apply {
        text = profile.name
        textSize = 15f
        setTextColor(TEXT)
        setTypeface(null, android.graphics.Typeface.BOLD)
    })
    val totalTracks = profile.playlists.sumOf { it.tracks.size }
    infoCol.addView(TextView(this).apply {
        text = "${profile.playlists.size} ${playlistWord(profile.playlists.size)} · $totalTracks треков"
        textSize = 12f
        setTextColor(SECONDARY)
        setPadding(0, dp(2), 0, 0)
    })
    avatarRow.addView(infoCol, LinearLayout.LayoutParams(0, -2, 1f))
    content.addView(avatarRow, LinearLayout.LayoutParams(-1, -2))

    if (profile.playlists.isEmpty()) {
        addText("Empty board", SECONDARY, 13f)
        return
    }

    val itemsPerRow = 2
    val gap = dp(14)
    val horizontalMargins = dp(8) * 2
    val horizontalPadding = content.paddingLeft + content.paddingRight
    val screenWidth = resources.displayMetrics.widthPixels
    val cellWidth = (screenWidth - horizontalPadding - horizontalMargins - gap) / itemsPerRow
    val cardHeight = cellWidth + dp(48)

    for (i in profile.playlists.indices step itemsPerRow) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (j in 0 until itemsPerRow) {
            val index = i + j
            val lp = LinearLayout.LayoutParams(0, cardHeight, 1f).apply {
                if (j < itemsPerRow - 1) marginEnd = gap
            }
            if (index < profile.playlists.size) {
                row.addView(
                    scannedPlaylistGridCard(profile.playlists[index], cellWidth, profile),
                    lp
                )
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

internal fun MainActivity.scannedPlaylistGridCard(
    pl: PublishedPlaylist,
    coverSize: Int,
    profile: PublishedProfile
): View {
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

    val coverTracks = pl.tracks.take(4)
    if (coverTracks.isEmpty()) {
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.music_note_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
            scaleType = ImageView.ScaleType.FIT_XY
            setColorFilter(SECONDARY)
        }
        val iconSize = (coverSize * 0.36f).toInt().coerceAtLeast(dp(28))
        cover.addView(icon, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
    } else {
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (r in 0 until 2) {
            val gridRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0 until 2) {
                val idx = r * 2 + c
                if (idx < coverTracks.size) {
                    val pub = coverTracks[idx]
                    val img = ImageView(this).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setBackgroundColor(Color.rgb(38, 38, 38))
                    }
                    loadCoverByIdIntoView(pub.id, img)
                    gridRow.addView(img, LinearLayout.LayoutParams(0, -1, 1f))
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

    box.addView(cover, LinearLayout.LayoutParams(coverSize, coverSize))

    box.addView(TextView(this).apply {
        text = pl.name
        textSize = 14f
        setTextColor(TEXT)
        setTypeface(null, android.graphics.Typeface.BOLD)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(0, dp(8), 0, 0)
    })

    box.addView(TextView(this).apply {
        val totalSec = pl.tracks.sumOf { it.duration.toLong() }
        text = if (totalSec > 0)
            "${pl.tracks.size} ${tracksWord(pl.tracks.size)} – ${formatDurationLong(totalSec)}"
        else
            "${pl.tracks.size} ${tracksWord(pl.tracks.size)}"
        textSize = 11f
        setTextColor(SECONDARY)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    })

    box.setOnClickListener {
        showPublishedPlaylistPreview(pl, profile)
    }

    return box
}

internal fun MainActivity.showPublishedPlaylistPreview(pl: PublishedPlaylist, profile: PublishedProfile) {
    try {
        showPublishedPlaylistPreviewInner(pl, profile)
    } catch (e: Throwable) {
        throw e
    }
}

@SuppressLint("SetTextI18n")
internal fun MainActivity.showPublishedPlaylistPreviewInner(
    pl: PublishedPlaylist,
    profile: PublishedProfile
) {
    inScannedPlaylistPreview = true
    currentScannedProfile = profile
    inSubScreen = true
    content.removeAllViews()
    contentPaddingDense()

    activeTrackListContainer = null
    activeTrackListSource = null
    activeTrackListPlaylist = null
    activeTrackListName = null

    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
        setBackgroundColor(BG)
    }

    val backBtn = ImageView(this).apply {
        setImageResource(R.drawable.arrow_back_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setColorFilter(TEXT)
        val p = dp(10); setPadding(p, p, p, p)
        setOnClickListener { showScannedProfile(profile) }
    }
    header.addView(backBtn, LinearLayout.LayoutParams(dp(44), dp(44)))

    val titleCol = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
    }
    titleCol.addView(TextView(this).apply {
        text = pl.name
        textSize = 18f
        setTextColor(TEXT)
        setTypeface(null, android.graphics.Typeface.BOLD)
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    })
    titleCol.addView(TextView(this).apply {
        val totalSec = pl.tracks.sumOf { it.duration.toLong() }
        val subtitle = buildString {
            append("${pl.tracks.size} ${tracksWord(pl.tracks.size)}")
            if (totalSec > 0) {
                append(" – ")
                append(formatDurationLong(totalSec))
            }
        }
        text = subtitle
        textSize = 12f
        setTextColor(SECONDARY)
        gravity = Gravity.CENTER
        setPadding(0, dp(2), 0, 0)
    })
    header.addView(titleCol, LinearLayout.LayoutParams(0, -2, 1f))

    val saveBtn = ImageView(this).apply {
        setImageResource(R.drawable.save_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setColorFilter(TEXT)
        val p = dp(10); setPadding(p, p, p, p)
        setOnClickListener {
            importPublishedPlaylist(pl, profile.name)
            saveState()
            Toast.makeText(
                this@showPublishedPlaylistPreviewInner,
                "Saved: ${pl.name}@${profile.name}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    header.addView(saveBtn, LinearLayout.LayoutParams(dp(44), dp(44)))

    showStickyHeader(header)

    if (pl.tracks.isEmpty()) {
        val tv = TextView(this).apply {
            text = "Empty playlist"
            textSize = 13f
            setTextColor(SECONDARY)
            setPadding(0, dp(10), 0, dp(10))
        }
        (tv.parent as? ViewGroup)?.removeView(tv)
        content.addView(tv, LinearLayout.LayoutParams(-1, -2))
        return
    }

    pl.tracks.forEach { pub ->
        val track = tracks.firstOrNull { it.id == pub.id } ?: Track(
            id = pub.id,
            title = pub.title,
            artist = pub.artist,
            imageUrl = bestThumbnailFor(pub.id),
            duration = pub.duration
        )
        val row = buildReadOnlyTrackRow(track, pl)
        (row.parent as? ViewGroup)?.removeView(row)
        content.addView(row)
    }
}

internal fun MainActivity.buildReadOnlyTrackRow(track: Track, pl: PublishedPlaylist): View {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(6), dp(8), dp(6))
        isClickable = true
        isFocusable = true
        setOnClickListener {
            val singles = pl.tracks.map { pub ->
                tracks.firstOrNull { it.id == pub.id } ?: Track(
                    id = pub.id, title = pub.title, artist = pub.artist,
                    imageUrl = bestThumbnailFor(pub.id), duration = pub.duration
                )
            }.toMutableList()
            playTrack(track, singles, pl.name)

            if (fullPlayerOverlay != null) {
                val old = fullPlayerOverlay
                fullPlayerOverlay = null
                root.removeView(old)
                showFullPlayer()
            }
        }
    }

    val cover = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(10).toFloat())
            }
        }
    }
    loadImageIntoView(track, cover)
    row.addView(cover, LinearLayout.LayoutParams(dp(56), dp(56)))

    val textCol = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
    }
    textCol.addView(TextView(this).apply {
        text = track.title
        textSize = 15f
        setTextColor(TEXT)
        setTypeface(null, android.graphics.Typeface.BOLD)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    })
    textCol.addView(TextView(this).apply {
        text = track.artist
        textSize = 13f
        setTextColor(SECONDARY)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    })
    if (track.duration > 0) {
        textCol.addView(TextView(this).apply {
            text = formatTime(track.duration.toLong() * 1000L)
            textSize = 12f
            setTextColor(SECONDARY)
            setPadding(0, dp(2), 0, 0)
        })
    }
    row.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f).apply {
        marginStart = dp(12)
    })

    return row
}

internal fun MainActivity.loadCoverByIdIntoView(trackId: String, target: ImageView) {
    val temp = Track(
        id = trackId,
        title = "",
        artist = "",
        imageUrl = bestThumbnailFor(trackId)
    )
    loadImageIntoView(temp, target)
}
