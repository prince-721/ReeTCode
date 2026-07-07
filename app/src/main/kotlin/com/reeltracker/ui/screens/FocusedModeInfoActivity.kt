package com.reeltracker.ui.screens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeltracker.data.database.ReelTrackerDatabase
import com.reeltracker.data.entities.FocusMode
import com.reeltracker.ui.theme.ReelTrackerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FocusedModeInfoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val modeId = intent.getLongExtra("modeId", 0L)
        val db = ReelTrackerDatabase.getDatabase(this)
        setContent {
            ReelTrackerTheme {
                FocusedModeInfoScreen(
                    modeId = modeId,
                    db = db,
                    onFinish = { finish() }
                )
            }
        }
    }
}

@Composable
fun FocusedModeInfoScreen(
    modeId: Long,
    db: ReelTrackerDatabase,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var focusMode by remember { mutableStateOf<FocusMode?>(null) }

    LaunchedEffect(modeId) {
        withContext(Dispatchers.IO) {
            val mode = if (modeId != 0L) {
                db.focusModeDao().getFocusModeById(modeId)
            } else {
                db.focusModeDao().getActiveFocusMode()
            }
            withContext(Dispatchers.Main) {
                focusMode = mode
            }
        }
    }

    val modeName = focusMode?.name ?: "Focus Mode"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Slate 900
                        Color(0xFF1E1B4B)  // Indigo 950
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.08f)
            ),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF9B5DE5).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎯", fontSize = 38.sp)
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = modeName,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "This application is currently blocked because '$modeName' is active.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = {
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                db.focusModeDao().disableAllFocusModes()
                            }
                            onFinish()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9B5DE5),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Deactivate $modeName", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        }
                        onFinish()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.2f))
                        )
                    )
                ) {
                    Text("Go to ReetCode", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
