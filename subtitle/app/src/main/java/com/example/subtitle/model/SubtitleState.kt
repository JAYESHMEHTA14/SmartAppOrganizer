package com.example.subtitle.model

/**
 * Data class representing the state of the subtitle overlay.
 * Includes partial/final text, language, positioning, opacity, and visibility settings.
 * Supports low-latency captions with partial results for Live Caption-like experience.
 */
data class SubtitleState(
    val partialText: String = "", // Live partial results (word-by-word updates)
    val finalText: String = "",   // Confirmed final results
    val language: String = "en", // Supported: "en" (English), "hi" (Hindi), "hi-en" (Hinglish)
    val positionX: Float = 0.5f, // Horizontal position (0.0 = left, 1.0 = right)
    val positionY: Float = 0.8f, // Vertical position (0.0 = top, 1.0 = bottom)
    val opacity: Float = 1.0f, // Opacity level (0.0 = transparent, 1.0 = opaque)
    val backgroundEnabled: Boolean = true, // Toggle for subtitle background
    val isVisible: Boolean = false // Whether the overlay is currently visible
)