package com.tauchaut.singly.youtube

import android.util.Log
import com.tauchaut.singly.models.Track
import com.tauchaut.singly.util.cleanYoutubeTitle
import com.tauchaut.singly.util.extractVideoId
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

internal fun extractRelatedTracksFromYouTube(
    seedVideoId: String,
    seedArtist: String,
    excludeIds: Set<String>
): List<Track> {
    val result = mutableListOf<Track>()
    var uploaderName = seedArtist
    try {
        val info = StreamInfo.getInfo(
            ServiceList.YouTube,
            "https://www.youtube.com/watch?v=$seedVideoId"
        )
        uploaderName = info.uploaderName ?: seedArtist
        val related: List<*> = try {
            info.relatedItems as? List<*> ?: emptyList<Any>()
        } catch (_: Throwable) {
            emptyList<Any>()
        }
        Log.d("Related", "seed=$seedVideoId relatedCount=${related.size}")
        for (raw in related) {
            val item = raw as? StreamInfoItem ?: continue
            if (!isLikelyMusicTrack(item)) continue
            val videoId = extractVideoId(item.url)
            if (videoId.isEmpty()) continue
            if (videoId == seedVideoId) continue
            if (videoId in excludeIds) continue
            if (result.any { it.id == videoId }) continue
            val (title, artist) = cleanYoutubeTitle(
                rawTitle = item.name ?: "Unknown Title",
                rawArtist = item.uploaderName ?: "YouTube"
            )
            result.add(
                Track(
                    id = videoId,
                    title = title,
                    artist = artist,
                    imageUrl = bestThumbnailFor(videoId),
                    duration = item.duration.toInt().coerceAtLeast(0)
                )
            )
        }
    } catch (e: Exception) {
        Log.e("Related", "StreamInfo.getInfo failed for $seedVideoId: ${e.message}", e)
    }
    if (result.isEmpty() && uploaderName.isNotBlank()) {
        try {
            Log.d("Related", "fallback search for '$uploaderName'")
            val search = ServiceList.YouTube.getSearchExtractor("$uploaderName songs")
            search.fetchPage()
            var page: org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage<*>? = search.initialPage
            var added = 0
            while (page != null && added < 15) {
                for (item in page.items) {
                    if (item !is StreamInfoItem) continue
                    if (!isLikelyMusicTrack(item)) continue
                    val vid = extractVideoId(item.url)
                    if (vid.isEmpty() || vid == seedVideoId) continue
                    if (vid in excludeIds) continue
                    if (result.any { it.id == vid }) continue
                    val (title, artist) = cleanYoutubeTitle(
                        rawTitle = item.name ?: "Unknown Title",
                        rawArtist = item.uploaderName ?: "YouTube"
                    )
                    result.add(
                        Track(
                            id = vid,
                            title = title,
                            artist = artist,
                            imageUrl = bestThumbnailFor(vid),
                            duration = item.duration.toInt().coerceAtLeast(0)
                        )
                    )
                    added++
                }
                val next = page.nextPage
                page = if (next != null) {
                    try { search.getPage(next) } catch (_: Exception) { null }
                } else null
            }
            Log.d("Related", "fallback added=$added")
        } catch (e: Exception) {
            Log.e("Related", "fallback failed: ${e.message}", e)
        }
    }
    Log.d("Related", "seed=$seedVideoId accepted=${result.size}")
    return result
}

internal fun isLikelyMusicTrack(item: StreamInfoItem): Boolean {
    val dur = item.duration.toInt()
    if (dur > 0 && (dur < 45 || dur > 15 * 60)) return false
    val st = item.streamType
    if (st == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM ||
        st == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM ||
        st == org.schabi.newpipe.extractor.stream.StreamType.POST_LIVE_STREAM) return false
    val t = item.name?.lowercase() ?: return false
    val hard = listOf(
        "interview", "podcast", "reaction", "reacts to", "reacting to",
        "tutorial", "behind the scenes", "making of", "teaser", "trailer",
        "top 10 ", "top ten ", "top 5 ", "best of ", "greatest hits",
        "compilation", "full album", "album stream",
        "try not to", "tier list", "ranking", "ranked",
        "documentary", "tiktok", "explained", "story of ", "history of ",
        "things you", "facts about", "trivia", "quiz",
        "mashup", "megamix"
    )
    if (hard.any { t.contains(it) }) return false
    if (t.length !in 3..150) return false
    return true
}

internal fun similarSearchSeeds(track: Track): List<String> {
    val seeds = LinkedHashSet<String>()
    if (track.artist.isNotBlank()) seeds.add("${track.artist} similar songs")
    if (track.title.isNotBlank() && track.artist.isNotBlank()) seeds.add("${track.artist} ${track.title} similar")
    if (track.title.isNotBlank()) seeds.add("songs like ${track.title}")
    if (seeds.isEmpty()) seeds.add("popular music")
    return seeds.toList()
}
