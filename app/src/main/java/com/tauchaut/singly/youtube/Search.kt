package com.tauchaut.singly.youtube

import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.models.Track
import com.tauchaut.singly.util.cleanYoutubeTitle
import com.tauchaut.singly.util.extractVideoId
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.net.URLEncoder
import kotlin.concurrent.thread

internal fun MainActivity.searchYouTube(
    query: String,
    page: Page? = null,
    onResult: (List<Track>, Page?) -> Unit
) {
    thread {
        val resultList = mutableListOf<Track>()
        var next: Page? = null
        try {
            val extractor: SearchExtractor = if (page == null) {
                ServiceList.YouTube.getSearchExtractor(query).also { it.fetchPage() }
            } else {
                this@searchYouTube.searchExtractor
                    ?: ServiceList.YouTube.getSearchExtractor(query).also { it.fetchPage() }
            }
            this@searchYouTube.searchExtractor = extractor
            val infoPage = if (page == null) extractor.initialPage else extractor.getPage(page)
            next = infoPage.nextPage
            infoPage.items.forEach { item ->
                if (item is StreamInfoItem) {
                    val videoId = extractVideoId(item.url)
                    if (videoId.isNotEmpty() && resultList.none { it.id == videoId }) {
                        val (cleanTitle, cleanArtist) = cleanYoutubeTitle(
                            rawTitle = item.name ?: "Unknown Title",
                            rawArtist = item.uploaderName ?: "YouTube"
                        )
                        resultList.add(
                            Track(
                                id = videoId,
                                title = cleanTitle,
                                artist = cleanArtist,
                                imageUrl = bestThumbnailFor(videoId),
                                duration = item.duration.toInt()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        this@searchYouTube.runOnUiThread { onResult(resultList, next) }
    }
}

internal fun MainActivity.fetchSearchSuggestions(
    query: String,
    onResult: (List<String>) -> Unit
) {
    thread {
        val suggestions = mutableListOf<String>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = OkHttpRequest.Builder()
                .url("https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=$encoded")
                .addHeader("User-Agent", "Mozilla/5.0")
                .build()
            val response = OkHttpClient().newCall(request).execute()
            if (response.isSuccessful) {
                val json = org.json.JSONArray(response.body?.string() ?: "[]")
                val values = json.optJSONArray(1)
                if (values != null) {
                    for (i in 0 until values.length()) {
                        val value = values.optString(i).trim()
                        if (value.isNotEmpty() && suggestions.none { it.equals(value, true) })
                            suggestions.add(value)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        this@fetchSearchSuggestions.runOnUiThread { onResult(suggestions.take(8)) }
    }
}
