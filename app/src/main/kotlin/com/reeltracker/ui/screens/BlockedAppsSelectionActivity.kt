package com.reeltracker.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.reeltracker.data.FocusedModeRepository

class BlockedAppsSelectionActivity : ComponentActivity() {
    private lateinit var repo: FocusedModeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = FocusedModeRepository(this)
        setContent {
            BlockedAppsSelectionScreen(repo = repo)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedAppsSelectionScreen(repo: FocusedModeRepository) {
    val activity = LocalContext.current as ComponentActivity
    var selectedApps by remember { mutableStateOf(repo.getBlockedApps().toMutableSet()) }
    val allApps = listOf(
        "com.instagram.android" to "Instagram",
        "com.snapchat.android" to "Snapchat",
        "com.google.android.youtube" to "YouTube",
        "com.tiktok.android" to "TikTok"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Blocked Apps") },
                navigationIcon = {
                    IconButton(onClick = { activity.finish() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            LazyColumn {
                items(allApps) { (pkg, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = name, modifier = Modifier.weight(1f))
                        Switch(
                            checked = selectedApps.contains(pkg),
                            onCheckedChange = { isChecked ->
                                if (isChecked) selectedApps.add(pkg) else selectedApps.remove(pkg)
                                repo.setBlockedApps(selectedApps)
                            }
                        )
                    }
                }
            }
        }
    }
}
