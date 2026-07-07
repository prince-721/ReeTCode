package com.reeltracker.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ReelTrackerViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReelTrackerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReelTrackerViewModel(application) as T
        }
        if (modelClass.isAssignableFrom(CodingUnlockViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CodingUnlockViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
