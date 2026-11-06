package com.tauchaut.singly.youtube

import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NPRequest
import org.schabi.newpipe.extractor.downloader.Response as NPResponse

internal class AppDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun execute(request: NPRequest): NPResponse {
        val builder = OkHttpRequest.Builder().url(request.url())
        val headers = request.headers()

        val hasUserAgent = headers.keys.any { it.equals("User-Agent", ignoreCase = true) }
        val hasCookie = headers.keys.any { it.equals("Cookie", ignoreCase = true) }

        headers.forEach { (key, values) ->
            values.forEach { value ->
                builder.addHeader(key, value)
            }
        }

        if (!hasUserAgent) {
            builder.addHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 16; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            )
        }

        if (!hasCookie) {
            builder.addHeader("Cookie", "SOCS=CAI; CONSENT=YES+")
        }

        builder.addHeader("Accept-Language", "en-US,en;q=0.9")

        val httpMethod = request.httpMethod()
        val requestData = request.dataToSend()
        val requestBody = requestData?.toRequestBody(
            null,
            0,
            requestData.size
        )

        when (httpMethod) {
            "GET" -> builder.get()
            "POST" -> builder.post(requestBody ?: run {
                val content = ByteArray(0)
                content.toRequestBody(null, 0, content.size)
            })
            "HEAD" -> builder.head()
            else -> builder.method(httpMethod, requestBody)
        }

        val okHttpResponse = client.newCall(builder.build()).execute()
        val responseBody = okHttpResponse.body?.string() ?: ""

        val headerMap = mutableMapOf<String, List<String>>()
        okHttpResponse.headers.toMultimap().forEach { (k, v) ->
            headerMap[k] = v
        }

        return NPResponse(
            okHttpResponse.code,
            okHttpResponse.message,
            headerMap,
            responseBody,
            request.url()
        )
    }
}
