package com.reeltracker.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "block_sessions")
data class BlockSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long, // epoch millis
    val endTime: Long,   // startTime + 6 hours
    val isActive: Boolean = true,
    val wasManuallyUnlocked: Boolean = false,
    val isStudyMode: Boolean = false
)
