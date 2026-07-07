package com.reeltracker.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.reeltracker.data.dao.BlockSessionDao
import com.reeltracker.data.dao.CodingPlatformConfigDao
import com.reeltracker.data.dao.DailyReelCountDao
import com.reeltracker.data.dao.FocusModeDao
import com.reeltracker.data.entities.BlockSession
import com.reeltracker.data.entities.CodingPlatformConfig
import com.reeltracker.data.entities.DailyReelCount
import com.reeltracker.data.entities.FocusMode

@Database(
    entities = [DailyReelCount::class, BlockSession::class, FocusMode::class, CodingPlatformConfig::class],
    version = 6,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class ReelTrackerDatabase : RoomDatabase() {

    abstract fun dailyReelCountDao(): DailyReelCountDao
    abstract fun blockSessionDao(): BlockSessionDao
    abstract fun focusModeDao(): FocusModeDao
    abstract fun codingPlatformConfigDao(): CodingPlatformConfigDao

    companion object {
        @Volatile
        private var INSTANCE: ReelTrackerDatabase? = null

        @Suppress("DEPRECATION")
        fun getDatabase(context: Context): ReelTrackerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ReelTrackerDatabase::class.java,
                    "reel_tracker_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
