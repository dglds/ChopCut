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

/**
 * Visualizador de Waveform com efeitos neon e animação
 *
 * Estilo osciloscópio com gradiente dark, glow e cores vibrantes
 *
 * @param amplitudes Lista de amplitudes do áudio (0.0 a 1.0)
 * @param maxAmp Amplitude máxima para normalização
 * @param avgAmp Amplitude média para cálculo de thresholds
 * @param mirrored Se true, espelha verticalmente (centrado)
 * @param modifier Modificador
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
            .height(36.dp) // Fixed height match parent
            .background(Color.Transparent)
    ) {
        if (amplitudes.isEmpty()) return@Canvas

        val path = androidx.compose.ui.graphics.Path()
        // Ensure we have enough width per sample to look good, or downsample if too dense?
        // For now, draw all points.
        val barWidth = size.width / amplitudes.size.coerceAtLeast(1)
        val centerY = size.height / 2f
        val maxApparentHeight = size.height / 2f

        // Start from center left
        path.moveTo(0f, centerY)

        // Draw top envelope
        amplitudes.forEachIndexed { index, amp ->
            val normalizedAmp = if (maxAmp > 0) (amp / maxAmp).coerceIn(0f, 1f) else 0f
            // Animate magnitude
            val value = normalizedAmp * animatedProgress.value
            
            val x = index * barWidth
            val y = centerY - (value * maxApparentHeight * 0.9f)
            
            if (index == 0) path.lineTo(x, y)
            else {
                val prevX = (index - 1) * barWidth
                val prevAmp = (if (maxAmp > 0) (amplitudes[index - 1] / maxAmp).coerceIn(0f, 1f) else 0f) * animatedProgress.value
                val prevY = centerY - (prevAmp * maxApparentHeight * 0.9f)
                
                // Smooth curve
                val midX = (prevX + x) / 2
                val midY = (prevY + y) / 2
                path.quadraticBezierTo(prevX, prevY, midX, midY)
            }
        }

        // Draw bottom envelope (reverse) to close the loop smoothly or just line back?
        // User wants "A LINE". A closed loop looks like a shape.
        // If I just draw top and bottom separately, they are lines.
        // Let's connect them at the end and go back.
        
        val lastX = amplitudes.size * barWidth
        path.lineTo(lastX, centerY)

        // Reverse for bottom
        for (i in amplitudes.indices.reversed()) {
            val amp = amplitudes[i]
            val normalizedAmp = if (maxAmp > 0) (amp / maxAmp).coerceIn(0f, 1f) else 0f
            val value = normalizedAmp * animatedProgress.value
            
            val x = i * barWidth
            val y = centerY + (value * maxApparentHeight * 0.9f)
            
            if (i == amplitudes.lastIndex) {
                 path.lineTo(x, y)
            } else {
                val prevI = i + 1
                val prevX = prevI * barWidth
                val prevAmp = (if (maxAmp > 0) (amplitudes[prevI] / maxAmp).coerceIn(0f, 1f) else 0f) * animatedProgress.value
                val prevY = centerY + (prevAmp * maxApparentHeight * 0.9f)
                
                val midX = (prevX + x) / 2
                val midY = (prevY + y) / 2
                path.quadraticBezierTo(prevX, prevY, midX, midY)
            }
        }
        
        path.close()

        // Draw the single continuous line (outline/envelope)
        drawPath(
            path = path,
            color = Color(0xFF00D9FF), // Cyan Neon
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 1.5.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
        // Add a subtle glow
        drawPath(
            path = path,
            color = Color(0xFF00D9FF).copy(alpha = 0.4f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
    }
}

/**
 * Modelo de dados do Waveform
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
