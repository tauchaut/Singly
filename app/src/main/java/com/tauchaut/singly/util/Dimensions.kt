package com.tauchaut.singly.util

import android.content.Context
import android.view.View
import kotlin.math.roundToInt

internal fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density).roundToInt()

internal fun View.dp(value: Int): Int =
    (value * resources.displayMetrics.density).roundToInt()
