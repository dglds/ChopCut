package com.chopcut.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.chopcut.ui.components.TrimRangeData
import kotlin.math.roundToInt

/**
 * Componente visual da fita de tempo (Timeline Strip).
 * Responsável por renderizar os frames, a régua de tempo e os blocos de edição (Ranges).
 */
@Composable
fun TimelineStrip(
    durationMs: Long,
    playheadPositionMs: Long,
    ranges: List<TrimRangeData>,
    listState: LazyListState,
    onSeek: (Long) -> Unit,
    onRangeSelect: (String) -> Unit,
    onRangeUpdate: (String, Long, Long) -> Unit,
    onScrubStart: () -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .height(80.dp)
            .background(Color.Black.copy(alpha = 0.1f))
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val centerX = containerWidth / 2f
        val handleWidthPx = with(density) { 16.dp.toPx() }

        // Calcular frames baseado na duração (1 frame por segundo)
        val frameWidth = with(density) { 80.dp.toPx() }
        val frameCount by remember(durationMs) {
            derivedStateOf {
                if (durationMs <= 0) 0
                else {
                    val framesPerSecond = durationMs / 1000
                    framesPerSecond.coerceAtLeast(10).coerceAtMost(60)
                }
            }
        }

        // Estado para drag de handles (Local ao componente visual)
        var handleDragType by remember { mutableStateOf(HandleDragType.NONE) }
        var dragStartX by remember { mutableFloatStateOf(0f) }
        var dragStartValue by remember { mutableLongStateOf(0L) }

        // Fita de Frames (LazyRow)
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = maxWidth / 2)
        ) {
            items(frameCount.toInt()) { index ->
                val frameStartMs = (index * (durationMs / frameCount.toFloat())).toLong()
                val frameEndMs = ((index + 1) * (durationMs / frameCount.toFloat())).toLong()

                // Verificar se algum range cobre este frame
                val affectedRanges = ranges.filter { range ->
                    frameStartMs < range.endMs && frameEndMs > range.startMs
                }

                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .fillMaxHeight()
                        .background(
                            if (index % 2 == 0)
                                Color.White
                            else
                                Color.LightGray.copy(alpha = 0.6f)
                        )
                        .border(
                            width = 0.5.dp,
                            color = Color.Gray.copy(alpha = 0.3f)
                        )
                        .clickable {
                            onScrubStart()
                            onSeek(frameStartMs)
                            // Nota: ScrubEnd deve ser chamado pelo pai quando o scroll parar ou delay
                        }
                ) {
                    // Desenhar ranges sobre este frame (Visualização estática)
                    affectedRanges.forEach { range ->
                        val rangeStartProgress = range.startMs.toFloat() / durationMs
                        val rangeEndProgress = range.endMs.toFloat() / durationMs
                        val frameStartProgress = frameStartMs.toFloat() / durationMs
                        val frameEndProgress = frameEndMs.toFloat() / durationMs

                        val overlapStart = maxOf(rangeStartProgress, frameStartProgress)
                        val overlapEnd = minOf(rangeEndProgress, frameEndProgress)
                        val overlapWidth = (overlapEnd - overlapStart) * frameWidth

                        val isPlayheadInsideRange = playheadPositionMs in range.startMs..range.endMs

                        Box(
                            modifier = Modifier
                                .width(with(density) { overlapWidth.toDp() })
                                .fillMaxHeight()
                                .background(
                                    when {
                                        range.isSelected -> Color(0xFF2196F3).copy(alpha = 0.3f)
                                        isPlayheadInsideRange -> Color(0xFFF44336).copy(alpha = 0.3f)
                                        else -> Color(0xFF4CAF50).copy(alpha = 0.3f)
                                    }
                                )
                                .border(
                                    width = if (range.isSelected) 2.dp else 1.dp,
                                    color = when {
                                        range.isSelected -> Color(0xFF2196F3)
                                        isPlayheadInsideRange -> Color(0xFFF44336)
                                        else -> Color(0xFF4CAF50)
                                    }
                                )
                                .clickable {
                                    onRangeSelect(range.id)
                                    onScrubStart()
                                    onSeek(range.startMs)
                                }
                        )
                    }

                    // Número do frame (debug/referência)
                    Text(
                        text = "${(frameStartMs / 1000).toInt()}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                    )
                }
            }
        }

        // Playhead fixo no centro
        Playhead(
            positionPx = centerX,
            containerWidth = containerWidth,
            durationMs = durationMs,
            onPositionChanged = { newPx ->
                val clickProgress = (newPx / containerWidth).coerceIn(0f, 1f)
                val newTimeMs = (clickProgress * durationMs).toLong()
                onSeek(newTimeMs)
            },
            onDragStart = { onScrubStart() },
            onDragEnd = { onScrubEnd() }
        )

        // Overlay de Ranges Interativos (Handles)
        ranges.forEach { range ->
            val startProgress = range.startMs.toFloat() / durationMs
            val endProgress = range.endMs.toFloat() / durationMs
            val rangeWidth = ((endProgress - startProgress) * containerWidth).coerceAtLeast(handleWidthPx)

            val playheadProgress = playheadPositionMs.toFloat() / durationMs
            val rangeCenterProgress = (startProgress + endProgress) / 2
            val offsetFromCenter = (rangeCenterProgress - playheadProgress) * containerWidth

            // Só desenha se estiver visível na viewport (otimização simples)
            if (offsetFromCenter > -containerWidth && offsetFromCenter < containerWidth) {
                // Caixa principal do Range
                Box(
                    modifier = Modifier
                        .height(80.dp)
                        .width(with(density) { rangeWidth.toDp() })
                        .align(Alignment.CenterStart)
                        .offset {
                            IntOffset(
                                (centerX + offsetFromCenter - rangeWidth / 2).roundToInt(),
                                0
                            )
                        }
                        .background(
                            if (range.isSelected)
                                Color(0xFF2196F3).copy(alpha = 0.3f)
                            else
                                Color(0xFF4CAF50).copy(alpha = 0.3f)
                        )
                        .border(
                            width = if (range.isSelected) 2.dp else 1.dp,
                            color = if (range.isSelected) Color(0xFF2196F3) else Color(0xFF4CAF50)
                        )
                        .clickable { onRangeSelect(range.id) }
                )

                // Handles do range selecionado
                if (range.isSelected) {
                    val startOffsetFromCenter = (startProgress - playheadProgress) * containerWidth
                    val endOffsetFromCenter = (endProgress - playheadProgress) * containerWidth

                    // Handle esquerdo
                    Box(
                        modifier = Modifier
                            .width(with(density) { handleWidthPx.toDp() })
                            .height(80.dp)
                            .align(Alignment.CenterStart)
                            .offset {
                                IntOffset(
                                    (centerX + startOffsetFromCenter).roundToInt(),
                                    0
                                )
                            }
                            .background(Color(0xFF2196F3))
                            .pointerInput(range.id) {
                                detectDragGestures(
                                    onDragStart = {
                                        onScrubStart()
                                        handleDragType = HandleDragType.LEFT
                                        dragStartX = it.x
                                        dragStartValue = range.startMs
                                    },
                                    onDragEnd = {
                                        onScrubEnd()
                                        handleDragType = HandleDragType.NONE
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    if (handleDragType == HandleDragType.LEFT) {
                                        val deltaX = dragAmount.x
                                        val deltaMs = ((deltaX / containerWidth) * durationMs).toLong()
                                        val newStart = (dragStartValue + deltaMs)
                                            .coerceIn(0, range.endMs - 100)
                                        onRangeUpdate(range.id, newStart, range.endMs)
                                    }
                                }
                            }
                    )

                    // Handle direito
                    Box(
                        modifier = Modifier
                            .width(with(density) { handleWidthPx.toDp() })
                            .height(80.dp)
                            .align(Alignment.CenterStart)
                            .offset {
                                IntOffset(
                                    (centerX + endOffsetFromCenter - handleWidthPx).roundToInt(),
                                    0
                                )
                            }
                            .background(Color(0xFF2196F3))
                            .pointerInput(range.id) {
                                detectDragGestures(
                                    onDragStart = {
                                        onScrubStart()
                                        handleDragType = HandleDragType.RIGHT
                                        dragStartX = it.x
                                        dragStartValue = range.endMs
                                    },
                                    onDragEnd = {
                                        onScrubEnd()
                                        handleDragType = HandleDragType.NONE
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    if (handleDragType == HandleDragType.RIGHT) {
                                        val deltaX = dragAmount.x
                                        val deltaMs = ((deltaX / containerWidth) * durationMs).toLong()
                                        val newEnd = (dragStartValue + deltaMs)
                                            .coerceIn(range.startMs + 100, durationMs)
                                        onRangeUpdate(range.id, range.startMs, newEnd)
                                    }
                                }
                            }
                    )
                }
            }
        }
    }
}

/**
 * Playhead fixo no centro com suporte a drag.
 */
@Composable
private fun Playhead(
    positionPx: Float,
    containerWidth: Float,
    durationMs: Long,
    onPositionChanged: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    color: Color = Color.Red,
    width: Dp = 2.dp
) {
    Canvas(
        modifier = Modifier
            .offset { IntOffset(positionPx.roundToInt(), 0) }
            .width(width)
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newPx = positionPx + dragAmount.x
                        onPositionChanged(newPx)
                    }
                )
            }
    ) {
        // Linha vertical
        drawLine(
            color = color,
            start = Offset(size.width / 2, 0f),
            end = Offset(size.width / 2, size.height),
            strokeWidth = width.toPx()
        )

        // Triângulo no topo
        val triangleWidth = 10.dp.toPx()
        val triangleHeight = 10.dp.toPx()
        val path = Path().apply {
            moveTo(size.width / 2 - triangleWidth / 2, 0f)
            lineTo(size.width / 2 + triangleWidth / 2, 0f)
            lineTo(size.width / 2, triangleHeight)
            close()
        }
        drawPath(path, color)
    }
}

/**
 * Tipo de drag em andamento no handle.
 */
private enum class HandleDragType { LEFT, RIGHT, NONE }
