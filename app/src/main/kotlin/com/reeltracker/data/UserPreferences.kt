package com.reeltracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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
    val currentStreak: Int = 0,
    // Code to Unlock settings
    val codeUnlockEnabled: Boolean = false,
    val leetcodeUsername: String = "",
    val codechefUsername: String = "",
    val gfgUsername: String = "",
    val isLeetcodeVerified: Boolean = false,
    val isCodechefVerified: Boolean = false,
    val isGfgVerified: Boolean = false,
    val problemsToFullUnlock: Int = 5,
    val minutesPerProblem: Int = 30,
    val tempUnlockUntilMs: Long = 0L,
    val useSameUsername: Boolean = false
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
        // Code to Unlock keys
        val CODE_UNLOCK_ENABLED = booleanPreferencesKey("code_unlock_enabled")
        val LEETCODE_USERNAME = stringPreferencesKey("leetcode_username")
        val CODECHEF_USERNAME = stringPreferencesKey("codechef_username")
        val GFG_USERNAME = stringPreferencesKey("gfg_username")
        val IS_LEETCODE_VERIFIED = booleanPreferencesKey("is_leetcode_verified")
        val IS_CODECHEF_VERIFIED = booleanPreferencesKey("is_codechef_verified")
        val IS_GFG_VERIFIED = booleanPreferencesKey("is_gfg_verified")
        val PROBLEMS_TO_FULL_UNLOCK = intPreferencesKey("problems_to_full_unlock")
        val MINUTES_PER_PROBLEM = intPreferencesKey("minutes_per_problem")
        val TEMP_UNLOCK_UNTIL_MS = longPreferencesKey("temp_unlock_until_ms")
        val USE_SAME_USERNAME = booleanPreferencesKey("use_same_username")
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
                currentStreak = preferences[PreferenceKeys.CURRENT_STREAK] ?: 0,
                codeUnlockEnabled = preferences[PreferenceKeys.CODE_UNLOCK_ENABLED] ?: false,
                leetcodeUsername = preferences[PreferenceKeys.LEETCODE_USERNAME] ?: "",
                codechefUsername = preferences[PreferenceKeys.CODECHEF_USERNAME] ?: "",
                gfgUsername = preferences[PreferenceKeys.GFG_USERNAME] ?: "",
                isLeetcodeVerified = preferences[PreferenceKeys.IS_LEETCODE_VERIFIED] ?: false,
                isCodechefVerified = preferences[PreferenceKeys.IS_CODECHEF_VERIFIED] ?: false,
                isGfgVerified = preferences[PreferenceKeys.IS_GFG_VERIFIED] ?: false,
                problemsToFullUnlock = preferences[PreferenceKeys.PROBLEMS_TO_FULL_UNLOCK] ?: 5,
                minutesPerProblem = preferences[PreferenceKeys.MINUTES_PER_PROBLEM] ?: 30,
                tempUnlockUntilMs = preferences[PreferenceKeys.TEMP_UNLOCK_UNTIL_MS] ?: 0L,
                useSameUsername = preferences[PreferenceKeys.USE_SAME_USERNAME] ?: false
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

    // Code to Unlock preferences
    suspend fun updateCodeUnlockEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.CODE_UNLOCK_ENABLED] = enabled }
    }

    suspend fun updateLeetcodeUsername(username: String) {
        dataStore.edit {
            it[PreferenceKeys.LEETCODE_USERNAME] = username
            it[PreferenceKeys.IS_LEETCODE_VERIFIED] = false
        }
    }

    suspend fun updateCodechefUsername(username: String) {
        dataStore.edit {
            it[PreferenceKeys.CODECHEF_USERNAME] = username
            it[PreferenceKeys.IS_CODECHEF_VERIFIED] = false
        }
    }

    suspend fun updateGfgUsername(username: String) {
        dataStore.edit {
            it[PreferenceKeys.GFG_USERNAME] = username
            it[PreferenceKeys.IS_GFG_VERIFIED] = false
        }
    }

    suspend fun updateLeetcodeVerified(verified: Boolean) {
        dataStore.edit { it[PreferenceKeys.IS_LEETCODE_VERIFIED] = verified }
    }

    suspend fun updateCodechefVerified(verified: Boolean) {
        dataStore.edit { it[PreferenceKeys.IS_CODECHEF_VERIFIED] = verified }
    }

    suspend fun updateGfgVerified(verified: Boolean) {
        dataStore.edit { it[PreferenceKeys.IS_GFG_VERIFIED] = verified }
    }

    suspend fun updateProblemsToFullUnlock(count: Int) {
        dataStore.edit { it[PreferenceKeys.PROBLEMS_TO_FULL_UNLOCK] = count }
    }

    suspend fun updateMinutesPerProblem(minutes: Int) {
        dataStore.edit { it[PreferenceKeys.MINUTES_PER_PROBLEM] = minutes }
    }

    suspend fun getTempUnlockUntilMs(): Long {
        return dataStore.data.catch { emit(emptyPreferences()) }.map { it[PreferenceKeys.TEMP_UNLOCK_UNTIL_MS] ?: 0L }.first()
    }

    suspend fun updateTempUnlockUntilMs(timeMs: Long) {
        dataStore.edit { it[PreferenceKeys.TEMP_UNLOCK_UNTIL_MS] = timeMs }
    }

    suspend fun updateUseSameUsername(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.USE_SAME_USERNAME] = enabled }
    }
}
