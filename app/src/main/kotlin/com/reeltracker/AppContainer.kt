package com.reeltracker

import android.content.Context
import com.reeltracker.data.UserPreferencesRepository
import com.reeltracker.data.database.ReelTrackerDatabase
import com.reeltracker.data.repository.ReelTrackerRepository

object AppContainer {
    private var _repository: ReelTrackerRepository? = null
    private var _prefsRepository: UserPreferencesRepository? = null

    fun initialize(context: Context) {
        val db = ReelTrackerDatabase.getDatabase(context)
        _repository = ReelTrackerRepository(
            dailyReelCountDao = db.dailyReelCountDao(),
            blockSessionDao = db.blockSessionDao(),
            focusModeDao = db.focusModeDao(),
            codingPlatformConfigDao = db.codingPlatformConfigDao()
        )
        _prefsRepository = UserPreferencesRepository(context)
    }

    val repository: ReelTrackerRepository
        get() = _repository ?: error("AppContainer not initialized")

    val prefsRepository: UserPreferencesRepository
        get() = _prefsRepository ?: error("AppContainer not initialized")
}
