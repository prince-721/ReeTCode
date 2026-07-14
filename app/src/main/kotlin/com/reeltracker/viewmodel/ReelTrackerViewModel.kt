package com.reeltracker.viewmodel

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reeltracker.AppContainer
import com.reeltracker.data.UserPreferences
import com.reeltracker.data.entities.BlockSession
import com.reeltracker.data.entities.DailyReelCount
import com.reeltracker.data.entities.FocusMode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class AppInfo(
    val packageName: String,
    val name: String
)

data class HomeUiState(
    val todayCount: DailyReelCount? = null,
    val preferences: UserPreferences = UserPreferences(),
    val activeBlock: BlockSession? = null,
    val weekHistory: List<DailyReelCount> = emptyList(),
    val isLoading: Boolean = true,
    val streak: Int = 0,
    val studySessions: List<BlockSession> = emptyList()
)

class ReelTrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppContainer.repository
    private val prefsRepository = AppContainer.prefsRepository

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Combine all reactive data sources
            combine(
                repository.observeTodayCount(),
                prefsRepository.userPreferencesFlow,
                repository.observeActiveBlock(),
                repository.observeLastSevenDays(),
                repository.observeAllStudySessions()
            ) { todayCount, prefs, activeBlock, weekHistory, studySessions ->
                HomeUiState(
                    todayCount = todayCount,
                    preferences = prefs,
                    activeBlock = activeBlock,
                    weekHistory = weekHistory,
                    isLoading = false,
                    streak = prefs.currentStreak,
                    studySessions = studySessions
                )
            }.collect { state ->
                _uiState.value = state

                // Auto-expire blocks
                repository.expireOldBlocks()
            }
        }

        // Ensure today's record exists
        viewModelScope.launch {
            val prefs = prefsRepository.userPreferencesFlow.first()
            repository.ensureTodayExists(prefs.dailyLimit)
        }
    }

    fun updateDailyLimit(limit: Int) {
        viewModelScope.launch {
            prefsRepository.updateDailyLimit(limit)
        }
    }

    fun updateTrackingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.updateTrackingEnabled(enabled)
        }
    }

    fun updateTrackInstagram(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.updateTrackInstagram(enabled) }
    }

    fun updateTrackSnapchat(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.updateTrackSnapchat(enabled) }
    }

    fun updateTrackYoutube(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.updateTrackYoutube(enabled) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { prefsRepository.completeOnboarding() }
    }

    fun manualUnlock(blockId: Long) {
        viewModelScope.launch {
            repository.manuallyUnlockBlock(blockId)
        }
    }

    fun startStudySession(durationMinutes: Int) {
        viewModelScope.launch {
            repository.startStudySession(durationMinutes)
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val serviceName = "${context.packageName}/com.reeltracker.service.ReelAccessibilityService"
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(serviceName)
    }

    fun isOverlayPermissionGranted(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun isExactAlarmPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: true
        } else {
            true
        }
    }

    fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    context.startActivity(intent)
                } catch (ex: Exception) {
                    android.widget.Toast.makeText(context, "Exact alarm permission settings not supported on this device.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val hasCompletedOnboarding: StateFlow<Boolean?> =
        prefsRepository.userPreferencesFlow
            .map { it.hasCompletedOnboarding }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null  // null = still loading
            )

    fun getBlockRemainingMs(block: BlockSession): Long {
        return maxOf(0L, block.endTime - System.currentTimeMillis())
    }

    fun formatRemainingTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m remaining"
            minutes > 0 -> "${minutes}m ${seconds}s remaining"
            else -> "${seconds}s remaining"
        }
    }

    fun getDateLabel(dateStr: String): String {
        return try {
            val date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val today = LocalDate.now()
            when {
                date == today -> "Today"
                date == today.minusDays(1) -> "Yesterday"
                else -> date.dayOfWeek.name.take(3).uppercase()
            }
        } catch (e: Exception) {
            dateStr
        }
    }

    // ---- Focus Modes Operations ----

    val focusModes: StateFlow<List<FocusMode>> = repository.observeAllFocusModes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeFocusMode: StateFlow<FocusMode?> = repository.observeActiveFocusMode()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun toggleFocusMode(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleFocusMode(id, enabled)
        }
    }

    suspend fun getFocusModeById(id: Long): FocusMode? {
        return repository.getFocusModeById(id)
    }

    fun saveFocusMode(id: Long, name: String, blockedApps: List<String>, allowedApps: List<String>) {
        viewModelScope.launch {
            val focusMode = FocusMode(
                id = id,
                name = name,
                isEnabled = false, // starts disabled
                blockedApps = blockedApps,
                allowedApps = allowedApps
            )
            repository.saveFocusMode(focusMode)
        }
    }

    fun deleteFocusMode(focusMode: FocusMode) {
        viewModelScope.launch {
            repository.deleteFocusMode(focusMode)
        }
    }

    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val appList = mutableListOf<AppInfo>()
        for (app in apps) {
            val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null && app.packageName != context.packageName) {
                val name = app.loadLabel(pm).toString()
                appList.add(AppInfo(packageName = app.packageName, name = name))
            }
        }
        return appList.sortedBy { it.name.lowercase() }
    }
}


