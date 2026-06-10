package com.reeltracker.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.Instant

class FocusedModeRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "focused_mode_prefs"
        private const val KEY_FOCUSED = "focused_mode"
        private const val KEY_START_TIME = "focused_start"
        private const val KEY_RESET_TIME = "focused_reset"
        private const val KEY_REEL_COUNT = "reel_count"
        private const val KEY_BLOCKED_APPS = "blocked_apps"
        const val DAILY_LIMIT = 5
    }

    fun isFocused(): Boolean = prefs.getBoolean(KEY_FOCUSED, false)

    fun setFocused(enabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_FOCUSED, enabled)
            if (enabled) {
                val now = Instant.now().toString()
                putString(KEY_START_TIME, now)
                // reset at next midnight UTC
                val reset = Instant.now().plusSeconds(24 * 60 * 60).truncatedTo(java.time.temporal.ChronoUnit.DAYS).toString()
                putString(KEY_RESET_TIME, reset)
                putInt(KEY_REEL_COUNT, 0)
            } else {
                remove(KEY_START_TIME)
                remove(KEY_RESET_TIME)
                remove(KEY_REEL_COUNT)
            }
        }
    }

    fun getStartTime(): String? = prefs.getString(KEY_START_TIME, null)
    fun getResetTime(): String? = prefs.getString(KEY_RESET_TIME, null)

    fun getReelCount(): Int = prefs.getInt(KEY_REEL_COUNT, 0)
    fun incrementReel() {
        val current = getReelCount()
        prefs.edit { putInt(KEY_REEL_COUNT, current + 1) }
    }
    fun resetReelCount() {
        prefs.edit { putInt(KEY_REEL_COUNT, 0) }
    }

    fun getBlockedApps(): Set<String> = prefs.getStringSet(KEY_BLOCKED_APPS, defaultBlockedApps()) ?: defaultBlockedApps()
    fun setBlockedApps(apps: Set<String>) {
        prefs.edit { putStringSet(KEY_BLOCKED_APPS, apps) }
    }

    private fun defaultBlockedApps(): Set<String> = setOf(
        "com.instagram.android",
        "com.snapchat.android",
        "com.google.android.youtube",
        "com.tiktok.android"
    )
}
