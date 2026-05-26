package com.chopcut.ui.components.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.chopcut.util.TimeUtils

/**
 * Régua de tempo isolada para exibição de ticks de segundos na timeline.
 *
 * É renderizada de forma 100% independente do leitor de vídeo ou miniaturas,
 * garantindo excelente performance por não alocar objetos em tempo de execução.
 *
 * @param smoothPositionMs Posição de rolagem contínua atual em milissegundos.
 * @param durationMs Duração total do vídeo em milissegundos.
 * @param pixelPerSecond Quantidade de pixels horizontais por segundo (escala de zoom).
 * @param modifier Modificador Compose.
 */
@Composable
fun TimelineRuler(
    smoothPositionMs: Float,
    durationMs: Long,
    pixelPerSecond: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    val rulerTickColor = remember { Color.White.copy(alpha = 0.4f) }
    val tickEndY = remember(density) { with(density) { 8.dp.toPx() } }
    val rulerTextY = remember(density) { with(density) { 20.dp.toPx() } }
    
    val rulePaint = remember(density) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            alpha = 100
            textSize = with(density) { 10.dp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    val totalSeconds = remember(durationMs) { (durationMs / 1000).toInt() }
    val showAllLabels = totalSeconds < 30
    
    val timeLabels = remember(durationMs) {
        (0..totalSeconds).associateWith { sec ->
            TimeUtils.formatDuration(sec * 1000L)
        }
    }

    Canvas(modifier = modifier.fillMaxWidth()) {
        val centerOffset = size.width / 2f
        val currentScrollPx = (smoothPositionMs / 1000f) * pixelPerSecond
        val startX = centerOffset - currentScrollPx
        val canvasWidth = size.width

        for (sec in 0..totalSeconds) {
            val tickX = startX + (sec * pixelPerSecond)
            if (tickX < -50 || tickX > canvasWidth + 50) continue

            drawLine(
                color = rulerTickColor,
                start = Offset(tickX, 0f),
                end = Offset(tickX, tickEndY),
                strokeWidth = 1.dp.toPx()
            )

            if (showAllLabels || sec % 5 == 0) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        timeLabels[sec] ?: "",
                        tickX,
                        rulerTextY,
                        rulePaint
                    )
                }
            }
        }
    }
}
