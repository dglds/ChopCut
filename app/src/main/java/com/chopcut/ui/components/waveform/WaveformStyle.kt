package com.chopcut.ui.components.waveform

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class WaveformStyle(
    val style: Style = Style.Line,
    val strokeWidth: Dp = 1.5.dp,
    val color: Color = Color(0xFF00D9FF), // Cyan Neon
    val isMirrored: Boolean = false,
    val isSmoothed: Boolean = true,
    val heightScale: Float = 0.8f,
    val baseline: Baseline = Baseline.Center
) {
    enum class Style {
        Line,
        Filled,
        Bars
    }

    enum class Baseline {
        Top,
        Center,
        Bottom
    }
}
