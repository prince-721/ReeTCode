package com.reeltracker.data.dao

import androidx.room.*
import com.reeltracker.data.entities.FocusMode
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusModeDao {
    @Query("SELECT * FROM focus_modes")
    fun getAllFocusModesFlow(): Flow<List<FocusMode>>

    @Query("SELECT * FROM focus_modes")
    suspend fun getAllFocusModes(): List<FocusMode>

    @Query("SELECT * FROM focus_modes WHERE isEnabled = 1")
    fun getActiveFocusModesFlow(): Flow<List<FocusMode>>

    @Query("SELECT * FROM focus_modes WHERE isEnabled = 1 LIMIT 1")
    suspend fun getActiveFocusMode(): FocusMode?

    @Query("SELECT * FROM focus_modes WHERE id = :id")
    suspend fun getFocusModeById(id: Long): FocusMode?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(focusMode: FocusMode): Long

    @Update
    suspend fun update(focusMode: FocusMode)

    @Delete
    suspend fun delete(focusMode: FocusMode)

    @Query("UPDATE focus_modes SET isEnabled = 0")
    suspend fun disableAllFocusModes()
}
