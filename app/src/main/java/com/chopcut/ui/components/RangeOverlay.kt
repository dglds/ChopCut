package com.chopcut.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.chopcut.data.model.VideoRange
import kotlin.math.abs

/**
 * Overlay de ranges para edição de vídeo com duas camadas separadas:
 * 1. Controles de redimensionamento (superior)
 * 2. Retângulos dos ranges (inferior)
 *
 * @param ranges Lista de ranges a serem exibidos
 * @param scrollOffsetPx Offset atual do scroll em pixels
 * @param pxPerSecond Pixels por segundo para conversão de posição
 * @param timelineWidthPx Largura da timeline em pixels
 * @param videoDurationMs Duração total do vídeo em milissegundos
 * @param onRangeClick Callback quando um range é clicado
 * @param onRangeResizeStart Callback quando inicia o redimensionamento (rangeId, isStartHandle)
 * @param onRangeResize Callback durante o redimensionamento (rangeId, deltaPx)
 * @param onRangeResizeEnd Callback quando termina o redimensionamento
 * @param modifier Modificador aplicado ao componente
 */
@Composable
fun RangeOverlay(
    ranges: List<VideoRange>,
    scrollOffsetPx: Float,
    pxPerSecond: Float,
    timelineWidthPx: Float,
    videoDurationMs: Long,
    onRangeClick: (String) -> Unit,
    onRangeResizeStart: (String, Boolean) -> Unit,
    onRangeResize: (String, Float) -> Unit,
    onRangeResizeEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // CAMADA 1: Controles de Redimensionamento (Superior)
        ControlHandlesLayer(
            ranges = ranges,
            scrollOffsetPx = scrollOffsetPx,
            pxPerSecond = pxPerSecond,
            timelineWidthPx = timelineWidthPx,
            onResizeStart = onRangeResizeStart,
            onResize = onRangeResize,
            onResizeEnd = onRangeResizeEnd
        )

        // CAMADA 2: Retângulos dos Ranges (Inferior)
        RangesLayer(
            ranges = ranges,
            scrollOffsetPx = scrollOffsetPx,
            pxPerSecond = pxPerSecond,
            timelineWidthPx = timelineWidthPx,
            onRangeClick = onRangeClick
        )
    }
}

/**
 * Camada de controles de redimensionamento.
 * Renderiza triângulos de controle nas extremidades de cada range.
 *
 * Triângulos:
 * - ⛛ (esquerda): Handle de INÍCIO - move a posição inicial
 * - ▼ (direita): Handle de FIM - move a posição final
 */
@Composable
fun ControlHandlesLayer(
    ranges: List<VideoRange>,
    scrollOffsetPx: Float,
    pxPerSecond: Float,
    timelineWidthPx: Float,
    onResizeStart: (String, Boolean) -> Unit,
    onResize: (String, Float) -> Unit,
    onResizeEnd: () -> Unit
) {
    val density = LocalDensity.current
    val handleTouchRadius = with(density) { 24.dp.toPx() }

    var isDragging by remember { mutableStateOf(false) }
    var currentRangeId by remember { mutableStateOf<String?>(null) }
    var isStartHandle by remember { mutableStateOf(true) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        // Detectar toque no handle esquerdo ou direito
                        ranges.forEach { range ->
                            val startX = timeToPx(range.startMs, pxPerSecond, scrollOffsetPx)
                            val endX = timeToPx(range.endMs, pxPerSecond, scrollOffsetPx)

                            when {
                                abs(offset.x - startX) < handleTouchRadius -> {
                                    currentRangeId = range.id
                                    isStartHandle = true
                                    isDragging = true
                                    onResizeStart(range.id, true)
                                }
                                abs(offset.x - endX) < handleTouchRadius -> {
                                    currentRangeId = range.id
                                    isStartHandle = false
                                    isDragging = true
                                    onResizeStart(range.id, false)
                                }
                            }
                        }
                    },
                    onDrag = { change: PointerInputChange, dragAmount: Offset ->
                        if (isDragging && currentRangeId != null) {
                            change.consume()
                            val deltaPx = if (isStartHandle) dragAmount.x else dragAmount.x
                            onResize(currentRangeId!!, deltaPx)
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        currentRangeId = null
                        onResizeEnd()
                    },
                    onDragCancel = {
                        isDragging = false
                        currentRangeId = null
                        onResizeEnd()
                    }
                )
            }
    ) {
        val centerX = size.width / 2f

        ranges.forEach { range ->
            val startX = timeToPx(range.startMs, pxPerSecond, scrollOffsetPx) + centerX
            val endX = timeToPx(range.endMs, pxPerSecond, scrollOffsetPx) + centerX
            val centerY = size.height / 2f

            // Handle esquerdo (⛛) - move INÍCIO
            drawStartHandle(startX, centerY, range.isSelected)

            // Handle direito (▼) - move FIM
            drawEndHandle(endX, centerY, range.isSelected)
        }
    }
}

/**
 * Camada de retângulos dos ranges.
 * Renderiza os retângulos semi-transparentes representando os intervalos.
 */
