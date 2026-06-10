package com.reeltracker.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_reel_counts")
data class DailyReelCount(
    @PrimaryKey
    val date: String, // Format: "YYYY-MM-DD"
    val instagramCount: Int = 0,
    val snapchatCount: Int = 0,
    val youtubeCount: Int = 0,
    val totalCount: Int = 0,
    val limitReached: Boolean = false,
    val limitValue: Int = 50
) {
    fun total() = instagramCount + snapchatCount + youtubeCount
}
