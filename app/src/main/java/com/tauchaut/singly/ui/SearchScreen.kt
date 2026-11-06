package com.tauchaut.singly.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.tauchaut.singly.MainActivity
import com.tauchaut.singly.R
import com.tauchaut.singly.data.persistSearchHistory
import com.tauchaut.singly.data.removeSearchQuery
import com.tauchaut.singly.models.Track
import com.tauchaut.singly.youtube.searchYouTube
import com.tauchaut.singly.util.dp
import com.tauchaut.singly.util.formatSearchTime
import com.tauchaut.singly.util.roundedDrawable
import com.tauchaut.singly.youtube.fetchSearchSuggestions
import kotlin.collections.forEach
import androidx.core.graphics.toColorInt
import com.tauchaut.singly.data.getSearchHistory
import com.tauchaut.singly.data.loadSearchHistory
import com.tauchaut.singly.data.rememberTrack
import com.tauchaut.singly.data.saveSearchQuery

@SuppressLint("SetTextI18n")
internal fun MainActivity.showSearchTab(initialQuery: String = "") {
    inSubScreen = false
    currentPlaylistView = null
    contentPaddingDense()
    content.removeAllViews()
    searchExtractor = null
    searchNextPage = null
    searchLoading = false
    currentSearchQuery = initialQuery
    hideStickyHeader()
    activeTrackListContainer = null
    activeTrackListSource = null
    activeTrackListPlaylist = null
    activeTrackListName = null
    hideAllSuggestionBoxes()
    val searchRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val searchField = EditText(this)
    configureSearchField(searchField, "Search YouTube...")
    searchField.setText(initialQuery)
    searchField.setSelection(initialQuery.length)
    currentSearchField = searchField
    searchField.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
    searchField.inputType = android.text.InputType.TYPE_CLASS_TEXT
    searchRow.addView(searchField, LinearLayout.LayoutParams(0, dp(44), 1f))
    val goButton = ImageView(this).apply {
        setImageResource(R.drawable.search_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setColorFilter(TEXT)
        val p = dp(10)
        setPadding(p, p, p, p)
        background = roundedDrawable(CARD, 22)
        setOnClickListener {
            val q = searchField.text.toString().trim()
            if (q.isEmpty()) {
                Toast.makeText(this@showSearchTab, "Query is empty", Toast.LENGTH_SHORT).show()
            } else {
                hideKeyboard(searchField)
                performSearchInTab(q)
            }
        }
    }
    searchRow.addView(goButton, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
        marginStart = dp(10)
    })
    content.addView(searchRow, LinearLayout.LayoutParams(-1, -2).apply {
        marginStart = dp(8)
        marginEnd   = dp(8)
    })
    val suggestions = LinearLayout(this)
    suggestions.orientation = LinearLayout.VERTICAL
    suggestions.visibility = View.GONE
    searchSuggestionsContainer = suggestions
    content.addView(suggestions)
    searchField.setOnEditorActionListener { _, actionId, event ->
        val isEnterAction =
            actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                    actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                    actionId == android.view.inputmethod.EditorInfo.IME_NULL ||
                    (event != null &&
                            event.keyCode == android.view.KeyEvent.KEYCODE_ENTER &&
                            event.action == android.view.KeyEvent.ACTION_DOWN)
        if (isEnterAction) {
            val q = searchField.text.toString().trim()
            if (q.isNotEmpty()) {
                hideKeyboard(searchField)
                performSearchInTab(q)
            } else {
                Toast.makeText(this@showSearchTab, "Query is empty", Toast.LENGTH_SHORT).show()
            }
            true
        } else false
    }
    searchField.setOnKeyListener { _, keyCode, event ->
        if (keyCode == android.view.KeyEvent.KEYCODE_ENTER &&
            event.action == android.view.KeyEvent.ACTION_DOWN) {
            val q = searchField.text.toString().trim()
            if (q.isNotEmpty()) {
                hideKeyboard(searchField)
                performSearchInTab(q)
            }
            true
        } else false
    }
    searchField.addTextChangedListener(SimpleTextWatcher { value ->
        if (programmaticTextChange) return@SimpleTextWatcher
        scheduleSuggestions(value)
    })
    searchField.setOnFocusChangeListener { _, hasFocus ->
        if (hasFocus && searchField.text.isNullOrEmpty()) {
            scheduleSuggestions("")
        }
    }
    val resultsContainer = LinearLayout(this)
    resultsContainer.orientation = LinearLayout.VERTICAL
    searchResultsContainer = resultsContainer
    content.addView(
        resultsContainer,
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
    )
    if (initialQuery.isBlank()) {
        val hintBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(60), 0, dp(20))
        }
        hintBox.addView(TextView(this).apply {
            text = "⌕"
            textSize = 64f
            setTextColor(SECONDARY)
            gravity = Gravity.CENTER
        })
        hintBox.addView(TextView(this).apply {
            text = "Find your music"
            textSize = 17f
            setTextColor(TEXT)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        })
        hintBox.addView(TextView(this).apply {
            text = "Input track title or author name"
            textSize = 13f
            setTextColor(SECONDARY)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        })
        resultsContainer.addView(hintBox)
        searchField.requestFocus()
    } else {
        performSearchInTab(initialQuery)
    }
    mainScroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
        val child = mainScroll.getChildAt(0)
        if (!searchLoading && searchNextPage != null && child != null &&
            scrollY + mainScroll.height >= child.measuredHeight - dp(500)) {
            loadNextSearchPage()
        }
    }
}

