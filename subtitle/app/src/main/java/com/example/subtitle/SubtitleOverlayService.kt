package com.example.subtitle

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.subtitle.viewmodel.SubtitleViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Service for displaying the subtitle overlay on top of other apps.
 * Uses WindowManager to create a system-level overlay window with a simple TextView.
 */
class SubtitleOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var subtitleTextView: TextView
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "SubtitleService"
    }


    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SubtitleOverlayService created")

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Create a simple LinearLayout with TextView for the overlay
        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER

            // Create TextView for subtitles
            subtitleTextView = TextView(this@SubtitleOverlayService).apply {
                text = ""
                textSize = 18f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#80000000")) // Semi-transparent black
                setPadding(16, 8, 16, 8)
                gravity = Gravity.CENTER
                visibility = View.GONE // Initially hidden
            }

            addView(subtitleTextView)
        }

        // Window parameters for overlay
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100 // Position from top
        }

        try {
            windowManager.addView(overlayView, params)
            Log.d(TAG, "Overlay view added to window manager")

            // Collect subtitle state changes
            serviceScope.launch {
                SubtitleViewModel.subtitleState.collectLatest { state ->
                    updateSubtitleDisplay(state)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view: ${e.message}")
            stopSelf()
        }
    }

    private fun updateSubtitleDisplay(state: com.example.subtitle.model.SubtitleState) {
        subtitleTextView.apply {
            // Display partial text if available, otherwise final text
            text = when {
                state.partialText.isNotEmpty() -> state.partialText
                state.finalText.isNotEmpty() -> state.finalText
                else -> ""
            }
            visibility = if (state.isVisible && text.isNotEmpty()) View.VISIBLE else View.GONE

            // Update position and appearance
            alpha = state.opacity

            // Update background
            if (state.backgroundEnabled) {
                setBackgroundColor(Color.parseColor("#80000000")) // Semi-transparent black
            } else {
                setBackgroundColor(Color.TRANSPARENT)
            }

            // Update position (simplified - you can enhance this)
            val layoutParams = layoutParams as? WindowManager.LayoutParams
            layoutParams?.let {
                it.x = (state.positionX * resources.displayMetrics.widthPixels).toInt()
                it.y = (state.positionY * resources.displayMetrics.heightPixels).toInt()
                try {
                    windowManager.updateViewLayout(overlayView, it)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update overlay position: ${e.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Overlay service stopped")

        // Cancel coroutine scope
        serviceScope.cancel()

        try {
            if (::overlayView.isInitialized) {
                windowManager.removeView(overlayView)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay view: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}