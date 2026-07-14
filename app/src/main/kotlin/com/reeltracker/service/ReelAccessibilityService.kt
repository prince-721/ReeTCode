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
import android.view.WindowManager
import android.view.Gravity
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import java.util.Locale
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
    private var tempUnlockUntilMs = 0L

    @Volatile
    private var activeFocusMode: com.reeltracker.data.entities.FocusMode? = null

    @Volatile
    private var isBlockingScreenVisible = false

    @Volatile
    private var isFocusedShared = false

    private lateinit var focusedModeRepository: FocusedModeRepository

    private var overlayView: android.view.View? = null
    private var overlayText: android.widget.TextView? = null
    private var overlayUpdateJob: Job? = null
    private var lastBlockLaunchTime = 0L

    private var fullScreenBlockerVal: android.view.View? = null
    private var blockerTimerJob: Job? = null

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
                    val mode = activeFocusMode
                    if (mode != null && mode.isEnabled && isPackageBlockedInFocusMode(currentPackage, mode)) {
                        withContext(Dispatchers.Main) {
                            showFullScreenBlocker(true)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error observing active focus mode", e)
            }
        }
        // Observe active preferences
        scope.launch {
            try {
                AppContainer.prefsRepository.userPreferencesFlow.collect { prefs ->
                    tempUnlockUntilMs = prefs.tempUnlockUntilMs
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error observing preferences", e)
            }
        }
        scope.launch {
            try {
                AppContainer.repository.observeActiveBlock().collect { block ->
                    isBlocked = block != null
                    if (isBlocked && isCurrentPackageBlocked(currentPackage)) {
                        val inTempUnlock = System.currentTimeMillis() < tempUnlockUntilMs
                        if (!inTempUnlock && !isBlockingScreenVisible && fullScreenBlockerVal == null) {
                            withContext(Dispatchers.Main) {
                                showBlockingOverlay()
                            }
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

        // Only update foreground package on window state changes (app launches/transitions) to prevent flickering
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || currentPackage.isEmpty()) {
            val isSystemOrInputMethod = packageName == "android" ||
                    packageName == "com.android.systemui" ||
                    packageName.contains("inputmethod", ignoreCase = true) ||
                    packageName.contains("keyboard", ignoreCase = true) ||
                    packageName.contains("swiftkey", ignoreCase = true) ||
                    packageName.contains("latin", ignoreCase = true)
            if (!isSystemOrInputMethod) {
                // If the event has our package name but our activities are NOT active in foreground, ignore it.
                // This prevents the overlay blocker window addition from resetting currentPackage back to our app.
                if (packageName == this.packageName && !isAppInForeground) {
                    return
                }
                currentPackage = packageName
                isBlockingScreenVisible = (packageName == this.packageName)
                // Refresh focus mode cache only on window state changes (not every scroll/content event)
                isFocusedShared = focusedModeRepository.isFocused()
            }
        }

        // If the user is currently looking at our app, do not block!
        if (currentPackage == this.packageName) {
            removeFullScreenBlocker()
            return
        }

        // If the user is on home screen, do not block!
        if (isLauncherPackage(currentPackage)) {
            removeFullScreenBlocker()
            return
        }

        // If the user is on system ui, do not block or show blocker
        if (packageName == "android" || packageName == "com.android.systemui") {
            return
        }

        // Battery optimization: skip all processing for irrelevant packages when no block/focus is active
        val hasFocusSession = activeFocusMode != null || isFocusedShared
        val isRelevantPackage = isCurrentPackageBlocked(currentPackage) ||
                (hasFocusSession && isSettingsOrInstallerPackage(currentPackage))
        if (!isRelevantPackage && !isBlocked && !hasFocusSession) {
            return
        }

        val inTempUnlock = System.currentTimeMillis() < tempUnlockUntilMs

        if (inTempUnlock && isCurrentPackageBlocked(packageName)) {
            showOverlay()
        } else {
            removeOverlay()
        }

        // Intercept and block if screen time limit is active — only block social media apps (not settings)
        val isAppBlocked = !inTempUnlock && isBlocked && isCurrentPackageBlocked(currentPackage)
        // Focus Mode blocks social media + settings + installer apps
        val isFocusBlocked = isFocusModeActive(currentPackage)

        if (isAppBlocked || isFocusBlocked) {
            removeOverlay() // Don't show the small floating pill if the full screen blocker is shown
            showFullScreenBlocker(isFocusBlocked)
            return
        } else {
            removeFullScreenBlocker()
        }

        // When the block screen is showing in front, ignore further details of blocked apps
        if (isBlockingScreenVisible) {
            return
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

    private fun isSettingsOrInstallerPackage(pkg: String): Boolean {
        val lower = pkg.lowercase(Locale.US)
        return lower == "com.android.settings" ||
                lower == "com.google.android.settings" ||
                lower == "com.samsung.android.settings" ||
                lower == "com.miui.securitycenter" ||
                lower == "com.android.packageinstaller" ||
                lower == "com.google.android.packageinstaller" ||
                lower == "com.android.vending" ||
                lower == "com.huawei.systemmanager" ||
                lower == "com.coloros.safecenter" ||
                lower == "com.oppo.safe"
    }

    private fun isFocusModeActive(packageName: String): Boolean {
        // 1. Check SharedPreferences Focus Session (using cached value)
        if (isFocusedShared) {
            val blockedApps = focusedModeRepository.getBlockedApps()
            if (blockedApps.contains(packageName) || isCurrentPackageBlocked(packageName) || isSettingsOrInstallerPackage(packageName)) {
                return true
            }
        }

        // 2. Check Room Database Focus Session
        val activeMode = activeFocusMode
        if (activeMode != null && activeMode.isEnabled) {
            val durationMs = activeMode.durationHours * 3600000L
            val elapsed = System.currentTimeMillis() - activeMode.activatedTime
            if (elapsed > durationMs) {
                // Auto-disable when focus session is expired
                scope.launch {
                    val db = com.reeltracker.data.database.ReelTrackerDatabase.getDatabase(this@ReelAccessibilityService)
                    db.focusModeDao().update(activeMode.copy(isEnabled = false))
                }
                return false
            }

            if (isPackageBlockedInFocusMode(packageName, activeMode)) {
                return true
            }
        }

        return false
    }

    private fun showBlockingOverlay() {
        val now = System.currentTimeMillis()
        if (now - lastBlockLaunchTime < 2000) {
            return
        }
        lastBlockLaunchTime = now

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
            "android"
        )
        if (safePackages.contains(packageName)) return false

        if (isLauncherPackage(packageName)) return false

        if (isSettingsOrInstallerPackage(packageName)) return true

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
        removeOverlay()
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

        @Volatile
        var isAppInForeground: Boolean = false
    }

    private fun showOverlay() {
        if (overlayView != null) return
        if (!android.provider.Settings.canDrawOverlays(this)) return

        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val borderDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12f).toFloat()
                setColor(android.graphics.Color.parseColor("#E61A1A2E")) // Dark background with alpha
                setStroke(dpToPx(1), android.graphics.Color.parseColor("#FF00F0FF")) // Teal border
            }

            val container = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = borderDrawable
                setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8))
            }

            val icon = android.widget.TextView(this).apply {
                text = "🔓"
                textSize = 14f
            }
            container.addView(icon)

            val spacer = android.view.View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(dpToPx(6), 1)
            }
            container.addView(spacer)

            overlayText = android.widget.TextView(this).apply {
                text = "Unlocked: --:--"
                setTextColor(android.graphics.Color.parseColor("#FF00F0FF")) // Teal
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            container.addView(overlayText)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dpToPx(48) // position below status bar
            }

            wm.addView(container, params)
            overlayView = container

            startOverlayTimer()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay", e)
        }
    }

    private fun startOverlayTimer() {
        overlayUpdateJob?.cancel()
        overlayUpdateJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val remainingMs = tempUnlockUntilMs - System.currentTimeMillis()
                if (remainingMs <= 0) {
                    removeOverlay()
                    break
                }
                val totalSecs = remainingMs / 1000
                val mins = totalSecs / 60
                val secs = totalSecs % 60
                val timeStr = String.format(Locale.US, "%02d:%02d", mins, secs)
                overlayText?.text = "Unlocked: $timeStr"
                delay(1000)
            }
        }
    }

    private fun removeOverlay() {
        overlayUpdateJob?.cancel()
        overlayUpdateJob = null
        if (overlayView != null) {
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(overlayView)
            } catch (e: Exception) {
                // ignore
            }
            overlayView = null
            overlayText = null
        }
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun showFullScreenBlocker(isFocus: Boolean) {
        if (fullScreenBlockerVal != null) return
        if (!android.provider.Settings.canDrawOverlays(this)) return

        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                PixelFormat.TRANSLUCENT
            )

            val layout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(android.graphics.Color.parseColor("#E60D0D1E"))
                setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))
            }

            val iconView = android.widget.TextView(this).apply {
                text = "🔒"
                textSize = 48f
                gravity = Gravity.CENTER
            }
            layout.addView(iconView)

            val spacer1 = android.view.View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(1, dpToPx(16))
            }
            layout.addView(spacer1)

            val titleView = android.widget.TextView(this).apply {
                text = if (isFocus) "Focus Mode Active" else "Reel Limit Reached"
                setTextColor(android.graphics.Color.WHITE)
                textSize = 22f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            layout.addView(titleView)

            val spacer2 = android.view.View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(1, dpToPx(8))
            }
            layout.addView(spacer2)

            val descView = android.widget.TextView(this).apply {
                text = "Take back your focus. Solve coding problems to earn scroll time, or wait for the timer to end."
                setTextColor(android.graphics.Color.parseColor("#FFC5C5D2"))
                textSize = 14f
                gravity = Gravity.CENTER
            }
            layout.addView(descView)

            val spacer3 = android.view.View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(1, dpToPx(32))
            }
            layout.addView(spacer3)

            val timerView = android.widget.TextView(this).apply {
                text = "00:00:00"
                setTextColor(android.graphics.Color.parseColor("#FFFF4646"))
                textSize = 36f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            layout.addView(timerView)

            val labelView = android.widget.TextView(this).apply {
                text = "remaining time"
                setTextColor(android.graphics.Color.parseColor("#FF8E8E9F"))
                textSize = 12f
                gravity = Gravity.CENTER
            }
            layout.addView(labelView)

            val spacer4 = android.view.View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(1, dpToPx(32))
            }
            layout.addView(spacer4)

            val buttonDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12f).toFloat()
                setColor(android.graphics.Color.parseColor("#FF00ADB5"))
            }
            
            val unlockButton = android.widget.Button(this).apply {
                text = "🎯 Unlock by Coding"
                setTextColor(android.graphics.Color.WHITE)
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                background = buttonDrawable
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(50)
                )
                setOnClickListener {
                    val intent = Intent(this@ReelAccessibilityService, com.reeltracker.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(intent)
                }
            }
            layout.addView(unlockButton)

            wm.addView(layout, params)
            fullScreenBlockerVal = layout

            startBlockerTimer(timerView)

        } catch (e: Exception) {
            Log.e(TAG, "Error showing full screen blocker", e)
        }
    }

    private fun startBlockerTimer(timerView: android.widget.TextView) {
        blockerTimerJob?.cancel()
        blockerTimerJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val db = com.reeltracker.data.database.ReelTrackerDatabase.getDatabase(this@ReelAccessibilityService)
                val activeBlock = AppContainer.repository.getActiveBlock()
                val activeFocusMode = db.focusModeDao().getActiveFocusMode()

                val endTime = activeBlock?.endTime
                    ?: activeFocusMode?.let { it.activatedTime + (it.durationHours * 3600000L) }
                    ?: 0L
                val remainingMs = endTime - System.currentTimeMillis()
                if (remainingMs <= 0) {
                    removeFullScreenBlocker()
                    break
                }
                
                val hours = (remainingMs / 3600000).toInt()
                val mins = ((remainingMs % 3600000) / 60000).toInt()
                val secs = ((remainingMs % 60000) / 1000).toInt()
                timerView.text = String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
                
                delay(1000)
            }
        }
    }

    private fun removeFullScreenBlocker() {
        blockerTimerJob?.cancel()
        blockerTimerJob = null
        if (fullScreenBlockerVal != null) {
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(fullScreenBlockerVal)
            } catch (e: Exception) {
                // ignore
            }
            fullScreenBlockerVal = null
        }
    }
}