@SuppressLint("SetTextI18n")
internal fun MainActivity.performSearchInTab(query: String) {
    hideAllSuggestionBoxes()
    val q = query.trim()
    if (q.isEmpty()) return
    saveSearchQuery(q)
    currentSearchQuery = q
    searchExtractor = null
    searchNextPage = null
    searchLoading = false
    val container = searchResultsContainer ?: return
    container.removeAllViews()
    val loading = TextView(this).apply {
        text = "Search YouTube..."
        setTextColor(SECONDARY)
        textSize = 15f
        setPadding(32, dp(10), 32, dp(10))
    }
    container.addView(loading)
    searchYouTube(q) { results, next ->
        container.removeView(loading)
        searchNextPage = next
        if (results.isEmpty()) {
            addTextTo(container, "Nothing found", SECONDARY, 15f)
        } else {
            appendSearchResults(results)
        }
    }
}

@SuppressLint("SetTextI18n")
internal fun MainActivity.loadNextSearchPage() {
    val page = searchNextPage ?: return
    if (searchLoading || currentSearchQuery.isBlank()) return
    searchLoading = true
    val container = searchResultsContainer ?: return
    val loading = TextView(this).apply {
        text = "Loading more..."
        setTextColor(SECONDARY)
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(0, dp(16), 0, dp(16))
    }
    container.addView(loading)
    searchYouTube(currentSearchQuery, page) { results, next ->
        container.removeView(loading)
        searchNextPage = next
        searchLoading = false
        appendSearchResults(results)
    }
}

internal fun MainActivity.appendSearchResults(results: List<Track>) {
    val container = searchResultsContainer ?: return
    results.forEach { track ->
        rememberTrack(track)
        val canonical = tracks.first { it.id == track.id }
        val single = mutableListOf(canonical)
        addTrackRowTo(
            container,
            canonical,
            playbackPlaylist = single,
            playbackPlaylistName = "Search"
        )
    }
}

internal fun MainActivity.configureSearchField(search: EditText, hint: String) {
    search.hint = hint
    search.setHintTextColor("#9A9A9A".toColorInt())
    search.setTextColor(TEXT)
    search.textSize = 15f
    search.isSingleLine = true
    search.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
    search.inputType = android.text.InputType.TYPE_CLASS_TEXT
    search.setPadding(dp(18), 0, dp(18), 0)
    search.background = roundedDrawable("#1E1E1E".toColorInt(), 22)
}

