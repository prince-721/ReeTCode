package com.reeltracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserPreferences(
    val dailyLimit: Int = 50,
    val isTrackingEnabled: Boolean = true,
    val trackInstagram: Boolean = true,
    val trackSnapchat: Boolean = true,
    val trackYoutube: Boolean = true,
    val hasCompletedOnboarding: Boolean = false,
    val blockDurationHours: Int = 6,
    val currentStreak: Int = 0
)

class UserPreferencesRepository(private val context: Context) {

    private val dataStore = context.userPreferencesDataStore

    private object PreferenceKeys {
        val DAILY_LIMIT = intPreferencesKey("daily_limit")
        val TRACKING_ENABLED = booleanPreferencesKey("tracking_enabled")
        val TRACK_INSTAGRAM = booleanPreferencesKey("track_instagram")
        val TRACK_SNAPCHAT = booleanPreferencesKey("track_snapchat")
        val TRACK_YOUTUBE = booleanPreferencesKey("track_youtube")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val BLOCK_DURATION_HOURS = intPreferencesKey("block_duration_hours")
        val CURRENT_STREAK = intPreferencesKey("current_streak")
    }

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            UserPreferences(
                dailyLimit = preferences[PreferenceKeys.DAILY_LIMIT] ?: 50,
                isTrackingEnabled = preferences[PreferenceKeys.TRACKING_ENABLED] ?: true,
                trackInstagram = preferences[PreferenceKeys.TRACK_INSTAGRAM] ?: true,
                trackSnapchat = preferences[PreferenceKeys.TRACK_SNAPCHAT] ?: true,
                trackYoutube = preferences[PreferenceKeys.TRACK_YOUTUBE] ?: true,
                hasCompletedOnboarding = preferences[PreferenceKeys.HAS_COMPLETED_ONBOARDING] ?: false,
                blockDurationHours = preferences[PreferenceKeys.BLOCK_DURATION_HOURS] ?: 6,
                currentStreak = preferences[PreferenceKeys.CURRENT_STREAK] ?: 0
            )
        }

    suspend fun updateDailyLimit(limit: Int) {
        dataStore.edit { it[PreferenceKeys.DAILY_LIMIT] = limit }
    }

    suspend fun updateTrackingEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.TRACKING_ENABLED] = enabled }
    }

    suspend fun updateTrackInstagram(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.TRACK_INSTAGRAM] = enabled }
    }

    suspend fun updateTrackSnapchat(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.TRACK_SNAPCHAT] = enabled }
    }

    suspend fun updateTrackYoutube(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.TRACK_YOUTUBE] = enabled }
    }

    suspend fun completeOnboarding() {
        dataStore.edit { it[PreferenceKeys.HAS_COMPLETED_ONBOARDING] = true }
    }

    suspend fun updateStreak(streak: Int) {
        dataStore.edit { it[PreferenceKeys.CURRENT_STREAK] = streak }
    }
}
