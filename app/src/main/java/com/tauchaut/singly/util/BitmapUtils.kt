package com.tauchaut.singly.util

import android.graphics.Bitmap

internal fun trimLetterbox(src: Bitmap): Bitmap {
    val w = src.width
    val h = src.height
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)
    fun isBlack(idx: Int): Boolean {
        val c = pixels[idx]
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return r < 24 && g < 24 && b < 24
    }
    var top = 0
    outerTop@ while (top < h) {
        var blackCount = 0
        for (x in 0 until w) {
            if (isBlack(top * w + x)) blackCount++
        }
        if (blackCount < w * 0.9) break@outerTop
        top++
    }
    var bottom = h - 1
    outerBottom@ while (bottom > top) {
        var blackCount = 0
        for (x in 0 until w) {
            if (isBlack(bottom * w + x)) blackCount++
        }
        if (blackCount < w * 0.9) break@outerBottom
        bottom--
    }
    var left = 0
    outerLeft@ while (left < w) {
        var blackCount = 0
        for (y in top..bottom) {
            if (isBlack(y * w + left)) blackCount++
        }
        if (blackCount < (bottom - top + 1) * 0.9) break@outerLeft
        left++
    }
    var right = w - 1
    outerRight@ while (right > left) {
        var blackCount = 0
        for (y in top..bottom) {
            if (isBlack(y * w + right)) blackCount++
        }
        if (blackCount < (bottom - top + 1) * 0.9) break@outerRight
        right--
    }
    val newW = right - left + 1
    val newH = bottom - top + 1
    if (newW <= 0 || newH <= 0 || (newW == w && newH == h)) return src
    val out = Bitmap.createBitmap(src, left, top, newW, newH)
    if (out !== src && !src.isRecycled) src.recycle()
    return out
}

internal fun cropToSquare(src: Bitmap): Bitmap {
    var bmp = trimLetterbox(src)
    if (bmp.width != bmp.height) {
        val side = minOf(bmp.width, bmp.height)
        val x = (bmp.width  - side) / 2
        val y = (bmp.height - side) / 2
        val cropped = Bitmap.createBitmap(bmp, x, y, side, side)
        if (cropped !== bmp && !bmp.isRecycled) bmp.recycle()
        bmp = cropped
    }
    return bmp
}

