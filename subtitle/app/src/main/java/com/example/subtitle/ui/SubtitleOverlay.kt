package com.example.subtitle.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.subtitle.model.SubtitleState

/**
 * Composable for rendering the subtitle overlay with low-latency captions.
 * Displays partial text (live updates) when available, otherwise final text.
 * Includes smooth animations for position and opacity changes.
 * Supports Live Caption-like experience with word-by-word updates.
 */
@Composable
fun SubtitleOverlay(state: SubtitleState) {
    // Determine which text to display: partial takes priority over final
    val displayText = when {
        state.partialText.isNotEmpty() -> state.partialText
        state.finalText.isNotEmpty() -> state.finalText
        else -> ""
    }

    // Don't render if no text or not visible
    if (!state.isVisible || displayText.isEmpty()) return

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    // Animate position and opacity for smooth transitions
    val animatedX = animateFloatAsState(targetValue = state.positionX)
    val animatedY = animateFloatAsState(targetValue = state.positionY)
    val animatedOpacity = animateFloatAsState(targetValue = state.opacity)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset(
                x = (animatedX.value * screenWidth.value).dp,
                y = (animatedY.value * screenHeight.value).dp
            )
            .alpha(animatedOpacity.value)
    ) {
        val textModifier = if (state.backgroundEnabled) {
            Modifier
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(8.dp)
        } else {
            Modifier
        }

        Text(
            text = displayText,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = textModifier
        )
    }
}