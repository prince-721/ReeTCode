package com.reeltracker.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeltracker.ui.theme.Coral
import com.reeltracker.ui.theme.Teal
import com.reeltracker.ui.theme.Amber

data class OnboardingStep(
    val icon: ImageVector,
    val iconTint: Color,
    val title: String,
    val description: String,
    val permissionLabel: String?,
    val permissionAction: ((android.content.Context) -> Unit)?
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var currentPage by remember { mutableIntStateOf(0) }

    val steps = listOf(
        OnboardingStep(
            icon = Icons.Default.Visibility,
            iconTint = Coral,
            title = "Welcome to Reel Tracker",
            description = "Take back control of your screen time. Reel Tracker counts how many short videos you scroll through on Instagram, Snapchat, and YouTube — and blocks those apps when you hit your daily limit.",
            permissionLabel = null,
            permissionAction = null
        ),
        OnboardingStep(
            icon = Icons.Default.Accessibility,
            iconTint = Teal,
            title = "Accessibility Access",
            description = "Reel Tracker needs Accessibility Service permission to detect when you scroll through reels and shorts. No personal content or data is ever read or stored — only scroll events are monitored.",
            permissionLabel = "Enable Accessibility Service →",
            permissionAction = { ctx ->
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        ),
        OnboardingStep(
            icon = Icons.Default.Layers,
            iconTint = Amber,
            title = "Overlay Permission",
            description = "To block Instagram, Snapchat, and YouTube when you hit your limit, Reel Tracker needs permission to display a screen overlay on top of other apps.",
            permissionLabel = "Grant Overlay Permission →",
            permissionAction = { ctx ->
                ctx.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${ctx.packageName}")
                    )
                )
            }
        ),
        OnboardingStep(
            icon = Icons.Default.Notifications,
            iconTint = Coral,
            title = "Background Service",
            description = "Reel Tracker runs a lightweight background service to track your reels in real time, even when you switch between apps. A persistent notification will show your live count.",
            permissionLabel = null,
            permissionAction = null
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // Page indicator
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                steps.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (index == currentPage) 24.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == currentPage) Coral
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }

            Spacer(Modifier.height(48.dp))

            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                },
                label = "page"
            ) { page ->
                val step = steps[page]
                OnboardingPageContent(step = step)
            }

            Spacer(Modifier.weight(1f))

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPage > 0) {
                    TextButton(onClick = { currentPage-- }) {
                        Text("Back", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Spacer(Modifier.width(80.dp))
                }

                Button(
                    onClick = {
                        if (currentPage < steps.size - 1) {
                            currentPage++
                        } else {
                            onComplete()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Coral),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text(
                        text = if (currentPage < steps.size - 1) "Next" else "Get Started",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        if (currentPage < steps.size - 1) Icons.AutoMirrored.Filled.ArrowForward
                        else Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun OnboardingPageContent(step: OnboardingStep) {
    val context = LocalContext.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Icon circle
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(step.iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = step.icon,
                contentDescription = null,
                tint = step.iconTint,
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = step.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = step.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 26.sp
        )

        if (step.permissionLabel != null && step.permissionAction != null) {
            Spacer(Modifier.height(28.dp))

            OutlinedButton(
                onClick = { step.permissionAction.invoke(context) },
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, step.iconTint),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = step.permissionLabel,
                    color = step.iconTint,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
