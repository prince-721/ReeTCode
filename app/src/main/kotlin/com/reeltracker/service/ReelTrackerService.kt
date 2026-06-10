package com.reeltracker.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.reeltracker.AppContainer
import com.reeltracker.MainActivity
import com.reeltracker.R
import com.reeltracker.ReelEventBus
import com.reeltracker.ReelTrackerApp
import com.reeltracker.receiver.MidnightResetReceiver
import com.reeltracker.ui.screens.BlockingActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.*

class ReelTrackerService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repository by lazy { AppContainer.repository }
    private val prefsRepository by lazy { AppContainer.prefsRepository }

    private var currentCount = 0
    private var dailyLimit = 50
    private var isTracking = true
    private var isBlocked = false

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_UNLOCK_APPS) {
                scope.launch { handleUnlock() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            RECEIVER_NOT_EXPORTED else 0
        registerReceiver(unlockReceiver, IntentFilter(ACTION_UNLOCK_APPS), flags)
        startForeground(NOTIFICATION_ID, buildNotification(0, 50, false))
        scheduleMidnightReset()
        scope.launch { initializeState() }
    }

    private suspend fun initializeState() {
        val prefs = prefsRepository.userPreferencesFlow.first()
        dailyLimit = prefs.dailyLimit
        isTracking = prefs.isTrackingEnabled

        repository.ensureTodayExists(dailyLimit)
        val todayCount = repository.getTodayCount()
        currentCount = todayCount?.totalCount ?: 0

        // Check for active block
        repository.expireOldBlocks()
        val activeBlock = repository.getActiveBlock()
        if (activeBlock != null) {
            isBlocked = true
            updateNotification(currentCount, dailyLimit, true)
        } else {
            updateNotification(currentCount, dailyLimit, false)
        }

        // Start monitoring prefs for live changes
        scope.launch {
            prefsRepository.userPreferencesFlow.collect { prefs ->
                dailyLimit = prefs.dailyLimit
                isTracking = prefs.isTrackingEnabled
                updateNotification(currentCount, dailyLimit, isBlocked)
            }
        }

        // Monitor today's count from DB
        scope.launch {
            repository.observeTodayCount().collect { count ->
                currentCount = count?.totalCount ?: 0
                updateNotification(currentCount, dailyLimit, isBlocked)
            }
        }

        // Monitor active block
        scope.launch {
            repository.observeActiveBlock().collect { block ->
                val wasBlocked = isBlocked
                isBlocked = block != null
                if (!wasBlocked && isBlocked) {
                    showBlockingOverlay()
                } else if (wasBlocked && !isBlocked) {
                    dismissBlockingOverlay()
                }
                updateNotification(currentCount, dailyLimit, isBlocked)
            }
        }

        // Collect reel detection events from the event bus (replaces broadcast receiver)
        scope.launch {
            ReelEventBus.reelEvents.collect { event ->
                if (isTracking && !isBlocked) {
                    handleReelDetected(event.packageName)
                }
            }
        }

        // Collect unlock events from the event bus
        scope.launch {
            ReelEventBus.unlockEvents.collect {
                handleUnlock()
            }
        }
    }

    private suspend fun handleReelDetected(packageName: String) {
        val prefs = prefsRepository.userPreferencesFlow.first()
        Log.d(TAG, "handleReelDetected called for $packageName with prefs: $prefs")

        val shouldCount = when (packageName) {
            ReelAccessibilityService.PACKAGE_INSTAGRAM -> prefs.trackInstagram
            ReelAccessibilityService.PACKAGE_SNAPCHAT -> prefs.trackSnapchat
            ReelAccessibilityService.PACKAGE_YOUTUBE -> prefs.trackYoutube
            else -> false
        }

        if (!shouldCount) return

        when (packageName) {
            ReelAccessibilityService.PACKAGE_INSTAGRAM -> {
                repository.incrementInstagram(dailyLimit)
                Log.d(TAG, "Incremented Instagram count")
            }
            ReelAccessibilityService.PACKAGE_SNAPCHAT -> {
                repository.incrementSnapchat(dailyLimit)
                Log.d(TAG, "Incremented Snapchat count")
            }
            ReelAccessibilityService.PACKAGE_YOUTUBE -> {
                repository.incrementYoutube(dailyLimit)
                Log.d(TAG, "Incremented YouTube count")
            }
        }

        val newCount = (repository.getTodayCount()?.totalCount ?: 0)
        currentCount = newCount

        Log.d(TAG, "Reel detected from $packageName. Count: $currentCount / $dailyLimit")

        // Check if limit reached
        if (newCount >= dailyLimit && !isBlocked) {
            repository.markTodayLimitReached()
            val session = repository.createBlockSession(prefs.blockDurationHours)
            isBlocked = true
            showBlockingOverlay()
            sendLimitReachedNotification(newCount, dailyLimit)
        }

        updateNotification(currentCount, dailyLimit, isBlocked)

        // Broadcast count update to UI
        val updateIntent = Intent(ACTION_COUNT_UPDATED).apply {
            putExtra(EXTRA_COUNT, currentCount)
            putExtra(EXTRA_LIMIT, dailyLimit)
        }
        sendBroadcast(updateIntent)
    }

    private fun showBlockingOverlay() {
        val intent = Intent(this, BlockingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    private fun dismissBlockingOverlay() {
        val intent = Intent(ACTION_DISMISS_BLOCK).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        sendBroadcast(intent)
    }

    private suspend fun handleUnlock() {
        val activeBlock = repository.getActiveBlock()
        if (activeBlock != null) {
            repository.manuallyUnlockBlock(activeBlock.id)
        }
        isBlocked = false
        updateNotification(currentCount, dailyLimit, false)
    }

    private fun buildNotification(count: Int, limit: Int, blocked: Boolean): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (blocked) "🔒 Apps Blocked" else "📱 Reel Tracker Active"
        val text = if (blocked) {
            "You've hit your reel limit. Apps blocked for 6 hours."
        } else {
            "Reels today: $count / $limit"
        }

        return NotificationCompat.Builder(this, ReelTrackerApp.CHANNEL_TRACKER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(count: Int, limit: Int, blocked: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(count, limit, blocked))
    }

    private fun sendLimitReachedNotification(count: Int, limit: Int) {
        val intent = Intent(this, BlockingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, ReelTrackerApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🚫 Daily Reel Limit Reached!")
            .setContentText("You've watched $count reels today. Instagram, Snapchat & YouTube are now blocked.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_LIMIT_ID, notification)
    }

    private fun scheduleMidnightReset() {
        MidnightResetReceiver.schedule(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> stopSelf()
            ACTION_UNLOCK_APPS -> scope.launch { handleUnlock() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(unlockReceiver)
        } catch (e: Exception) { /* ignore */ }
        scope.cancel()
    }

    companion object {
        private const val TAG = "ReelTrackerService"
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_LIMIT_ID = 1002
        const val ACTION_REEL_DETECTED = "com.reeltracker.ACTION_REEL_DETECTED"
        const val ACTION_COUNT_UPDATED = "com.reeltracker.ACTION_COUNT_UPDATED"
        const val ACTION_UNLOCK_APPS = "com.reeltracker.ACTION_UNLOCK_APPS"
        const val ACTION_DISMISS_BLOCK = "com.reeltracker.ACTION_DISMISS_BLOCK"
        const val ACTION_STOP_SERVICE = "com.reeltracker.ACTION_STOP_SERVICE"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_COUNT = "count"
        const val EXTRA_LIMIT = "limit"

        fun start(context: Context) {
            val intent = Intent(context, ReelTrackerService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ReelTrackerService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
