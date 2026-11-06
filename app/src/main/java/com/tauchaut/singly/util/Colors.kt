package com.tauchaut.singly.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable

internal fun Context.roundedDrawable(color: Int, radius: Int): Drawable {
    val d = GradientDrawable()
    d.setColor(color)
    d.cornerRadius = dp(radius).toFloat()
    return d
}

internal fun blendWithBg(dominant: Int, bg: Int, ratio: Float): Int {
    val inv = 1f - ratio
    return Color.argb(
        255,
        (Color.red(dominant) * ratio + Color.red(bg) * inv).toInt().coerceIn(0, 255),
        (Color.green(dominant) * ratio + Color.green(bg) * inv).toInt().coerceIn(0, 255),
        (Color.blue(dominant) * ratio + Color.blue(bg) * inv).toInt().coerceIn(0, 255)
    )
}
