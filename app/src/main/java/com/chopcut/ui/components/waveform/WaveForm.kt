package com.chopcut.ui.components.waveform

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
import androidx.compose.ui.graphics.drawscope.Fill
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
    style: WaveformStyle = WaveformStyle(),
    modifier: Modifier = Modifier
) {
    if (amplitudes.isEmpty()) return

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(amplitudes) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600)
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
    ) {
        if (amplitudes.isEmpty()) return@Canvas

        val path = androidx.compose.ui.graphics.Path()
        val barWidth = size.width / amplitudes.size.coerceAtLeast(1)
        
        val centerY = when (style.baseline) {
            WaveformStyle.Baseline.Top -> 0f
            WaveformStyle.Baseline.Center -> size.height / 2f
            WaveformStyle.Baseline.Bottom -> size.height
        }
        
        val maxAvailableHeight = when (style.baseline) {
            WaveformStyle.Baseline.Center -> size.height / 2f
            else -> size.height
        }

        path.moveTo(0f, centerY)

        amplitudes.forEachIndexed { index, amp ->
            val normalizedAmp = if (maxAmp > 0) (amp / maxAmp).coerceIn(0f, 1f) else 0f
            val value = normalizedAmp * animatedProgress.value * style.heightScale
            
            val x = index * barWidth
            
            // Y is offset from baseline based on baseline position
            // Center: Subtract value * height (goes up)
            // Bottom: Subtract value * height (goes up)
            // Top: Add value * height (goes down)
            
            val yOffset = value * maxAvailableHeight
            val y = if (style.baseline == WaveformStyle.Baseline.Top) {
                centerY + yOffset
            } else {
                centerY - yOffset
            }
            
            if (index == 0) {
                path.lineTo(x, y)
            } else {
                 if (style.isSmoothed) {
                    val prevX = (index - 1) * barWidth
                    val prevAmp = (if (maxAmp > 0) (amplitudes[index - 1] / maxAmp).coerceIn(0f, 1f) else 0f) * animatedProgress.value * style.heightScale
                    val prevYOffset = prevAmp * maxAvailableHeight
                    val prevY = if (style.baseline == WaveformStyle.Baseline.Top) {
                        centerY + prevYOffset
                    } else {
                        centerY - prevYOffset
                    }
                    
                    val midX = (prevX + x) / 2
                    val midY = (prevY + y) / 2
                    path.quadraticTo(prevX, prevY, midX, midY)
                 } else {
                     path.lineTo(x, y)
                 }
            }
        }
        
        // Final point
        val lastX = amplitudes.size * barWidth
        path.lineTo(lastX, centerY) // Return to baseline if loop? Or just lineTo last point?
        // To fill or close, we usually return to baseline.
        // For a line graph, we don't necessarily close. But path.close() closes to start.
        
        // If filled, close the path to baseline
        if (style.style == WaveformStyle.Style.Filled) {
            path.lineTo(lastX, centerY)
            path.lineTo(0f, centerY)
            path.close()
            
            drawPath(
                path = path,
                color = style.color.copy(alpha = 0.5f),
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
            // Also draw stroke on top?
            drawPath(
                path = path,
                color = style.color,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = style.strokeWidth.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        } else {
            // Line Style
            drawPath(
                path = path,
                color = style.color,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = style.strokeWidth.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        }

        // Add mirrored part if requested
        if (style.isMirrored) {
            val mirrorPath = androidx.compose.ui.graphics.Path()
            mirrorPath.moveTo(0f, centerY)
            
            amplitudes.forEachIndexed { index, amp ->
                val normalizedAmp = if (maxAmp > 0) (amp / maxAmp).coerceIn(0f, 1f) else 0f
                val value = normalizedAmp * animatedProgress.value * style.heightScale
                val x = index * barWidth
                val yOffset = value * maxAvailableHeight
                
                 val y = if (style.baseline == WaveformStyle.Baseline.Top) {
                    centerY - yOffset // Invert for mirror
                } else {
                    centerY + yOffset // Invert for mirror (down if baseline is center/bottom but center usually mirrors down)
                }

                if (index == 0) {
                    mirrorPath.lineTo(x, y)
                } else {
                    if (style.isSmoothed) {
                        val prevX = (index - 1) * barWidth
                        val prevAmp = (if (maxAmp > 0) (amplitudes[index - 1] / maxAmp).coerceIn(0f, 1f) else 0f) * animatedProgress.value * style.heightScale
                        val prevYOffset = prevAmp * maxAvailableHeight
                        val prevY = if (style.baseline == WaveformStyle.Baseline.Top) centerY - prevYOffset else centerY + prevYOffset
                        
                        val midX = (prevX + x) / 2
                        val midY = (prevY + y) / 2
                        mirrorPath.quadraticTo(prevX, prevY, midX, midY)
                    } else {
                        mirrorPath.lineTo(x, y)
                    }
                }
            }
            mirrorPath.lineTo(lastX, centerY)
            if (style.style == WaveformStyle.Style.Filled) {
                mirrorPath.lineTo(0f, centerY)
                mirrorPath.close()
                drawPath(
                    path = mirrorPath, 
                    color = style.color.copy(alpha = 0.3f), 
                    style = Fill
                )
            }
            drawPath(
                path = mirrorPath,
                color = style.color.copy(alpha = 0.6f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = style.strokeWidth.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        }
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