@SuppressLint("SetTextI18n")
internal fun MainActivity.buildShowFullHistoryButton(): View {
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(14), dp(8), dp(14))
        isClickable = true
        isFocusable = true
        addView(ImageView(this@buildShowFullHistoryButton).apply {
            setImageResource(R.drawable.history_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(SECONDARY)
        }, LinearLayout.LayoutParams(dp(14), dp(14)))
        addView(TextView(this@buildShowFullHistoryButton).apply {
            text = "Show all history"
            textSize = 13f
            setTextColor(SECONDARY)
            setPadding(dp(6), 0, 0, 0)
        })
        setOnClickListener {
            hideAllSuggestionBoxes()
            currentSearchField?.let { hideKeyboard(it) }
            showFullSearchHistory()
        }
    }
}

@SuppressLint("SetTextI18n")
internal fun MainActivity.showFullSearchHistory() {
    inSubScreen = true
    currentPlaylistView = null
    content.removeAllViews()
    contentPaddingDense()
    hideStickyHeader()
    hideAllSuggestionBoxes()
    activeTrackListContainer = null
    activeTrackListSource = null
    activeTrackListPlaylist = null
    activeTrackListName = null
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
        setBackgroundColor(BG)
    }
    val backBtn = ImageView(this).apply {
        setImageResource(R.drawable.arrow_back_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setColorFilter(TEXT)
        val p = dp(10); setPadding(p, p, p, p)
        setOnClickListener { showSearchTab() }
    }
    header.addView(backBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
    header.addView(TextView(this).apply {
        text = "Search history"
        textSize = 20f
        setTextColor(TEXT)
        setTypeface(null, Typeface.BOLD)
    }, LinearLayout.LayoutParams(0, -2, 1f))
    header.addView(TextView(this).apply {
        text = "Clear"
        textSize = 13f
        setTextColor(SECONDARY)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setOnClickListener {
            AlertDialog.Builder(this@showFullSearchHistory)
                .setTitle("Clear search history?")
                .setMessage("All queries will be cleared")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ ->
                    persistSearchHistory(emptyList())
                    showFullSearchHistory()
                }
                .show()
        }
    }, LinearLayout.LayoutParams(-2, -2))
    showStickyHeader(header)
    val history = loadSearchHistory()
    if (history.isEmpty()) {
        addText("История пуста", SECONDARY, 15f)
        return
    }
    history.forEach { entry ->
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(4), dp(10))
            isClickable = true
            isFocusable = true
        }
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.history_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(SECONDARY)
        }, LinearLayout.LayoutParams(dp(18), dp(18)))
        val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textCol.addView(TextView(this).apply {
            text = entry.query
            textSize = 15f
            setTextColor(TEXT)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-1, -2))
        textCol.addView(TextView(this).apply {
            text = formatSearchTime(entry.timestamp)
            textSize = 11f
            setTextColor(SECONDARY)
            setPadding(0, dp(2), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
        row.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12)
        })
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.close_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(SECONDARY)
            val p = dp(6); setPadding(p, p, p, p)
            setOnClickListener {
                val cur = loadSearchHistory()
                cur.removeAll { it.query.equals(entry.query, true) }
                persistSearchHistory(cur)
                showFullSearchHistory()
            }
        }, LinearLayout.LayoutParams(dp(32), dp(32)))
        row.setOnClickListener {
            currentTab = TAB_SEARCH
            updateTabHighlight()
            showSearchTab(entry.query)
        }
        content.addView(row)
    }
}

