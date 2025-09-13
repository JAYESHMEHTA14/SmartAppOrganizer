package com.example.subtitle.ui.controls

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.subtitle.viewmodel.SubtitleViewModel
import kotlinx.coroutines.flow.collect

/**
 * Composable for subtitle overlay controls.
 * Provides sliders for position and opacity, and toggle for background.
 */
@Composable
fun SubtitleControls(viewModel: SubtitleViewModel) {
    val state by viewModel.subtitleState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text("Horizontal Position (0.0 - 1.0)")
        Slider(
            value = state.positionX,
            onValueChange = { viewModel.updatePosition(it, state.positionY) },
            valueRange = 0f..1f
        )

        Text("Vertical Position (0.0 - 1.0)")
        Slider(
            value = state.positionY,
            onValueChange = { viewModel.updatePosition(state.positionX, it) },
            valueRange = 0f..1f
        )

        Text("Opacity (0.0 - 1.0)")
        Slider(
            value = state.opacity,
            onValueChange = { viewModel.updateOpacity(it) },
            valueRange = 0f..1f
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Background")
            Switch(
                checked = state.backgroundEnabled,
                onCheckedChange = { viewModel.toggleBackground() }
            )
        }
    }
}