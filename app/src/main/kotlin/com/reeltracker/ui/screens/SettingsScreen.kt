package com.reeltracker.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import com.reeltracker.data.FocusedModeRepository
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.reeltracker.receiver.TrackerDeviceAdminReceiver
import com.reeltracker.ui.theme.*
import com.reeltracker.viewmodel.HomeUiState
import com.reeltracker.viewmodel.ReelTrackerViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: HomeUiState,
    viewModel: ReelTrackerViewModel,
    onNavigateToFocusModes: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = uiState.preferences
    var limitInput by remember(prefs.dailyLimit) { mutableStateOf(prefs.dailyLimit.toString()) }
    var showLimitDialog by remember { mutableStateOf(false) }

    // Device Admin configuration for Anti-Uninstall
    val adminComponent = remember { ComponentName(context, TrackerDeviceAdminReceiver::class.java) }
    val dpm = remember { context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    var isDeviceAdminEnabled by remember { mutableStateOf(dpm.isAdminActive(adminComponent)) }
    var isExactAlarmEnabled by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                viewModel.isExactAlarmPermissionGranted(context)
            } else {
                true
            }
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDeviceAdminEnabled = dpm.isAdminActive(adminComponent)
                isExactAlarmEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    viewModel.isExactAlarmPermissionGranted(context)
                } else {
                    true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Settings", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
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

            // Tracking settings
            SettingsSectionHeader("Tracking")

            SettingsCard {
                SettingsToggleRow(
                    icon = Icons.Default.TrackChanges,
                    iconTint = Teal,
                    title = "Enable Tracking",
                    subtitle = "Count reels across apps",
                    checked = prefs.isTrackingEnabled,
                    onCheckedChange = { viewModel.updateTrackingEnabled(it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                SettingsToggleRow(
                    icon = Icons.Default.CameraAlt,
                    iconTint = InstagramPink,
                    title = "Instagram Reels",
                    subtitle = "Track Instagram scrolling",
                    checked = prefs.trackInstagram,
                    onCheckedChange = { viewModel.updateTrackInstagram(it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                SettingsToggleRow(
                    icon = Icons.Default.Camera,
                    iconTint = SnapchatYellow,
                    title = "Snapchat Stories/Spotlight",
                    subtitle = "Track Snapchat scrolling",
                    checked = prefs.trackSnapchat,
                    onCheckedChange = { viewModel.updateTrackSnapchat(it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                SettingsToggleRow(
                    icon = Icons.Default.PlayCircle,
                    iconTint = YoutubeRed,
                    title = "YouTube Shorts",
                    subtitle = "Track YouTube scrolling",
                    checked = prefs.trackYoutube,
                    onCheckedChange = { viewModel.updateTrackYoutube(it) }
                )
            }

            Spacer(Modifier.height(20.dp))

            // Limits
            SettingsSectionHeader("Limits")

            SettingsCard {
                SettingsActionRow(
                    icon = Icons.Default.Timer,
                    iconTint = Coral,
                    title = "Daily Reel Limit",
                    subtitle = "${prefs.dailyLimit} reels per day",
                    onClick = { showLimitDialog = true }
                )
            }

            Spacer(Modifier.height(20.dp))

            // Focused Mode
            SettingsSectionHeader("Focused Mode")
            val activeFocusMode by viewModel.activeFocusMode.collectAsStateWithLifecycle()
            SettingsCard {
                SettingsActionRow(
                    icon = Icons.Default.Lock,
                    iconTint = Color(0xFF6A1B9A),
                    title = "Focus Modes",
                    subtitle = if (activeFocusMode != null) "Active: ${activeFocusMode?.name}" else "Tap to configure and enable Focus Modes",
                    onClick = onNavigateToFocusModes
                )
            }

            Spacer(Modifier.height(20.dp))

            // Permissions
            SettingsSectionHeader("Permissions")

            SettingsCard {
                SettingsActionRow(
                    icon = Icons.Default.Accessibility,
                    iconTint = Purple,
                    title = "Accessibility Service",
                    subtitle = if (viewModel.isAccessibilityServiceEnabled(context))
                        "✅ Enabled" else "❌ Disabled — tap to enable",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                SettingsActionRow(
                    icon = Icons.Default.Layers,
                    iconTint = Amber,
                    title = "Draw Over Other Apps",
                    subtitle = if (viewModel.isOverlayPermissionGranted(context))
                        "✅ Granted" else "❌ Not granted — tap to grant",
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    SettingsActionRow(
                        icon = Icons.Default.Alarm,
                        iconTint = Coral,
                        title = "Exact Alarm Reset",
                        subtitle = if (isExactAlarmEnabled)
                            "✅ Granted (resets counts at midnight)" else "❌ Not granted — tap to grant",
                        onClick = {
                            viewModel.requestExactAlarmPermission(context)
                        }
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                SettingsActionRow(
                    icon = Icons.Default.Notifications,
                    iconTint = Teal,
                    title = "Notifications",
                    subtitle = "Manage notification settings",
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        )
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                SettingsToggleRow(
                    icon = Icons.Default.Security,
                    iconTint = Coral,
                    title = "Anti-Uninstall Protection",
                    subtitle = if (isDeviceAdminEnabled)
                        "🔒 Active (Prevents app uninstallation)" else "🔓 Inactive — tap to activate Device Admin",
                    checked = isDeviceAdminEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Protects the app from being uninstalled to help preserve your screen time goals.")
                            }
                            context.startActivity(intent)
                        } else {
                            val intent = Intent().apply {
                                action = "android.settings.DEVICE_ADMIN_SETTINGS"
                            }
                            context.startActivity(intent)
                        }
                    }
                )
            }

            Spacer(Modifier.height(20.dp))

            // About
            SettingsSectionHeader("About")
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Coral.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎬", fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Reel Tracker", fontWeight = FontWeight.Bold)
                        Text("Version 1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Built to reclaim your attention", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Limit dialog
    if (showLimitDialog) {
        AlertDialog(
            onDismissRequest = { showLimitDialog = false },
            title = { Text("Set Daily Limit", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Enter the maximum number of reels you want to watch per day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = limitInput,
                        onValueChange = { if (it.length <= 3 && it.all { c -> c.isDigit() }) limitInput = it },
                        label = { Text("Reels per day") },
                        suffix = { Text("reels") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val limit = limitInput.toIntOrNull()
                        if (limit != null && limit in 1..999) {
                            viewModel.updateDailyLimit(limit)
                            showLimitDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Coral)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLimitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = iconTint
            )
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
