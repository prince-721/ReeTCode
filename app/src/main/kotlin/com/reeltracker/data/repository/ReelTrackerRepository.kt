package com.reeltracker.data.repository

import android.util.Log
import com.reeltracker.data.dao.BlockSessionDao
import com.reeltracker.data.dao.CodingPlatformConfigDao
import com.reeltracker.data.dao.DailyReelCountDao
import com.reeltracker.data.dao.FocusModeDao
import com.reeltracker.data.entities.BlockSession
import com.reeltracker.data.entities.CodingPlatformConfig
import com.reeltracker.data.entities.DailyReelCount
import com.reeltracker.data.entities.FocusMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val TAG = "ReelTrackerRepository"

class ReelTrackerRepository(
    private val dailyReelCountDao: DailyReelCountDao,
    private val blockSessionDao: BlockSessionDao,
    private val focusModeDao: FocusModeDao,
    private val codingPlatformConfigDao: CodingPlatformConfigDao
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private fun today(): String = LocalDate.now().format(dateFormatter)

    // ---- Daily Counts ----

    fun observeTodayCount(): Flow<DailyReelCount?> =
        dailyReelCountDao.observeByDate(today())

    fun observeLastSevenDays(): Flow<List<DailyReelCount>> =
        dailyReelCountDao.observeLastSevenDays()

    suspend fun getTodayCount(): DailyReelCount? =
        dailyReelCountDao.getByDate(today())

    suspend fun ensureTodayExists(limit: Int) {
        val today = today()
        val existing = dailyReelCountDao.getByDate(today)
        if (existing == null) {
            dailyReelCountDao.upsert(
                DailyReelCount(date = today, limitValue = limit)
            )
        }
    }

    suspend fun incrementInstagram(limit: Int) {
        ensureTodayExists(limit)
        Log.d(TAG, "Incrementing Instagram count for today")
        dailyReelCountDao.incrementInstagram(today())
    }

    suspend fun incrementSnapchat(limit: Int) {
        ensureTodayExists(limit)
        Log.d(TAG, "Incrementing Snapchat count for today")
        dailyReelCountDao.incrementSnapchat(today())
    }

    suspend fun incrementYoutube(limit: Int) {
        ensureTodayExists(limit)
        Log.d(TAG, "Incrementing YouTube count for today")
        dailyReelCountDao.incrementYoutube(today())
    }

    suspend fun markTodayLimitReached() {
        dailyReelCountDao.markLimitReached(today())
    }

    suspend fun calculateStreak(): Int {
        val today = today()
        val pastDays = dailyReelCountDao.getAllBeforeToday(today)
            .sortedByDescending { it.date }
        var streak = 0
        var expectedDate = LocalDate.now().minusDays(1)
        for (day in pastDays) {
            val recordDate = try {
                LocalDate.parse(day.date, dateFormatter)
            } catch (e: Exception) {
                null
            }
            if (recordDate == expectedDate) {
                if (day.totalCount <= day.limitValue) {
                    streak++
                    expectedDate = expectedDate.minusDays(1)
                } else {
                    break // Exceeded limit, streak ends
                }
            } else {
                break // Gap in records, streak ends
            }
        }
        return streak
    }

    // ---- Block Sessions ----

    fun observeActiveBlock(): Flow<BlockSession?> =
        blockSessionDao.observeActiveSession()

    suspend fun getActiveBlock(): BlockSession? {
        blockSessionDao.expireOldSessions(System.currentTimeMillis())
        return blockSessionDao.getActiveSession()
    }

    suspend fun createBlockSession(durationHours: Int): BlockSession {
        val now = System.currentTimeMillis()
        val end = now + (durationHours * 60 * 60 * 1000L)
        val session = BlockSession(startTime = now, endTime = end, isActive = true)
        val id = blockSessionDao.insert(session)
        return session.copy(id = id)
    }

    suspend fun expireOldBlocks() {
        blockSessionDao.expireOldSessions(System.currentTimeMillis())
    }

    suspend fun deactivateBlock(id: Long) {
        blockSessionDao.deactivate(id)
    }

    suspend fun manuallyUnlockBlock(id: Long) {
        blockSessionDao.deactivateWithManualUnlock(id)
    }

    fun observeAllStudySessions(): Flow<List<BlockSession>> =
        blockSessionDao.observeAllStudySessions()

    suspend fun startStudySession(durationMinutes: Int): BlockSession {
        expireOldBlocks()
        val activeBlock = blockSessionDao.getActiveSession()
        if (activeBlock != null) {
            deactivateBlock(activeBlock.id)
        }
        val now = System.currentTimeMillis()
        val end = now + (durationMinutes * 60 * 1000L)
        val session = BlockSession(
            startTime = now,
            endTime = end,
            isActive = true,
            isStudyMode = true
        )
        val id = blockSessionDao.insert(session)
        return session.copy(id = id)
    }

    suspend fun codeUnlockBlock(id: Long, problemsSolved: Int, earnedMs: Long) {
        blockSessionDao.deactivateWithCodeUnlock(id, problemsSolved, earnedMs)
    }

    suspend fun reduceBlockTime(id: Long, problemsSolved: Int, earnedMs: Long, newEndTime: Long) {
        blockSessionDao.updateCodeUnlockProgress(id, problemsSolved, earnedMs)
        blockSessionDao.updateEndTime(id, newEndTime)
    }

    suspend fun updateGfgInitialCount(id: Long, count: Int) {
        blockSessionDao.updateGfgInitialCount(id, count)
    }

    suspend fun updateCodechefInitialCount(id: Long, count: Int) {
        blockSessionDao.updateCodechefInitialCount(id, count)
    }

    // ---- Focus Modes ----

    fun observeAllFocusModes(): Flow<List<FocusMode>> =
        focusModeDao.getAllFocusModesFlow()

    fun observeActiveFocusMode(): Flow<FocusMode?> =
        focusModeDao.getActiveFocusModesFlow().map { it.firstOrNull() }

    suspend fun getActiveFocusMode(): FocusMode? =
        focusModeDao.getActiveFocusMode()

    suspend fun getFocusModeById(id: Long): FocusMode? =
        focusModeDao.getFocusModeById(id)

    suspend fun saveFocusMode(focusMode: FocusMode): Long {
        return if (focusMode.id == 0L) {
            focusModeDao.insert(focusMode)
        } else {
            focusModeDao.update(focusMode)
            focusMode.id
        }
    }

    suspend fun deleteFocusMode(focusMode: FocusMode) {
        focusModeDao.delete(focusMode)
    }

    suspend fun toggleFocusMode(id: Long, enabled: Boolean) {
        if (enabled) {
            focusModeDao.disableAllFocusModes()
            val mode = focusModeDao.getFocusModeById(id)
            if (mode != null) {
                focusModeDao.update(mode.copy(isEnabled = true, activatedTime = System.currentTimeMillis()))
            }
        } else {
            val mode = focusModeDao.getFocusModeById(id)
            if (mode != null) {
                focusModeDao.update(mode.copy(isEnabled = false))
            }
        }
    }

    suspend fun saveCodingConfig(config: CodingPlatformConfig) {
        codingPlatformConfigDao.upsert(config)
    }

    suspend fun getCodingConfig(): CodingPlatformConfig? {
        return codingPlatformConfigDao.getConfig()
    }

    fun observeCodingConfig(): Flow<CodingPlatformConfig?> {
        return codingPlatformConfigDao.observeConfig()
    }
}
