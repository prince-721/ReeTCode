package com.reeltracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class ReelTrackerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppContainer.initialize(this)
        createNotificationChannels()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var activeActivities = 0

            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) {
                activeActivities++
                com.reeltracker.service.ReelAccessibilityService.isAppInForeground = activeActivities > 0
            }
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {
                activeActivities--
                com.reeltracker.service.ReelAccessibilityService.isAppInForeground = activeActivities > 0
            }
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Main tracker channel
            val trackerChannel = NotificationChannel(
                CHANNEL_TRACKER,
                "ReetCode",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows your live reel count and tracking status"
                setShowBadge(false)
            }

            // Alert channel for limit warnings
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "ReetCode Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when you reach your reel limit"
            }

            manager.createNotificationChannels(listOf(trackerChannel, alertChannel))
        }
    }

    companion object {
        const val CHANNEL_TRACKER = "reel_tracker_channel"
        const val CHANNEL_ALERTS = "reel_tracker_alerts"
    }
}
