package com.reeltracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeltracker.ui.components.CircularProgressRing
import com.reeltracker.ui.theme.*
import com.reeltracker.viewmodel.HomeUiState
import com.reeltracker.viewmodel.ReelTrackerViewModel
import com.reeltracker.viewmodel.CodingUnlockViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    viewModel: ReelTrackerViewModel,
    onNavigateToStudyMode: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val todayCount = uiState.todayCount
    val prefs = uiState.preferences
    val isBlocked = uiState.activeBlock != null

    val codingViewModel: CodingUnlockViewModel = viewModel()
    val codingState by codingViewModel.uiState.collectAsStateWithLifecycle()
    var showCodeSheet by remember { mutableStateOf(false) }

    val currentCount = todayCount?.totalCount ?: 0
    val dailyLimit = prefs.dailyLimit

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "ReetCode",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToStudyMode) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, "Study Mode")
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.BarChart, "History")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // Permission warnings
            val accessibilityEnabled = viewModel.isAccessibilityServiceEnabled(context)
            val overlayGranted = viewModel.isOverlayPermissionGranted(context)

            if (!accessibilityEnabled || !overlayGranted) {
                PermissionWarningCard(
                    accessibilityEnabled = accessibilityEnabled,
                    overlayGranted = overlayGranted,
                    context = context
                )
                Spacer(Modifier.height(16.dp))
            }

            // Tracking toggle
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (prefs.isTrackingEnabled)
                        Teal.copy(alpha = 0.15f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (prefs.isTrackingEnabled) Icons.Default.TrackChanges else Icons.Default.PauseCircle,
                            contentDescription = null,
                            tint = if (prefs.isTrackingEnabled) Teal else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (prefs.isTrackingEnabled) "Tracking Active" else "Tracking Paused",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = if (prefs.isTrackingEnabled) "Counting your reels" else "Not counting",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = prefs.isTrackingEnabled,
                        onCheckedChange = { viewModel.updateTrackingEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Teal
                        )
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // Big circular progress ring
            CircularProgressRing(
                current = currentCount,
                total = dailyLimit,
                size = 220.dp,
                strokeWidth = 18.dp,
                isBlocked = isBlocked
            )

            Spacer(Modifier.height(24.dp))

            val tempUnlockUntil = prefs.tempUnlockUntilMs
            val currentTime = System.currentTimeMillis()
            val hasTempUnlock = currentTime < tempUnlockUntil

            if (hasTempUnlock) {
                TempUnlockStatusCard(
                    tempUnlockUntilMs = tempUnlockUntil,
                    onUnlockMoreClick = {
                        uiState.activeBlock?.let {
                            codingViewModel.fetchProblemsSinceBlock(it)
                            showCodeSheet = true
                        }
                    }
                )
                Spacer(Modifier.height(24.dp))
            } else if (isBlocked && uiState.activeBlock != null) {
                BlockStatusCard(
                    activeBlock = uiState.activeBlock,
                    onUnlockClick = {
                        codingViewModel.fetchProblemsSinceBlock(uiState.activeBlock)
                        showCodeSheet = true
                    }
                )
                Spacer(Modifier.height(24.dp))
            }

            // App-specific counts
            if (todayCount != null && !isBlocked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AppCountChip(
                        emoji = "📸",
                        label = "Instagram",
                        count = todayCount.instagramCount,
                        color = InstagramPink
                    )
                    AppCountChip(
                        emoji = "👻",
                        label = "Snapchat",
                        count = todayCount.snapchatCount,
                        color = SnapchatYellow
                    )
                    AppCountChip(
                        emoji = "▶️",
                        label = "YouTube",
                        count = todayCount.youtubeCount,
                        color = YoutubeRed
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

            // Streak card
            StreakCard(streak = uiState.streak)

            Spacer(Modifier.height(16.dp))

            // Daily limit slider
            LimitSliderCard(
                currentLimit = dailyLimit,
                currentCount = currentCount,
                onLimitChange = { viewModel.updateDailyLimit(it) }
            )

            Spacer(Modifier.height(16.dp))

            // Study Mode card
            StudyModeCard(
                isActive = isBlocked && uiState.activeBlock?.isStudyMode == true,
                onClick = onNavigateToStudyMode
            )

            Spacer(Modifier.height(16.dp))

            // Quick stats
            if (uiState.weekHistory.isNotEmpty()) {
                QuickHistoryPreview(
                    weekHistory = uiState.weekHistory,
                    viewModel = viewModel,
                    onViewAll = onNavigateToHistory
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showCodeSheet && uiState.activeBlock != null) {
        CodeToUnlockSheet(
            state = codingState,
            activeBlock = uiState.activeBlock,
            onCheckNow = { block -> codingViewModel.fetchProblemsSinceBlock(block) },
            onClaimUnlock = { block -> codingViewModel.claimUnlock(block) },
            onDismiss = { showCodeSheet = false }
        )
    }
}

@Composable
private fun PermissionWarningCard(
    accessibilityEnabled: Boolean,
    overlayGranted: Boolean,
    context: android.content.Context
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = WarnOrange.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, WarnOrange.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = WarnOrange)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Permissions Required",
                    fontWeight = FontWeight.Bold,
                    color = WarnOrange
                )
            }
            Spacer(Modifier.height(8.dp))
            if (!accessibilityEnabled) {
                TextButton(
                    onClick = {
                        try {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            )
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Could not open accessibility settings.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    Icon(Icons.Default.Accessibility, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Enable Accessibility Service")
                }
            }
            if (!overlayGranted) {
                TextButton(
                    onClick = {
                        try {
                            context.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                            )
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Could not open overlay permission settings.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    Icon(Icons.Default.Layers, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Grant Overlay Permission")
                }
            }
        }
    }
}

@Composable
private fun AppCountChip(
    emoji: String,
    label: String,
    count: Int,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 22.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "$count",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StreakCard(streak: Int) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (streak > 0) Amber.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (streak > 0) "🔥" else "💤",
                fontSize = 32.sp
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = if (streak > 0) "$streak day streak!" else "No streak yet",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (streak > 0) "Days under your reel limit" else "Stay under your limit to start a streak",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LimitSliderCard(
    currentLimit: Int,
    currentCount: Int,
    onLimitChange: (Int) -> Unit
) {
    var sliderValue by remember(currentLimit) { mutableFloatStateOf(currentLimit.toFloat()) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Daily Limit",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Coral.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${sliderValue.toInt()} reels",
                        fontWeight = FontWeight.Bold,
                        color = Coral
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onLimitChange(sliderValue.toInt()) },
                valueRange = 5f..200f,
                steps = 38, // 5-step increments
                colors = SliderDefaults.colors(
                    thumbColor = Coral,
                    activeTrackColor = Coral,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("5", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("200", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QuickHistoryPreview(
    weekHistory: List<com.reeltracker.data.entities.DailyReelCount>,
    viewModel: ReelTrackerViewModel,
    onViewAll: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "This Week",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
                TextButton(onClick = onViewAll) {
                    Text("See All", color = Coral)
                }
            }
            Spacer(Modifier.height(8.dp))

            // Mini bar chart
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                weekHistory.sortedBy { it.date }.takeLast(7).forEach { day ->
                    val label = viewModel.getDateLabel(day.date)
                    val maxCount = weekHistory.maxOfOrNull { it.totalCount }?.coerceAtLeast(1) ?: 1
                    val fraction = day.totalCount.toFloat() / maxCount.toFloat()
                    MiniBar(
                        label = label,
                        fraction = fraction,
                        isOverLimit = day.totalCount >= day.limitValue && day.totalCount > 0
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniBar(label: String, fraction: Float, isOverLimit: Boolean) {
    val animFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(500),
        label = "minibar"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(36.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            )
            if (animFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(animFraction)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isOverLimit) BlockRed else Coral)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StudyModeCard(
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Purple.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isActive) BorderStroke(1.dp, Purple.copy(alpha = 0.4f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🎯",
                fontSize = 32.sp
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isActive) "Study Session Active" else "Distraction-Free Study Mode",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (isActive) "Tap to view remaining focus time" else "Immediately lock social media to focus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BlockStatusCard(
    activeBlock: com.reeltracker.data.entities.BlockSession,
    onUnlockClick: () -> Unit
) {
    var remainingMs by remember(activeBlock.endTime) {
        mutableLongStateOf(maxOf(0L, activeBlock.endTime - System.currentTimeMillis()))
    }

    LaunchedEffect(activeBlock.endTime) {
        while (remainingMs > 0) {
            delay(1000)
            remainingMs = maxOf(0L, activeBlock.endTime - System.currentTimeMillis())
        }
    }

    val hours = (remainingMs / 3600000).toInt()
    val mins = ((remainingMs % 3600000) / 60000).toInt()
    val secs = ((remainingMs % 60000) / 1000).toInt()
    val countdownStr = String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, mins, secs)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (activeBlock.isStudyMode) "Study Session Active" else "Social Apps Blocked",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Wait the remaining time or solve coding problems to unlock early:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = countdownStr,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "remaining time",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onUnlockClick,
                colors = ButtonDefaults.buttonColors(containerColor = Teal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🎯 Unlock by Coding", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TempUnlockStatusCard(
    tempUnlockUntilMs: Long,
    onUnlockMoreClick: () -> Unit
) {
    var remainingMs by remember(tempUnlockUntilMs) {
        mutableLongStateOf(maxOf(0L, tempUnlockUntilMs - System.currentTimeMillis()))
    }

    LaunchedEffect(tempUnlockUntilMs) {
        while (remainingMs > 0) {
            delay(1000)
            remainingMs = maxOf(0L, tempUnlockUntilMs - System.currentTimeMillis())
        }
    }

    val mins = (remainingMs / 60000).toInt()
    val secs = ((remainingMs % 60000) / 1000).toInt()
    val countdownStr = String.format(java.util.Locale.US, "%02d:%02d", mins, secs)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Teal.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, Teal.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.LockOpen,
                contentDescription = null,
                tint = Teal,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Earned Scroll Time Active",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Teal
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "You can scroll social media apps. Remaining earned time:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = countdownStr,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Teal
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "unlocked scroll time",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onUnlockMoreClick,
                colors = ButtonDefaults.buttonColors(containerColor = Teal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🎯 Earn More Time by Coding", fontWeight = FontWeight.Bold)
            }
        }
    }
}

