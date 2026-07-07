package com.reeltracker.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeltracker.data.entities.BlockSession
import com.reeltracker.service.SolvedProblem
import com.reeltracker.ui.theme.SuccessGreen
import com.reeltracker.ui.theme.Teal
import com.reeltracker.viewmodel.CodingUnlockUiState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeToUnlockSheet(
    state: CodingUnlockUiState,
    activeBlock: BlockSession,
    onCheckNow: (BlockSession) -> Unit,
    onClaimUnlock: (BlockSession) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A2E),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(40.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF444466))
            )
        }
    ) {
        // Congrats overlay
        if (state.showCongrats) {
            CongratsContent(
                message = state.congratsMessage,
                onDismiss = onDismiss
            )
        } else {
            SheetContent(
                state = state,
                activeBlock = activeBlock,
                onCheckNow = onCheckNow,
                onClaimUnlock = onClaimUnlock
            )
        }
    }
}

@Composable
private fun SheetContent(
    state: CodingUnlockUiState,
    activeBlock: BlockSession,
    onCheckNow: (BlockSession) -> Unit,
    onClaimUnlock: (BlockSession) -> Unit
) {
    var remainingTimeMs by remember(state.tempUnlockUntilMs) {
        mutableLongStateOf(maxOf(0L, state.tempUnlockUntilMs - System.currentTimeMillis()))
    }

    LaunchedEffect(state.tempUnlockUntilMs) {
        while (remainingTimeMs > 0) {
            delay(1000)
            remainingTimeMs = maxOf(0L, state.tempUnlockUntilMs - System.currentTimeMillis())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text("🎯", fontSize = 36.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Code to Unlock",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Solve coding problems to earn scroll time",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF888899),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        if (remainingTimeMs > 0) {
            val minutes = (remainingTimeMs / 60000)
            val seconds = ((remainingTimeMs % 60000) / 1000)
            val timeStr = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SuccessGreen.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, SuccessGreen.copy(alpha = 0.3f)),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Timer",
                        tint = SuccessGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$timeStr remaining",
                        color = SuccessGreen,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        } else {
            Spacer(Modifier.height(12.dp))
        }

        // Progress ring
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            val progress = if (state.problemsToFullUnlock > 0)
                (state.totalProblemsSolved.toFloat() / state.problemsToFullUnlock).coerceIn(0f, 1f)
            else 0f

            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(800, easing = FastOutSlowInEasing),
                label = "progress"
            )

            val ringColor = if (state.totalProblemsSolved >= state.problemsToFullUnlock)
                SuccessGreen else Teal
            val trackColor = Color(0xFF2A2A3E)

            Canvas(modifier = Modifier.fillMaxSize()) {
                val sw = 14.dp.toPx()
                val diameter = size.minDimension - sw
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)

                // Track
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = sw, cap = StrokeCap.Round)
                )

                // Progress
                if (animatedProgress > 0f) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = sw, cap = StrokeCap.Round)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${state.totalProblemsSolved}",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    "/ ${state.problemsToFullUnlock}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF888899)
                )
                Text(
                    "problems",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF666677)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Time earned
        val totalMinutes = (state.timeEarnedMs / 60_000).toInt()
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        val timeStr = when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            totalMinutes > 0 -> "${mins}m"
            else -> "0m"
        }
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF252540))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = Teal,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "You've earned $timeStr of scroll time",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Platform usernames
        if (state.leetcodeUsername.isNotBlank() && state.isLeetcodeVerified) {
            PlatformBadge(
                platform = "LeetCode",
                username = state.leetcodeUsername,
                problemCount = state.leetcodeProblems.size,
                color = Color(0xFFFFA116)
            )
            Spacer(Modifier.height(8.dp))
        }
        if (state.codechefUsername.isNotBlank() && state.isCodechefVerified) {
            PlatformBadge(
                platform = "CodeChef",
                username = state.codechefUsername,
                problemCount = state.codechefProblems.size,
                color = Color(0xFF5B4638)
            )
            Spacer(Modifier.height(8.dp))
        }
        if (state.gfgUsername.isNotBlank() && state.isGfgVerified) {
            PlatformBadge(
                platform = "GeeksforGeeks",
                username = state.gfgUsername,
                problemCount = state.gfgProblems.size,
                color = Color(0xFF2F8D46)
            )
            Spacer(Modifier.height(8.dp))
        }

        // Solved problems list
        if (state.allProblems.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "RECENTLY SOLVED",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF666677),
                letterSpacing = 1.5.sp,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
            ) {
                items(state.allProblems) { problem ->
                    SolvedProblemItem(problem)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Error message
        if (state.fetchError != null) {
            Text(
                state.fetchError,
                color = Color(0xFFFF6B6B),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
        }

        // Check Now button
        OutlinedButton(
            onClick = { onCheckNow(activeBlock) },
            enabled = !state.isFetchingProblems,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal)
        ) {
            if (state.isFetchingProblems) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Teal
                )
                Spacer(Modifier.width(8.dp))
                Text("Checking...")
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Check Now", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Claim Unlock button
        val newlySolved = maxOf(0, state.totalProblemsSolved - state.alreadyClaimed)
        val claimMinutes = newlySolved * state.minutesPerProblem

        Button(
            onClick = { onClaimUnlock(activeBlock) },
            enabled = state.canClaimUnlock && !state.isFetchingProblems,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.canClaimUnlock) SuccessGreen else Color(0xFF333344),
                disabledContainerColor = Color(0xFF333344)
            )
        ) {
            Icon(Icons.Default.LockOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                if (state.totalProblemsSolved >= state.problemsToFullUnlock)
                    "Claim Full Unlock"
                else if (newlySolved > 0)
                    "Claim ${claimMinutes}m Unlock"
                else
                    "Already Claimed",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PlatformBadge(
    platform: String,
    username: String,
    problemCount: Int,
    color: Color
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF252540))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (platform == "LeetCode") "LC" else if (platform == "CodeChef") "CC" else "GFG",
                    fontWeight = FontWeight.Bold,
                    color = color,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(platform, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                Text("@$username", color = Color(0xFF888899), fontSize = 12.sp)
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (problemCount > 0) SuccessGreen.copy(alpha = 0.15f) else Color(0xFF2A2A3E)
            ) {
                Text(
                    "$problemCount solved",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color = if (problemCount > 0) SuccessGreen else Color(0xFF666677),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SolvedProblemItem(problem: SolvedProblem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = SuccessGreen,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                problem.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            val timeStr = formatTimestamp(problem.timestamp)
            Text(
                timeStr,
                color = Color(0xFF666677),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun CongratsContent(
    message: String,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "celebrate")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size((100 * scale).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            SuccessGreen.copy(alpha = 0.3f),
                            SuccessGreen.copy(alpha = 0.05f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("🎉", fontSize = 48.sp)
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Congratulations!",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )

        Spacer(Modifier.height(12.dp))

        Text(
            message,
            fontSize = 18.sp,
            color = SuccessGreen,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Great work investing in your coding skills!",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF888899),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
        ) {
            Text("Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

private fun formatTimestamp(ms: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ms
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> {
            val sdf = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.US)
            sdf.format(java.util.Date(ms))
        }
    }
}
