package com.example.subtitle

import android.content.Context
import android.content.Intent
import android.media.*
import android.media.AudioPlaybackCaptureConfiguration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Dedicated debugging helper for SpeechRecognizer with AudioPlaybackCapture
 * Focuses on isolating audio capture and recognition issues
 */
class DebugSpeechRecognizerHelper(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false

    // Audio configuration
    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

    companion object {
        private const val TAG = "DebugSpeech"
    }

    init {
        Log.d(TAG, "🔧 DebugSpeechRecognizerHelper initialized")
        checkAvailability()
    }

    private fun checkAvailability() {
        val available = SpeechRecognizer.isRecognitionAvailable(context)
        Log.d(TAG, "🎤 Speech recognition available: $available")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d(TAG, "📱 Android ${Build.VERSION.SDK_INT} - AudioPlaybackCapture supported")
        } else {
            Log.w(TAG, "⚠️ Android ${Build.VERSION.SDK_INT} - AudioPlaybackCapture not supported")
        }
    }

    fun startListening() {
        if (isListening) {
            Log.d(TAG, "⏰ Already listening, skipping")
            return
        }

        mainHandler.post {
            try {
                Log.d(TAG, "🎤 Starting debug speech recognition...")

                // Create new SpeechRecognizer instance
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                if (speechRecognizer == null) {
                    onError("Failed to create SpeechRecognizer")
                    return@post
                }

                speechRecognizer?.setRecognitionListener(createRecognitionListener())
                Log.d(TAG, "✅ SpeechRecognizer created and listener attached")

                // Setup AudioPlaybackCapture if available
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setupAudioPlaybackCapture()
                } else {
                    Log.d(TAG, "📱 Using microphone (AudioPlaybackCapture not available)")
                }

                // Create recognition intent
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                }

                Log.d(TAG, "🎯 Starting listening...")
                speechRecognizer?.startListening(intent)
                isListening = true
                Log.d(TAG, "✅ Listening started successfully")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start listening: ${e.message}", e)
                onError("Start listening failed: ${e.message}")
                isListening = false
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun setupAudioPlaybackCapture() {
        try {
            Log.d(TAG, "🎵 AudioPlaybackCapture not yet implemented, using microphone")
            // TODO: Implement AudioPlaybackCapture when API issues are resolved
            // For now, just use microphone capture for debugging
        } catch (e: Exception) {
            Log.e(TAG, "❌ AudioPlaybackCapture setup failed: ${e.message}", e)
        }
    }

    private fun startAudioMonitoring() {
        audioRecord?.let { record ->
            Thread {
                try {
                    record.startRecording()
                    Log.d(TAG, "🎤 Audio monitoring started")

                    val audioData = ShortArray(BUFFER_SIZE / 2)
                    var sampleCount = 0

                    while (isListening && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val readResult = record.read(audioData, 0, audioData.size)
                        sampleCount++

                        if (readResult > 0) {
                            // Calculate RMS for audio level monitoring
                            var sum = 0.0
                            for (i in 0 until readResult) {
                                sum += audioData[i] * audioData[i]
                            }
                            val rms = Math.sqrt(sum / readResult)

                            // Log RMS levels periodically
                            if (sampleCount % 50 == 0) {
                                Log.d(TAG, "📊 Audio RMS: ${rms.toInt()} (samples: $readResult)")
                            }
                        } else {
                            Log.w(TAG, "❌ Audio read failed: $readResult")
                            Thread.sleep(100)
                        }
                    }

                    Log.d(TAG, "🛑 Audio monitoring stopped")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Audio monitoring error: ${e.message}", e)
                }
            }.start()
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(p0: Bundle?) {
                Log.d(TAG, "🎤 onReadyForSpeech - Ready for speech input")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "🎤 onBeginningOfSpeech - Speech detected, listening...")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Log RMS changes to monitor audio levels
                if (rmsdB > 0) {
                    Log.d(TAG, "📊 onRmsChanged - RMS: ${rmsdB.toInt()} dB")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partialText = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""

                if (partialText.isNotEmpty()) {
                    Log.d(TAG, "📝 onPartialResults - '$partialText'")
                    onPartial(partialText)
                } else {
                    Log.d(TAG, "📝 onPartialResults - Empty partial results")
                }
            }

            override fun onResults(results: Bundle?) {
                val finalText = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""

                if (finalText.isNotEmpty()) {
                    Log.d(TAG, "✅ onResults - Final: '$finalText'")
                    onFinal(finalText)
                } else {
                    Log.w(TAG, "⚠️ onResults - Empty final results (no speech detected)")
                    onError("Empty results - no speech detected")
                }

                isListening = false

                // Auto-restart for continuous listening
                mainHandler.postDelayed({
                    if (isListening) { // Check if we should continue
                        startListening()
                    }
                }, 500)
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error (session issue)"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing RECORD_AUDIO permission"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error (offline mode?)"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected (speak louder/clearer)"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy (multiple sessions?)"
                    SpeechRecognizer.ERROR_SERVER -> "Server error (Google services issue)"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout (silence too long)"
                    else -> "Unknown error: $error"
                }

                Log.e(TAG, "❌ onError - Code: $error, Message: $errorMessage")
                onError("$errorMessage (code: $error)")
                isListening = false

                // Retry after error
                mainHandler.postDelayed({
                    if (isListening) {
                        Log.d(TAG, "🔄 Retrying after error...")
                        startListening()
                    }
                }, 2000)
            }

            override fun onEndOfSpeech() {
                Log.d(TAG, "🏁 onEndOfSpeech - Speech input ended")
            }

            override fun onBufferReceived(p0: ByteArray?) {
                Log.d(TAG, "📦 onBufferReceived - Audio buffer received (${p0?.size ?: 0} bytes)")
            }

            override fun onEvent(p0: Int, p1: Bundle?) {
                Log.d(TAG, "📋 onEvent - Event: $p0")
            }
        }
    }

    fun stopListening() {
        Log.d(TAG, "🛑 Stopping speech recognition...")
        isListening = false

        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null

                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null

                Log.d(TAG, "✅ Speech recognition stopped")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error stopping recognition: ${e.message}", e)
            }
        }
    }

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun getDebugInfo(): String {
        return """
            Speech Recognition Debug Info:
            - Available: ${isAvailable()}
            - Android Version: ${Build.VERSION.SDK_INT}
            - AudioPlaybackCapture: ${Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q}
            - Is Listening: $isListening
            - SpeechRecognizer: ${speechRecognizer != null}
            - AudioRecord: ${audioRecord != null}
        """.trimIndent()
    }
}