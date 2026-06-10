package com.reeltracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand colors
val Coral = Color(0xFFFF6B6B)
val CoralDark = Color(0xFFE05252)
val Teal = Color(0xFF4ECDC4)
val TealDark = Color(0xFF38B2A7)
val Amber = Color(0xFFFFD93D)
val Purple = Color(0xFF9B5DE5)
val DeepPurple = Color(0xFF6C3DB5)
val NavyDark = Color(0xFF0D1117)
val NavySurface = Color(0xFF161B22)
val NavyCard = Color(0xFF21262D)
val BlockRed = Color(0xFFFF4444)
val SuccessGreen = Color(0xFF4CAF50)
val WarnOrange = Color(0xFFFF9800)

// Instagram gradient: pink-orange
val InstagramPink = Color(0xFFE1306C)
val SnapchatYellow = Color(0xFFFFFC00)
val YoutubeRed = Color(0xFFFF0000)

private val DarkColorScheme = darkColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A1E1E),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Teal,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF003735),
    onSecondaryContainer = Color(0xFF70F5EC),
    tertiary = Amber,
    onTertiary = Color.Black,
    background = NavyDark,
    onBackground = Color(0xFFE6EDF3),
    surface = NavySurface,
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = NavyCard,
    onSurfaceVariant = Color(0xFF8B949E),
    outline = Color(0xFF30363D),
    error = BlockRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF3B0909),
    secondary = TealDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2EFEC),
    onSecondaryContainer = Color(0xFF002020),
    tertiary = Color(0xFFB8860B),
    onTertiary = Color.White,
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1A1A2E),
    surface = Color.White,
    onSurface = Color(0xFF1A1A2E),
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE5E7EB),
    error = Color(0xFFD32F2F),
    onError = Color.White
)

@Composable
fun ReelTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
