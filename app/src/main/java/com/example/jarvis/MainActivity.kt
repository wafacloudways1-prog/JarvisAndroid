package com.example.jarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        permissionLauncher.launch(permissions.toTypedArray())

        setContent {
            JarvisTheme {
                var status by remember { mutableStateOf("Systems ready. Grant permissions to begin.") }
                var overlayOn by remember { mutableStateOf(false) }
                Surface(Modifier.fillMaxSize(), color = Color(0xFF050B16)) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("J.A.R.V.I.S", color = Color(0xFF53D9FF), fontSize = 32.sp)
                        Text("PERSONAL AI SYSTEM", color = Color.LightGray, fontSize = 11.sp)
                        Spacer(Modifier.height(38.dp))
                        Box(
                            Modifier.size(190.dp).background(
                                Brush.radialGradient(listOf(Color(0xFF145B80), Color(0xFF071629), Color(0xFF050B16))),
                                CircleShape
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Mic, null, tint = Color(0xFF62DFFF), modifier = Modifier.size(70.dp))
                        }
                        Spacer(Modifier.height(28.dp))
                        Text(status, color = Color.White)
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = {
                                if (!Settings.canDrawOverlays(this@MainActivity)) {
                                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                                    status = "Allow Display over other apps, then tap Start again."
                                } else {
                                    ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, OverlayService::class.java))
                                    overlayOn = true
                                    status = "Floating JARVIS is active. Tap the orb to speak."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Icon(Icons.Default.PowerSettingsNew, null); Spacer(Modifier.width(8.dp)); Text(if (overlayOn) "Restart floating assistant" else "Start floating assistant") }
                        OutlinedButton(
                            onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Enable optional screen-reading service") }
                        OutlinedButton(
                            onClick = { startActivity(Intent(this@MainActivity, AiSettingsActivity::class.java)) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("AI connection settings") }
                        Text("Wake phrase works during an active listening session.", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF35CFFF), background = Color(0xFF050B16)), content = content)
}

class AiSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        setContent {
            JarvisTheme {
                var endpoint by remember { mutableStateOf(prefs.getString("endpoint", "https://api.openai.com/v1/chat/completions") ?: "") }
                var model by remember { mutableStateOf(prefs.getString("model", "gpt-4o-mini") ?: "") }
                var key by remember { mutableStateOf(prefs.getString("key", "") ?: "") }
                Column(Modifier.fillMaxSize().background(Color(0xFF050B16)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("AI CONNECTION", color = Color(0xFF53D9FF), fontSize = 22.sp)
                    OutlinedTextField(endpoint, { endpoint = it }, label = { Text("Chat completions endpoint") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(model, { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(key, { key = it }, label = { Text("API key (local testing only)") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        prefs.edit().putString("endpoint", endpoint.trim()).putString("model", model.trim()).putString("key", key.trim()).apply()
                        finish()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
                    Text("For production, use an authenticated backend proxy. APK-stored keys can be extracted.", color = Color.LightGray)
                }
            }
        }
    }
}
