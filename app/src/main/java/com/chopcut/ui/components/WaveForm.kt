package com.chopcut.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp

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

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(amplitudes) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600)
        )
    }

    val highThreshold = avgAmp + (maxAmp - avgAmp) * 0.3f
    val lowThreshold = avgAmp * 0.6f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460)
                    )
                )
            )
    ) {
        val barWidth = size.width / amplitudes.size
        val gap = 1f

        amplitudes.forEachIndexed { index, amp ->
            val normalizedAmp = if (maxAmp > 0) (amp / maxAmp).coerceIn(0f, 1f) else 0f
            val animatedAmp = normalizedAmp * animatedProgress.value

            val barHeight = when {
                amp <= lowThreshold -> size.height * 0.08f
                else -> size.height * (animatedAmp * 0.80f + 0.15f)
            }

            val x = index * barWidth
            val y = if (mirrored) {
                (size.height - barHeight) / 2
            } else {
                size.height - barHeight
            }

            val baseColor = Color.White

            val glowColor = baseColor.copy(alpha = 0.3f)

            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = glowColor.hashCode()
                    maskFilter = android.graphics.BlurMaskFilter(8f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                }
                canvas.nativeCanvas.drawRoundRect(
                    x + gap,
                    y - 4f,
                    x + barWidth - gap * 2,
                    y + barHeight + 4f,
                    4f,
                    4f,
                    paint
                )
            }

            drawRoundRect(
                color = Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(x + gap, y),
                size = androidx.compose.ui.geometry.Size(barWidth - gap * 2, barHeight),
                cornerRadius = CornerRadius(2f, 2f)
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
