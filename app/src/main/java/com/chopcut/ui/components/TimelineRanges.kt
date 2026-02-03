package com.chopcut.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Estados de drag para as alças
 */
private sealed class DragState {
    data object None : DragState()
    data class DraggingStart(val rangeId: String, val initialMs: Long) : DragState()
    data class DraggingEnd(val rangeId: String, val initialMs: Long) : DragState()
}

@Composable
fun TimelineRangesOverlay(
    ranges: List<TrimRangeData>,
    currentTimeMs: Long,
    videoDurationMs: Long,
    pxPerSecond: Float,
    scrollOffsetPx: Float,
    timelineWidth: Float,
    onRangeUpdate: (String, Long, Long) -> Unit,
    onRangeSelect: (String?) -> Unit,
    onRangeDelete: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val handleWidth = with(density) { 12.dp.toPx() }
    val handleHeight = with(density) { 24.dp.toPx() }
    val handleTouchArea = with(density) { 32.dp.toPx() }
    val deleteZoneHeight = with(density) { 60.dp.toPx() }
    
    var dragState by remember { mutableStateOf<DragState>(DragState.None) }
    var isDraggingHandle by remember { mutableStateOf(false) }
    var isDraggingForDelete by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    // Cor padrão para trim (vermelho escuro semi-transparente)
    val trimColor = Color(0xFFB71C1C)
    val trimFillColor = Color(0x4DB71C1C) // 30% opacidade
    val deleteZoneColor = Color(0xFFFF1744) // Vermelho brilhante para zona de delete

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(ranges, scrollOffsetPx, pxPerSecond, isDraggingHandle) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent().changes.firstOrNull()
                            if (down == null) continue
                            
                            val offset = down.position
                            val centerOffset = timelineWidth / 2f
                            var handleDetected: Pair<String, Boolean>? = null
                            
                            // Verifica se clicou em alguma alça (só se for draft e playhead dentro)
                            for (range in ranges) {
                                // Só permite arrastar alças se é draft E playhead está dentro
                                val isPlayheadInside = currentTimeMs in range.startMs..range.endMs
                                if (!range.isDraft || !isPlayheadInside) continue
                                
                                val startX = timeToX(range.startMs, pxPerSecond, scrollOffsetPx, centerOffset)
                                val endX = timeToX(range.endMs, pxPerSecond, scrollOffsetPx, centerOffset)
                                
                                // Alça start (retângulo vertical na esquerda)
                                val startHandleRect = Rect(
                                    left = startX - handleWidth / 2,
                                    top = size.height / 2 - handleHeight / 2,
                                    right = startX + handleWidth / 2,
                                    bottom = size.height / 2 + handleHeight / 2
                                )
                                
                                // Alça end (retângulo vertical na direita)
                                val endHandleRect = Rect(
                                    left = endX - handleWidth / 2,
                                    top = size.height / 2 - handleHeight / 2,
                                    right = endX + handleWidth / 2,
                                    bottom = size.height / 2 + handleHeight / 2
                                )
                                
                                when {
                                    startHandleRect.inflate(handleTouchArea).contains(offset) -> {
                                        handleDetected = range.id to true
                                        break
                                    }
                                    endHandleRect.inflate(handleTouchArea).contains(offset) -> {
                                        handleDetected = range.id to false
                                        break
                                    }
                                }
                            }
                            
                            // Se não clicou em alça, verifica se clicou dentro do range
                            if (handleDetected == null) {
                                var clickedInsideRange = false
                                for (range in ranges) {
                                    val startX = timeToX(range.startMs, pxPerSecond, scrollOffsetPx, centerOffset)
                                    val endX = timeToX(range.endMs, pxPerSecond, scrollOffsetPx, centerOffset)
                                    if (offset.x in startX..endX && offset.y >= 0 && offset.y <= size.height) {
                                        onRangeSelect(range.id)
                                        clickedInsideRange = true
                                        break
                                    }
                                }
                                if (!clickedInsideRange) {
                                    onRangeSelect(null)
                                }
                                continue
                            }
                            
                            // Clicou em uma alça - inicia o drag
                            val (rangeId, isStart) = handleDetected
                            val range = ranges.find { it.id == rangeId } ?: continue
                            onRangeSelect(rangeId)
                            isDraggingHandle = true
                            
                            dragState = if (isStart) {
                                DragState.DraggingStart(rangeId, range.startMs)
                            } else {
                                DragState.DraggingEnd(rangeId, range.endMs)
                            }
                            
                            down.consume()
                            
                            // Inicializa tracking para detectar drag para cima (delete)
                            var lastPosition = down.position
                            
                            // Aguarda movimentos
                            var drag = awaitPointerEvent().changes.firstOrNull()
                            var totalDragUp = 0f
                            val deleteThreshold = -50.dp.toPx() // Drag de 50dp para cima ativa delete
                            
                            while (drag != null && drag.pressed) {
                                val dragAmount = drag.positionChange()
                                val deltaMs = (dragAmount.x / pxPerSecond * 1000).toLong()
                                
                                // Detecta drag vertical para cima (delete)
                                totalDragUp += dragAmount.y
                                if (totalDragUp < deleteThreshold && !isDraggingForDelete) {
                                    isDraggingForDelete = true
                                }
                                
                                // Se está em modo delete, não atualiza o range
                                if (!isDraggingForDelete) {
                                    when (val state = dragState) {
                                        is DragState.DraggingStart -> {
                                            val r = ranges.find { it.id == state.rangeId }
                                            if (r != null) {
                                                val rawNewStart = (state.initialMs + deltaMs)
                                                val newStart = calculateValidStart(
                                                    rawNewStart, 
                                                    r.endMs, 
                                                    r.id,
                                                    ranges,
                                                    videoDurationMs
                                                )
                                                onRangeUpdate(r.id, newStart, r.endMs)
                                            }
                                        }
                                        is DragState.DraggingEnd -> {
                                            val r = ranges.find { it.id == state.rangeId }
                                            if (r != null) {
                                                val rawNewEnd = (state.initialMs + deltaMs)
                                                val newEnd = calculateValidEnd(
                                                    r.startMs,
                                                    rawNewEnd,
                                                    r.id,
                                                    ranges,
                                                    videoDurationMs
                                                )
                                                onRangeUpdate(r.id, r.startMs, newEnd)
                                            }
                                        }
                                        else -> {}
                                    }
                                } else {
                                    // Modo delete: atualiza offset visual
                                    dragOffset = Offset(0f, totalDragUp.coerceIn(-150f, 0f))
                                }
                                
                                lastPosition = drag.position
                                drag.consume()
                                drag = awaitPointerEvent().changes.firstOrNull()
                            }
                            
                            // Fim do drag: verifica se deve deletar
                            if (isDraggingForDelete && totalDragUp < deleteThreshold) {
                                val rangeToDelete = when (val state = dragState) {
                                    is DragState.DraggingStart -> state.rangeId
                                    is DragState.DraggingEnd -> state.rangeId
                                    else -> null
                                }
                                rangeToDelete?.let { onRangeDelete(it) }
                            }
                            
                            dragState = DragState.None
                            isDraggingHandle = false
                            isDraggingForDelete = false
                            dragOffset = Offset.Zero
                        }
                    }
                }
        ) {
            val centerOffset = timelineWidth / 2f
            
            // Desenha zona de delete no topo se estiver em modo delete
            if (isDraggingForDelete) {
                drawRect(
                    color = deleteZoneColor.copy(alpha = 0.3f),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, deleteZoneHeight)
                )
                // Ícone/texto indicando delete
                drawLine(
                    color = deleteZoneColor,
                    start = Offset(size.width / 2 - 10.dp.toPx(), deleteZoneHeight / 2),
                    end = Offset(size.width / 2 + 10.dp.toPx(), deleteZoneHeight / 2),
                    strokeWidth = 3.dp.toPx()
                )
            }
            
            ranges.forEach { range ->
                val startX = timeToX(range.startMs, pxPerSecond, scrollOffsetPx, centerOffset)
                val endX = timeToX(range.endMs, pxPerSecond, scrollOffsetPx, centerOffset)
                
                if (endX >= 0 && startX <= size.width) {
                    // Alças só aparecem em ranges draft quando o playhead está dentro
                    val isPlayheadInside = currentTimeMs in range.startMs..range.endMs
                    val showHandles = range.isDraft && isPlayheadInside
                    
                    // Verifica se este range está sendo arrastado para delete
                    val isThisRangeDragging = isDraggingForDelete && 
                        range.isSelected && 
                        when (dragState) {
                            is DragState.DraggingStart, is DragState.DraggingEnd -> true
                            else -> false
                        }
                    
                    drawTrimRange(
                        range = range,
                        startX = startX,
                        endX = endX,
                        timelineHeight = size.height,
                        handleWidth = handleWidth,
                        handleHeight = handleHeight,
                        trimColor = if (isThisRangeDragging) deleteZoneColor else trimColor,
                        trimFillColor = if (isThisRangeDragging) 
                            deleteZoneColor.copy(alpha = 0.5f) else trimFillColor,
                        isSelected = range.isSelected,
                        showHandles = showHandles,
                        verticalOffset = if (isThisRangeDragging) dragOffset.y else 0f
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawTrimRange(
    range: TrimRangeData,
    startX: Float,
    endX: Float,
    timelineHeight: Float,
    handleWidth: Float,
    handleHeight: Float,
    trimColor: Color,
    trimFillColor: Color,
    isSelected: Boolean,
    showHandles: Boolean,
    verticalOffset: Float = 0f
) {
    val clampedStart = startX.coerceIn(-100f, size.width + 100f)
    val clampedEnd = endX.coerceIn(-100f, size.width + 100f)
    val width = clampedEnd - clampedStart
    
    if (width <= 0) return
    
    val strokeColor = if (isSelected) trimColor else trimColor.copy(alpha = 0.7f)
    val strokeWidth = if (isSelected) 3f else 2f
    
    // Aplica offset vertical para efeito de "levitação" durante delete
    val topY = verticalOffset.coerceIn(-150f, 0f)
    
    // Preenchimento vermelho semi-transparente (área removida)
    drawRect(
        color = trimFillColor,
        topLeft = Offset(clampedStart, topY.coerceAtLeast(0f)),
        size = Size(width, timelineHeight)
    )
    
    // Sombra/suporte visual durante levitação
    if (verticalOffset < -10f) {
        drawRect(
            color = Color.Black.copy(alpha = 0.2f),
            topLeft = Offset(clampedStart + 4f, topY.coerceAtLeast(0f) + 4f),
            size = Size(width, timelineHeight)
        )
    }
    
    // Bordas verticais
    drawLine(
        color = strokeColor,
        start = Offset(clampedStart, topY.coerceAtLeast(0f)),
        end = Offset(clampedStart, timelineHeight + topY.coerceAtLeast(0f)),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = strokeColor,
        start = Offset(clampedEnd, topY.coerceAtLeast(0f)),
        end = Offset(clampedEnd, timelineHeight + topY.coerceAtLeast(0f)),
        strokeWidth = strokeWidth
    )
    
    // Alças só aparecem quando o playhead está dentro do range
    if (showHandles) {
        val handleTop = timelineHeight / 2 - handleHeight / 2
        
        // Alça START (retângulo vertical)
        drawRect(
            color = strokeColor,
            topLeft = Offset(clampedStart - handleWidth / 2, handleTop),
            size = Size(handleWidth, handleHeight)
        )
        // Linha branca no meio da alça (indicador visual)
        drawLine(
            color = Color.White,
            start = Offset(clampedStart, handleTop + 4.dp.toPx()),
            end = Offset(clampedStart, handleTop + handleHeight - 4.dp.toPx()),
            strokeWidth = 2f
        )
        
        // Alça END (retângulo vertical)
        drawRect(
            color = strokeColor,
            topLeft = Offset(clampedEnd - handleWidth / 2, handleTop),
            size = Size(handleWidth, handleHeight)
        )
        // Linha branca no meio da alça
        drawLine(
            color = Color.White,
            start = Offset(clampedEnd, handleTop + 4.dp.toPx()),
            end = Offset(clampedEnd, handleTop + handleHeight - 4.dp.toPx()),
            strokeWidth = 2f
        )
    }
    
    // Label com duração (círculo indicador no centro)
    if (width > 40.dp.toPx()) {
        drawCircle(
            color = if (showHandles) trimColor.copy(alpha = 0.8f) else trimColor.copy(alpha = 0.4f),
            radius = if (showHandles) 8.dp.toPx() else 5.dp.toPx(),
            center = Offset(clampedStart + width / 2, timelineHeight / 2)
        )
    }
}

/**
 * Calcula o valor válido de start respeitando outros ranges
 */
private fun calculateValidStart(
    rawStart: Long,
    endMs: Long,
    currentId: String,
    allRanges: List<TrimRangeData>,
    videoDurationMs: Long
): Long {
    // Mínimo 100ms de duração
    val minStart = 0L
    val maxStart = endMs - 100
    
    // Encontra o range anterior (mais próximo à esquerda)
    val otherRanges = allRanges.filter { it.id != currentId }
    val nearestEndBefore = otherRanges
        .filter { it.endMs <= rawStart }
        .maxByOrNull { it.endMs }
        ?.endMs ?: minStart
    
    return rawStart.coerceIn(nearestEndBefore, maxStart)
}

/**
 * Calcula o valor válido de end respeitando outros ranges
 */
private fun calculateValidEnd(
    startMs: Long,
    rawEnd: Long,
    currentId: String,
    allRanges: List<TrimRangeData>,
    videoDurationMs: Long
): Long {
    // Mínimo 100ms de duração
    val minEnd = startMs + 100
    val maxEnd = videoDurationMs
    
    // Encontra o range seguinte (mais próximo à direita)
    val otherRanges = allRanges.filter { it.id != currentId }
    val nearestStartAfter = otherRanges
        .filter { it.startMs >= rawEnd }
        .minByOrNull { it.startMs }
        ?.startMs ?: maxEnd
    
    return rawEnd.coerceIn(minEnd, nearestStartAfter)
}

private fun timeToX(timeMs: Long, pxPerSecond: Float, scrollOffsetPx: Float, centerOffset: Float): Float {
    return (timeMs / 1000f) * pxPerSecond - scrollOffsetPx + centerOffset
}

private fun Rect.inflate(amount: Float): Rect {
    return Rect(
        left - amount,
        top - amount,
        right + amount,
        bottom + amount
    )
}

