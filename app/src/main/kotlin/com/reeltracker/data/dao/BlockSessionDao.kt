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
}
