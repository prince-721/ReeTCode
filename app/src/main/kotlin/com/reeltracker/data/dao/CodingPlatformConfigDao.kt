package com.reeltracker.data.dao

import androidx.room.*
import com.reeltracker.data.entities.CodingPlatformConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface CodingPlatformConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: CodingPlatformConfig)

    @Query("SELECT * FROM coding_platform_configs WHERE id = 1 LIMIT 1")
    suspend fun getConfig(): CodingPlatformConfig?

    @Query("SELECT * FROM coding_platform_configs WHERE id = 1 LIMIT 1")
    fun observeConfig(): Flow<CodingPlatformConfig?>
}
