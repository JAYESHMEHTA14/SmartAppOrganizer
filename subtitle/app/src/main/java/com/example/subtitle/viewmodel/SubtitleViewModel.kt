package com.example.subtitle.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.subtitle.model.SubtitleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Singleton ViewModel for managing subtitle overlay state and real-time subtitle generation.
 * Handles UI state updates and integrates with MediaProjection Service for audio capture and speech-to-text.
 * Used by both MainActivity and MediaProjectionService for shared state.
 */
object SubtitleViewModel : ViewModel() {

    private val _subtitleState = MutableStateFlow(SubtitleState())
    val subtitleState: StateFlow<SubtitleState> = _subtitleState

    // Media projection state
    private var isMediaProjectionActive = false

    /**
     * Updates partial subtitle text (live word-by-word updates).
     */
    fun updatePartialText(text: String) {
        Log.d("SubtitleViewModel", "📝 Updating partial text: '$text'")
        _subtitleState.value = _subtitleState.value.copy(partialText = text)
    }

    /**
     * Updates final subtitle text (confirmed results).
     */
    fun updateFinalText(text: String) {
        Log.d("SubtitleViewModel", "✅ Updating final text: '$text'")
        _subtitleState.value = _subtitleState.value.copy(
            partialText = "",
            finalText = text
        )
    }

    /**
     * Legacy method for backward compatibility.
     */
    fun updateText(text: String) {
        Log.d("SubtitleViewModel", "Updating subtitle text: '$text'")
        updateFinalText(text)
    }

    /**
     * Updates the subtitle position.
     */
    fun updatePosition(x: Float, y: Float) {
        _subtitleState.value = _subtitleState.value.copy(positionX = x, positionY = y)
    }

    /**
     * Updates the subtitle opacity.
     */
    fun updateOpacity(opacity: Float) {
        _subtitleState.value = _subtitleState.value.copy(opacity = opacity)
    }

    /**
     * Toggles the subtitle background.
     */
    fun toggleBackground() {
        _subtitleState.value = _subtitleState.value.copy(backgroundEnabled = !_subtitleState.value.backgroundEnabled)
    }

    /**
     * Sets the visibility of the subtitle overlay.
     */
    fun setVisible(visible: Boolean) {
        Log.d("SubtitleViewModel", "Setting subtitle visibility: $visible")
        _subtitleState.value = _subtitleState.value.copy(isVisible = visible)
    }

    /**
     * Clears the current subtitle text (both partial and final).
     */
    fun clearText() {
        Log.d("SubtitleViewModel", "Clearing subtitle text")
        _subtitleState.value = _subtitleState.value.copy(
            partialText = "",
            finalText = ""
        )
    }

    /**
     * Gets the current subtitle text (partial takes priority over final).
     */
    fun getCurrentText(): String {
        val state = _subtitleState.value
        return when {
            state.partialText.isNotEmpty() -> state.partialText
            state.finalText.isNotEmpty() -> state.finalText
            else -> ""
        }
    }

    /**
     * Checks if subtitles are currently visible.
     */
    fun isVisible(): Boolean {
        return _subtitleState.value.isVisible
    }

    /**
     * Sets media projection active state.
     */
    fun setMediaProjectionActive(active: Boolean) {
        isMediaProjectionActive = active
        Log.d("SubtitleViewModel", "Media projection active: $active")
    }

    /**
     * Checks if media projection is active.
     */
    fun isMediaProjectionActive(): Boolean {
        return isMediaProjectionActive
    }

    /**
     * Starts real-time subtitle generation via media projection.
     */
    fun startRealTimeSubtitles() {
        Log.d("SubtitleViewModel", "Real-time subtitle generation requested")
        // MediaProjectionService handles the actual audio capture and processing
        setVisible(true)
    }

    /**
     * Stops real-time subtitle generation.
     */
    fun stopRealTimeSubtitles() {
        Log.d("SubtitleViewModel", "Real-time subtitle generation stopped")
        setVisible(false)
        clearText()
        setMediaProjectionActive(false)
    }

    /**
     * Legacy method for backward compatibility.
     */
    fun startSpeechRecognition() {
        Log.d("SubtitleViewModel", "Speech recognition start requested - using media projection")
        startRealTimeSubtitles()
    }

    /**
     * Legacy method for backward compatibility.
     */
    fun stopSpeechRecognition() {
        Log.d("SubtitleViewModel", "Speech recognition stop requested")
        stopRealTimeSubtitles()
    }
}