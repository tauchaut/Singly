package com.tauchaut.singly.data

import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.models.SearchEntry
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.content.edit

internal fun MainActivity.loadSearchHistory(): MutableList<SearchEntry> {
    val raw = prefs.getString("search_history_v2", "") ?: ""
    if (raw.isEmpty()) {
        val old = (prefs.getString("search_history", "") ?: "")
            .split("|").filter { it.isNotEmpty() }
        if (old.isEmpty()) return mutableListOf()
        val migrated = old.mapIndexed { idx, q ->
            SearchEntry(q, System.currentTimeMillis() - idx * 1000L)
        }.toMutableList()
        persistSearchHistory(migrated)
        prefs.edit { remove("search_history") }
        return migrated
    }
    return try {
        val arr = JSONArray(raw)
        val list = mutableListOf<SearchEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(SearchEntry(obj.getString("q"), obj.getLong("ts")))
        }
        list
    } catch (_: Exception) {
        mutableListOf()
    }
}

internal fun MainActivity.persistSearchHistory(list: List<SearchEntry>) {
    val arr = JSONArray()
    list.forEach {
        arr.put(JSONObject().apply {
            put("q", it.query)
            put("ts", it.timestamp)
        })
    }
    prefs.edit { putString("search_history_v2", arr.toString()) }
}

internal fun MainActivity.saveSearchQuery(q: String) {
    val trimmed = q.trim()
    if (trimmed.isEmpty()) return
    val history = loadSearchHistory()
    history.removeAll { it.query.equals(trimmed, ignoreCase = true) }
    history.add(0, SearchEntry(trimmed, System.currentTimeMillis()))
    while (history.size > 500) history.removeAt(history.size - 1)
    persistSearchHistory(history)
}

internal fun MainActivity.getSearchHistory(): List<String> =
    loadSearchHistory().map { it.query }

internal fun MainActivity.removeSearchQuery(q: String) {
    val history = loadSearchHistory()
    history.removeAll { it.query.equals(q, ignoreCase = true) }
    persistSearchHistory(history)
}
