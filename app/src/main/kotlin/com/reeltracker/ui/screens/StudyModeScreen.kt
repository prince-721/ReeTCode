package com.reeltracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeltracker.data.entities.BlockSession
import com.reeltracker.ui.theme.BlockRed
import com.reeltracker.ui.theme.Coral
import com.reeltracker.ui.theme.Teal
import com.reeltracker.ui.theme.Purple
import com.reeltracker.ui.theme.Amber
import com.reeltracker.viewmodel.HomeUiState
import com.reeltracker.viewmodel.ReelTrackerViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyModeScreen(
    uiState: HomeUiState,
    viewModel: ReelTrackerViewModel,
    onBack: () -> Unit
) {
    val activeBlock = uiState.activeBlock
    val isStudyActive = activeBlock != null && activeBlock.isStudyMode

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Study Mode",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isStudyActive) {
                ActiveStudySessionView(
                    activeBlock = activeBlock!!,
                    viewModel = viewModel
                )
            } else {
                StudySessionSetupView(
                    onStartSession = { duration ->
                        viewModel.startStudySession(duration)
                    }
                )
            }
        }
    }
}

@Composable
fun StudySessionSetupView(
    onStartSession: (Int) -> Unit
) {
    var selectedDuration by remember { mutableIntStateOf(30) }
    var customDuration by remember { mutableFloatStateOf(45f) }
    val presets = listOf(15, 30, 60)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Icon Circle
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Purple.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text("🎯", fontSize = 44.sp)
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Distraction-Free Focus",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Lock yourself out of Instagram, Snapchat, and YouTube immediately. No limits to count — just pure focused time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(36.dp))

        // Preset cards
        Text(
            "Quick Select Duration",
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            presets.forEach { minutes ->
                val isSelected = selectedDuration == minutes
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Purple else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedDuration = minutes }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$minutes",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "MINS",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // Custom duration slider
        val isCustomSelected = selectedDuration !in presets
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isCustomSelected) Purple.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = if (isCustomSelected) BorderStroke(1.5.dp, Purple) else null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Custom Duration",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Purple.copy(alpha = 0.2f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${customDuration.toInt()} mins",
                            fontWeight = FontWeight.Bold,
                            color = Purple
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Slider(
                    value = customDuration,
                    onValueChange = {
                        customDuration = it
                        selectedDuration = 0 // Select custom
                    },
                    valueRange = 5f..180f,
                    colors = SliderDefaults.colors(
                        thumbColor = Purple,
                        activeTrackColor = Purple,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        // Start button
        Button(
            onClick = {
                val duration = if (selectedDuration in presets) selectedDuration else customDuration.toInt()
                onStartSession(duration)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Purple),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(
                "Start Study Session",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun ActiveStudySessionView(
    activeBlock: BlockSession,
    viewModel: ReelTrackerViewModel
) {
    var remainingMs by remember { mutableLongStateOf(maxOf(0L, activeBlock.endTime - System.currentTimeMillis())) }
    var showEmergencyConfirm by remember { mutableStateOf(false) }
    var emergencyCountdown by remember { mutableIntStateOf(120) }
    var emergencyActive by remember { mutableStateOf(false) }

    // Live countdown timer
    LaunchedEffect(activeBlock.endTime) {
        while (remainingMs > 0) {
            delay(1000)
            remainingMs = maxOf(0L, activeBlock.endTime - System.currentTimeMillis())
        }
    }

    // Emergency unlock countdown (120s wait)
    LaunchedEffect(emergencyActive) {
        if (emergencyActive) {
            emergencyCountdown = 120
            while (emergencyCountdown > 0) {
                delay(1000)
                emergencyCountdown--
            }
        }
    }

    val totalDurationMs = activeBlock.endTime - activeBlock.startTime
    val progressFraction = if (totalDurationMs > 0) {
        (remainingMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val minutes = (remainingMs / 60_000)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Pulse Icon
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .background(Purple.copy(alpha = 0.1f * pulseAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Purple.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text("🎯", fontSize = 48.sp)
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            "Study Mode Active",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Purple
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Instagram, Snapchat, and YouTube are locked.\nKeep focusing on your tasks!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(40.dp))

        // Circular Countdown Progress
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(200.dp)
        ) {
            CircularProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 12.dp,
                color = Purple,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("%02d:%02d", minutes, seconds),
                    fontWeight = FontWeight.Black,
                    fontSize = 38.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "REMAINING",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(Modifier.height(48.dp))

        // Emergency unlock section (120s delay)
        if (!showEmergencyConfirm) {
            OutlinedButton(
                onClick = { showEmergencyConfirm = true },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Cancel Study Session",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "⚠️ Cancel Focus Session?",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = BlockRed
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Giving up early requires waiting 120 seconds to help prevent impulse distraction decisions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text("Go Back", color = MaterialTheme.colorScheme.onSurface)
                            }
                            Button(
                                onClick = { emergencyActive = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BlockRed)
                            ) {
                                Text("Start 120s Delay")
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(16.dp))
                            if (emergencyCountdown == 0) {
                                Button(
                                    onClick = {
                                        viewModel.manualUnlock(activeBlock.id)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BlockRed),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.LockOpen, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Stop Session Now", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        showEmergencyConfirm = false
                                        emergencyActive = false
                                    },
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
