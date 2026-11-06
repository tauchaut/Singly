package com.tauchaut.singly

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Outline
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.tauchaut.singly.accounts.loadActiveAccount
import com.tauchaut.singly.accounts.migrateToAccountsIfNeeded
import com.tauchaut.singly.cover.maybeTrimCoverDiskCache
import com.tauchaut.singly.data.loadPlaybackFlags
import com.tauchaut.singly.data.loadState
import com.tauchaut.singly.data.saveState
import com.tauchaut.singly.media.setupMediaSession
import com.tauchaut.singly.models.AccountProfile
import com.tauchaut.singly.models.DownloadJob
import com.tauchaut.singly.models.Playlist
import com.tauchaut.singly.models.PublishedProfile
import com.tauchaut.singly.models.Track
import com.tauchaut.singly.player.attachPlayerListener
import com.tauchaut.singly.player.restoreLastTrackForMiniPlayer
import com.tauchaut.singly.ui.TimeLabelView
import com.tauchaut.singly.ui.closeContextSheet
import com.tauchaut.singly.ui.closeSimilarPlaylistOverlay
import com.tauchaut.singly.ui.createUI
import com.tauchaut.singly.ui.handleAvatarCameraResult
import com.tauchaut.singly.ui.handleAvatarPermissionResult
import com.tauchaut.singly.ui.handleAvatarPickResult
import com.tauchaut.singly.ui.handleBoardExportResult
import com.tauchaut.singly.ui.handleBoardImportResult
import com.tauchaut.singly.ui.handleExportResult
import com.tauchaut.singly.ui.handleImportResult
import com.tauchaut.singly.ui.handleQrScanResult
import com.tauchaut.singly.ui.showHome
import com.tauchaut.singly.ui.showPlaylistsTab
import com.tauchaut.singly.ui.showProfile
import com.tauchaut.singly.ui.showScannedProfile
import com.tauchaut.singly.ui.showSearchTab
import com.tauchaut.singly.ui.updateMiniTimeLabel
import com.tauchaut.singly.ui.updateTabHighlight
import com.tauchaut.singly.util.dp
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.search.SearchExtractor
import androidx.core.graphics.toColorInt
import androidx.core.content.edit

