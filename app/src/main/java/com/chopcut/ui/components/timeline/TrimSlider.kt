package com.chopcut.ui.components.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.RectangleShape
import com.chopcut.ui.theme.ChopCutSpacing
import com.chopcut.ui.theme.OnSurface
import com.chopcut.ui.theme.Playhead
import com.chopcut.ui.theme.Surface
import com.chopcut.ui.theme.TimelineTrack

/**
 * Slider de trim para seleção de range de vídeo
 *
 * Componente simplificado para seleção de início e fim
 *
 * @param startPosition Posição inicial (0.0 a 1.0)
 * @param endPosition Posição final (0.0 a 1.0)
 * @param onPositionChange Callback quando as posições mudam (start, end)
 * @param modifier Modificador
 * @param enabled Se o slider está habilitado
 */
@Composable
fun TrimSlider(
    startPosition: Float,
    endPosition: Float,
    onPositionChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var isDraggingStart by remember { mutableStateOf(false) }
    var isDraggingEnd by remember { mutableStateOf(false) }
    var sliderWidth by remember { mutableFloatStateOf(0f) }

    // Garantir ordem correta
    val actualStart = startPosition.coerceAtMost(endPosition)
    val actualEnd = endPosition.coerceAtLeast(startPosition)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = ChopCutSpacing.md)
    ) {
        // Slider visual
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .align(Alignment.Center)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = ChopCutSpacing.trimHandleSize / 2)
            ) {
                val width = size.width
                val height = size.height
                val centerY = height / 2
                sliderWidth = width

                // Linha de fundo (track)
                drawLine(
                    color = TimelineTrack,
                    start = Offset(0f, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = 4.dp.toPx()
                )

                // Linha de seleção (entre os handles)
                drawLine(
                    color = Playhead.copy(alpha = 0.5f),
                    start = Offset(width * actualStart, centerY),
                    end = Offset(width * actualEnd, centerY),
                    strokeWidth = 4.dp.toPx()
                )
            }

            // Handle inicial
            TrimHandle(
                position = actualStart,
                isDragging = isDraggingStart,
                onDragStart = { isDraggingStart = true },
                onDrag = { delta ->
                    val newPosition = actualStart + delta / sliderWidth
                    val clamped = newPosition.coerceIn(0f, actualEnd - 0.05f)
                    onPositionChange(clamped, actualEnd)
                },
                onDragEnd = { isDraggingStart = false },
                enabled = enabled
            )

            // Handle final
            TrimHandle(
                position = actualEnd,
                isDragging = isDraggingEnd,
                onDragStart = { isDraggingEnd = true },
                onDrag = { delta ->
                    val newPosition = actualEnd + delta / sliderWidth
                    val clamped = newPosition.coerceIn(actualStart + 0.05f, 1f)
                    onPositionChange(actualStart, clamped)
                },
                onDragEnd = { isDraggingEnd = false },
                enabled = enabled
            )
        }
    }
}

/**
 * Handle do slider
 */
@Composable
private fun TrimHandle(
    position: Float,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    enabled: Boolean
) {
    var offsetX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(if (enabled) {
                Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            onDragStart()
                            offsetX = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            onDrag(offsetX)
                        },
                        onDragEnd = {
                            onDragEnd()
                            offsetX = 0f
                        }
                    )
                }
            } else Modifier),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = (position * 1000000).dp) // Hack: será ajustado no layout real
                .size(ChopCutSpacing.trimHandleSize)
                .background(
                    if (isDragging) Playhead else Surface,
                    RectangleShape
                )
                .then(
                    if (isDragging) {
                        Modifier.border(
                            2.dp,
                            OnSurface,
                            RectangleShape
                        )
                    } else {
                        Modifier.border(
                            2.dp,
                            Playhead.copy(alpha = 0.5f),
                            RectangleShape
                        )
                    }
                )
        )
    }
}

/**
 * Slider de posição simples (playhead)
 *
 * @param position Posição atual (0.0 a 1.0)
 * @param onPositionChange Callback quando posição muda
 * @param modifier Modificador
 */
@Composable
fun PositionSlider(
    position: Float,
    onPositionChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var sliderWidth by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = ChopCutSpacing.md)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .align(Alignment.Center)
        ) {
            val width = size.width
            val height = size.height
            val centerY = height / 2
            sliderWidth = width

            // Track
            drawLine(
                color = TimelineTrack,
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 4.dp.toPx()
            )
        }

        // Thumb
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .align(Alignment.Center)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // TODO(human): Implementar lógica de drag
                            // Calcular nova posição baseada no dragAmount.x
                        },
                        onDragEnd = { isDragging = false }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .padding(start = (position * 1000000).dp) // Hack temporário
                    .size(20.dp)
                    .background(
                        if (isDragging) Playhead else Surface,
                        RectangleShape
                    )
                    .border(
                        2.dp,
                        Playhead,
                        RectangleShape
                    )
            )
        }
    }
}
