package com.tauchaut.singly.ui

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.R
import com.tauchaut.singly.util.dp

internal fun MainActivity.createBottomNav() {
    bottomNav = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setBackgroundColor(BG)
        elevation = dp(8).toFloat()
    }
    val items = listOf(
        TAB_HOME to R.drawable.home_24dp_e3e3e3_fill0_wght300_grad0_opsz24,
        TAB_SEARCH to R.drawable.search_24dp_e3e3e3_fill0_wght300_grad0_opsz24,
        TAB_PLAYLISTS to R.drawable.library_music_24dp_e3e3e3_fill0_wght300_grad0_opsz24__1_,
        TAB_ACCOUNT to R.drawable.person_24dp_e3e3e3_fill0_wght300_grad0_opsz24
    )
    items.forEach { (id, iconRes) ->
        val item = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            tag = id
            setOnClickListener { selectTab(id) }
        }
        val iconView = ImageView(this).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(SECONDARY)
            val p = dp(12)
            setPadding(p, p, p, p)
        }
        item.addView(iconView, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        bottomNav.addView(item, LinearLayout.LayoutParams(0, dp(52), 1f))
    }
    val params = FrameLayout.LayoutParams(-1, dp(56)).apply {
        gravity = Gravity.BOTTOM
    }
    root.addView(bottomNav, params)
    updateTabHighlight()
}

internal fun MainActivity.selectTab(tab: String) {
    if (similarPlaylistOverlay != null) closeSimilarPlaylistOverlay()
    currentScannedProfile = null
    inScannedPlaylistPreview = false
    currentTab = tab
    updateTabHighlight()
    contentPaddingNormal()
    swipeRefresh?.isEnabled = (tab == TAB_HOME)
    mainScroll.setOnScrollChangeListener(null)
    when (tab) {
        TAB_HOME -> showHome()
        TAB_SEARCH -> showSearchTab()
        TAB_PLAYLISTS -> showPlaylistsTab()
        TAB_ACCOUNT -> showProfile()
    }
    mainScroll.scrollTo(0, 0)
}

internal fun MainActivity.updateTabHighlight() {
    for (i in 0 until bottomNav.childCount) {
        val child = bottomNav.getChildAt(i) as View
        val active = (child.tag as? String) == currentTab
        val icon = (child as? FrameLayout)?.getChildAt(0) as? ImageView
        val color = if (active) TEXT else SECONDARY
        icon?.setColorFilter(color)
        child.alpha = if (active) 1f else 0.55f
    }
}
