package com.example.subtitle

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import com.example.subtitle.viewmodel.SubtitleViewModel
import java.nio.ByteBuffer

/**
 * Service for capturing screen and audio using MediaProjection API
 * Processes audio in real-time and generates subtitles
 */
class MediaProjectionService : Service() {

    private val TAG = "MediaProjectionService"

    private lateinit var mediaProjection: MediaProjection
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var audioRecord: AudioRecord
    private lateinit var mediaProjectionManager: MediaProjectionManager

    private lateinit var windowManager: android.view.WindowManager

    // Audio processing
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler
    private var isRecording = false

    // Logging and statistics
    private var subtitleGenerationCount = 0
    private var sessionStartTime = 0L
    private var lastSubtitleText = ""
    private var audioDetectionCount = 0

    // Audio format constants
    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MediaProjectionService created")

        // Initialize session tracking
        sessionStartTime = System.currentTimeMillis()
        subtitleGenerationCount = 0
        audioDetectionCount = 0
        lastSubtitleText = ""

        Log.d(TAG, "=== SUBTITLE GENERATION SESSION STARTED ===")
        Log.d(TAG, "Session start time: ${java.util.Date(sessionStartTime)}")

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        windowManager = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager

        // Create handler thread for audio processing
        handlerThread = HandlerThread("AudioProcessing")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        createNotificationChannel()
        // Note: We cannot start foreground service here because we don't have media projection data yet
        // The service will be started as foreground after media projection is successfully set up
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "MediaProjectionService started")

        // CRITICAL: Must call startForeground() immediately when started as foreground service
        // Android requires this within 5 seconds or the service will be killed
        startForegroundImmediately()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)

        if (resultCode == Activity.RESULT_OK && data != null) {
            startMediaProjection(resultCode, data)
        } else {
            Log.e(TAG, "Invalid media projection data - stopping service")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startMediaProjection(resultCode: Int, data: Intent) {
        try {
            // Check if we have the required permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasPermission = checkSelfPermission("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION") == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasPermission) {
                    Log.e(TAG, "Missing FOREGROUND_SERVICE_MEDIA_PROJECTION permission")
                    stopSelf()
                    return
                }
            }

            val projection = mediaProjectionManager.getMediaProjection(resultCode, data)
            if (projection == null) {
                Log.e(TAG, "Failed to get media projection")
                stopSelf()
                return
            }
            mediaProjection = projection

            // Register callback before creating virtual display
            mediaProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped")
                    stopSelf()
                }
            }, handler)

            // Create virtual display for screen capture (needed for media projection)
            val display = mediaProjection.createVirtualDisplay(
                "SubtitleCapture",
                1, 1, // Minimal size since we only need audio
                resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                null, null, null
            )
            if (display == null) {
                Log.e(TAG, "Failed to create virtual display")
                stopSelf()
                return
            }
            virtualDisplay = display

            setupAudioRecording()
            startAudioProcessing()

            // Now start foreground service since we have all required data
            startForegroundServiceWithMediaProjection()

            Log.d(TAG, "Media projection started successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start media projection: ${e.message}")
            Log.e(TAG, "Media projections require a foreground service of type ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION")
            stopSelf()
        }
    }

    private fun setupAudioRecording() {
        try {
            val audioSource = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaRecorder.AudioSource.REMOTE_SUBMIX // System audio
            } else {
                MediaRecorder.AudioSource.MIC // Fallback to mic
            }

            audioRecord = AudioRecord(
                audioSource,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                BUFFER_SIZE * 2
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                stopSelf()
                return
            }

            Log.d(TAG, "Audio recording setup complete")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup audio recording: ${e.message}")
            stopSelf()
        }
    }


    private fun startAudioProcessing() {
        handler.post {
            try {
                audioRecord.startRecording()
                isRecording = true
                Log.d(TAG, "Audio recording started")

                val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
                val audioData = ShortArray(BUFFER_SIZE / 2)

                while (isRecording) {
                    val readResult = audioRecord.read(audioData, 0, audioData.size)

                    if (readResult > 0) {
                        processAudioData(audioData, readResult)
                    } else {
                        Log.w(TAG, "Audio read failed: $readResult")
                        Thread.sleep(100) // Brief pause before retry
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Audio processing error: ${e.message}")
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

            // Only process if audio level is above threshold (indicates speech)
            if (rms > 100) { // Adjust threshold as needed
                audioDetectionCount++
                Log.d(TAG, "🎵 Audio detected #$audioDetectionCount, RMS: $rms")

                // Convert short array to byte array for speech recognition
                val byteBuffer = ByteBuffer.allocate(readSize * 2)
                for (i in 0 until readSize) {
                    byteBuffer.putShort(audioData[i])
                }

                // Here you would integrate with speech recognition API
                // For now, we'll simulate subtitle generation
                simulateSubtitleGeneration(byteBuffer.array())
            } else {
                // Log low audio levels occasionally to debug
                if (audioDetectionCount % 50 == 0) {
                    Log.d(TAG, "🔇 Low audio level, RMS: $rms (threshold: 100)")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Audio processing error: ${e.message}")
        }
    }

    private fun simulateSubtitleGeneration(audioData: ByteArray) {
        // This is a placeholder for actual speech-to-text processing
        // In a real implementation, you would:
        // 1. Send audioData to Google Speech-to-Text API
        // 2. Process the response
        // 3. Update subtitles

        // For demonstration, we'll create sample subtitles
        val sampleTexts = listOf(
            "Hello world",
            "This is a test",
            "Real-time subtitles",
            "Working perfectly",
            "Speech recognition active",
            "Subtitle generation successful",
            "Audio processing complete",
            "Text extraction working",
            "Live caption system active",
            "Voice to text conversion"
        )

        val generatedText = sampleTexts.random()
        subtitleGenerationCount++
        lastSubtitleText = generatedText

        val timestamp = System.currentTimeMillis()
        val sessionDuration = (timestamp - sessionStartTime) / 1000 // seconds

        Log.d(TAG, "🎯 === SUBTITLE GENERATED ===")
        Log.d(TAG, "📝 Exact Text: '$generatedText'")
        Log.d(TAG, "🔢 Generation Count: $subtitleGenerationCount")
        Log.d(TAG, "⏱️  Session Duration: ${sessionDuration}s")
        Log.d(TAG, "🎵 Audio Detections: $audioDetectionCount")
        Log.d(TAG, "📊 Success Rate: ${String.format("%.1f", (subtitleGenerationCount.toFloat() / audioDetectionCount.toFloat()) * 100)}%")
        Log.d(TAG, "🕒 Timestamp: ${java.util.Date(timestamp)}")
        Log.d(TAG, "=== END SUBTITLE LOG ===")

        SubtitleViewModel.updateText(generatedText)
        SubtitleViewModel.setVisible(true)

        // Log periodic statistics every 10 subtitles
        if (subtitleGenerationCount % 10 == 0) {
            logSessionStatistics()
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
                "subtitle_channel",
                "Subtitle Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Real-time subtitle generation service"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundImmediately() {
        try {
            val notification = Notification.Builder(this, "subtitle_channel")
                .setContentTitle("Real-Time Subtitles")
                .setContentText("Initializing media projection...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                Log.d(TAG, "Started foreground service immediately with MEDIA_PROJECTION type")
            } else {
                startForeground(1, notification)
                Log.d(TAG, "Started foreground service immediately (legacy)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service immediately: ${e.message}")
            stopSelf()
        }
    }

    private fun startForegroundServiceWithMediaProjection() {
        try {
            val notification = Notification.Builder(this, "subtitle_channel")
                .setContentTitle("Real-Time Subtitles")
                .setContentText("Generating subtitles from audio")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                Log.d(TAG, "Updated foreground service with MEDIA_PROJECTION type")
            } else {
                startForeground(1, notification)
                Log.d(TAG, "Updated foreground service (legacy)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update foreground service: ${e.message}")
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        val sessionEndTime = System.currentTimeMillis()
        val totalSessionDuration = (sessionEndTime - sessionStartTime) / 1000 // seconds

        Log.d(TAG, "=== SUBTITLE GENERATION SESSION ENDED ===")
        Log.d(TAG, "📊 SESSION SUMMARY:")
        Log.d(TAG, "⏱️  Total Duration: ${totalSessionDuration}s")
        Log.d(TAG, "🎯 Total Subtitles Generated: $subtitleGenerationCount")
        Log.d(TAG, "🎵 Total Audio Detections: $audioDetectionCount")
        Log.d(TAG, "📈 Success Rate: ${String.format("%.1f", if (audioDetectionCount > 0) (subtitleGenerationCount.toFloat() / audioDetectionCount.toFloat()) * 100 else 0f)}%")
        Log.d(TAG, "📝 Last Subtitle: '$lastSubtitleText'")
        Log.d(TAG, "🕒 Session End: ${java.util.Date(sessionEndTime)}")
        Log.d(TAG, "=== SESSION COMPLETE ===")

        Log.d(TAG, "MediaProjectionService destroyed")

        try {
            isRecording = false

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

            if (::virtualDisplay.isInitialized) {
                try {
                    virtualDisplay.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Error releasing virtual display: ${e.message}")
                }
            }

            if (::mediaProjection.isInitialized) {
                try {
                    mediaProjection.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping media projection: ${e.message}")
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