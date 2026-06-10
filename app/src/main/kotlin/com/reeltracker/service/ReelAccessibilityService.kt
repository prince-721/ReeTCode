package com.reeltracker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.reeltracker.AppContainer
import com.reeltracker.ReelEventBus
import kotlinx.coroutines.*
import com.reeltracker.data.FocusedModeRepository
import com.reeltracker.ui.screens.FocusedModeInfoActivity

class ReelAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastScrollTime = 0L
    private var lastScrolledPackage = ""
    private var currentPackage = ""
    
    @Volatile
    private var isBlocked = false

    @Volatile
    private var activeFocusMode: com.reeltracker.data.entities.FocusMode? = null

    @Volatile
    private var isBlockingScreenVisible = false

    private lateinit var focusedModeRepository: FocusedModeRepository

    // Debounce: minimum ms between counting one reel scroll
    private val SCROLL_DEBOUNCE_MS = 600L

    // Reel/Shorts section identifiers
    private val INSTAGRAM_REELS_IDS = setOf(
        "clips_viewer_grid_view",
        "clips_viewer_container",
        "clips_viewer",
        "reel_viewer_fragment",
        "reel_viewer_root",
        "clips_recycler_view",
        "reels_viewer",
        "clips_tab_fragment"
    )

    private val YOUTUBE_SHORTS_IDS = setOf(
        "shorts_container",
        "reel_recycler_view",
        "shorts_camera_roll_picker",
        "reel_player_page",
        "reel_fragment_camera_roll_bottom_sheet",
        "shorts_pivot_item"
    )

    private val SNAPCHAT_STORIES_IDS = setOf(
        "stories_feed_recycler_view",
        "spotlight_container",
        "snap_view",
        "story_view",
        "spotlight_view",
        "stories_recycler",
        "discover_feed"
    )

    private val blockScreenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_BLOCK_SCREEN_SHOWN -> {
                    isBlockingScreenVisible = true
                    Log.d(TAG, "Block screen is now visible")
                }
                ACTION_BLOCK_SCREEN_HIDDEN -> {
                    isBlockingScreenVisible = false
                    Log.d(TAG, "Block screen is now hidden")
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
            packageNames = arrayOf(
                PACKAGE_INSTAGRAM,
                PACKAGE_SNAPCHAT,
                PACKAGE_YOUTUBE
            )
        }
        serviceInfo = info
        Log.d(TAG, "ReelAccessibilityService connected")

        focusedModeRepository = FocusedModeRepository(this)

        val filter = IntentFilter().apply {
            addAction(ACTION_BLOCK_SCREEN_SHOWN)
            addAction(ACTION_BLOCK_SCREEN_HIDDEN)
        }
        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        registerReceiver(blockScreenReceiver, filter, receiverFlags)

        // Initialize Room Database and observe active focus mode
        val db = com.reeltracker.data.database.ReelTrackerDatabase.getDatabase(this)
        scope.launch {
            try {
                db.focusModeDao().getActiveFocusModesFlow().collect { modes ->
                    activeFocusMode = modes.firstOrNull()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error observing active focus mode", e)
            }
        }
        // Observe active block session
        scope.launch {
            try {
                AppContainer.repository.observeActiveBlock().collect { block ->
                    isBlocked = block != null
                    if (isBlocked && isCurrentPackageBlocked(currentPackage)) {
                        if (!isBlockingScreenVisible) {
                            showBlockingOverlay()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error observing active block", e)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return

        // When the block screen is already showing, ignore all further events from blocked apps
        if (isBlockingScreenVisible) {
            if (isCurrentPackageBlocked(packageName)) {
                return
            }
        }

        // Intercept and block if screen time limit or Focus Mode is active
        val isAppBlocked = isBlocked && isCurrentPackageBlocked(packageName)
        val isFocusBlocked = isFocusModeActive(packageName)

        if (isAppBlocked || isFocusBlocked) {
            if (!isBlockingScreenVisible) {
                isBlockingScreenVisible = true
                showBlockingOverlay()
            }
            return
        }

        // Update current foreground package
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentPackage = packageName
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> handleScrollEvent(event, packageName)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleContentChange(event, packageName)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowStateChange(event, packageName)
        }
    }

    private fun handleScrollEvent(event: AccessibilityEvent, packageName: String) {
        val now = System.currentTimeMillis()

        // Debounce: don't count the same scroll twice quickly
        if (packageName == lastScrolledPackage && now - lastScrollTime < SCROLL_DEBOUNCE_MS) {
            return
        }

        // Only count if it's a vertical scroll (reels are vertical)
        val isVerticalScroll = isVerticalScrollEvent(event)
        if (!isVerticalScroll) return

        val isInReelSection = when (packageName) {
            PACKAGE_INSTAGRAM -> isInInstagramReels(event)
            PACKAGE_SNAPCHAT -> isInSnapchatStories(event)
            PACKAGE_YOUTUBE -> isInYoutubeShorts(event)
            else -> false
        }

        if (isInReelSection) {
            lastScrollTime = now
            lastScrolledPackage = packageName
            notifyScrollDetected(packageName)
        }
    }

    private fun handleContentChange(event: AccessibilityEvent, packageName: String) {
        // Additional detection for content changes in reel sections
        val source = event.source ?: return
        val viewId = source.viewIdResourceName ?: return

        val isReelContent = when (packageName) {
            PACKAGE_INSTAGRAM -> INSTAGRAM_REELS_IDS.any { viewId.contains(it) }
            PACKAGE_YOUTUBE -> YOUTUBE_SHORTS_IDS.any { viewId.contains(it) }
            PACKAGE_SNAPCHAT -> SNAPCHAT_STORIES_IDS.any { viewId.contains(it) }
            else -> false
        }

        source.recycle()

        if (isReelContent) {
            val now = System.currentTimeMillis()
            if (packageName == lastScrolledPackage && now - lastScrollTime < SCROLL_DEBOUNCE_MS * 2) {
                return
            }
            // Content changed in reel section — likely a reel transition
            // Only trigger if we haven't just triggered from scroll
            if (now - lastScrollTime > SCROLL_DEBOUNCE_MS * 3) {
                lastScrollTime = now
                lastScrolledPackage = packageName
                notifyScrollDetected(packageName)
            }
        }
    }

    private fun handleWindowStateChange(event: AccessibilityEvent, packageName: String) {
        // Detect when user enters YouTube Shorts tab
        if (packageName == PACKAGE_YOUTUBE) {
            val className = event.className?.toString() ?: return
            if (className.contains("ShortsActivity") || className.contains("ReelWatchFragment")) {
                // User just entered Shorts — don't count yet, wait for first scroll
            }
        }
    }

    private fun isVerticalScrollEvent(event: AccessibilityEvent): Boolean {
        // ScrollY changing indicates vertical scroll, which is how reels work
        val scrollY = event.scrollY
        val maxScrollY = event.maxScrollY
        return maxScrollY > 0 || scrollY > 0
    }

    private fun isInInstagramReels(event: AccessibilityEvent): Boolean {
        val source = event.source ?: return false
        val result = isNodeInReelSection(source, INSTAGRAM_REELS_IDS)
        source.recycle()
        return result
    }

    private fun isInSnapchatStories(event: AccessibilityEvent): Boolean {
        val source = event.source ?: return false
        val result = isNodeInReelSection(source, SNAPCHAT_STORIES_IDS)
        source.recycle()
        return result
    }

    private fun isInYoutubeShorts(event: AccessibilityEvent): Boolean {
        val source = event.source ?: return false
        val result = isNodeInReelSection(source, YOUTUBE_SHORTS_IDS)

        // Also check if the window class is Shorts-related
        val className = event.className?.toString() ?: ""
        val isShortsClass = className.contains("Shorts", ignoreCase = true) ||
                className.contains("Reel", ignoreCase = true)

        source.recycle()
        return result || isShortsClass
    }

    private fun isNodeInReelSection(node: AccessibilityNodeInfo, targetIds: Set<String>): Boolean {
        val viewId = node.viewIdResourceName ?: ""
        if (targetIds.any { viewId.contains(it) }) return true

        // Check ancestors (up to 5 levels)
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            val parentId = parent.viewIdResourceName ?: ""
            if (targetIds.any { parentId.contains(it) }) {
                parent.recycle()
                return true
            }
            val grandParent = parent.parent
            parent.recycle()
            parent = grandParent
            depth++
        }
        return false
    }

    private fun notifyScrollDetected(packageName: String) {
        Log.d(TAG, "Scroll detected for package: $packageName")
        scope.launch {
            ReelEventBus.emitReelDetected(packageName)
        }
    }

    private fun isCurrentPackageBlocked(pkg: String): Boolean {
        return pkg == PACKAGE_INSTAGRAM ||
                pkg == PACKAGE_SNAPCHAT ||
                pkg == PACKAGE_YOUTUBE
    }

    private fun isFocusModeActive(packageName: String): Boolean {
        // 1. Check SharedPreferences Focus Session
        val isFocusedShared = focusedModeRepository.isFocused()
        if (isFocusedShared) {
            val blockedApps = focusedModeRepository.getBlockedApps()
            if (blockedApps.contains(packageName) || isCurrentPackageBlocked(packageName)) {
                return true
            }
        }

        // 2. Check Room Database Focus Session
        val activeMode = activeFocusMode
        if (activeMode != null && activeMode.isEnabled) {
            if (activeMode.blockedApps.contains(packageName) || isCurrentPackageBlocked(packageName)) {
                return true
            }
        }

        return false
    }

    private fun showBlockingOverlay() {
        val intent = Intent(this, com.reeltracker.ui.screens.BlockingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    private fun showFocusedModeInfo(modeId: Long) {
        val intent = Intent(this, com.reeltracker.ui.screens.FocusedModeInfoActivity::class.java).apply {
            putExtra("modeId", modeId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun isPackageBlockedInFocusMode(packageName: String, mode: com.reeltracker.data.entities.FocusMode): Boolean {
        val safePackages = setOf(
            this.packageName,
            "android",
            "com.android.settings",
            "com.android.providers.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller"
        )
        if (safePackages.contains(packageName)) return false

        if (isLauncherPackage(packageName)) return false

        if (mode.blockedApps.contains(packageName)) return true

        if (mode.allowedApps.isNotEmpty() && !mode.allowedApps.contains(packageName)) {
            return true
        }

        return false
    }

    private fun isLauncherPackage(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    override fun onInterrupt() {
        Log.d(TAG, "ReelAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(blockScreenReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        scope.cancel()
    }

    companion object {
        private const val TAG = "ReelAccessService"
        const val PACKAGE_INSTAGRAM = "com.instagram.android"
        const val PACKAGE_SNAPCHAT = "com.snapchat.android"
        const val PACKAGE_YOUTUBE = "com.google.android.youtube"

        const val ACTION_BLOCK_SCREEN_SHOWN = "com.reeltracker.ACTION_BLOCK_SCREEN_SHOWN"
        const val ACTION_BLOCK_SCREEN_HIDDEN = "com.reeltracker.ACTION_BLOCK_SCREEN_HIDDEN"
    }
}
