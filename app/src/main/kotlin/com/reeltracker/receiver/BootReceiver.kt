package com.reeltracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reeltracker.AppContainer
import com.reeltracker.service.ReelTrackerService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            AppContainer.initialize(context)
            ReelTrackerService.start(context)
        }
    }
}
