package com.tauchaut.singly.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.Switch
import android.widget.TextView
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.util.dp
import com.tauchaut.singly.util.roundedDrawable

internal class SimpleTextWatcher(private val callback: (String) -> Unit) : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        callback(s?.toString() ?: "")
    }
    override fun afterTextChanged(s: android.text.Editable?) = Unit
}

internal fun MainActivity.addTitle(text: String) {
    val view = TextView(this)
    view.text = text
    view.textSize = 19f
    view.setTextColor(TEXT)
    view.setTypeface(null, android.graphics.Typeface.BOLD)
    view.setPadding(0, dp(22), 0, dp(12))
    content.addView(view)
}

internal fun MainActivity.addTextTo(container: LinearLayout, text: String, color: Int, size: Float) {
    val view = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        setPadding(0, dp(10), 0, dp(10))
    }
    container.addView(view)
}

internal fun MainActivity.addText(text: String, color: Int, size: Float) {
    val view = TextView(this)
    view.text = text
    view.textSize = size
    view.setTextColor(color)
    content.addView(view, LinearLayout.LayoutParams(-1, -2))
}

internal fun MainActivity.addSpace(height: Int) {
    val space = Space(this)
    content.addView(space, LinearLayout.LayoutParams(1, dp(height)))
}

internal fun MainActivity.addBackButton(action: () -> Unit) {
    val backButton = ImageView(this).apply {
        setImageResource(com.tauchaut.singly.R.drawable.arrow_back_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setColorFilter(TEXT)
        val p = dp(10)
        setPadding(p, p, p, p)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }
    content.addView(backButton, LinearLayout.LayoutParams(dp(44), dp(44)))
}

internal fun MainActivity.button(text: String, action: () -> Unit) {
    val button = TextView(this)
    button.text = text
    button.textSize = 15f
    button.setTextColor(TEXT)
    button.gravity = Gravity.CENTER_VERTICAL
    button.setPadding(dp(18), 0, dp(18), 0)
    button.background = roundedDrawable(CARD, 12)
    button.setOnClickListener { action() }
    content.addView(button, LinearLayout.LayoutParams(-1, dp(52)).apply {
        bottomMargin = dp(10)
    })
}

internal fun MainActivity.settingSwitch(title: String, checked: Boolean, callback: (Boolean) -> Unit) {
    val row = LinearLayout(this)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER_VERTICAL

    val text = TextView(this)
    text.text = title
    text.textSize = 16f
    text.setTextColor(TEXT)
    row.addView(text, LinearLayout.LayoutParams(0, dp(55), 1f))

    val switch = Switch(this)
    switch.isChecked = checked
    switch.setOnCheckedChangeListener { _, value -> callback(value) }
    row.addView(switch)

    content.addView(row)
}

internal fun MainActivity.addDialogButton(box: LinearLayout, text: String, action: () -> Unit) {
    val button = TextView(this)
    button.text = text
    button.textSize = 15f
    button.setTextColor(TEXT)
    button.gravity = Gravity.CENTER_VERTICAL
    button.setPadding(dp(8), 0, dp(8), 0)
    button.setOnClickListener { action() }
    box.addView(button, LinearLayout.LayoutParams(-1, dp(52)))
}

internal fun MainActivity.iconButton(resId: Int, viewSizeDp: Int, iconSizeDp: Int): ImageView {
    val pad = (viewSizeDp - iconSizeDp) / 2
    return ImageView(this).apply {
        setImageResource(resId)
        scaleType = ImageView.ScaleType.FIT_XY
        setColorFilter(TEXT)
        setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
    }
}

internal fun MainActivity.glowFadeDrawableRect(width: Int, height: Int): Drawable {
    val w = width.toFloat()
    val h = height.toFloat()
    val fadeX = width * 0.28f
    val fadeY = height * 0.32f
    val bg = BG

    val shaderTop = LinearGradient(0f, 0f, 0f, fadeY, bg, Color.TRANSPARENT, Shader.TileMode.CLAMP)
    val shaderBottom = LinearGradient(0f, h, 0f, h - fadeY, bg, Color.TRANSPARENT, Shader.TileMode.CLAMP)
    val shaderLeft = LinearGradient(0f, 0f, fadeX, 0f, bg, Color.TRANSPARENT, Shader.TileMode.CLAMP)
    val shaderRight = LinearGradient(w, 0f, w - fadeX, 0f, bg, Color.TRANSPARENT, Shader.TileMode.CLAMP)
    val paint = Paint().apply { isAntiAlias = true }

    return object : Drawable() {
        override fun draw(canvas: Canvas) {
            paint.shader = shaderTop; canvas.drawRect(0f, 0f, w, fadeY, paint)
            paint.shader = shaderBottom; canvas.drawRect(0f, h - fadeY, w, h, paint)
            paint.shader = shaderLeft; canvas.drawRect(0f, 0f, fadeX, h, paint)
            paint.shader = shaderRight; canvas.drawRect(w - fadeX, 0f, w, h, paint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in API 29")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}

internal fun MainActivity.sectionLabel(text: String) {
    content.addView(TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(SECONDARY)
        setTypeface(null, android.graphics.Typeface.BOLD)
        letterSpacing = 0.08f
        setPadding(dp(4), dp(16), dp(4), dp(8))
    })
}
