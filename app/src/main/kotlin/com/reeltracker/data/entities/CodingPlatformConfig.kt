package com.reeltracker.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "coding_platform_configs")
data class CodingPlatformConfig(
    @PrimaryKey
    val id: Int = 1,
    val leetcodeUsername: String = "",
    val codechefUsername: String = "",
    val gfgUsername: String = "",
    val isLeetcodeVerified: Boolean = false,
    val isCodechefVerified: Boolean = false,
    val isGfgVerified: Boolean = false
)
