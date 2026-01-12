package com.chopcut.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp

/**
 * Simple waveform visualization showing downsampled amplitude values
 * Uses Canvas for efficient rendering of many bars
 *
 * @param amplitudes List of amplitude values (0.0 to 1.0, pre-downsampled)
 * @param maxAmp Maximum amplitude value for color scaling
 * @param avgAmp Average amplitude value for color scaling
 * @param mirrored If true, bars are mirrored from center (waveform style)
 * @param modifier Modifier
 */
@Composable
fun WaveForm(
    amplitudes: List<Float>,
    maxAmp: Float = 0.5f,
    avgAmp: Float = 0.1f,
    mirrored: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (amplitudes.isEmpty()) {
        Text(
            text = "No audio data",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // Calculate dynamic thresholds - less red, more uniform yellow/green
    val highThreshold = avgAmp + (maxAmp - avgAmp) * 0.3f
    val lowThreshold = avgAmp * 0.6f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(Color.DarkGray)
    ) {
        val barWidth = size.width / amplitudes.size
        val gap = 0.5f

        amplitudes.forEachIndexed { index, amp ->
            val normalizedAmp = if (maxAmp > 0) (amp / maxAmp).coerceIn(0f, 1f) else 0f

            // Red/orange bars are 10%, yellow/green are tall
            val barHeight = when {
                amp <= lowThreshold -> size.height * 0.05f  // Red = 5%
                else -> size.height * (normalizedAmp * 0.75f + 0.20f)  // Yellow/green = tall
            }

            val x = index * barWidth
            val y = if (mirrored) {
                (size.height - barHeight) / 2  // Centered
            } else {
                size.height - barHeight  // Bottom aligned
            }

            val color = when {
                amp > highThreshold -> Color(0xFF4CAF50)  // Green
                amp > lowThreshold -> Color(0xFFFFC107)  // Yellow
                else -> Color(0xFFFF5722)  // Orange (red) - small
            }

            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(x + gap, y),
                size = androidx.compose.ui.geometry.Size(barWidth - gap * 2, barHeight),
                cornerRadius = CornerRadius(1f, 1f)
            )
        }
    }
}

/**
 * Waveform data model
 */
data class WaveformData(
    val amplitudes: List<Float>,
    val sampleRate: Int,
    val durationMs: Long
) {
    companion object {
        fun empty() = WaveformData(
            amplitudes = emptyList(),
            sampleRate = 44100,
            durationMs = 0
        )
    }
}
