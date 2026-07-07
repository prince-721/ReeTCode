package com.reeltracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reeltracker.AppContainer
import com.reeltracker.data.UserPreferences
import com.reeltracker.data.entities.BlockSession
import com.reeltracker.service.CodingPlatformService
import com.reeltracker.service.CodingResult
import com.reeltracker.service.PlatformProfile
import com.reeltracker.service.SolvedProblem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CodingUnlockUiState(
    val leetcodeUsername: String = "",
    val codechefUsername: String = "",
    val gfgUsername: String = "",
    val isLeetcodeVerified: Boolean = false,
    val isCodechefVerified: Boolean = false,
    val isGfgVerified: Boolean = false,
    val codeUnlockEnabled: Boolean = false,
    val problemsToFullUnlock: Int = 5,
    val minutesPerProblem: Int = 30,
    val useSameUsername: Boolean = false,
    val leetcodeVerificationCode: String? = null,
    val codechefVerificationCode: String? = null,
    val gfgVerificationCode: String? = null,
    // Verification state
    val isVerifyingLeetcode: Boolean = false,
    val isVerifyingCodechef: Boolean = false,
    val isVerifyingGfg: Boolean = false,
    val leetcodeProfile: PlatformProfile? = null,
    val codechefProfile: PlatformProfile? = null,
    val gfgProfile: PlatformProfile? = null,
    val verifyError: String? = null,
    // Fetch problems state
    val isFetchingProblems: Boolean = false,
    val leetcodeProblems: List<SolvedProblem> = emptyList(),
    val codechefProblems: List<SolvedProblem> = emptyList(),
    val gfgProblems: List<SolvedProblem> = emptyList(),
    val fetchError: String? = null,
    val totalProblemsSolved: Int = 0,
    val timeEarnedMs: Long = 0,
    val alreadyClaimed: Int = 0,
    val tempUnlockUntilMs: Long = 0L,
    // Claim state
    val showCongrats: Boolean = false,
    val congratsMessage: String = ""
) {
    val canClaimUnlock: Boolean
        get() = totalProblemsSolved > alreadyClaimed

    val allProblems: List<SolvedProblem>
        get() = (leetcodeProblems + codechefProblems + gfgProblems).sortedByDescending { it.timestamp }
}

class CodingUnlockViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepository = AppContainer.prefsRepository
    private val repository = AppContainer.repository

    private val _uiState = MutableStateFlow(CodingUnlockUiState())
    val uiState: StateFlow<CodingUnlockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefsRepository.userPreferencesFlow.collect { prefs ->
                _uiState.update { state ->
                    state.copy(
                        leetcodeUsername = prefs.leetcodeUsername,
                        codechefUsername = prefs.codechefUsername,
                        gfgUsername = prefs.gfgUsername,
                        isLeetcodeVerified = prefs.isLeetcodeVerified,
                        isCodechefVerified = prefs.isCodechefVerified,
                        isGfgVerified = prefs.isGfgVerified,
                        codeUnlockEnabled = prefs.codeUnlockEnabled,
                        problemsToFullUnlock = prefs.problemsToFullUnlock,
                        minutesPerProblem = prefs.minutesPerProblem,
                        useSameUsername = prefs.useSameUsername,
                        tempUnlockUntilMs = prefs.tempUnlockUntilMs
                    )
                }
                repository.saveCodingConfig(
                    com.reeltracker.data.entities.CodingPlatformConfig(
                        leetcodeUsername = prefs.leetcodeUsername,
                        codechefUsername = prefs.codechefUsername,
                        gfgUsername = prefs.gfgUsername,
                        isLeetcodeVerified = prefs.isLeetcodeVerified,
                        isCodechefVerified = prefs.isCodechefVerified,
                        isGfgVerified = prefs.isGfgVerified
                    )
                )
            }
        }
    }

    // ---- Settings actions ----

    fun updateCodeUnlockEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.updateCodeUnlockEnabled(enabled) }
    }

    fun updateLeetcodeUsername(username: String) {
        viewModelScope.launch {
            prefsRepository.updateLeetcodeUsername(username)
            if (_uiState.value.useSameUsername) {
                prefsRepository.updateCodechefUsername(username)
                prefsRepository.updateGfgUsername(username)
            }
        }
    }

    fun updateCodechefUsername(username: String) {
        viewModelScope.launch {
            prefsRepository.updateCodechefUsername(username)
            if (_uiState.value.useSameUsername) {
                prefsRepository.updateLeetcodeUsername(username)
                prefsRepository.updateGfgUsername(username)
            }
        }
    }

    fun updateGfgUsername(username: String) {
        viewModelScope.launch {
            prefsRepository.updateGfgUsername(username)
            if (_uiState.value.useSameUsername) {
                prefsRepository.updateLeetcodeUsername(username)
                prefsRepository.updateCodechefUsername(username)
            }
        }
    }

    fun updateUseSameUsername(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.updateUseSameUsername(enabled)
            if (enabled) {
                val currentVal = _uiState.value.leetcodeUsername.ifEmpty {
                    _uiState.value.codechefUsername.ifEmpty { _uiState.value.gfgUsername }
                }
                if (currentVal.isNotEmpty()) {
                    prefsRepository.updateLeetcodeUsername(currentVal)
                    prefsRepository.updateCodechefUsername(currentVal)
                    prefsRepository.updateGfgUsername(currentVal)
                }
            }
        }
    }

    fun updateProblemsToFullUnlock(count: Int) {
        viewModelScope.launch { prefsRepository.updateProblemsToFullUnlock(count) }
    }

    fun updateMinutesPerProblem(minutes: Int) {
        viewModelScope.launch { prefsRepository.updateMinutesPerProblem(minutes) }
    }

    fun verifyLeetcode() {
        val username = _uiState.value.leetcodeUsername
        if (username.isBlank()) return

        _uiState.update { it.copy(isVerifyingLeetcode = true, verifyError = null) }

        viewModelScope.launch {
            when (val result = CodingPlatformService.verifyLeetCodeUsername(username)) {
                is CodingResult.Success -> {
                    val code = "ReetCode-${(1000..9999).random()}"
                    _uiState.update {
                        it.copy(
                            isVerifyingLeetcode = false,
                            leetcodeProfile = result.data,
                            leetcodeVerificationCode = code,
                            isLeetcodeVerified = false
                        )
                    }
                    prefsRepository.updateLeetcodeVerified(false)
                }
                is CodingResult.Error -> {
                    prefsRepository.updateLeetcodeVerified(false)
                    _uiState.update {
                        it.copy(
                            isVerifyingLeetcode = false,
                            verifyError = result.message,
                            isLeetcodeVerified = false,
                            leetcodeVerificationCode = null
                        )
                    }
                }
            }
        }
    }

    fun confirmLeetcodeBio() {
        val username = _uiState.value.leetcodeUsername
        val code = _uiState.value.leetcodeVerificationCode
        if (username.isBlank() || code == null) return

        _uiState.update { it.copy(isVerifyingLeetcode = true, verifyError = null) }

        viewModelScope.launch {
            when (val result = CodingPlatformService.verifyLeetCodeUsername(username)) {
                is CodingResult.Success -> {
                    val profile = result.data
                    if (profile.bio.contains(code, ignoreCase = true)) {
                        prefsRepository.updateLeetcodeVerified(true)
                        _uiState.update {
                            it.copy(
                                isVerifyingLeetcode = false,
                                leetcodeProfile = profile,
                                isLeetcodeVerified = true,
                                leetcodeVerificationCode = null
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isVerifyingLeetcode = false,
                                verifyError = "Code '$code' not found in bio. Please update your LeetCode profile 'About me' (bio) and try again."
                            )
                        }
                    }
                }
                is CodingResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isVerifyingLeetcode = false,
                            verifyError = result.message
                        )
                    }
                }
            }
        }
    }

    fun verifyCodechef() {
        val username = _uiState.value.codechefUsername
        if (username.isBlank()) return

        _uiState.update { it.copy(isVerifyingCodechef = true, verifyError = null) }

        viewModelScope.launch {
            when (val result = CodingPlatformService.verifyCodeChefUsername(username)) {
                is CodingResult.Success -> {
                    val code = "ReetCode-${(1000..9999).random()}"
                    _uiState.update {
                        it.copy(
                            isVerifyingCodechef = false,
                            codechefProfile = result.data,
                            codechefVerificationCode = code,
                            isCodechefVerified = false
                        )
                    }
                    prefsRepository.updateCodechefVerified(false)
                }
                is CodingResult.Error -> {
                    prefsRepository.updateCodechefVerified(false)
                    _uiState.update {
                        it.copy(
                            isVerifyingCodechef = false,
                            verifyError = result.message,
                            isCodechefVerified = false,
                            codechefVerificationCode = null
                        )
                    }
                }
            }
        }
    }

    fun confirmCodechefBio() {
        val username = _uiState.value.codechefUsername
        val code = _uiState.value.codechefVerificationCode
        if (username.isBlank() || code == null) return

        _uiState.update { it.copy(isVerifyingCodechef = true, verifyError = null) }

        viewModelScope.launch {
            when (val result = CodingPlatformService.verifyCodeChefUsername(username)) {
                is CodingResult.Success -> {
                    val profile = result.data
                    if (profile.bio.contains(code, ignoreCase = true)) {
                        prefsRepository.updateCodechefVerified(true)
                        _uiState.update {
                            it.copy(
                                isVerifyingCodechef = false,
                                codechefProfile = profile,
                                isCodechefVerified = true,
                                codechefVerificationCode = null
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isVerifyingCodechef = false,
                                verifyError = "Code '$code' not found in Full Name. Please update your CodeChef profile Full Name and try again."
                            )
                        }
                    }
                }
                is CodingResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isVerifyingCodechef = false,
                            verifyError = result.message
                        )
                    }
                }
            }
        }
    }

    fun verifyGfg() {
        val username = _uiState.value.gfgUsername
        if (username.isBlank()) return

        _uiState.update { it.copy(isVerifyingGfg = true, verifyError = null) }

        viewModelScope.launch {
            when (val result = CodingPlatformService.verifyGfgUsername(username)) {
                is CodingResult.Success -> {
                    val code = "ReetCode-${(1000..9999).random()}"
                    _uiState.update {
                        it.copy(
                            isVerifyingGfg = false,
                            gfgProfile = result.data,
                            gfgVerificationCode = code,
                            isGfgVerified = false
                        )
                    }
                    prefsRepository.updateGfgVerified(false)
                }
                is CodingResult.Error -> {
                    prefsRepository.updateGfgVerified(false)
                    _uiState.update {
                        it.copy(
                            isVerifyingGfg = false,
                            verifyError = result.message,
                            isGfgVerified = false,
                            gfgVerificationCode = null
                        )
                    }
                }
            }
        }
    }

    fun confirmGfgBio() {
        val username = _uiState.value.gfgUsername
        val code = _uiState.value.gfgVerificationCode
        if (username.isBlank() || code == null) return

        _uiState.update { it.copy(isVerifyingGfg = true, verifyError = null) }

        viewModelScope.launch {
            when (val result = CodingPlatformService.verifyGfgUsername(username)) {
                is CodingResult.Success -> {
                    val profile = result.data
                    if (profile.bio.contains(code, ignoreCase = true)) {
                        prefsRepository.updateGfgVerified(true)
                        _uiState.update {
                            it.copy(
                                isVerifyingGfg = false,
                                gfgProfile = profile,
                                isGfgVerified = true,
                                gfgVerificationCode = null
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                  isVerifyingGfg = false,
                                  verifyError = "Code '$code' not found in bio. Please update your GeeksforGeeks profile info/description and try again."
                            )
                        }
                    }
                }
                is CodingResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isVerifyingGfg = false,
                            verifyError = result.message
                        )
                    }
                }
            }
        }
    }

    // ---- Blocking screen actions ----

    fun fetchProblemsSinceBlock(activeBlock: BlockSession) {
        _uiState.update { it.copy(isFetchingProblems = true, fetchError = null) }

        viewModelScope.launch {
            val state = _uiState.value
            val blockStartTime = activeBlock.startTime
            val alreadyClaimed = activeBlock.problemsSolvedDuringBlock

            var leetcodeProblems = emptyList<SolvedProblem>()
            var codechefProblems = emptyList<SolvedProblem>()
            var gfgProblems = emptyList<SolvedProblem>()
            var error: String? = null

            // Fetch LeetCode
            if (state.leetcodeUsername.isNotBlank() && state.isLeetcodeVerified) {
                when (val result = CodingPlatformService.fetchLeetCodeRecentSubmissions(
                    state.leetcodeUsername, blockStartTime
                )) {
                    is CodingResult.Success -> leetcodeProblems = result.data
                    is CodingResult.Error -> error = result.message
                }
            }

            // Fetch CodeChef
            if (state.codechefUsername.isNotBlank() && state.isCodechefVerified) {
                when (val result = CodingPlatformService.fetchCodeChefSolvedCount(state.codechefUsername)) {
                    is CodingResult.Success -> {
                        val currentCount = result.data
                        if (activeBlock.initialCodechefSolvedCount < 0) {
                            // Save initial baseline count in database
                            repository.updateCodechefInitialCount(activeBlock.id, currentCount)
                        } else {
                            val newlySolved = maxOf(0, currentCount - activeBlock.initialCodechefSolvedCount)
                            val problemsList = mutableListOf<SolvedProblem>()
                            for (i in 1..newlySolved) {
                                problemsList.add(
                                    SolvedProblem(
                                        title = "CodeChef Problem #$i",
                                        titleSlug = "codechef_problem_${activeBlock.startTime}_$i",
                                        timestamp = System.currentTimeMillis() - (i * 60 * 1000L)
                                    )
                                )
                            }
                            codechefProblems = problemsList
                        }
                    }
                    is CodingResult.Error -> {
                        if (error == null) error = result.message
                    }
                }
            }

            // Fetch GeeksforGeeks
            if (state.gfgUsername.isNotBlank() && state.isGfgVerified) {
                when (val result = CodingPlatformService.fetchGfgSolvedCount(state.gfgUsername)) {
                    is CodingResult.Success -> {
                        val currentCount = result.data
                        if (activeBlock.initialGfgSolvedCount < 0) {
                            // Save initial baseline count in database
                            repository.updateGfgInitialCount(activeBlock.id, currentCount)
                        } else {
                            val newlySolved = maxOf(0, currentCount - activeBlock.initialGfgSolvedCount)
                            val problemsList = mutableListOf<SolvedProblem>()
                            for (i in 1..newlySolved) {
                                problemsList.add(
                                    SolvedProblem(
                                        title = "GFG Problem #$i",
                                        titleSlug = "gfg_problem_${activeBlock.startTime}_$i",
                                        timestamp = System.currentTimeMillis() - (i * 60 * 1000L)
                                    )
                                )
                            }
                            gfgProblems = problemsList
                        }
                    }
                    is CodingResult.Error -> {
                        if (error == null) error = result.message
                    }
                }
            }

            val totalSolved = leetcodeProblems.size + codechefProblems.size + gfgProblems.size
            val minutesEarned = minOf(totalSolved, state.problemsToFullUnlock) * state.minutesPerProblem
            val earnedMs = minutesEarned * 60 * 1000L

            _uiState.update {
                it.copy(
                    isFetchingProblems = false,
                    leetcodeProblems = leetcodeProblems,
                    codechefProblems = codechefProblems,
                    gfgProblems = gfgProblems,
                    fetchError = error,
                    totalProblemsSolved = totalSolved,
                    timeEarnedMs = earnedMs,
                    alreadyClaimed = alreadyClaimed
                )
            }
        }
    }

    fun claimUnlock(activeBlock: BlockSession) {
        viewModelScope.launch {
            val state = _uiState.value
            val totalSolved = state.totalProblemsSolved
            // Number of problems already claimed in this block session
            val alreadyClaimed = activeBlock.problemsSolvedDuringBlock
            val newlySolved = maxOf(0, totalSolved - alreadyClaimed)
            if (newlySolved <= 0) return@launch

            val minutesEarned = newlySolved * state.minutesPerProblem
            val earnedMs = minutesEarned * 60 * 1000L

            val totalSolvedAccumulated = totalSolved
            val totalEarnedMsAccumulated = activeBlock.timeEarnedMs + earnedMs

            // Set temp unlock in preferences
            val currentTempUnlock = prefsRepository.getTempUnlockUntilMs()
            val baseTime = maxOf(System.currentTimeMillis(), currentTempUnlock)
            prefsRepository.updateTempUnlockUntilMs(baseTime + earnedMs)

            if (totalSolvedAccumulated >= state.problemsToFullUnlock) {
                // Fully dismiss the block
                repository.codeUnlockBlock(activeBlock.id, totalSolvedAccumulated, totalEarnedMsAccumulated)
                // Clear temp unlock since block is fully dismissed
                prefsRepository.updateTempUnlockUntilMs(0L)
            } else {
                // Save progress in DB block session
                repository.reduceBlockTime(activeBlock.id, totalSolvedAccumulated, totalEarnedMsAccumulated, activeBlock.endTime)
            }

            val hours = minutesEarned / 60
            val mins = minutesEarned % 60
            val timeStr = when {
                hours > 0 && mins > 0 -> "${hours}h ${mins}m"
                hours > 0 -> "${hours}h"
                else -> "${mins}m"
            }

            _uiState.update {
                it.copy(
                    showCongrats = true,
                    congratsMessage = "🎉 You earned $timeStr of scroll time!"
                )
            }
        }
    }

    fun dismissCongrats() {
        _uiState.update { it.copy(showCongrats = false) }
    }

    fun clearVerifyError() {
        _uiState.update { it.copy(verifyError = null) }
    }

    fun clearFetchError() {
        _uiState.update { it.copy(fetchError = null) }
    }
}
