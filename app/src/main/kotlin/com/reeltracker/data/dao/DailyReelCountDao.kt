package com.reeltracker.data.dao

import androidx.room.*
import com.reeltracker.data.entities.DailyReelCount
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyReelCountDao {

    @Query("SELECT * FROM daily_reel_counts WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailyReelCount?

    @Query("SELECT * FROM daily_reel_counts WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<DailyReelCount?>

    @Query("SELECT * FROM daily_reel_counts ORDER BY date DESC LIMIT 7")
    fun observeLastSevenDays(): Flow<List<DailyReelCount>>

    @Query("SELECT * FROM daily_reel_counts ORDER BY date DESC LIMIT 7")
    suspend fun getLastSevenDays(): List<DailyReelCount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(count: DailyReelCount)

    @Query("UPDATE daily_reel_counts SET instagramCount = instagramCount + 1, totalCount = totalCount + 1 WHERE date = :date")
    suspend fun incrementInstagram(date: String)

    @Query("UPDATE daily_reel_counts SET snapchatCount = snapchatCount + 1, totalCount = totalCount + 1 WHERE date = :date")
    suspend fun incrementSnapchat(date: String)

    @Query("UPDATE daily_reel_counts SET youtubeCount = youtubeCount + 1, totalCount = totalCount + 1 WHERE date = :date")
    suspend fun incrementYoutube(date: String)

    @Query("UPDATE daily_reel_counts SET limitReached = 1 WHERE date = :date")
    suspend fun markLimitReached(date: String)

    @Query("DELETE FROM daily_reel_counts WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)

    @Query("SELECT * FROM daily_reel_counts WHERE date < :today ORDER BY date DESC")
    suspend fun getAllBeforeToday(today: String): List<DailyReelCount>
}
