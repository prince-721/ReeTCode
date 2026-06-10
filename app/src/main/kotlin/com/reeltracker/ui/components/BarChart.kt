package com.reeltracker.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.reeltracker.data.entities.DailyReelCount
import com.reeltracker.ui.theme.Coral
import com.reeltracker.ui.theme.BlockRed
import com.reeltracker.ui.theme.WarnOrange

@Composable
fun WeeklyBarChart(
    data: List<Pair<String, DailyReelCount?>>, // label to count
    modifier: Modifier = Modifier
) {
    val maxCount = data.maxOfOrNull { it.second?.totalCount ?: 0 }?.coerceAtLeast(1) ?: 1

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { (label, count) ->
            BarItem(
                label = label,
                value = count?.totalCount ?: 0,
                limit = count?.limitValue ?: 50,
                maxValue = maxCount,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BarItem(
    label: String,
    value: Int,
    limit: Int,
    maxValue: Int,
    modifier: Modifier = Modifier
) {
    val fraction = value.toFloat() / maxValue.toFloat()
    val limitFraction = limit.toFloat() / maxValue.toFloat()
    val isOverLimit = value >= limit && value > 0

    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "bar_$label"
    )

    val barColor = when {
        isOverLimit -> BlockRed
        fraction >= 0.8f * limitFraction -> WarnOrange
        else -> Coral
    }

    Column(
        modifier = modifier.padding(horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Value label
        if (value > 0) {
            Text(
                text = "$value",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(4.dp))

        // Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Background track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            )

            // Filled bar
            if (animatedFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(animatedFraction)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    barColor.copy(alpha = 0.6f),
                                    barColor
                                )
                            )
                        )
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Day label
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
