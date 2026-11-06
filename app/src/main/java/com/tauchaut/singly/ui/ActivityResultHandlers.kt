package com.tauchaut.singly.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.accounts.loadAccounts
import com.tauchaut.singly.accounts.saveAccounts
import com.tauchaut.singly.qr.handleScannedQr
import java.io.File

internal fun MainActivity.handleQrScanResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?
): Boolean {
    val scanResult = com.google.zxing.integration.android.IntentIntegrator
        .parseActivityResult(requestCode, resultCode, data) ?: return false
    scanResult.contents?.let { handleScannedQr(it) }
    return true
}

internal fun MainActivity.handleAvatarCameraResult(requestCode: Int, resultCode: Int): Boolean {
    if (requestCode != AVATAR_CAMERA_REQUEST) return false
    if (resultCode == android.app.Activity.RESULT_OK && pendingCameraUri != null) {
        applyAvatarFromUri(pendingCameraUri!!)
    } else {
        pendingCameraUri?.let { camUri ->
            try {
                val path = camUri.path?.substringAfter("/files/")
                if (path != null) File(filesDir, path).takeIf { it.exists() }?.delete()
            } catch (_: Exception) {}
        }
        pendingCameraUri = null
    }
    return true
}

internal fun MainActivity.handleAvatarPickResult(requestCode: Int, uri: Uri): Boolean {
    if (requestCode != AVATAR_PICK_REQUEST) return false
    val acc = currentAccount ?: return true
    if (saveAvatarPhoto(acc.id, uri)) {
        val updated = acc.copy(avatarPhotoPath = "avatars/${acc.id}.jpg")
        val list = loadAccounts().toMutableList()
        val idx = list.indexOfFirst { it.id == acc.id }
        if (idx >= 0) {
            list[idx] = updated
            saveAccounts(list)
            currentAccount = updated
        }
        showEditProfile()
        Toast.makeText(this, "Photo updated", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(this, "Failed to load photo", Toast.LENGTH_SHORT).show()
    }
    return true
}

internal fun MainActivity.handleBoardImportResult(requestCode: Int, uri: Uri): Boolean {
    if (requestCode != BOARD_IMPORT_REQUEST) return false
    val text = contentResolver.openInputStream(uri)
        ?.bufferedReader()?.use { it.readText() }
    if (text != null && text.startsWith("$QR_PREFIX|")) {
        handleScannedQr(text)
    } else {
        Toast.makeText(this, "Not a board file", Toast.LENGTH_SHORT).show()
    }
    return true
}

internal fun MainActivity.handleBoardExportResult(requestCode: Int, uri: Uri): Boolean {
    if (requestCode != BOARD_EXPORT_REQUEST) return false
    try {
        val payload = pendingBoardExport
        if (payload != null) {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(payload.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, "Board was saved", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
    } finally {
        pendingBoardExport = null
    }
    return true
}

internal fun MainActivity.handleExportResult(requestCode: Int, uri: Uri): Boolean {
    if (requestCode != EXPORT_REQUEST) return false
    try {
        contentResolver.openOutputStream(uri)?.use { out ->
            out.write(exportStateToJson().toByteArray(Charsets.UTF_8))
        }
        Toast.makeText(this, "Data was exported", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
    }
    return true
}

internal fun MainActivity.handleImportResult(requestCode: Int, uri: Uri): Boolean {
    if (requestCode != IMPORT_REQUEST) return false
    android.app.AlertDialog.Builder(this)
        .setTitle("Data import")
        .setMessage("Current account data will be overwritten, continue?")
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Import") { _, _ ->
            try {
                val json = contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                if (json != null && importStateFromJson(json)) {
                    Toast.makeText(this, "Data restored", Toast.LENGTH_SHORT).show()
                    showProfile()
                } else {
                    Toast.makeText(this, "Wrong file format", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        .show()
    return true
}

internal fun MainActivity.handleAvatarPermissionResult(
    requestCode: Int,
    grantResults: IntArray
): Boolean {
    if (requestCode != AVATAR_CAMERA_PERMISSION_REQUEST) return false
    if (grantResults.isNotEmpty() &&
        grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        launchCamera()
    } else {
        Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
    }
    return true
}
