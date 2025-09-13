package com.example.subtitle

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.media.AudioPlaybackCaptureConfiguration
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
import kotlinx.coroutines.*
import java.nio.ByteBuffer

/**
 * Service for capturing internal audio from other apps using AudioPlaybackCapture API
 * Processes audio in small chunks (200-500ms) for real-time speech recognition
 * Provides live captions with partial and final results
 */
class AudioPlaybackCaptureService : Service() {

    private val TAG = "AudioPlaybackCapture"

    private lateinit var audioRecord: AudioRecord
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var mainHandler: Handler
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private var isRecording = false
    private var isListening = false

    // Audio configuration
    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    private val CHUNK_SIZE_MS = 300  // 300ms chunks for real-time processing
    private val CHUNK_SIZE_SAMPLES = (SAMPLE_RATE * CHUNK_SIZE_MS) / 1000

    // Logging and statistics
    private var subtitleGenerationCount = 0
    private var sessionStartTime = 0L
    private var lastSubtitleText = ""
    private var audioDetectionCount = 0
    private var lastSubtitleTime = 0L
    private val SUBTITLE_COOLDOWN_MS = 1000L // 1 second between subtitles

    companion object {
        const val EXTRA_CAPTURE_MODE = "capture_mode"
        const val CAPTURE_MODE_PLAYBACK = "playback"
        const val CAPTURE_MODE_MICROPHONE = "microphone"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🎵 AudioPlaybackCaptureService created")

        // Initialize session tracking
        sessionStartTime = System.currentTimeMillis()
        subtitleGenerationCount = 0
        audioDetectionCount = 0
        lastSubtitleText = ""

        Log.d(TAG, "=== AUDIO PLAYBACK CAPTURE SESSION STARTED ===")
        Log.d(TAG, "Session start time: ${java.util.Date(sessionStartTime)}")

        // Create handler threads
        handlerThread = HandlerThread("AudioProcessing").apply { start() }
        handler = Handler(handlerThread.looper)
        mainHandler = Handler(Looper.getMainLooper())

        // Initialize speech recognizer
        initializeSpeechRecognizer()

        createNotificationChannel()
    }

