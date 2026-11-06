package com.tauchaut.singly.ui

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.R
import com.tauchaut.singly.recommendations.loadMoreHomeRecommendations
import com.tauchaut.singly.recommendations.setupHomeRecommendations
import com.tauchaut.singly.recommendations.showRecommendationFilterMenu
import com.tauchaut.singly.util.dp
import androidx.core.view.isEmpty

@SuppressLint("SetTextI18n")
internal fun MainActivity.showHome() {
    inSubScreen = false
    contentPaddingDense()
    currentPlaylistView = null
    hideAllSuggestionBoxes()
    activeTrackListContainer = null
    activeTrackListSource = null
    activeTrackListPlaylist = null
    activeTrackListName = null
    hideStickyHeader()
    val existingContainer = homeRecommendationContainer
    existingContainer?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
    content.removeAllViews()
    searchExtractor = null
    searchNextPage = null
    currentSearchQuery = ""
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val search = EditText(this)
    configureSearchField(search, "Search YouTube...")
    header.addView(search, LinearLayout.LayoutParams(0, dp(44), 1f))
    content.addView(header, LinearLayout.LayoutParams(-1, -2).apply {
        marginStart = dp(8)
        marginEnd   = dp(8)
    })
    val homeSuggestions = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
    }
    homeSuggestionBox = homeSuggestions
    searchSuggestionsContainer = homeSuggestions
    content.addView(homeSuggestions)
    search.setOnEditorActionListener { _, _, _ ->
        val q = search.text.toString().trim()
        if (q.isNotEmpty()) {
            currentTab = TAB_SEARCH
            updateTabHighlight()
            showSearchTab(q)
        }
        true
    }
    search.addTextChangedListener(SimpleTextWatcher { value ->
        scheduleSuggestions(value)
    })
    val pinned = playlists.filter { isPinned(it.id) }
    if (pinned.isNotEmpty()) {
        content.addView(TextView(this).apply {
            text = "Pinned"
            textSize = 20f
            setTextColor(TEXT)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(8), dp(22), dp(8), dp(12))
        })
        val gap = dp(14)
        val perRow = 2
        val horizontalMargins = dp(8) * 2
        val cellWidth = (resources.displayMetrics.widthPixels
                - content.paddingLeft - content.paddingRight
                - horizontalMargins
                - gap * (perRow - 1)) / perRow
        val cardHeight = cellWidth + dp(48)
        for (i in pinned.indices step perRow) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (j in 0 until perRow) {
                val index = i + j
                val lp = LinearLayout.LayoutParams(0, cardHeight, 1f).apply {
                    if (j < perRow - 1) marginEnd = gap
                }
                if (index < pinned.size) {
                    row.addView(playlistGridCard(pinned[index], cellWidth), lp)
                } else {
                    row.addView(View(this), lp)
                }
            }
            content.addView(row, LinearLayout.LayoutParams(-1, cardHeight).apply {
                marginStart = dp(8)
                marginEnd = dp(8)
                bottomMargin = dp(8)
            })
        }
    }
    val recommendationHeader = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val recommendationTitle = TextView(this).apply {
        text = "Recommendations"
        textSize = 20f
        setTextColor(TEXT)
        setTypeface(null, Typeface.BOLD)
        setPadding(24, dp(22), 24, dp(12))
    }
    recommendationHeader.addView(recommendationTitle, LinearLayout.LayoutParams(0, -2, 1f))
    val recFilterBtn = ImageView(this).apply {
        setImageResource(R.drawable.menu_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setColorFilter(TEXT)
        setOnClickListener { showRecommendationFilterMenu() }
    }
    recommendationHeader.addView(recFilterBtn, LinearLayout.LayoutParams(dp(42), dp(42)))
    content.addView(recommendationHeader)
    val recContainer = existingContainer ?: LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }
    homeRecommendationContainer = recContainer
    content.addView(recContainer, LinearLayout.LayoutParams(-1, -2))
    homeRecommendationScrollLoading = TextView(this).apply {
        text = "Loading recommendations..."
        textSize = 13f
        setTextColor(SECONDARY)
        gravity = Gravity.CENTER
        setPadding(0, dp(16), 0, dp(16))
        visibility = View.GONE
    }
    content.addView(homeRecommendationScrollLoading)
    homeRecommendationRefreshBtn = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(16), dp(12), dp(16))
        visibility = View.GONE
        isClickable = true
        isFocusable = true
        val icon = ImageView(this@showHome).apply {
            setImageResource(R.drawable.update_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(SECONDARY)
        }
        addView(icon, LinearLayout.LayoutParams(dp(18), dp(18)))
        addView(TextView(this@showHome).apply {
            text = "Update recommendations"
            textSize = 13f
            setTextColor(SECONDARY)
            setPadding(dp(8), 0, 0, 0)
        })
        setOnClickListener {
            homeRecommendationContainer?.let {
                (it.parent as? android.view.ViewGroup)?.removeView(it)
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
            mainScroll.post { mainScroll.scrollTo(0, 0) }
        }
    }
    content.addView(homeRecommendationRefreshBtn)
    attachRecommendationScrollListener()
    if (recContainer.isEmpty()) {
        setupHomeRecommendations()
    }
}

internal fun MainActivity.attachRecommendationScrollListener() {
    mainScroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
        if (currentTab != TAB_HOME) return@setOnScrollChangeListener
        val child = mainScroll.getChildAt(0) ?: return@setOnScrollChangeListener
        if (child.measuredHeight <= 0) return@setOnScrollChangeListener
        if (recommendationLoading) return@setOnScrollChangeListener
        if (scrollY + mainScroll.height >= child.measuredHeight - dp(600)) {
            loadMoreHomeRecommendations()
        }
    }
}
