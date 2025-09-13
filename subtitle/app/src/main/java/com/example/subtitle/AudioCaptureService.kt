package com.example.subtitle

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.subtitle.viewmodel.SubtitleViewModel
import java.nio.ByteBuffer
import java.util.*

/**
 * Service for capturing audio using AudioPlaybackCapture API (Android 10+)
 * Falls back to microphone capture when AudioPlaybackCapture is not available
 */
class AudioCaptureService : Service() {

    private val TAG = "AudioCaptureService"

    private lateinit var audioRecord: AudioRecord
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var mainHandler: Handler
    private var isRecording = false
    private var isListening = false

    // Audio processing
    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

    // Logging and statistics
    private var subtitleGenerationCount = 0
    private var sessionStartTime = 0L
    private var lastSubtitleText = ""
    private var audioDetectionCount = 0
    private var lastSubtitleTime = 0L
    private val SUBTITLE_COOLDOWN_MS = 2000L // 2 seconds between subtitles

    companion object {
        const val EXTRA_CAPTURE_MODE = "capture_mode"
        const val CAPTURE_MODE_PLAYBACK = "playback"
        const val CAPTURE_MODE_MICROPHONE = "microphone"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AudioCaptureService created")

        // Initialize session tracking
        sessionStartTime = System.currentTimeMillis()
        subtitleGenerationCount = 0
        audioDetectionCount = 0
        lastSubtitleText = ""

        Log.d(TAG, "=== AUDIO CAPTURE SESSION STARTED ===")
        Log.d(TAG, "Session start time: ${java.util.Date(sessionStartTime)}")

        // Create handler thread for audio processing
        handlerThread = HandlerThread("AudioProcessing")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        // Create main thread handler for speech recognition
        mainHandler = Handler(Looper.getMainLooper())

        // Check if speech recognition is available
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("SubtitleDebug", "❌ Speech recognition not available on this device")
            stopSelf()
            return
        }

        Log.d("SubtitleDebug", "✅ Speech recognition is available")

        // Initialize speech recognizer with proper session management
        initializeSpeechRecognizer()

