package com.reeltracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.reeltracker.data.FocusedModeRepository

@Composable
fun FocusedModeToggle(
    repo: FocusedModeRepository,
    modifier: Modifier = Modifier
) {
    // Remember the current toggle state
    val isFocused = remember { mutableStateOf(repo.isFocused()) }

    // Sync repository changes back to UI
    LaunchedEffect(isFocused.value) {
        repo.setFocused(isFocused.value)
    }

    // Glass‑morphic background
    val glassBackground = Modifier
        .fillMaxWidth()
        .height(56.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(Color.White.copy(alpha = 0.2f))

    androidx.compose.foundation.layout.Row(
        modifier = modifier.then(glassBackground).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = "Focused Mode",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = isFocused.value,
            onCheckedChange = { isFocused.value = it },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}


