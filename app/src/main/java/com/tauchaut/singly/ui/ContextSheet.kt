package com.tauchaut.singly.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.models.ContextSheetOption
import com.tauchaut.singly.util.dp
import com.tauchaut.singly.util.roundedDrawable

@SuppressLint("ViewConstructor")
internal class ContextSheetScrollView(context: Context, private val maxHeightPx: Int) : ScrollView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val capped = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, capped)
    }
}

internal fun MainActivity.closeContextSheet(instant: Boolean = false) {
    val overlay = contextSheetOverlay ?: return
    val container = contextSheetContainer
    contextSheetOverlay = null
    contextSheetContainer = null
    container?.animate()?.cancel()
    if (instant || container == null) {
        root.removeView(overlay)
        return
    }
    container.animate()
        .translationY(container.height.toFloat())
        .setDuration(200)
        .setInterpolator(android.view.animation.AccelerateInterpolator())
        .withEndAction { root.removeView(overlay) }
        .start()
}

@SuppressLint("ClickableViewAccessibility")
internal fun MainActivity.showContextSheet(
    title: String,
    subtitle: String? = null,
    options: List<ContextSheetOption>
) {
    closeContextSheet(instant = true)
    val overlay = FrameLayout(this)
    overlay.addView(View(this).apply {
        setBackgroundColor("#CC000000".toColorInt())
        isClickable = true
        setOnClickListener { closeContextSheet() }
    }, FrameLayout.LayoutParams(-1, -1))
    val sheet = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor("#1E1E1E".toColorInt())
            cornerRadii = floatArrayOf(
                dp(24).toFloat(), dp(24).toFloat(),
                dp(24).toFloat(), dp(24).toFloat(),
                0f, 0f, 0f, 0f
            )
        }
        isClickable = true
    }
    val topArea = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }
    topArea.addView(View(this).apply {
        background = roundedDrawable("#3A3A3A".toColorInt(), 2)
    }, LinearLayout.LayoutParams(dp(36), dp(4)).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        topMargin = dp(10)
        bottomMargin = dp(6)
    })
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(10), dp(24), dp(10))
    }
    header.addView(TextView(this).apply {
        text = title
        textSize = 17f
        setTextColor(TEXT)
        setTypeface(null, android.graphics.Typeface.BOLD)
        isSingleLine = true
        ellipsize = android.text.TextUtils.TruncateAt.END
    })
    if (!subtitle.isNullOrBlank()) {
        header.addView(TextView(this).apply {
            text = subtitle
            textSize = 13f
            setTextColor(SECONDARY)
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(2), 0, 0)
        })
    }
    topArea.addView(header)
    sheet.addView(topArea)
    options.forEach { opt ->
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(24), 0)
            isClickable = true
            isFocusable = true
            if (!opt.enabled) alpha = 0.4f
        }
        row.addView(TextView(this).apply {
            text = opt.label
            textSize = 15f
            setTextColor(if (opt.destructive) "#FF6B6B".toColorInt() else TEXT)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (opt.trailingCheck) {
            row.addView(TextView(this).apply {
                text = "✓"
                textSize = 16f
                setTextColor(TEXT)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(24), -2))
        }
        if (opt.trailingSwitch != null) {
            val sw = Switch(this).apply {
                isChecked = opt.trailingSwitch
                setOnCheckedChangeListener { _, c -> opt.onSwitch?.invoke(c) }
            }
            row.addView(sw)
            row.setOnClickListener { sw.isChecked = !sw.isChecked }
        } else if (opt.enabled && opt.action != null) {
            val act = opt.action
            row.setOnClickListener {
                closeContextSheet()
                act.invoke()
            }
        }
        sheet.addView(row, LinearLayout.LayoutParams(-1, dp(52)))
    }
    sheet.addView(View(this), LinearLayout.LayoutParams(-1, dp(24)))
    val screenW = resources.displayMetrics.widthPixels
    val screenH = resources.displayMetrics.heightPixels
    val maxH = (screenH * 0.72f).toInt()
    sheet.measure(
        View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    val contentH = sheet.measuredHeight
    val sheetH = minOf(contentH, maxH)
    val container = FrameLayout(this)
    if (contentH > maxH) {
        container.addView(ScrollView(this).apply {
            isFillViewport = false
            addView(sheet, FrameLayout.LayoutParams(-1, -2))
        }, FrameLayout.LayoutParams(-1, -1))
    } else {
        container.addView(sheet, FrameLayout.LayoutParams(-1, -1))
    }
    overlay.addView(container, FrameLayout.LayoutParams(-1, sheetH).apply {
        gravity = Gravity.BOTTOM
    })
    var touchStartY = 0f
    topArea.setOnTouchListener { _, ev ->
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartY = ev.rawY
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.rawY - touchStartY
                if (dy > 0) container.translationY = dy
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dy = container.translationY
                if (dy > sheetH * 0.3f) closeContextSheet()
                else container.animate().translationY(0f).setDuration(150).start()
                true
            }
            else -> false
        }
    }
    contextSheetOverlay = overlay
    contextSheetContainer = container
    root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
    overlay.elevation = dp(100).toFloat()
    container.translationY = sheetH.toFloat()
    container.animate()
        .translationY(0f)
        .setDuration(250)
        .setInterpolator(android.view.animation.DecelerateInterpolator())
        .start()
}