        createNotificationChannel()
    }

    private fun initializeSpeechRecognizer() {
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(createSpeechRecognizerListener())
            Log.d(TAG, "✅ Speech recognizer initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize speech recognizer: ${e.message}")
        }
    }

    private fun createSpeechRecognizerListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("SubtitleDebug", "🎤 Ready for speech")
                isListening = true
            }

            override fun onBeginningOfSpeech() {
                Log.d("SubtitleDebug", "🎤 Beginning of speech detected")
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partialText = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""

                if (partialText.isNotEmpty()) {
                    SubtitleViewModel.updatePartialText(partialText)
                    Log.d("SubtitleDebug", "📝 Partial: $partialText")
                }
            }

            override fun onResults(results: Bundle?) {
                val finalText = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""

                if (finalText.isNotEmpty()) {
                    SubtitleViewModel.updateFinalText(finalText)
                    Log.d("SubtitleDebug", "✅ Final: $finalText")
                }
                isListening = false

                // Continue listening for next speech
                mainHandler.postDelayed({
                    if (isRecording) {
                        startRecognition()
                    }
                }, 500)
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    else -> "Unknown error: $error"
                }
                Log.e("SubtitleDebug", "❌ Speech recognition error: $errorMessage")
                isListening = false

                // Restart recognition after error
                mainHandler.postDelayed({
                    if (isRecording) {
                        startRecognition()
                    }
                }, 1000)
            }

            override fun onEndOfSpeech() {
                Log.d("SubtitleDebug", "🏁 End of speech")
                isListening = false
            }

            // Required but unused methods
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun startRecognition() {
        if (isListening) {
            Log.d("SubtitleDebug", "⏰ Already listening, skipping restart")
            return
        }

        mainHandler.post {
            try {
                Log.d("SubtitleDebug", "🎤 Initializing SpeechRecognizer...")

                // Create new instance each time to avoid session issues
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                if (speechRecognizer == null) {
                    Log.e("SubtitleDebug", "❌ Failed to create SpeechRecognizer")
                    return@post
                }

                speechRecognizer?.setRecognitionListener(createSpeechRecognizerListener())
                Log.d("SubtitleDebug", "✅ SpeechRecognizer created and listener set")

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US") // Start with English only
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                }

                Log.d("SubtitleDebug", "🎤 Starting speech recognition...")
                speechRecognizer?.startListening(intent)
                isListening = true
                Log.d("SubtitleDebug", "✅ Recognition started successfully")

            } catch (e: Exception) {
                Log.e("SubtitleDebug", "❌ Failed to start recognition: ${e.message}", e)
                isListening = false
                // Try to restart after a delay
                mainHandler.postDelayed({
                    if (isRecording) {
                        Log.d("SubtitleDebug", "🔄 Retrying recognition after error...")
                        startRecognition()
                    }
                }, 2000)
            }
        }
    }

    private fun stopRecognition() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                isListening = false
                Log.d("SubtitleDebug", "🛑 Stopped recognition")
            } catch (e: Exception) {
                Log.e("SubtitleDebug", "⚠️ Failed to stop recognition", e)
            }
        }
    }

    private fun restartRecognition() {
        stopRecognition()
        mainHandler.postDelayed({
            if (isRecording) {
                startRecognition()
            }
        }, 500) // Small delay to avoid crash
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AudioCaptureService started")

        // Start foreground service
        startForegroundService()

        val captureMode = intent?.getStringExtra(EXTRA_CAPTURE_MODE) ?: CAPTURE_MODE_MICROPHONE

        when (captureMode) {
            CAPTURE_MODE_PLAYBACK -> startPlaybackCapture()
            CAPTURE_MODE_MICROPHONE -> startMicrophoneCapture()
            else -> startMicrophoneCapture()
        }

        return START_NOT_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startPlaybackCapture() {
        try {
            Log.d("SubtitleDebug", "🎵 AudioPlaybackCapture not yet implemented, using microphone capture")
            // TODO: Implement AudioPlaybackCapture when API issues are resolved
            startMicrophoneCapture()
        } catch (e: Exception) {
            Log.e("SubtitleDebug", "Playback capture setup error: ${e.message}")
            startMicrophoneCapture()
        }
    }

    private fun startMicrophoneCapture() {
        try {
            Log.d("SubtitleDebug", "🎤 Starting microphone capture...")

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                BUFFER_SIZE * 2
            )

            Log.d("SubtitleDebug", "🎤 AudioRecord state: ${audioRecord.state}")
            Log.d("SubtitleDebug", "🎤 AudioRecord buffer size: ${audioRecord.bufferSizeInFrames}")

            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                Log.d("SubtitleDebug", "✅ Microphone capture initialized successfully")
                startAudioProcessing()
                // Start speech recognition for real-time transcription
                startRecognition()
            } else {
                Log.e("SubtitleDebug", "❌ Microphone capture initialization failed - state: ${audioRecord.state}")
                stopSelf()
            }

        } catch (e: Exception) {
            Log.e("SubtitleDebug", "❌ Microphone capture error: ${e.message}", e)
            stopSelf()
        }
    }

    private fun startAudioProcessing() {
        handler.post {
            try {
                Log.d("SubtitleDebug", "🎤 Starting audio recording...")
                audioRecord.startRecording()
                isRecording = true
                Log.d("SubtitleDebug", "✅ Audio recording started successfully")

                val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
                val audioData = ShortArray(BUFFER_SIZE / 2)
                var processingCount = 0

                while (isRecording) {
                    val readResult = audioRecord.read(audioData, 0, audioData.size)
                    processingCount++

                    if (readResult > 0) {
                        // Log processing status occasionally
                        if (processingCount % 100 == 0) {
                            Log.d("SubtitleDebug", "🎤 Audio processing active, read: $readResult samples")
                        }
                        processAudioData(audioData, readResult)
                    } else {
                        Log.w("SubtitleDebug", "❌ Audio read failed: $readResult")
                        Thread.sleep(100) // Brief pause before retry
                    }
                }

            } catch (e: Exception) {
                Log.e("SubtitleDebug", "❌ Audio processing error: ${e.message}", e)
            }
        }
    }

    private fun processAudioData(audioData: ShortArray, readSize: Int) {
        try {
            // Log audio processing start occasionally
            if (audioDetectionCount % 100 == 0) {
                Log.d(TAG, "🎤 Processing audio data, size: $readSize")
            }

            // Calculate RMS (Root Mean Square) to detect if there's audio
            var sum = 0.0
            for (i in 0 until readSize) {
                sum += audioData[i] * audioData[i]
            }
            val rms = Math.sqrt(sum / readSize)

            // Debug: Log RMS levels more frequently to see if audio is being captured
            if (audioDetectionCount % 50 == 0) {
                Log.d("SubtitleDebug", "🎤 Audio RMS: $rms (threshold: 100)")
            }

            // Only process if audio level is above threshold (indicates speech)
            if (rms > 100) { // Lowered threshold for better sensitivity
                audioDetectionCount++
                Log.d("SubtitleDebug", "🎵 Audio detected #$audioDetectionCount, RMS: $rms")

                // Check cooldown to prevent subtitles from appearing too quickly
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSubtitleTime > SUBTITLE_COOLDOWN_MS) {
                    lastSubtitleTime = currentTime
                    Log.d("SubtitleDebug", "🚀 Triggering speech recognition...")

                    // Start speech recognition for real-time transcription
                    startRecognition()
                } else {
                    Log.d("SubtitleDebug", "⏰ Subtitle cooldown active, skipping generation")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Audio processing error: ${e.message}")
        }
    }


    private fun logSessionStatistics() {
        val currentTime = System.currentTimeMillis()
        val sessionDuration = (currentTime - sessionStartTime) / 1000 // seconds

        Log.d(TAG, "📊 === SESSION STATISTICS UPDATE ===")
        Log.d(TAG, "⏱️  Running Duration: ${sessionDuration}s")
        Log.d(TAG, "🎯 Subtitles Generated: $subtitleGenerationCount")
        Log.d(TAG, "🎵 Audio Detections: $audioDetectionCount")
        Log.d(TAG, "📈 Current Success Rate: ${String.format("%.1f", if (audioDetectionCount > 0) (subtitleGenerationCount.toFloat() / audioDetectionCount.toFloat()) * 100 else 0f)}%")
        Log.d(TAG, "📝 Latest Subtitle: '$lastSubtitleText'")
        Log.d(TAG, "=== STATISTICS UPDATE ===")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "audio_capture_channel",
                "Audio Capture Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Real-time audio capture for subtitle generation"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        try {
            val notification = Notification.Builder(this, "audio_capture_channel")
                .setContentTitle("Real-Time Subtitles")
                .setContentText("Capturing audio for subtitle generation")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                Log.d(TAG, "Started foreground service with MICROPHONE type")
            } else {
                startForeground(2, notification)
                Log.d(TAG, "Started foreground service (legacy)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}")
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        val sessionEndTime = System.currentTimeMillis()
        val totalSessionDuration = (sessionEndTime - sessionStartTime) / 1000 // seconds

        Log.d(TAG, "=== AUDIO CAPTURE SESSION ENDED ===")
        Log.d(TAG, "📊 SESSION SUMMARY:")
        Log.d(TAG, "⏱️  Total Duration: ${totalSessionDuration}s")
        Log.d(TAG, "🎯 Total Subtitles Generated: $subtitleGenerationCount")
        Log.d(TAG, "🎵 Total Audio Detections: $audioDetectionCount")
        Log.d(TAG, "📈 Success Rate: ${String.format("%.1f", if (audioDetectionCount > 0) (subtitleGenerationCount.toFloat() / audioDetectionCount.toFloat()) * 100 else 0f)}%")
        Log.d(TAG, "📝 Last Subtitle: '$lastSubtitleText'")
        Log.d(TAG, "🕒 Session End: ${java.util.Date(sessionEndTime)}")
        Log.d(TAG, "=== SESSION COMPLETE ===")

        Log.d(TAG, "AudioCaptureService destroyed")

        try {
            isRecording = false
            isListening = false

            // Stop speech recognition on main thread
            mainHandler.post {
                try {
                    speechRecognizer?.stopListening()
                    speechRecognizer?.destroy()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping speech recognizer: ${e.message}")
                }
            }

            if (::audioRecord.isInitialized) {
                try {
                    audioRecord.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping audio record: ${e.message}")
                }
                try {
                    audioRecord.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Error releasing audio record: ${e.message}")
                }
            }

            try {
                handlerThread.quitSafely()
            } catch (e: Exception) {
                Log.w(TAG, "Error quitting handler thread: ${e.message}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}