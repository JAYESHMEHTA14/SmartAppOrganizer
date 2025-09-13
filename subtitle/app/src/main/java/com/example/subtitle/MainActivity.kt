package com.example.subtitle

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.subtitle.ui.controls.SubtitleControls
import com.example.subtitle.ui.theme.SubtitleTheme
import com.example.subtitle.viewmodel.SubtitleViewModel

class MainActivity : ComponentActivity() {

    internal lateinit var mediaProjectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize media projection manager
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setContent {
            SubtitleTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current as MainActivity
    var audioCaptureStarted by remember { mutableStateOf(false) }
    var overlayStarted by remember { mutableStateOf(false) }
    var captureMode by remember { mutableStateOf(AudioCaptureService.CAPTURE_MODE_PLAYBACK) }

    // Permission launcher for overlay
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { }

    // Audio permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.d("AudioCapture", "Audio permission granted")
        } else {
            Log.e("AudioCapture", "Audio permission denied")
        }
    }

    val hasOverlayPermission = android.provider.Settings.canDrawOverlays(context)
    val hasAudioPermission = android.content.pm.PackageManager.PERMISSION_GRANTED ==
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Real-Time Subtitle Generator", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        // Controls
        SubtitleControls(SubtitleViewModel)

        Spacer(modifier = Modifier.height(16.dp))

        // Overlay Control Buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (hasOverlayPermission) {
                        if (!overlayStarted) {
                            context.startService(
                                Intent(context, SubtitleOverlayService::class.java)
                            )
                            overlayStarted = true
                            Log.d("SubtitleService", "Overlay service started")
                        }
                    } else {
                        // Request overlay permission
                        val intent = Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                        overlayPermissionLauncher.launch(intent)
                    }
                }
            ) {
                Text(if (overlayStarted) "Overlay Running" else "Start Overlay")
            }

            Button(
                onClick = {
                    context.stopService(Intent(context, SubtitleOverlayService::class.java))
                    overlayStarted = false
                    Log.d("SubtitleService", "Overlay service stopped")
                }
            ) {
                Text("Stop Overlay")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Audio Capture Setup
        if (!audioCaptureStarted) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasAudioPermission) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (hasAudioPermission) "🎤 Audio Capture Ready" else "🎤 Audio Permission Required",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (hasAudioPermission) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (hasAudioPermission) {
                            "Choose your audio capture method. AudioPlaybackCapture works with YouTube, Chrome, etc. Microphone captures all audio."
                        } else {
                            "Grant microphone permission to enable audio capture for real-time subtitles."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hasAudioPermission) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )

                    if (!hasAudioPermission) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Grant Audio Permission")
                        }
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Audio capture mode selection
                        Text("Select Audio Capture Mode:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { captureMode = AudioCaptureService.CAPTURE_MODE_PLAYBACK },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (captureMode == AudioCaptureService.CAPTURE_MODE_PLAYBACK)
                                        MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Text("🎵 Playback Capture")
                            }

                            OutlinedButton(
                                onClick = { captureMode = AudioCaptureService.CAPTURE_MODE_MICROPHONE },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (captureMode == AudioCaptureService.CAPTURE_MODE_MICROPHONE)
                                        MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Text("🎤 Microphone")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val serviceIntent = Intent(context, AudioPlaybackCaptureService::class.java).apply {
                                    putExtra(AudioPlaybackCaptureService.EXTRA_CAPTURE_MODE, captureMode)
                                }
                                ContextCompat.startForegroundService(context, serviceIntent)
                                audioCaptureStarted = true
                                SubtitleViewModel.setMediaProjectionActive(true)
                                Log.d("AudioPlaybackCapture", "Audio playback capture service started with mode: $captureMode")
                            }
                        ) {
                            Text("Start Audio Capture")
                        }
                
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when (captureMode) {
                                AudioPlaybackCaptureService.CAPTURE_MODE_PLAYBACK ->
                                    "🎵 Captures audio from YouTube, Chrome, Netflix, and other media apps (recommended)"
                                AudioPlaybackCaptureService.CAPTURE_MODE_MICROPHONE ->
                                    "🎤 Captures microphone audio for testing"
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "✅ Audio Capture Active (${if (captureMode == AudioCaptureService.CAPTURE_MODE_PLAYBACK) "Playback" else "Microphone"})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Real-time subtitle generation is active. ${if (captureMode == AudioCaptureService.CAPTURE_MODE_PLAYBACK) "Open YouTube, Chrome, or other media apps to see subtitles." else "Speak into the microphone to generate subtitles."}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Audio Capture Control Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        SubtitleViewModel.startRealTimeSubtitles()
                    }
                ) {
                    Text("Start Subtitles")
                }

                Button(
                    onClick = {
                        SubtitleViewModel.stopRealTimeSubtitles()
                        context.stopService(Intent(context, AudioPlaybackCaptureService::class.java))
                        audioCaptureStarted = false
                    }
                ) {
                    Text("Stop Subtitles")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status Messages
        if (!hasOverlayPermission) {
            Text(
                "⚠️ Overlay permission required for subtitles to appear over other apps.",
                color = MaterialTheme.colorScheme.error
            )
        }

        if (!hasAudioPermission) {
            Text(
                "⚠️ Audio permission required for subtitle generation.",
                color = MaterialTheme.colorScheme.error
            )
        }

        if (hasOverlayPermission && hasAudioPermission && audioCaptureStarted) {
            Text(
                if (captureMode == AudioCaptureService.CAPTURE_MODE_PLAYBACK) {
                    "🎬 Ready! Open YouTube, Chrome, or other media apps to see real-time subtitles."
                } else {
                    "🎤 Ready! Speak into the microphone to generate subtitles."
                },
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "📝 Check Logcat with filter 'AudioCapture' to see subtitle generation logs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            if (captureMode == AudioCaptureService.CAPTURE_MODE_PLAYBACK) {
                "🎯 Playback Capture: Works with YouTube, Chrome, Netflix, Prime Video, Disney+, browsers"
            } else {
                "🎤 Microphone: Captures all audio from device (including system sounds)"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}