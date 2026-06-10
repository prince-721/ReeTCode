package com.reeltracker.ui.screens

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.reeltracker.viewmodel.AppInfo
import com.reeltracker.viewmodel.ReelTrackerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusModeEditorScreen(
    viewModel: ReelTrackerViewModel,
    modeId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var modeName by remember { mutableStateOf("") }
    var blockedApps by remember { mutableStateOf<Set<String>>(emptySet()) }
    var allowedApps by remember { mutableStateOf<Set<String>>(emptySet()) }

    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Blocked, 1 = Allowed
    var isLoadingApps by remember { mutableStateOf(true) }

    // Load focus mode if editing
    LaunchedEffect(modeId) {
        if (modeId != 0L) {
            val mode = viewModel.getFocusModeById(modeId)
            if (mode != null) {
                modeName = mode.name
                blockedApps = mode.blockedApps.toSet()
                allowedApps = mode.allowedApps.toSet()
            }
        }
    }

    // Load installed apps asynchronously
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val apps = viewModel.getInstalledApps(context)
            withContext(Dispatchers.Main) {
                installedApps = apps
                isLoadingApps = false
            }
        }
    }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isEmpty()) {
            installedApps
        } else {
            installedApps.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (modeId == 0L) "Create Focus Mode" else "Edit Focus Mode",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (modeName.isBlank()) {
                            android.widget.Toast.makeText(context, "Please enter a focus mode name", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.saveFocusMode(
                                id = modeId,
                                name = modeName.trim(),
                                blockedApps = blockedApps.toList(),
                                allowedApps = allowedApps.toList()
                            )
                            onBack()
                        }
                    }) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Save",
                            tint = MaterialTheme.colorScheme.primary
                        )
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
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Mode Name input
            OutlinedTextField(
                value = modeName,
                onValueChange = { modeName = it },
                label = { Text("Focus Mode Name") },
                placeholder = { Text("e.g. Study Mode, Work Mode") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            // Tabs for Blocked vs Allowed apps
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Blocked Apps (${blockedApps.size})", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Allowed Apps (${allowedApps.size})", fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Search query textfield
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search installed apps...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            if (isLoadingApps) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isBlocked = blockedApps.contains(app.packageName)
                        val isAllowed = allowedApps.contains(app.packageName)
                        val isChecked = if (selectedTab == 0) isBlocked else isAllowed

                        AppSelectionRow(
                            app = app,
                            isChecked = isChecked,
                            onCheckedChange = { checked ->
                                if (selectedTab == 0) {
                                    if (checked) {
                                        blockedApps = blockedApps + app.packageName
                                        allowedApps = allowedApps - app.packageName
                                    } else {
                                        blockedApps = blockedApps - app.packageName
                                    }
                                } else {
                                    if (checked) {
                                        allowedApps = allowedApps + app.packageName
                                        blockedApps = blockedApps - app.packageName
                                    } else {
                                        allowedApps = allowedApps - app.packageName
                                    }
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (modeName.isBlank()) {
                        android.widget.Toast.makeText(context, "Please enter a focus mode name", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.saveFocusMode(
                            id = modeId,
                            name = modeName.trim(),
                            blockedApps = blockedApps.toList(),
                            allowedApps = allowedApps.toList()
                        )
                        onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Focus Mode", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AppSelectionRow(
    app: AppInfo,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!isChecked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                packageName = app.packageName,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Checkbox(
                checked = isChecked,
                onCheckedChange = null
            )
        }
    }
}

@Composable
private fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        },
        modifier = modifier,
        update = { imageView ->
            try {
                val icon = context.packageManager.getApplicationIcon(packageName)
                imageView.setImageDrawable(icon)
            } catch (e: Exception) {
                imageView.setImageResource(android.R.drawable.sym_def_app_icon)
            }
        }
    )
}
