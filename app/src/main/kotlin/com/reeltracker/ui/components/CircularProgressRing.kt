package com.reeltracker.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeltracker.ui.theme.Coral
import com.reeltracker.ui.theme.Teal
import com.reeltracker.ui.theme.WarnOrange
import com.reeltracker.ui.theme.BlockRed

@Composable
fun CircularProgressRing(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    strokeWidth: Dp = 16.dp,
    isBlocked: Boolean = false
) {
    val progress = if (total > 0) (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val percentage = (progress * 100).toInt()

    val ringColor = when {
        isBlocked -> BlockRed
        progress >= 0.9f -> BlockRed
        progress >= 0.7f -> WarnOrange
        else -> Coral
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isBlocked) 1.04f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val glowColor = ringColor.copy(alpha = 0.2f)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val diameter = this.size.minDimension - sw
            val topLeft = Offset((this.size.width - diameter) / 2f, (this.size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            // Glow effect (outer)
            if (progress > 0f) {
                drawArc(
                    color = glowColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = Offset(topLeft.x - 4f, topLeft.y - 4f),
                    size = Size(arcSize.width + 8f, arcSize.height + 8f),
                    style = Stroke(width = sw + 8f, cap = StrokeCap.Round)
                )
            }

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

            // Progress arc
            if (animatedProgress > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            ringColor.copy(alpha = 0.7f),
                            ringColor,
                            ringColor
                        )
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = sw, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isBlocked) {
                Text(
                    text = "🔒",
                    fontSize = 32.sp
                )
                Text(
                    text = "BLOCKED",
                    style = MaterialTheme.typography.labelMedium,
                    color = BlockRed,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            } else {
                Text(
                    text = "$current",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "/ $total reels",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.labelMedium,
                    color = ringColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
