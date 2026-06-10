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
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Main tracker channel
            val trackerChannel = NotificationChannel(
                CHANNEL_TRACKER,
                "Reel Tracker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows your live reel count and tracking status"
                setShowBadge(false)
            }

            // Alert channel for limit warnings
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Reel Tracker Alerts",
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
