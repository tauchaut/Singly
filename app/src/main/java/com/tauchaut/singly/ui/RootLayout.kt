package com.tauchaut.singly.ui

import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.util.dp

internal fun MainActivity.createUI() {
    root = FrameLayout(this)
    root.setBackgroundColor(BG)
    content = LinearLayout(this)
    content.orientation = LinearLayout.VERTICAL
    content.setPadding(dp(24), dp(10), dp(24), dp(150))
    val scroll = ScrollView(this)
    mainScroll = scroll
    scroll.setBackgroundColor(BG)
    scroll.addView(content)
    val swipe = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(this).apply {
        setColorSchemeColors(TEXT)
        setProgressBackgroundColorSchemeColor(CARD)
        setDistanceToTriggerSync(dp(80))
        setSlingshotDistance(dp(120))
        isEnabled = currentTab == TAB_HOME
        setOnRefreshListener {
            if (currentTab != TAB_HOME) {
                swipeRefresh?.isRefreshing = false
                return@setOnRefreshListener
            }
            homeRecommendationContainer?.let {
                (it.parent as? ViewGroup)?.removeView(it)
            }
            homeRecommendationContainer = null
            recommendationGeneration++
            recommendationSeenIds.clear()
            recommendationSeedIndex = 0
            recommendationExtractor = null
            recommendationNextPage = null
            recommendationPlaybackTracks.clear()
            recommendationLoading = false
            showHome()
        }
        addView(scroll)
    }
    swipeRefresh = swipe
    root.addView(swipe, FrameLayout.LayoutParams(-1, -1))
    stickyHeaderHost = FrameLayout(this).apply {
        visibility = View.GONE
        setBackgroundColor(BG)
        elevation = dp(4).toFloat()
    }
    root.addView(
        stickyHeaderHost,
        FrameLayout.LayoutParams(-1, -2).apply { gravity = Gravity.TOP }
    )
    createBottomNav()
    createMiniPlayer()
    setContentView(root)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
        ) {
            handleBackPressed()
        }
    }
}

internal fun MainActivity.showStickyHeader(header: View) {
    val host = stickyHeaderHost ?: return
    (header.parent as? ViewGroup)?.removeView(header)
    host.removeAllViews()
    host.addView(header, FrameLayout.LayoutParams(-1, -2))
    host.visibility = View.VISIBLE
    val widthPx = when {
        host.width > 0 -> host.width
        root.width > 0 -> root.width
        else -> resources.displayMetrics.widthPixels
    }
    header.measure(
        View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    val h = header.measuredHeight
    if (h > 0 && h != stickyTopOffset) {
        stickyTopOffset = h
        applyContentPadding()
    }
}

internal fun MainActivity.hideStickyHeader() {
    stickyHeaderHost?.visibility = View.GONE
    stickyHeaderHost?.removeAllViews()
    if (stickyTopOffset != 0) {
        stickyTopOffset = 0
        applyContentPadding()
    }
}

internal fun MainActivity.contentPaddingNormal() {
    currentLeftPad = 24
    currentRightPad = 24
    applyContentPadding()
}

internal fun MainActivity.contentPaddingDense() {
    currentLeftPad = 8
    currentRightPad = 8
    applyContentPadding()
}

internal fun MainActivity.applyContentPadding() {
    content.setPadding(
        dp(currentLeftPad),
        dp(10) + stickyTopOffset,
        dp(currentRightPad),
        dp(180)
    )
}
