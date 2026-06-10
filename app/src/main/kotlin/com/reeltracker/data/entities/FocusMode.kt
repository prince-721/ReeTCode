package com.reeltracker.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_modes")
data class FocusMode(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isEnabled: Boolean = false,
    val durationHours: Int = 2,
    val activatedTime: Long = 0L,
    val blockedApps: List<String> = emptyList(),
    val allowedApps: List<String> = emptyList()
)
