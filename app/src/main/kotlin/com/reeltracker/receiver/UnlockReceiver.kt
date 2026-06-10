package com.reeltracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reeltracker.service.ReelTrackerService

class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ReelTrackerService.ACTION_UNLOCK_APPS) {
            // Forward to service which handles the actual unlock
            val serviceIntent = Intent(context, ReelTrackerService::class.java).apply {
                action = ReelTrackerService.ACTION_UNLOCK_APPS
            }
            context.startForegroundService(serviceIntent)
        }
    }
}
