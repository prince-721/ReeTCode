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
import com.reeltracker.viewmodel.CodingUnlockViewModel
import com.reeltracker.viewmodel.CodingUnlockUiState
import com.reeltracker.viewmodel.HomeUiState
import com.reeltracker.viewmodel.ReelTrackerViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

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

    val activeFocusMode by viewModel.activeFocusMode.collectAsStateWithLifecycle()
    val focusedModeRepo = remember { com.reeltracker.data.FocusedModeRepository(context) }
    val isFocusedShared = remember { focusedModeRepo.isFocused() }
    val isBlocked = uiState.activeBlock != null || activeFocusMode != null || isFocusedShared

    val checkBlockAndExecute: (() -> Unit) -> Unit = { action ->
        if (isBlocked) {
            android.widget.Toast.makeText(
                context,
                "Permissions cannot be modified during an active block or focus session.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        } else {
            action()
        }
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

            // Code to Unlock
            val codingViewModel: CodingUnlockViewModel = viewModel()
            val codingState by codingViewModel.uiState.collectAsStateWithLifecycle()

            SettingsSectionHeader("Code to Unlock")

            SettingsCard {
                // Enable toggle
                SettingsToggleRow(
                    icon = Icons.Default.Code,
                    iconTint = Teal,
                    title = "Enable Code to Unlock",
                    subtitle = "Earn scroll time by solving coding problems",
                    checked = codingState.codeUnlockEnabled,
                    onCheckedChange = { codingViewModel.updateCodeUnlockEnabled(it) }
                )

                if (codingState.codeUnlockEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                    // Same username toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Same username for both",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Use the same username for LeetCode and CodeChef",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = codingState.useSameUsername,
                            onCheckedChange = { codingViewModel.updateUseSameUsername(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Teal
                            )
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                    // LeetCode username
                    CodePlatformUsernameRow(
                        platform = "LeetCode",
                        username = codingState.leetcodeUsername,
                        isVerified = codingState.isLeetcodeVerified,
                        isVerifying = codingState.isVerifyingLeetcode,
                        profile = codingState.leetcodeProfile,
                        platformColor = Color(0xFFFFA116),
                        onUsernameChange = { codingViewModel.updateLeetcodeUsername(it) },
                        onVerify = { codingViewModel.verifyLeetcode() }
                    )

                    if (codingState.verifyError != null) {
                        Text(
                            text = codingState.verifyError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    if (codingState.leetcodeVerificationCode != null) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "Verify Ownership of LeetCode Account",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "To verify this account is yours, please temporarily add the following code to your LeetCode profile \"About me\" (bio) section:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    Text(
                                        text = codingState.leetcodeVerificationCode!!,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { codingViewModel.confirmLeetcodeBio() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Confirm Bio Details", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                    // CodeChef username
                    CodePlatformUsernameRow(
                        platform = "CodeChef",
                        username = codingState.codechefUsername,
                        isVerified = codingState.isCodechefVerified,
                        isVerifying = codingState.isVerifyingCodechef,
                        profile = codingState.codechefProfile,
                        platformColor = Color(0xFF5B4638),
                        onUsernameChange = { codingViewModel.updateCodechefUsername(it) },
                        onVerify = { codingViewModel.verifyCodechef() }
                    )

                    if (codingState.codechefVerificationCode != null) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "Verify Ownership of CodeChef Account",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "To verify this account is yours, please temporarily add the following code to your CodeChef profile Full Name field:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    Text(
                                        text = codingState.codechefVerificationCode!!,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { codingViewModel.confirmCodechefBio() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Confirm Name Details", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                    // GeeksforGeeks username
                    CodePlatformUsernameRow(
                        platform = "GeeksforGeeks",
                        username = codingState.gfgUsername,
                        isVerified = codingState.isGfgVerified,
                        isVerifying = codingState.isVerifyingGfg,
                        profile = codingState.gfgProfile,
                        platformColor = Color(0xFF2F8D46),
                        onUsernameChange = { codingViewModel.updateGfgUsername(it) },
                        onVerify = { codingViewModel.verifyGfg() }
                    )

                    if (codingState.gfgVerificationCode != null) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "Verify Ownership of GeeksforGeeks Account",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "To verify this account is yours, please temporarily add the following code to your GeeksforGeeks profile info/description section:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    Text(
                                        text = codingState.gfgVerificationCode!!,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { codingViewModel.confirmGfgBio() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Confirm Bio/Info Details", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                    // Problems to fully unlock slider
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Problems to fully unlock",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${codingState.problemsToFullUnlock}",
                                fontWeight = FontWeight.Bold,
                                color = Teal
                            )
                        }
                        Slider(
                            value = codingState.problemsToFullUnlock.toFloat(),
                            onValueChange = { codingViewModel.updateProblemsToFullUnlock(it.roundToInt()) },
                            valueRange = 1f..10f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = Teal,
                                activeTrackColor = Teal
                            )
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                    // Minutes per problem slider
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Minutes earned per problem",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${codingState.minutesPerProblem}m",
                                fontWeight = FontWeight.Bold,
                                color = Teal
                            )
                        }
                        Slider(
                            value = codingState.minutesPerProblem.toFloat(),
                            onValueChange = { codingViewModel.updateMinutesPerProblem(it.roundToInt()) },
                            valueRange = 10f..60f,
                            steps = 4,
                            colors = SliderDefaults.colors(
                                thumbColor = Teal,
                                activeTrackColor = Teal
                            )
                        )
                    }
                }
            }

            // Verification error
            if (codingState.verifyError != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    codingState.verifyError!!,
                    color = BlockRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp)
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
                        checkBlockAndExecute {
                            try {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Could not open accessibility settings. Please enable it manually in system settings.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
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
                        checkBlockAndExecute {
                            try {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                )
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Could not open overlay permission settings.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
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
                        checkBlockAndExecute {
                            viewModel.requestExactAlarmPermission(context)
                        }
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
                        checkBlockAndExecute {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                )
                            } catch (e: Exception) {
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = android.net.Uri.fromParts("package", context.packageName, null)
                                        }
                                    )
                                } catch (ex: Exception) {
                                    android.widget.Toast.makeText(context, "Could not open notification settings.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
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
                        if (isBlocked) {
                            android.widget.Toast.makeText(
                                context,
                                "Anti-Uninstall settings cannot be modified during an active block or focus session.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        } else {
                            if (checked) {
                                try {
                                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Protects the app from being uninstalled to help preserve your screen time goals.")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Device admin settings not available on this device.", android.widget.Toast.LENGTH_LONG).show()
                                }
                            } else {
                                val intentsToTry = listOf(
                                    Intent(Settings.ACTION_SECURITY_SETTINGS),
                                    Intent(Settings.ACTION_SETTINGS)
                                )
                                var launched = false
                                for (intent in intentsToTry) {
                                    try {
                                        context.startActivity(intent)
                                        launched = true
                                        break
                                    } catch (_: Exception) { }
                                }
                                if (!launched) {
                                    android.widget.Toast.makeText(context, "Could not open settings. Please disable Device Admin manually in Settings > Security.", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
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
                        Text("💻", fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("ReetCode", fontWeight = FontWeight.Bold)
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

@Composable
private fun CodePlatformUsernameRow(
    platform: String,
    username: String,
    isVerified: Boolean,
    isVerifying: Boolean,
    profile: com.reeltracker.service.PlatformProfile?,
    platformColor: Color,
    onUsernameChange: (String) -> Unit,
    onVerify: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("$platform Username") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = platformColor,
                    focusedLabelColor = platformColor
                )
            )
            Spacer(Modifier.width(12.dp))
            if (isVerifying) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = platformColor
                )
            } else {
                Button(
                    onClick = onVerify,
                    colors = ButtonDefaults.buttonColors(containerColor = platformColor),
                    enabled = username.isNotBlank(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (isVerified) "Re-Verify" else "Verify", fontSize = 12.sp)
                }
            }
        }

        if (isVerified) {
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(platformColor.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(platformColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (username.isNotEmpty()) username.take(1).uppercase() else "👤",
                        fontWeight = FontWeight.Bold,
                        color = platformColor,
                        fontSize = 16.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile?.username ?: username,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                    Row {
                        Text(
                            text = "Solved: ${profile?.totalSolved ?: 0}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Rating: ${profile?.rating ?: "N/A"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text("✅ Verified", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