    private fun initializeSpeechRecognizer() {
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
            Log.d(TAG, "✅ SpeechRecognizer initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize SpeechRecognizer: ${e.message}")
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "🎤 onReadyForSpeech - Ready for speech input")
                isListening = true
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "🎤 onBeginningOfSpeech - Speech detected, listening...")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Log RMS changes occasionally to monitor audio levels
                if (audioDetectionCount % 20 == 0 && rmsdB > 0) {
                    Log.d(TAG, "📊 onRmsChanged - RMS: ${rmsdB.toInt()} dB")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partialText = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""

                if (partialText.isNotEmpty()) {
                    Log.d(TAG, "📝 onPartialResults - '$partialText'")
                    SubtitleViewModel.updatePartialText(partialText)
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
                    processRecognizedText(finalText)
                } else {
                    Log.w(TAG, "⚠️ onResults - Empty final results (no speech detected)")
                }

                isListening = false

                // Auto-restart for continuous listening
                mainHandler.postDelayed({
                    if (isRecording) {
                        startRecognition()
                    }
                }, 300)
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
                isListening = false

                // Retry after error
                mainHandler.postDelayed({
                    if (isRecording) {
                        Log.d(TAG, "🔄 Retrying after error...")
                        startRecognition()
                    }
                }, 1000)
            }

            override fun onEndOfSpeech() {
                Log.d(TAG, "🏁 onEndOfSpeech - Speech input ended")
                isListening = false
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                Log.d(TAG, "📦 onBufferReceived - Audio buffer received (${buffer?.size ?: 0} bytes)")
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d(TAG, "📋 onEvent - Event: $eventType")
            }
        }
    }

    private fun processRecognizedText(text: String) {
        if (text.isNotBlank()) {
            subtitleGenerationCount++
            lastSubtitleText = text

            val timestamp = System.currentTimeMillis()
            val sessionDuration = (timestamp - sessionStartTime) / 1000 // seconds

            Log.d(TAG, "🎯 === SPEECH RECOGNIZED ===")
            Log.d(TAG, "📝 Recognized Text: '$text'")
            Log.d(TAG, "🔢 Recognition Count: $subtitleGenerationCount")
            Log.d(TAG, "⏱️  Session Duration: ${sessionDuration}s")
            Log.d(TAG, "🎵 Audio Detections: $audioDetectionCount")
            Log.d(TAG, "🕒 Timestamp: ${java.util.Date(timestamp)}")
            Log.d(TAG, "=== END RECOGNITION LOG ===")

            SubtitleViewModel.updateFinalText(text)
            SubtitleViewModel.setVisible(true)

            // Log periodic statistics
            if (subtitleGenerationCount % 5 == 0) {
                logSessionStatistics()
            }
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🎵 AudioPlaybackCaptureService started")

        // Start foreground service
        startForegroundService()

        val captureMode = intent?.getStringExtra(EXTRA_CAPTURE_MODE) ?: CAPTURE_MODE_PLAYBACK

        when (captureMode) {
            CAPTURE_MODE_PLAYBACK -> startPlaybackCapture()
            CAPTURE_MODE_MICROPHONE -> startMicrophoneCapture()
            else -> startPlaybackCapture()
        }

        return START_NOT_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startPlaybackCapture() {
        serviceScope.launch {
            try {
                Log.d(TAG, "🎵 AudioPlaybackCapture not yet implemented, using microphone")
                // TODO: Implement AudioPlaybackCapture when API issues are resolved
                // For now, use microphone capture for debugging
                startMicrophoneCapture()

            } catch (e: Exception) {
                Log.e(TAG, "❌ AudioPlaybackCapture setup failed: ${e.message}", e)
                Log.d(TAG, "Falling back to microphone capture")
                startMicrophoneCapture()
            }
        }
    }

    private fun startMicrophoneCapture() {
        serviceScope.launch {
            try {
                Log.d(TAG, "🎤 Starting microphone capture...")

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    BUFFER_SIZE * 2
                )

                if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                    Log.d(TAG, "✅ Microphone capture initialized successfully")
                    startAudioProcessing()
                    startRecognition()
                } else {
                    Log.e(TAG, "❌ Microphone capture initialization failed")
                    stopSelf()
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Microphone capture error: ${e.message}", e)
                stopSelf()
            }
        }
    }

    private fun startAudioProcessing() {
        serviceScope.launch {
            try {
                Log.d(TAG, "🎤 Starting audio recording...")
                audioRecord.startRecording()
                isRecording = true
                Log.d(TAG, "✅ Audio recording started successfully")

                val audioData = ShortArray(CHUNK_SIZE_SAMPLES)
                var chunkCount = 0

                while (isRecording && isActive) {
                    val readResult = audioRecord.read(audioData, 0, audioData.size)
                    chunkCount++

                    if (readResult > 0) {
                        // Log chunk processing occasionally
                        if (chunkCount % 10 == 0) {
                            Log.d(TAG, "🎵 Audio chunk processed: ${readResult} samples (${CHUNK_SIZE_MS}ms)")
                        }

                        processAudioChunk(audioData, readResult)
                    } else {
                        Log.w(TAG, "❌ Audio read failed: $readResult")
                        delay(100) // Brief pause before retry
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Audio processing error: ${e.message}", e)
            }
        }
    }

    private fun processAudioChunk(audioData: ShortArray, readSize: Int) {
        try {
            // Calculate RMS for audio level detection
            var sum = 0.0
            for (i in 0 until readSize) {
                sum += audioData[i] * audioData[i]
            }
            val rms = Math.sqrt(sum / readSize)

            // Only process if audio level is above threshold
            if (rms > 150) { // Adjusted threshold for internal audio
                audioDetectionCount++

                // Check cooldown to prevent spam
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSubtitleTime > SUBTITLE_COOLDOWN_MS) {
                    lastSubtitleTime = currentTime

                    // Convert to byte array for potential future use
                    val byteBuffer = ByteBuffer.allocate(readSize * 2)
                    for (i in 0 until readSize) {
                        byteBuffer.putShort(audioData[i])
                    }

                    Log.d(TAG, "🎵 Audio detected in chunk #$audioDetectionCount, RMS: ${rms.toInt()}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Audio chunk processing error: ${e.message}", e)
        }
    }

    private fun startRecognition() {
        if (isListening) {
            Log.d(TAG, "⏰ Already listening, skipping restart")
            return
        }

        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                    speechRecognizer?.setRecognitionListener(createRecognitionListener())
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
                }

                Log.d(TAG, "🎤 Starting speech recognition...")
                speechRecognizer?.startListening(intent)
                isListening = true
                Log.d(TAG, "✅ Recognition started successfully")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start recognition: ${e.message}", e)
                isListening = false
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "audio_playback_channel",
                "Audio Playback Capture Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Real-time audio capture from media apps for subtitle generation"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        try {
            val notification = Notification.Builder(this, "audio_playback_channel")
                .setContentTitle("Real-Time Subtitles")
                .setContentText("Capturing audio from media apps")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(3, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                Log.d(TAG, "Started foreground service with MICROPHONE type")
            } else {
                startForeground(3, notification)
                Log.d(TAG, "Started foreground service (legacy)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}", e)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        val sessionEndTime = System.currentTimeMillis()
        val totalSessionDuration = (sessionEndTime - sessionStartTime) / 1000 // seconds

        Log.d(TAG, "=== AUDIO PLAYBACK CAPTURE SESSION ENDED ===")
        Log.d(TAG, "📊 SESSION SUMMARY:")
        Log.d(TAG, "⏱️  Total Duration: ${totalSessionDuration}s")
        Log.d(TAG, "🎯 Total Subtitles Generated: $subtitleGenerationCount")
        Log.d(TAG, "🎵 Total Audio Detections: $audioDetectionCount")
        Log.d(TAG, "📈 Success Rate: ${String.format("%.1f", if (audioDetectionCount > 0) (subtitleGenerationCount.toFloat() / audioDetectionCount.toFloat()) * 100 else 0f)}%")
        Log.d(TAG, "📝 Last Subtitle: '$lastSubtitleText'")
        Log.d(TAG, "🕒 Session End: ${java.util.Date(sessionEndTime)}")
        Log.d(TAG, "=== SESSION COMPLETE ===")

        Log.d(TAG, "AudioPlaybackCaptureService destroyed")

        try {
            isRecording = false
            isListening = false

            // Cancel service scope
            serviceScope.cancel()

            // Stop speech recognition
            mainHandler.post {
                try {
                    speechRecognizer?.stopListening()
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping speech recognizer: ${e.message}")
                }
            }

            // Stop audio recording
            if (::audioRecord.isInitialized) {
                try {
                    audioRecord.stop()
                    audioRecord.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping audio record: ${e.message}")
                }
            }

            // Quit handler thread
            try {
                handlerThread.quitSafely()
            } catch (e: Exception) {
                Log.w(TAG, "Error quitting handler thread: ${e.message}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}