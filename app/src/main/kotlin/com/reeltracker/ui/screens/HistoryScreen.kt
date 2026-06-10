package com.reeltracker.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeltracker.data.entities.BlockSession
import com.reeltracker.data.entities.DailyReelCount
import com.reeltracker.ui.components.WeeklyBarChart
import com.reeltracker.ui.theme.*
import com.reeltracker.viewmodel.HomeUiState
import com.reeltracker.viewmodel.ReelTrackerViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    uiState: HomeUiState,
    viewModel: ReelTrackerViewModel,
    onBack: () -> Unit
) {
    val history = uiState.weekHistory.sortedBy { it.date }
    val limit = uiState.preferences.dailyLimit

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("History", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Summary stats
            val totalWeek = history.sumOf { it.totalCount }
            val avgDaily = if (history.isNotEmpty()) totalWeek / history.size else 0
            val daysOverLimit = history.count { it.totalCount >= it.limitValue && it.totalCount > 0 }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = "This Week",
                    value = "$totalWeek",
                    subtitle = "total reels",
                    color = Coral,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Daily Avg",
                    value = "$avgDaily",
                    subtitle = "reels/day",
                    color = Teal,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Over Limit",
                    value = "$daysOverLimit",
                    subtitle = "days",
                    color = if (daysOverLimit > 0) BlockRed else SuccessGreen,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(24.dp))

            // Bar chart
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Daily Reel Count",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Last 7 days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))

                    val chartData = (0..6).map { daysAgo ->
                        val date = LocalDate.now().minusDays(daysAgo.toLong())
                        val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        val label = viewModel.getDateLabel(dateStr)
                        val data = history.find { it.date == dateStr }
                        label to data
                    }.reversed()

                    WeeklyBarChart(
                        data = chartData,
                        modifier = Modifier.height(160.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    // Legend
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendItem(color = Coral, label = "Under limit")
                        LegendItem(color = WarnOrange, label = "Near limit")
                        LegendItem(color = BlockRed, label = "Over limit")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Day-by-day breakdown
            Text(
                "Day Breakdown",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(12.dp))

            history.sortedByDescending { it.date }.forEach { day ->
                val daySessions = uiState.studySessions.filter { session ->
                    val sessionDate = try {
                        java.time.Instant.ofEpochMilli(session.startTime)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    } catch (e: Exception) {
                        ""
                    }
                    sessionDate == day.date
                }
                DayBreakdownCard(
                    day = day,
                    label = viewModel.getDateLabel(day.date),
                    studySessions = daySessions
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                color = color
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DayBreakdownCard(
    day: DailyReelCount,
    label: String,
    studySessions: List<BlockSession>
) {
    val isOverLimit = day.totalCount >= day.limitValue && day.totalCount > 0
    val progressFraction = if (day.limitValue > 0) {
        (day.totalCount.toFloat() / day.limitValue.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isOverLimit) "🔴" else if (progressFraction > 0.7f) "🟡" else "🟢", fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(label, fontWeight = FontWeight.SemiBold)
                        Text(
                            day.date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    "${day.totalCount} / ${day.limitValue}",
                    fontWeight = FontWeight.Bold,
                    color = if (isOverLimit) BlockRed else MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isOverLimit) BlockRed else if (progressFraction > 0.7f) WarnOrange else Coral,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
            if (day.instagramCount > 0 || day.snapchatCount > 0 || day.youtubeCount > 0) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (day.instagramCount > 0) Text("📸 ${day.instagramCount}", style = MaterialTheme.typography.labelSmall)
                    if (day.snapchatCount > 0) Text("👻 ${day.snapchatCount}", style = MaterialTheme.typography.labelSmall)
                    if (day.youtubeCount > 0) Text("▶️ ${day.youtubeCount}", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (studySessions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Study Sessions",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    studySessions.forEach { session ->
                        val durationMins = (session.endTime - session.startTime) / 60000
                        val statusText = when {
                            session.isActive -> "Active"
                            session.wasManuallyUnlocked -> "Focus Broke"
                            else -> "Completed"
                        }
                        val statusEmoji = when {
                            session.isActive -> "⏳"
                            session.wasManuallyUnlocked -> "⚠️"
                            else -> "✅"
                        }
                        val color = when {
                            session.isActive -> Purple
                            session.wasManuallyUnlocked -> BlockRed
                            else -> SuccessGreen
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(color.copy(alpha = 0.1f))
                                .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(statusEmoji, fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Focused Study (${durationMins}m)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        }
                    }
                }
            }
        }
    }
}
