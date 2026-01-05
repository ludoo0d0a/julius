package com.antigravity.voiceai

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.antigravity.voiceai.shared.ConversationStore
import com.antigravity.voiceai.shared.VoiceEvent
import com.antigravity.voiceai.ui.SettingsScreen
import com.antigravity.voiceai.ui.components.ParticleEffectCanvas
import com.antigravity.voiceai.ui.components.TrayLight
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val store: ConversationStore by inject()
    private val settingsManager: SettingsManager by inject()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        
        setContent {
            val state by store.state.collectAsState()
            var showSettings by remember { mutableStateOf(false) }
            
            MaterialTheme(
                colorScheme = darkColorScheme(background = Color(0xFF0F172A))
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSettings) {
                        SettingsScreen(settingsManager) { showSettings = false }
                    } else {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            // 1. Background Particles (Fill Screen)
                            ParticleEffectCanvas(
                                isActive = state.status == VoiceEvent.Listening || state.status == VoiceEvent.Speaking
                            )
                            
                            // Responsive Container
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 600.dp)
                                    .fillMaxSize()
                            ) {
                            
                                // 3. Chat Content (Overlay)
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = state.status.name,
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = if (state.status == VoiceEvent.Listening) state.currentTranscript else state.messages.lastOrNull()?.text ?: "Hi, how can I help?",
                                        color = Color.White,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
    
                                // 4. Bottom Tray Light (Constrained)
                                TrayLight(
                                    isActive = state.status == VoiceEvent.Listening,
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                                
                                // 5. Controls (Centered Mic/Speaker Button)
                                IconButton(
                                    onClick = { 
                                        if (state.status == VoiceEvent.Listening) store.stopListening() else store.startListening()
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 40.dp)
                                        .size(64.dp)
                                ) {
                                     // Rounded background for the button
                                     Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF6366F1), shape = androidx.compose.foundation.shape.CircleShape),
                                        contentAlignment = Alignment.Center
                                     ) {
                                         Icon(
                                             painter = painterResource(id = R.drawable.ic_speaker),
                                             contentDescription = "Speak",
                                             tint = if (state.status == VoiceEvent.Listening) Color.Red else Color.White,
                                             modifier = Modifier.size(32.dp)
                                         )
                                     }
                                }
                                
                                // 2. Settings Button (Bottom Left)
                                IconButton(
                                    onClick = { showSettings = true },
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start=24.dp, bottom=48.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_settings),
                                        contentDescription = "Settings",
                                        tint = Color.White.copy(alpha=0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
