package com.reeltracker.data.dao

import androidx.room.*
import com.reeltracker.data.entities.BlockSession
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: BlockSession): Long

    @Query("SELECT * FROM block_sessions WHERE isActive = 1 ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveSession(): BlockSession?

    @Query("SELECT * FROM block_sessions WHERE isActive = 1 ORDER BY startTime DESC LIMIT 1")
    fun observeActiveSession(): Flow<BlockSession?>

    @Query("UPDATE block_sessions SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)

    @Query("UPDATE block_sessions SET isActive = 0, wasManuallyUnlocked = 1 WHERE id = :id")
    suspend fun deactivateWithManualUnlock(id: Long)

    @Query("UPDATE block_sessions SET isActive = 0 WHERE endTime <= :now")
    suspend fun expireOldSessions(now: Long)

    @Query("DELETE FROM block_sessions WHERE startTime < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT * FROM block_sessions WHERE isStudyMode = 1 ORDER BY startTime DESC")
    fun observeAllStudySessions(): Flow<List<BlockSession>>

    @Query("SELECT * FROM block_sessions WHERE isStudyMode = 1 ORDER BY startTime DESC")
    suspend fun getAllStudySessions(): List<BlockSession>

    @Query("UPDATE block_sessions SET problemsSolvedDuringBlock = :solved, timeEarnedMs = :earnedMs WHERE id = :id")
    suspend fun updateCodeUnlockProgress(id: Long, solved: Int, earnedMs: Long)

    @Query("UPDATE block_sessions SET isActive = 0, wasCodeUnlocked = 1, problemsSolvedDuringBlock = :solved, timeEarnedMs = :earnedMs WHERE id = :id")
    suspend fun deactivateWithCodeUnlock(id: Long, solved: Int, earnedMs: Long)

    @Query("UPDATE block_sessions SET endTime = :newEndTime WHERE id = :id")
    suspend fun updateEndTime(id: Long, newEndTime: Long)

    @Query("UPDATE block_sessions SET initialGfgSolvedCount = :count WHERE id = :id")
    suspend fun updateGfgInitialCount(id: Long, count: Int)

    @Query("UPDATE block_sessions SET initialCodechefSolvedCount = :count WHERE id = :id")
    suspend fun updateCodechefInitialCount(id: Long, count: Int)
}
