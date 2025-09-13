package com.example.subtitle

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Simple debug activity to test speech recognition in isolation
 * Use this to debug audio capture and recognition issues
 */
class DebugActivity : ComponentActivity() {

    private var debugHelper: DebugSpeechRecognizerHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DebugSpeechTestScreen()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        debugHelper?.stopListening()
    }

    @Composable
    fun DebugSpeechTestScreen() {
        val context = LocalContext.current
        var isListening by remember { mutableStateOf(false) }
        var partialText by remember { mutableStateOf("") }
        var finalText by remember { mutableStateOf("") }
        var errorText by remember { mutableStateOf("") }
        var debugInfo by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            debugHelper = DebugSpeechRecognizerHelper(
                context = context,
                onPartial = { text ->
                    partialText = text
                    Log.d("DebugActivity", "Partial: $text")
                },
                onFinal = { text ->
                    finalText = text
                    Log.d("DebugActivity", "Final: $text")
                },
                onError = { error ->
                    errorText = error
                    Log.e("DebugActivity", "Error: $error")
                }
            )
            debugInfo = debugHelper?.getDebugInfo() ?: "Helper not initialized"
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎤 Speech Recognition Debug", style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(16.dp))

            // Debug Info
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🔧 Debug Info:", style = MaterialTheme.typography.titleMedium)
                    Text(debugInfo, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Control Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        debugHelper?.startListening()
                        isListening = true
                        errorText = ""
                    },
                    enabled = !isListening
                ) {
                    Text("🎤 Start Listening")
                }

                Button(
                    onClick = {
                        debugHelper?.stopListening()
                        isListening = false
                    },
                    enabled = isListening
                ) {
                    Text("🛑 Stop Listening")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status
            Text(
                text = if (isListening) "🎧 Listening..." else "⏸️ Stopped",
                style = MaterialTheme.typography.titleMedium,
                color = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Results
            if (partialText.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📝 Partial Results:", style = MaterialTheme.typography.titleMedium)
                        Text(partialText, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            if (finalText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("✅ Final Results:", style = MaterialTheme.typography.titleMedium)
                        Text(finalText, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            if (errorText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("❌ Error:", style = MaterialTheme.typography.titleMedium)
                        Text(errorText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Instructions
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📋 Instructions:", style = MaterialTheme.typography.titleMedium)
                    Text("1. Tap 'Start Listening'", style = MaterialTheme.typography.bodyMedium)
                    Text("2. Speak clearly into microphone", style = MaterialTheme.typography.bodyMedium)
                    Text("3. Watch logs in Android Studio", style = MaterialTheme.typography.bodyMedium)
                    Text("4. Check 'DebugSpeech' tag in Logcat", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}