@SuppressLint("SetTextI18n")
internal fun MainActivity.scheduleSuggestions(value: String) {
    if (programmaticTextChange) return
    searchSuggestionHandler.removeCallbacksAndMessages(null)
    val q = value.trim()
    val box = searchSuggestionsContainer ?: return
    if (q.isEmpty()) {
        val allHistory = loadSearchHistory()
        if (allHistory.isEmpty()) { box.visibility = View.GONE; return }
        box.removeAllViews()
        box.addView(TextView(this).apply {
            text = "Recent queries"
            textSize = 12f
            setTextColor(SECONDARY)
            setPadding(dp(8), dp(8), 8, dp(4))
        })
        allHistory.take(10).forEach { buildHistoryRow(box, it.query) }
        if (allHistory.size > 10) {
            box.addView(buildShowFullHistoryButton())
        }
        box.visibility = View.VISIBLE
        return
    }
    if (q.length < 2) { box.visibility = View.GONE; return }
    val historyMatches = getSearchHistory()
        .filter { it.contains(q, ignoreCase = true) }
        .take(4)
    searchSuggestionHandler.postDelayed({
        fetchSearchSuggestions(q) { suggestions ->
            box.removeAllViews()
            val filtered = suggestions.filter { s ->
                historyMatches.none { it.equals(s, ignoreCase = true) }
            }
            if (historyMatches.isEmpty() && filtered.isEmpty()) {
                box.visibility = View.GONE
                return@fetchSearchSuggestions
            }
            if (historyMatches.isNotEmpty()) {
                box.addView(TextView(this).apply {
                    text = "Recent queries"
                    textSize = 12f
                    setTextColor(SECONDARY)
                    setPadding(dp(8), dp(8), 8, dp(4))
                })
                historyMatches.forEach { buildHistoryRow(box, it) }
            }
            if (filtered.isNotEmpty()) {
                if (historyMatches.isNotEmpty()) {
                    box.addView(TextView(this).apply {
                        text = "Hints"
                        textSize = 12f
                        setTextColor(SECONDARY)
                        setPadding(dp(8), dp(8), 8, dp(4))
                    })
                }
                filtered.forEach { suggestion ->
                    box.addView(TextView(this).apply {
                        text = "⌕  $suggestion"
                        textSize = 15f
                        setTextColor(TEXT)
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(8), 0, dp(8), 0)
                        setOnClickListener {
                            hideAllSuggestionBoxes()
                            currentSearchField?.let {
                                it.setText(suggestion)
                                it.setSelection(suggestion.length)
                            }
                            hideKeyboard(currentSearchField ?: box)
                            if (currentTab == TAB_SEARCH) performSearchInTab(suggestion)
                            else {
                                currentTab = TAB_SEARCH
                                updateTabHighlight()
                                showSearchTab(suggestion)
                            }
                        }
                    }, LinearLayout.LayoutParams(-1, dp(44)))
                }
            }
            box.visibility = View.VISIBLE
        }
    }, 300)
}

internal fun MainActivity.buildHistoryRow(box: LinearLayout, query: String) {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
    }
    val histIcon = ImageView(this).apply {
        setImageResource(R.drawable.history_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setColorFilter(SECONDARY)
    }
    row.addView(histIcon, LinearLayout.LayoutParams(dp(18), dp(18)))
    val text = TextView(this).apply {
        text = query
        textSize = 15f
        setTextColor(TEXT)
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(dp(12), 0, dp(8), 0)
        setOnClickListener {
            hideAllSuggestionBoxes()
            programmaticTextChange = true
            currentSearchField?.let {
                it.setText(query)
                it.setSelection(query.length)
            }
            programmaticTextChange = false
            hideKeyboard(currentSearchField ?: box)
            if (currentTab == TAB_SEARCH) {
                performSearchInTab(query)
            } else {
                currentTab = TAB_SEARCH
                updateTabHighlight()
                showSearchTab(query)
            }
        }
    }
    row.addView(text, LinearLayout.LayoutParams(0, -1, 1f))
    val closeIcon = ImageView(this).apply {
        setImageResource(R.drawable.close_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setColorFilter(SECONDARY)
        val p = dp(4)
        setPadding(p, p, p, p)
        setOnClickListener {
            removeSearchQuery(query)
            scheduleSuggestions(currentSearchField?.text?.toString() ?: "")
        }
    }
    row.addView(closeIcon, LinearLayout.LayoutParams(dp(26), dp(26)))
    box.addView(row, LinearLayout.LayoutParams(-1, dp(44)))
}

internal fun MainActivity.hideAllSuggestionBoxes() {
    searchSuggestionsContainer?.visibility = View.GONE
    searchSuggestionsContainer?.removeAllViews()
    searchSuggestionHandler.removeCallbacksAndMessages(null)
    homeSuggestionBox?.visibility = View.GONE
    homeSuggestionBox?.removeAllViews()
}

internal fun MainActivity.hideKeyboard(view: View) {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager
    imm.hideSoftInputFromWindow(view.windowToken, 0)
    view.clearFocus()
}