class MainActivity : androidx.activity.ComponentActivity() {
    companion object {
        internal var sharedPlayer: ExoPlayer? = null
        internal var sharedIsPlaying: Boolean = false
        internal var sharedTrackId: String? = null
        internal val sharedDownloadJobs = ConcurrentHashMap<String, DownloadJob>()
    }
    internal var wakeLock: PowerManager.WakeLock? = null
    internal var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    internal var playbackServiceStarted = false
    internal val BG = "#040907".toColorInt()
    internal val CARD = "#303030".toColorInt()
    internal val CARD_ACTIVE = "#535353".toColorInt()
    internal val TEXT = "#ECECEC".toColorInt()
    internal val SECONDARY = "#747474".toColorInt()
    internal var mediaSession: android.support.v4.media.session.MediaSessionCompat? = null
    internal val NOTIFICATION_ID = 1001
    internal val CHANNEL_ID = "Singly_playback"
    internal lateinit var root: FrameLayout
    internal lateinit var content: LinearLayout
    internal lateinit var miniPlayer: LinearLayout
    internal lateinit var mainScroll: ScrollView
    internal var miniCover: ImageView? = null
    internal var miniTitle: TextView? = null
    internal var miniArtist: TextView? = null
    internal var miniProgress: ProgressBar? = null
    internal var fpPlayCircle: android.graphics.drawable.GradientDrawable? = null
    internal var fpPlayIcon: ImageView? = null
    internal var fpPlayButton: FrameLayout? = null
    internal var fpLoadingSpinner: View? = null
    internal var miniPlayButton: ImageView? = null
    internal val miniProgressHandler = Handler(Looper.getMainLooper())
    internal val imageCache: android.util.LruCache<String, Bitmap> by lazy {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
        object : android.util.LruCache<String, Bitmap>(maxKb) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    }
    internal var searchExtractor: SearchExtractor? = null
    internal var searchNextPage: Page? = null
    internal var searchLoading = false
    internal var currentSearchQuery = ""
    internal var searchResultsContainer: LinearLayout? = null
    internal var searchSuggestionsContainer: LinearLayout? = null
    internal val searchSuggestionHandler = Handler(Looper.getMainLooper())
    internal var currentTrack: Track? = null
    internal var currentPlaylist: MutableList<Track> = mutableListOf()
    internal var currentPlaylistName: String = "Queue"
    internal var repeatSimilar = false
    internal var mediaPlayer: ExoPlayer? = null
    @delegate:UnstableApi
    internal val httpDataSourceFactory by lazy {
        DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/120 Safari/537.36")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
    }
    internal var playbackRequestId = 0
    internal var isLoadingTrack = false
    internal val newPipeReady = CountDownLatch(1)
    internal var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout? = null
    internal var isPlaying = false
    internal var repeatTrack = false
    internal var repeatPlaylist = false
    internal var randomOrder = false
    internal var reverseOrder = false
    internal var shuffledOrder: MutableList<Track>? = null
    internal val recentlyPlayed = mutableMapOf<String, Long>()
    internal val COOLDOWN_MS = 3 * 60 * 60 * 1000L
    internal lateinit var bottomNav: LinearLayout
    internal var currentTab = "home"
    internal val TAB_HOME = "home"
    internal val TAB_SEARCH = "search"
    internal val TAB_PLAYLISTS = "playlists"
    internal val TAB_ACCOUNT = "account"
    internal var inSubScreen = false
    internal val recentTracks = mutableListOf<Track>()
    internal val downloadedTracks = mutableListOf<Track>()
    internal val likedTracks = mutableListOf<Track>()
    internal val playlists = mutableListOf<Playlist>()
    internal val recommendationHits = mutableMapOf<String, Int>()
    internal var lastRecommendationPlayStart = 0L
    internal var lastRecommendationTrackId: String? = null
    internal lateinit var prefs: SharedPreferences
    internal val tracks = mutableListOf<Track>()
    internal val pinnedPlaylistIds = mutableListOf<String>()
    internal lateinit var globalPrefs: SharedPreferences
    internal var currentAccount: AccountProfile? = null
    internal val avatarEmojis = listOf(
        "🎵","🎧","🎤","🎸","🎹","🎺","🥁","🎷",
        "🌙","⭐","🔥","💫","🎨","💿","📀","🎼",
        "🦊","🐺","🐻","🦁","🐸","🐙","🦄","👾"
    )
    internal val avatarColors = listOf(
        "#3B5BDB".toColorInt(), "#7048E8".toColorInt(),
        "#AE3EC9".toColorInt(), "#D6336C".toColorInt(),
        "#E8590C".toColorInt(), "#F08C00".toColorInt(),
        "#2B8A3E".toColorInt(), "#0C8599".toColorInt(),
        "#495057".toColorInt(), "#212529".toColorInt()
    )
    internal val QR_PREFIX = "SINGLY"
    internal var currentLeftPad = 24
    internal var currentRightPad = 24
    internal var programmaticTextChange = false
    internal var pendingCameraUri: Uri? = null
    internal var pendingBoardExport: String? = null
    internal val EXPORT_REQUEST = 2001
    internal val IMPORT_REQUEST = 2002
    internal val AVATAR_PICK_REQUEST = 2003
    internal val AVATAR_CAMERA_REQUEST = 2004
    internal val AVATAR_CAMERA_PERMISSION_REQUEST = 2005
    internal val BOARD_EXPORT_REQUEST = 2010
    internal val BOARD_IMPORT_REQUEST = 2011
    internal var stickyHeaderHost: FrameLayout? = null
    internal var stickyTopOffset = 0
    internal var contextSheetOverlay: View? = null
    internal var contextSheetContainer: FrameLayout? = null
    internal var currentPlaylistView: Playlist? = null
    internal var homeRecommendationContainer: LinearLayout? = null
    internal var homeRecommendationScrollLoading: TextView? = null
    internal var recommendationLoading = false
    internal val recommendationSeenIds = java.util.LinkedHashSet<String>()
    internal val recommendationPlaybackTracks = mutableListOf<Track>()
    internal var recommendationSeedIndex = 0
    internal var recommendationExtractor: SearchExtractor? = null
    internal var recommendationNextPage: Page? = null
    internal var recommendationGeneration = 0
    internal var recommendationFilterMode = "all"
    internal var homeRecommendationRefreshBtn: LinearLayout? = null
    internal val recommendationUsedSeedIds = mutableSetOf<String>()
    internal var recommendationEmptyStreak = 0
    internal val MAX_EMPTY_STREAK = 3
    internal var similarTracksContainer: LinearLayout? = null
    internal var similarTracksLoading: TextView? = null
    internal var similarTracksLoadingState = false
    internal val similarTracksSeenIds = mutableSetOf<String>()
    internal var similarTracksExtractor: SearchExtractor? = null
    internal var similarTracksNextPage: Page? = null
    internal val similarPlaybackTracks = mutableListOf<Track>()
    internal var fullPlayerOverlay: View? = null
    internal var lastNotificationTrackId: String? = null
    internal var lastNotificationState: Int = -1
    internal var lastNotificationTime = 0L
    internal var similarPlaylistSeedTrack: Track? = null
    internal var similarPlaylistOverlay: View? = null
    internal var similarPlaylistContainer: LinearLayout? = null
    internal val similarPlaylistTracks = mutableListOf<Track>()
    internal val similarPlaylistSeenIds = mutableSetOf<String>()
    internal var similarPlaylistExtractor: SearchExtractor? = null
    internal var similarPlaylistNextPage: Page? = null
    internal var similarPlaylistLoading = false
    internal var similarPlaylistTitle = ""
    internal var similarPlaylistSourceArtists = mutableListOf<String>()
    internal val coverExecutor by lazy {
        java.util.concurrent.Executors.newFixedThreadPool(8)
    }
    internal val coverDiskDir: File by lazy {
        File(filesDir, "covers").apply { if (!exists()) mkdirs() }
    }
    internal val inflightCoverRequests =
        ConcurrentHashMap<String, MutableList<(Bitmap?) -> Unit>>()
    internal val coverGradientCache = mutableMapOf<String, IntArray>()
    internal val notificationIconCache by lazy {
        object : android.util.LruCache<String, Bitmap>(24 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
        }
    }
    internal val notificationIconLoading = mutableSetOf<String>()
    internal val preloadingIds = mutableSetOf<String>()
    internal val likedIds = mutableSetOf<String>()
    internal val downloadedIds = mutableSetOf<String>()
    internal var currentSearchField: android.widget.EditText? = null
    internal var homeSuggestionBox: LinearLayout? = null
    internal var lastHighlightedTrackId: String? = null
    internal var lastHighlightedSource: List<Track>? = null
    internal var refreshingTrackList = false
    internal var activeTrackListContainer: LinearLayout? = null
    internal var activeTrackListSource: List<Track>? = null
    internal var activeTrackListPlaylist: Playlist? = null
    internal var activeTrackListName: String? = null
    internal var currentSourceKey: String = ""
    internal var fpLikeBtn: ImageView? = null
    internal var fpDownloadBtn: ImageView? = null
    internal var fpDownloadPercent: TextView? = null
    internal val downloadJobs get() = sharedDownloadJobs
    internal var editProfileName: String? = null
    internal var editProfileEmoji: String? = null
    internal var editProfileColor: Int? = null
    internal var currentScannedProfile: PublishedProfile? = null
    internal var inScannedPlaylistPreview = false
    internal var miniTimeLabel: TimeLabelView? = null
    internal var miniLikedIcon: ImageView? = null
    internal var miniDownloadedIcon: ImageView? = null
    internal val miniTimeHandler = Handler(Looper.getMainLooper())
    internal val miniTimeRunnable = object : Runnable {
        override fun run() {
            updateMiniTimeLabel()
            if (currentTrack != null && mediaPlayer?.isPlaying == true) {
                miniTimeHandler.postDelayed(this, 500)
            }
        }
    }
    internal var reorderStartScrollY = 0
    internal var reorderLastRawY = 0f
    internal var reorderAutoScrollRunnable: Runnable? = null
    internal val reorderEdgeZonePx by lazy { dp(90) }
    internal val reorderMaxStepPx by lazy { dp(14) }
    internal var reorderActive = false
    internal var reorderRow: View? = null
    internal var reorderContainer: LinearLayout? = null
    internal var reorderPlaylist: Playlist? = null
    internal var reorderSourceIndex = -1
    internal var reorderTargetIndex = -1
    internal var reorderStartRawY = 0f
    internal var reorderRowHeight = 0
    internal var reorderRows: List<View> = emptyList()
    internal var reorderOriginalTops = FloatArray(0)
    internal var reorderScreenTopAtStart = 0f
    internal var reorderTopLimitScreen = 0f
    internal var reorderBottomLimitScreen = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    this@MainActivity.handleBackPressed()
                }
            }
        )
        window.statusBarColor = BG
        window.navigationBarColor = BG
        window.decorView.systemUiVisibility = 0
        globalPrefs = getSharedPreferences("singly_global", MODE_PRIVATE)
        migrateToAccountsIfNeeded()
        loadActiveAccount()
        if (!prefs.getBoolean("queue_purged_v3", false)) {
            currentPlaylist = mutableListOf()
            currentPlaylistName = ""
            prefs.edit { putBoolean("queue_purged_v3", true) }
        }
        thread {
            try {
                NewPipe.init(com.tauchaut.singly.youtube.AppDownloader())
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                newPipeReady.countDown()
            }
        }
        loadState()
        loadPlaybackFlags()
        maybeTrimCoverDiskCache()
        tracks.forEach { it.streamUrl = "" }
        recentTracks.forEach { it.streamUrl = "" }
        likedTracks.forEach { it.streamUrl = "" }
        playlists.forEach { p -> p.tracks.forEach { it.streamUrl = "" } }
        ensureSystemPlaylists()
        setupMediaSession()
        if (sharedPlayer != null) {
            mediaPlayer = sharedPlayer
            sharedPlayer = null
            isPlaying = sharedIsPlaying
            playbackRequestId++
            attachPlayerListener(mediaPlayer!!)
            Log.d("Lifecycle", "Restored sharedPlayer, isPlaying=$isPlaying")
        }
        createUI()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top,
                view.paddingRight,
                systemBars.bottom
            )
            insets
        }
        restoreLastTrackForMiniPlayer()
        showHome()
    }

    override fun onDestroy() {
        miniTimeHandler.removeCallbacksAndMessages(null)
        if (mediaPlayer?.isPlaying == true || isPlaying) {
            sharedPlayer = mediaPlayer
            sharedIsPlaying = isPlaying
            sharedTrackId = currentTrack?.id
            mediaPlayer = null
            Log.d("Lifecycle", "Player preserved in sharedPlayer")
        } else {
            mediaPlayer?.release()
            mediaPlayer = null
        }
        saveState()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }



    internal fun handleBackPressed() {
        if (contextSheetOverlay != null) {
            closeContextSheet()
            return
        }
        if (similarPlaylistOverlay != null) {
            closeSimilarPlaylistOverlay()
            return
        }
        val overlay = fullPlayerOverlay
        if (overlay != null) {
            root.removeView(overlay)
            fullPlayerOverlay = null
            return
        }
        if (inScannedPlaylistPreview && currentScannedProfile != null) {
            inScannedPlaylistPreview = false
            showScannedProfile(currentScannedProfile!!)
            mainScroll.scrollTo(0, 0)
            return
        }
        if (inSubScreen) {
            inSubScreen = false
            when (currentTab) {
                TAB_PLAYLISTS -> showPlaylistsTab()
                TAB_ACCOUNT -> showProfile()
                TAB_HOME -> showHome()
                TAB_SEARCH -> showSearchTab()
            }
            mainScroll.scrollTo(0, 0)
            return
        }
        if (currentTab != TAB_HOME) {
            currentTab = TAB_HOME
            updateTabHighlight()
            showHome()
            mainScroll.scrollTo(0, 0)
            return
        }
        finishAfterTransition()
    }

    @Deprecated("Deprecated in API 33, kept for backwards compat")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (handleQrScanResult(requestCode, resultCode, data)) return
        if (handleAvatarCameraResult(requestCode, resultCode)) return
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (handleBoardImportResult(requestCode, uri)) return
        if (handleBoardExportResult(requestCode, uri)) return
        if (handleAvatarPickResult(requestCode, uri)) return
        if (handleExportResult(requestCode, uri)) return
        if (handleImportResult(requestCode, uri)) return
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        handleAvatarPermissionResult(requestCode, grantResults)
    }

    internal fun ensureSystemPlaylists() {
        fun ensure(id: String, name: String, list: MutableList<Track>?) {
            val existing = playlists.find { it.id == id }
            if (existing == null) {
                playlists.add(Playlist(id, name, list ?: mutableListOf()))
            } else if (list != null && existing.tracks !== list && existing.tracks.isEmpty()) {
                existing.tracks = list
            }
        }
        ensure("recent", "Recent tracks", recentTracks)
        ensure("liked", "Liked", likedTracks)
        ensure("listen_later", "Listen later", null)
        ensure("downloads", "Downloads", downloadedTracks)
    }

    internal fun makeUniquePlaylistName(base: String): String {
        val trimmed = base.trim().ifEmpty { "Плейлист" }
        val existing = playlists.map { it.name }.toSet()
        if (trimmed !in existing) return trimmed
        var n = 1
        while ("$trimmed ($n)" in existing) n++
        return "$trimmed ($n)"
    }

    internal fun isPinned(id: String) = id in pinnedPlaylistIds

    class TrackRowView(context: Context) : LinearLayout(context) {
        private val cover = ImageView(context)
        private val title = TextView(context)
        private val artist = TextView(context)
        private val metaRow = LinearLayout(context)
        private val duration = TextView(context)
        private val likedIcon = ImageView(context)
        private val downloadedIcon = ImageView(context)
        private val menu = ImageView(context)

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(4), dp(8))
            cover.scaleType = ImageView.ScaleType.CENTER_CROP
            cover.clipToOutline = true
            cover.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(10).toFloat())
                }
            }
            addView(cover, LayoutParams(dp(62), dp(62)))
            val textCol = LinearLayout(context).apply { orientation = VERTICAL }
            title.apply {
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            artist.apply {
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            textCol.addView(title, LayoutParams(-1, -2))
            textCol.addView(artist, LayoutParams(-1, -2))
            metaRow.orientation = HORIZONTAL
            metaRow.gravity = Gravity.CENTER_VERTICAL
            duration.apply { textSize = 12f; setPadding(0, 0, 0, 0) }
            likedIcon.setImageResource(R.drawable.favorite_filled_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
            likedIcon.scaleType = ImageView.ScaleType.CENTER_INSIDE
            downloadedIcon.setImageResource(R.drawable.download_done_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
            downloadedIcon.scaleType = ImageView.ScaleType.CENTER_INSIDE
            metaRow.addView(duration)
            metaRow.addView(likedIcon, LayoutParams(dp(16), dp(16)).apply { marginStart = dp(8) })
            metaRow.addView(downloadedIcon, LayoutParams(dp(16), dp(16)).apply { marginStart = dp(6) })
            textCol.addView(metaRow, LayoutParams(-2, -2).apply { topMargin = dp(3) })
            addView(textCol, LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
            menu.setImageResource(R.drawable.more_vert_24dp_e3e3e3_fill0_wght300_grad0_opsz24)
            menu.scaleType = ImageView.ScaleType.CENTER_INSIDE
            addView(menu, LayoutParams(dp(36), dp(62)))
        }

    }

}
