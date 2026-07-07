package com.reeltracker.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.reeltracker.AppContainer
import com.reeltracker.service.ReelTrackerService
import com.reeltracker.ui.theme.ReelTrackerTheme
import com.reeltracker.ui.theme.BlockRed
import com.reeltracker.ui.theme.Coral
import com.reeltracker.ui.theme.Teal
import com.reeltracker.viewmodel.CodingUnlockViewModel
import com.reeltracker.viewmodel.ReelTrackerViewModel
import kotlinx.coroutines.*

class BlockingActivity : ComponentActivity() {

    private val viewModel: ReelTrackerViewModel by viewModels()
    private val codingViewModel: CodingUnlockViewModel by viewModels()

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ReelTrackerService.ACTION_DISMISS_BLOCK) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            RECEIVER_NOT_EXPORTED else 0
        registerReceiver(dismissReceiver, IntentFilter(ReelTrackerService.ACTION_DISMISS_BLOCK), flags)

        setContent {
            ReelTrackerTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val activeBlock = uiState.activeBlock
                val activeFocusMode by viewModel.activeFocusMode.collectAsStateWithLifecycle()
                val focusedModeRepo = remember { com.reeltracker.data.FocusedModeRepository(this@BlockingActivity) }
                
                var isFocusedShared by remember { mutableStateOf(focusedModeRepo.isFocused()) }
                val isAnyFocusActive = activeFocusMode != null || isFocusedShared

                if (activeBlock == null && !isAnyFocusActive) {
                    finish()
                    return@ReelTrackerTheme
                }

                if (activeBlock != null) {
                    val codingState by codingViewModel.uiState.collectAsStateWithLifecycle()
                    val showCodeUnlock = codingState.codeUnlockEnabled &&
                        (codingState.isLeetcodeVerified || codingState.isCodechefVerified)

                    BlockingScreen(
                        endTimeMs = activeBlock.endTime,
                        isStudyMode = activeBlock.isStudyMode,
                        showCodeToUnlock = showCodeUnlock,
                        codingState = codingState,
                        activeBlock = activeBlock,
                        onCodeToUnlockClick = {
                            codingViewModel.fetchProblemsSinceBlock(activeBlock)
                        },
                        onCheckNow = { block ->
                            codingViewModel.fetchProblemsSinceBlock(block)
                        },
                        onClaimUnlock = { block ->
                            codingViewModel.claimUnlock(block)
                        },
                        onDismissCongrats = {
                            codingViewModel.dismissCongrats()
                            finish()
                        },
                        onEmergencyUnlock = {
                            viewModel.manualUnlock(activeBlock.id)
                            finish()
                        }
                    )
                } else {
                    val focusModeName = activeFocusMode?.name ?: "Focus Mode"
                    val durationHours = activeFocusMode?.durationHours ?: 2
                    val activatedTime = activeFocusMode?.activatedTime ?: 0L
                    val endTime = if (activatedTime > 0L) {
                        activatedTime + (durationHours * 3600 * 1000L)
                    } else {
                        val startStr = focusedModeRepo.getStartTime()
                        if (startStr != null) {
                            try {
                                java.time.Instant.parse(startStr).toEpochMilli() + (2 * 3600 * 1000L)
                            } catch (e: Exception) {
                                System.currentTimeMillis() + (2 * 3600 * 1000L)
                            }
                        } else {
                            System.currentTimeMillis() + (2 * 3600 * 1000L)
                        }
                    }

                    FocusModeBlockingScreen(
                        name = focusModeName,
                        endTimeMs = endTime,
                        onDeactivate = {
                            if (activeFocusMode != null) {
                                viewModel.toggleFocusMode(activeFocusMode!!.id, false)
                            }
                            if (isFocusedShared) {
                                focusedModeRepo.setFocused(false)
                                isFocusedShared = false
                            }
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        sendBroadcast(Intent("com.reeltracker.ACTION_BLOCK_SCREEN_SHOWN"))

        // Assert block: check if any block session or focus session is active. If not, finish immediately.
        lifecycleScope.launch {
            val db = com.reeltracker.data.database.ReelTrackerDatabase.getDatabase(this@BlockingActivity)
            val activeBlock = AppContainer.repository.getActiveBlock()
            val activeFocusMode = db.focusModeDao().getActiveFocusMode()
            val focusedModeRepo = com.reeltracker.data.FocusedModeRepository(this@BlockingActivity)
            val isFocusedShared = focusedModeRepo.isFocused()

            if (activeBlock == null && activeFocusMode == null && !isFocusedShared) {
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sendBroadcast(Intent("com.reeltracker.ACTION_BLOCK_SCREEN_HIDDEN"))
    }

    override fun onDestroy() {
        super.onDestroy()
        sendBroadcast(Intent("com.reeltracker.ACTION_BLOCK_SCREEN_HIDDEN"))
        try { unregisterReceiver(dismissReceiver) } catch (e: Exception) { /* ignore */ }
    }

    // Prevent going back to the blocked app
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Do nothing - block is intentional
    }
}

@Composable
fun BlockingScreen(
    endTimeMs: Long,
    isStudyMode: Boolean,
    showCodeToUnlock: Boolean = false,
    codingState: com.reeltracker.viewmodel.CodingUnlockUiState? = null,
    activeBlock: com.reeltracker.data.entities.BlockSession? = null,
    onCodeToUnlockClick: () -> Unit = {},
    onCheckNow: (com.reeltracker.data.entities.BlockSession) -> Unit = {},
    onClaimUnlock: (com.reeltracker.data.entities.BlockSession) -> Unit = {},
    onDismissCongrats: () -> Unit = {},
    onEmergencyUnlock: () -> Unit
) {
    var remainingMs by remember { mutableLongStateOf(maxOf(0L, endTimeMs - System.currentTimeMillis())) }
    var showEmergencyConfirm by remember { mutableStateOf(false) }
    var showCodeSheet by remember { mutableStateOf(false) }
    val defaultCountdown = if (isStudyMode) 120 else 60
    var emergencyCountdown by remember(isStudyMode) { mutableIntStateOf(defaultCountdown) }
    var emergencyActive by remember { mutableStateOf(false) }

    // Live countdown timer
    LaunchedEffect(endTimeMs) {
        while (remainingMs > 0) {
            delay(1000)
            remainingMs = maxOf(0L, endTimeMs - System.currentTimeMillis())
        }
    }

    // Emergency unlock countdown
    LaunchedEffect(emergencyActive) {
        if (emergencyActive) {
            emergencyCountdown = defaultCountdown
            while (emergencyCountdown > 0) {
                delay(1000)
                emergencyCountdown--
            }
        }
    }

    val hours = remainingMs / 3_600_000
    val minutes = (remainingMs % 3_600_000) / 60_000
    val seconds = (remainingMs % 60_000) / 1_000

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1A0000),
                        Color(0xFF0D0D0D)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Lock icon with pulse
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(BlockRed.copy(alpha = 0.15f * pulseAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(BlockRed.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🔒", fontSize = 44.sp)
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = if (isStudyMode) "Study Session Active" else "Apps Blocked",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (isStudyMode) {
                    "Instagram, Snapchat & YouTube are locked.\nTake this time to focus on your studies!"
                } else {
                    "You've reached your daily reel limit.\nTake a break — your future self will thank you."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF999999),
                textAlign = TextAlign.Center,
                lineHeight = 26.sp
            )

            Spacer(Modifier.height(40.dp))

            // Countdown timer
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "UNLOCKS IN",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF666666),
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimeUnit(value = hours.toString().padStart(2, '0'), label = "HRS")
                        Text(":", color = BlockRed, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp))
                        TimeUnit(value = minutes.toString().padStart(2, '0'), label = "MIN")
                        Text(":", color = BlockRed, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp))
                        TimeUnit(value = seconds.toString().padStart(2, '0'), label = "SEC")
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Blocked apps list
            Text(
                "Blocked Apps",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF666666),
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BlockedAppChip("📸", "Instagram")
                BlockedAppChip("👻", "Snapchat")
                BlockedAppChip("▶️", "YouTube")
            }

            Spacer(Modifier.height(40.dp))

            // Code to Unlock button
            if (showCodeToUnlock) {
                Button(
                    onClick = {
                        onCodeToUnlockClick()
                        showCodeSheet = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Teal.copy(alpha = 0.15f)
                    )
                ) {
                    Text("🎯", fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Code to Unlock",
                            fontWeight = FontWeight.Bold,
                            color = Teal,
                            fontSize = 15.sp
                        )
                        Text(
                            "Solve ${codingState?.problemsToFullUnlock ?: 5} problems to unlock early",
                            color = Teal.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Emergency unlock
            if (!showEmergencyConfirm) {
                TextButton(
                    onClick = { showEmergencyConfirm = true }
                ) {
                    Icon(
                        Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = Color(0xFF555555),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Emergency Unlock",
                        color = Color(0xFF555555),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } else {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isStudyMode) "⚠️ Cancel Focus Session?" else "⚠️ Emergency Unlock",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (isStudyMode) {
                                "Are you sure you want to stop this study session? To prevent impulse distraction, you must wait 120 seconds to confirm."
                            } else {
                                "Are you sure? This will reset your block and allow access to social media apps. Please wait 60 seconds to confirm this isn't an impulse decision."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF888888),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        if (!emergencyActive) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        showEmergencyConfirm = false
                                        emergencyActive = false
                                    },
                                    border = BorderStroke(1.dp, Color(0xFF444444))
                                ) {
                                    Text("Cancel", color = Color(0xFF888888))
                                }
                                Button(
                                    onClick = { emergencyActive = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = BlockRed)
                                ) {
                                    Text(if (isStudyMode) "Start 120s Timer" else "Start 60s Timer")
                                }
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$emergencyCountdown",
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = BlockRed
                                )
                                Text(
                                    "seconds remaining",
                                    color = Color(0xFF666666),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(Modifier.height(12.dp))
                                if (emergencyCountdown == 0) {
                                    Button(
                                        onClick = onEmergencyUnlock,
                                        colors = ButtonDefaults.buttonColors(containerColor = BlockRed),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.LockOpen, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Unlock Now", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            showEmergencyConfirm = false
                                            emergencyActive = false
                                        },
                                        border = BorderStroke(1.dp, Color(0xFF444444)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Cancel", color = Color(0xFF888888))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Code to Unlock bottom sheet
    if (showCodeSheet && codingState != null && activeBlock != null) {
        CodeToUnlockSheet(
            state = codingState,
            activeBlock = activeBlock,
            onCheckNow = onCheckNow,
            onClaimUnlock = onClaimUnlock,
            onDismiss = {
                if (codingState.showCongrats) {
                    onDismissCongrats()
                }
                showCodeSheet = false
            }
        )
    }
}

@Composable
fun FocusModeBlockingScreen(
    name: String,
    endTimeMs: Long,
    onDeactivate: () -> Unit
) {
    var remainingMs by remember { mutableLongStateOf(maxOf(0L, endTimeMs - System.currentTimeMillis())) }

    // Live countdown timer
    LaunchedEffect(endTimeMs) {
        while (remainingMs > 0) {
            delay(1000)
            remainingMs = maxOf(0L, endTimeMs - System.currentTimeMillis())
        }
    }

    val hours = remainingMs / 3_600_000
    val minutes = (remainingMs % 3_600_000) / 60_000
    val seconds = (remainingMs % 60_000) / 1_000

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Slate 900
                        Color(0xFF1E1B4B)  // Indigo 950
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Pulse Icon
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF9B5DE5).copy(alpha = 0.15f * pulseAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF9B5DE5).copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎯", fontSize = 44.sp)
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = name,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Instagram, Snapchat & YouTube are locked.\nThis application is blocked because Focus Mode is active.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFCCCCCC),
                textAlign = TextAlign.Center,
                lineHeight = 26.sp
            )

            Spacer(Modifier.height(40.dp))

            // Countdown timer card if we have remaining time
            if (remainingMs > 0) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "ACTIVE FOR",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8),
                            letterSpacing = 2.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TimeUnit(value = hours.toString().padStart(2, '0'), label = "HRS")
                            Text(":", color = Color(0xFF9B5DE5), fontSize = 32.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp))
                            TimeUnit(value = minutes.toString().padStart(2, '0'), label = "MIN")
                            Text(":", color = Color(0xFF9B5DE5), fontSize = 32.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp))
                            TimeUnit(value = seconds.toString().padStart(2, '0'), label = "SEC")
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }

            // Blocked apps list
            Text(
                "Blocked Apps",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF666666),
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BlockedAppChip("📸", "Instagram")
                BlockedAppChip("👻", "Snapchat")
                BlockedAppChip("▶️", "YouTube")
            }

            Spacer(Modifier.height(40.dp))

            // Deactivate and navigation buttons
            Button(
                onClick = onDeactivate,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B5DE5)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Deactivate $name", fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(Modifier.height(12.dp))

            val context = LocalContext.current
            OutlinedButton(
                onClick = {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.3f)
                )
            ) {
                Text("Go to ReetCode", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TimeUnit(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF666666),
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun BlockedAppChip(emoji: String, name: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 24.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF555555)
        )
    }
}

