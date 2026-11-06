package com.tauchaut.singly.accounts

import android.content.Context.MODE_PRIVATE
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.data.loadPlaybackFlags
import com.tauchaut.singly.data.loadState
import com.tauchaut.singly.data.saveState
import com.tauchaut.singly.models.AccountProfile
import com.tauchaut.singly.ui.avatarFile
import com.tauchaut.singly.ui.closeContextSheet
import com.tauchaut.singly.ui.closeFullPlayer
import com.tauchaut.singly.ui.closeSimilarPlaylistOverlay
import com.tauchaut.singly.ui.showProfile
import com.tauchaut.singly.ui.updateMiniPlayer
import com.tauchaut.singly.util.prefsNameFor
import org.json.JSONArray
import org.json.JSONObject
private const val DEFAULT_ID = "default"
private const val DEFAULT_NAME = "My account"
private const val DEFAULT_EMOJI = "🎧"
private val DEFAULT_COLOR = 0xFF3B5BDB.toInt()

internal fun MainActivity.migrateToAccountsIfNeeded() {
    if (globalPrefs.getBoolean("accounts_migrated_v1", false)) return
    val legacy = getSharedPreferences("singly", MODE_PRIVATE)
    val oldName = legacy.getString("username", "").orEmpty()
    val name = oldName.ifBlank { DEFAULT_NAME }
    val acc = AccountProfile(
        id = DEFAULT_ID,
        name = name,
        avatarEmoji = DEFAULT_EMOJI,
        avatarColor = "#3B5BDB".toColorInt(),
        createdAt = System.currentTimeMillis()
    )
    val arr = JSONArray().apply { put(accountToJson(acc)) }
    globalPrefs.edit {
        putString("accounts", arr.toString())
        putString("active_account", DEFAULT_ID)
        putBoolean("accounts_migrated_v1", true)
    }
}

internal fun MainActivity.loadActiveAccount() {
    val id = globalPrefs.getString("active_account", DEFAULT_ID)
    val list = loadAccounts()
    val acc = list.firstOrNull { it.id == id } ?: list.firstOrNull()
    if (acc != null) {
        currentAccount = acc
        prefs = getSharedPreferences(prefsNameFor(acc.id), MODE_PRIVATE)
        return
    }
    currentAccount = AccountProfile(
        DEFAULT_ID, DEFAULT_NAME, DEFAULT_EMOJI, DEFAULT_COLOR,
        System.currentTimeMillis()
    )
    prefs = getSharedPreferences("singly", MODE_PRIVATE)
}

internal fun accountToJson(a: AccountProfile) = JSONObject().apply {
    put("id", a.id)
    put("name", a.name)
    put("emoji", a.avatarEmoji)
    put("color", a.avatarColor)
    put("createdAt", a.createdAt)
    if (a.avatarPhotoPath != null) put("photoPath", a.avatarPhotoPath)
}

internal fun jsonToAccount(o: JSONObject): AccountProfile {
    val photo = o.optString("photoPath", "")
    return AccountProfile(
        id = o.getString("id"),
        name = o.getString("name"),
        avatarEmoji = o.optString("emoji", "🎵"),
        avatarColor = o.optInt("color", DEFAULT_COLOR),
        createdAt = o.optLong("createdAt", 0L),
        avatarPhotoPath = if (photo.isEmpty()) null else photo
    )
}

internal fun MainActivity.loadAccounts(): MutableList<AccountProfile> {
    val raw = globalPrefs.getString("accounts", "").orEmpty()
    if (raw.isEmpty()) return mutableListOf()
    val out = mutableListOf<AccountProfile>()
    try {
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            out.add(jsonToAccount(arr.getJSONObject(i)))
        }
    } catch (_: Exception) {

    }
    return out
}

internal fun MainActivity.switchToAccount(id: String) {
    if (currentAccount?.id == id) return
    val target = loadAccounts().firstOrNull { it.id == id } ?: return
    if (contextSheetOverlay != null) closeContextSheet(instant = true)
    closeFullPlayer()
    if (similarPlaylistOverlay != null) closeSimilarPlaylistOverlay()
    saveState()
    try { mediaPlayer?.stop() } catch (_: Exception) { }
    try { mediaPlayer?.release() } catch (_: Exception) { }
    mediaPlayer = null
    currentTrack = null
    currentPlaylist = mutableListOf()
    currentPlaylistName = ""
    isPlaying = false
    isLoadingTrack = false
    globalPrefs.edit { putString("active_account", id) }
    currentAccount = target
    prefs = getSharedPreferences(prefsNameFor(id), MODE_PRIVATE)
    loadState()
    loadPlaybackFlags()
    updateMiniPlayer(forceRebuild = true)
    showProfile()
    Toast.makeText(this, "Account: ${target.name}", Toast.LENGTH_SHORT).show()
}

internal fun MainActivity.deleteAccountById(id: String) {
    val accounts = loadAccounts()
    if (accounts.size <= 1) {
        Toast.makeText(this, "You cannot delete the last account.", Toast.LENGTH_SHORT).show()
        return
    }
    if (id == currentAccount?.id) {
        Toast.makeText(this, "First, switch to another account", Toast.LENGTH_SHORT).show()
        return
    }
    val target = accounts.firstOrNull { it.id == id } ?: return
    val avatar = avatarFile(id)
    if (avatar.exists()) avatar.delete()
    if (id != DEFAULT_ID) {
        getSharedPreferences(prefsNameFor(id), MODE_PRIVATE).edit { clear() }
    }
    saveAccounts(accounts.filter { it.id != id })
    showProfile()
    Toast.makeText(this, "«${target.name}» deleted", Toast.LENGTH_SHORT).show()
}

internal fun MainActivity.saveAccounts(list: List<AccountProfile>) {
    val arr = JSONArray()
    for (a in list) arr.put(accountToJson(a))
    globalPrefs.edit { putString("accounts", arr.toString()) }
}
