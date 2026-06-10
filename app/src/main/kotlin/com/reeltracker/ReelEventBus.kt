package com.reeltracker

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Application-scoped event bus using SharedFlow.
 * Replaces broadcast-based IPC between ReelAccessibilityService and ReelTrackerService
 * for more reliable in-process communication.
 */
object ReelEventBus {

    // Reel detection events (Accessibility → Tracker)
    private val _reelEvents = MutableSharedFlow<ReelDetectedEvent>(extraBufferCapacity = 64)
    val reelEvents: SharedFlow<ReelDetectedEvent> = _reelEvents.asSharedFlow()

    // Unlock events (UI / Receiver → Tracker)
    private val _unlockEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val unlockEvents: SharedFlow<Unit> = _unlockEvents.asSharedFlow()

    suspend fun emitReelDetected(packageName: String) {
        _reelEvents.emit(ReelDetectedEvent(packageName))
    }

    fun tryEmitReelDetected(packageName: String): Boolean {
        return _reelEvents.tryEmit(ReelDetectedEvent(packageName))
    }

    suspend fun emitUnlock() {
        _unlockEvents.emit(Unit)
    }

    fun tryEmitUnlock(): Boolean {
        return _unlockEvents.tryEmit(Unit)
    }
}

data class ReelDetectedEvent(val packageName: String)
