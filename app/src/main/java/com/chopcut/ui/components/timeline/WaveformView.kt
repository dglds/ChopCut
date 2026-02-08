package com.chopcut.ui.components.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.chopcut.ui.theme.ChopCutSpacing
import com.chopcut.ui.theme.Playhead
import com.chopcut.ui.theme.TimelineBackground
import com.chopcut.ui.theme.Waveform

/**
 * Visualizador de waveform (forma de onda do áudio)
 *
 * Exibe a representação visual do áudio do vídeo
 *
 * @param samples Amostras do áudio (valores de 0 a 1, normalizados)
 * @param highlightedRange Range destacado (startRatio a endRatio, 0-1)
 * @param modifier Modificador
 */
@Composable
fun WaveformView(
    samples: List<Float>,
    highlightedRange: ClosedRange<Float>? = null, // 0.0 a 1.0
    modifier: Modifier = Modifier
) {
    val backgroundColor = TimelineBackground
    val waveformColor = Waveform
    val highlightColor = Playhead

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(ChopCutSpacing.waveformHeight)
            .background(backgroundColor, RoundedCornerShape(4.dp))
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        if (samples.isEmpty()) {
            // Desenhar linha central se não há amostras
            drawLine(
                color = waveformColor.copy(alpha = 0.3f),
                start = androidx.compose.ui.geometry.Offset(0f, centerY),
                end = androidx.compose.ui.geometry.Offset(width, centerY),
                strokeWidth = 1.dp.toPx()
            )
            return@Canvas
        }

        // Número de barras baseado na largura
        val barCount = samples.size.coerceAtMost(width.toInt() / 4)
        val barWidth = width / barCount
        val step = samples.size.toFloat() / barCount.toFloat()

        // Desenhar waveform
        for (i in 0 until barCount) {
            val startIndex = (i * step).toInt().coerceAtMost(samples.size - 1)
            val endIndex = ((i + 1) * step).toInt().coerceAtMost(samples.size)
            val sample = samples.subList(startIndex, endIndex).maxOrNull() ?: 0f

            val x = i * barWidth
            val barHeight = (sample * height * 0.8f).coerceAtLeast(2.dp.toPx())

            // Verificar se está na área destacada
            val ratio = i.toFloat() / barCount.toFloat()
            val isHighlighted = highlightedRange?.contains(ratio) == true

            val color = if (isHighlighted) highlightColor else waveformColor

            // Desenhar barra (centrada verticalmente)
            drawRoundRect(
                color = color.copy(alpha = if (isHighlighted) 1f else 0.6f),
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = x + 1.dp.toPx(),
                    y = centerY - barHeight / 2
                ),
                size = androidx.compose.ui.geometry.Size(
                    width = (barWidth - 2.dp.toPx()).coerceAtLeast(1.dp.toPx()),
                    height = barHeight
                ),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }

        // Desenhar linha central
        drawLine(
            color = waveformColor.copy(alpha = 0.2f),
            start = androidx.compose.ui.geometry.Offset(0f, centerY),
            end = androidx.compose.ui.geometry.Offset(width, centerY),
            strokeWidth = 1.dp.toPx()
        )
    }
}

/**
 * Visualizador de waveform simples (linha contínua)
 *
 * Alternativa ao estilo de barras, mais leve para renderizar
 *
 * @param samples Amostras do áudio (valores de 0 a 1, normalizados)
 * @param modifier Modificador
 */
@Composable
fun SimpleWaveformView(
    samples: List<Float>,
    modifier: Modifier = Modifier
) {
    val waveformColor = Waveform
    val backgroundColor = TimelineBackground

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(ChopCutSpacing.waveformHeight)
            .background(backgroundColor, RoundedCornerShape(4.dp))
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        if (samples.isEmpty()) {
            drawLine(
                color = waveformColor.copy(alpha = 0.3f),
                start = androidx.compose.ui.geometry.Offset(0f, centerY),
                end = androidx.compose.ui.geometry.Offset(width, centerY),
                strokeWidth = 1.dp.toPx()
            )
            return@Canvas
        }

        val path = Path()
        val step = width / samples.size.toFloat()

        path.moveTo(0f, centerY)

        for ((i, sample) in samples.withIndex()) {
            val x = i * step
            val y = centerY - (sample * height * 0.4f)
            path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = waveformColor,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
