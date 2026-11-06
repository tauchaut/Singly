package com.tauchaut.singly.models

import java.net.HttpURLConnection

internal data class DownloadJob(
    val trackId: String,
    var progress: Int = 0,
    var bytesRead: Long = 0L,
    @Volatile var cancelled: Boolean = false,
    @Volatile var connection: HttpURLConnection? = null
)