@Composable
fun RangesLayer(
    ranges: List<VideoRange>,
    scrollOffsetPx: Float,
    pxPerSecond: Float,
    timelineWidthPx: Float,
    onRangeClick: (String) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        // Detectar clique no range para selecionar
                        val centerX = size.width / 2f
                        ranges.forEach { range ->
                            val startX = timeToPx(range.startMs, pxPerSecond, scrollOffsetPx) + centerX
                            val endX = timeToPx(range.endMs, pxPerSecond, scrollOffsetPx) + centerX

                            if (offset.x in startX..endX) {
                                onRangeClick(range.id)
                            }
                        }
                    }
                )
            }
    ) {
        val centerX = size.width / 2f

        ranges.forEach { range ->
            val startX = timeToPx(range.startMs, pxPerSecond, scrollOffsetPx) + centerX
            val endX = timeToPx(range.endMs, pxPerSecond, scrollOffsetPx) + centerX
            val width = endX - startX

            if (width > 0f) {
                // Retângulo semi-transparente
                drawRoundRect(
                    color = range.color.copy(alpha = 0.3f),
                    topLeft = Offset(startX, 0f),
                    size = androidx.compose.ui.geometry.Size(width, size.height),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )

                // Borda do range
                drawRoundRect(
                    color = if (range.isSelected) Color.Yellow else range.color,
                    style = Stroke(width = if (range.isSelected) 3.dp.toPx() else 2.dp.toPx()),
                    topLeft = Offset(startX, 0f),
                    size = androidx.compose.ui.geometry.Size(width, size.height),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )

                // Glow effect quando selecionado
                if (range.isSelected) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.2f),
                        style = Stroke(width = 6.dp.toPx()),
                        topLeft = Offset(startX, 0f),
                        size = androidx.compose.ui.geometry.Size(width, size.height),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }
            }
        }
    }
}

/**
 * Desenha o handle de INÍCIO (⛛).
 * Triângulo apontando para CIMA.
 */
private fun DrawScope.drawStartHandle(centerX: Float, centerY: Float, isSelected: Boolean) {
    val handleSize = 12.dp.toPx()
    val color = if (isSelected) Color.Yellow else Color.White

    // Glow effect (multi-camada)
    val glowLayers = listOf(
        Pair(8.dp.toPx(), 0.1f),
        Pair(6.dp.toPx(), 0.15f),
        Pair(4.dp.toPx(), 0.2f)
    )

    glowLayers.forEach { (glowSize, alpha) ->
        drawPath(
            path = Path().apply {
                moveTo(centerX, centerY - handleSize / 2)
                lineTo(centerX - handleSize / 2, centerY + handleSize / 2)
                lineTo(centerX + handleSize / 2, centerY + handleSize / 2)
                close()
            },
            color = color.copy(alpha = alpha),
            style = Stroke(width = glowSize)
        )
    }

    // Triângulo principal
    drawPath(
        path = Path().apply {
            moveTo(centerX, centerY - handleSize / 2)
            lineTo(centerX - handleSize / 2, centerY + handleSize / 2)
            lineTo(centerX + handleSize / 2, centerY + handleSize / 2)
            close()
        },
        color = color
    )
}

/**
 * Desenha o handle de FIM (▼).
 * Triângulo apontando para BAIXO.
 */
private fun DrawScope.drawEndHandle(centerX: Float, centerY: Float, isSelected: Boolean) {
    val handleSize = 12.dp.toPx()
    val color = if (isSelected) Color.Yellow else Color.White

    // Glow effect (multi-camada)
    val glowLayers = listOf(
        Pair(8.dp.toPx(), 0.1f),
        Pair(6.dp.toPx(), 0.15f),
        Pair(4.dp.toPx(), 0.2f)
    )

    glowLayers.forEach { (glowSize, alpha) ->
        drawPath(
            path = Path().apply {
                moveTo(centerX, centerY + handleSize / 2)
                lineTo(centerX - handleSize / 2, centerY - handleSize / 2)
                lineTo(centerX + handleSize / 2, centerY - handleSize / 2)
                close()
            },
            color = color.copy(alpha = alpha),
            style = Stroke(width = glowSize)
        )
    }

    // Triângulo principal
    drawPath(
        path = Path().apply {
            moveTo(centerX, centerY + handleSize / 2)
            lineTo(centerX - handleSize / 2, centerY - handleSize / 2)
            lineTo(centerX + handleSize / 2, centerY - handleSize / 2)
            close()
        },
        color = color
    )
}

/**
 * Converte tempo em milissegundos para posição em pixels.
 */
fun timeToPx(timeMs: Long, pxPerSecond: Float, scrollOffset: Float): Float {
    val pxPerMs = pxPerSecond / 1000f
    return (timeMs * pxPerMs) - scrollOffset
}

/**
 * Converte posição em pixels para tempo em milissegundos.
 */
fun pxToTime(px: Float, pxPerSecond: Float, scrollOffset: Float): Long {
    val pxPerMs = pxPerSecond / 1000f
    return ((px + scrollOffset) / pxPerMs).toLong()
}
