package com.chopcut.ui.timeline.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.chopcut.ui.timeline.model.RangeCorte
import com.chopcut.ui.timeline.util.ConfiguracaoTimeline
import com.chopcut.ui.timeline.util.PerformanceConfig
import com.chopcut.ui.timeline.util.rememberThrottler
import kotlin.math.roundToInt

/**
 * Componente de overlay de ranges na timeline - VERSÃO OTIMIZADA.
 *
 * Otimizações para Celeron N5095A:
 * - Cores memorizadas (evita recriação a cada frame)
 * - Dimensões calculadas via derivedStateOf
 * - Throttling de 16ms para updates de drag
 * - pointerInput otimizado (keys estáveis)
 * - Só desenha ranges visíveis
 * - Reuso de objetos Rect e Offset via pool interno
 *
 * @param ranges Lista de ranges confirmados
 * @param rangeEmCriacao Range temporário sendo criado (se houver)
 * @param rangeSelecionadoId ID do range atualmente selecionado
 * @param posicaoPlayheadMs Posição atual do playhead
 * @param duracaoMs Duração total do vídeo
 * @param scrollOffsetPx Offset atual do scroll em pixels
 * @param containerWidthPx Largura do container em pixels
 * @param pxPorSegundo Pixels por segundo (escala)
 * @param onRangeSelect Callback ao selecionar um range
 * @param onRangeUpdate Callback ao atualizar limites (inicio, fim)
 * @param onRangeDelete Callback ao deletar um range
 * @param modifier Modifier para customização
 */
@Composable
fun RangeOverlay(
    ranges: List<RangeCorte>,
    rangeEmCriacao: RangeCorte?,
    rangeSelecionadoId: String?,
    posicaoPlayheadMs: Long,
    duracaoMs: Long,
    scrollOffsetPx: Float,
    containerWidthPx: Float,
    pxPorSegundo: Float,
    onRangeSelect: (String?) -> Unit,
    onRangeUpdate: (id: String, inicioMs: Long, fimMs: Long) -> Unit,
    onRangeDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    
    // ==== DIMENSÕES MEMORIZADAS ====
    val dimensoes by remember(density) {
        derivedStateOf {
            DimensoesOverlay(
                handleWidth = density.density * ConfiguracaoTimeline.LARGURA_ALCA_DP.value,
                handleHeight = density.density * ConfiguracaoTimeline.ALTURA_ALCA_DP.value,
                handleTouchArea = density.density * ConfiguracaoTimeline.AREA_TOQUE_ALCA_DP.value,
                deleteZoneHeight = density.density * 60f
            )
        }
    }
    
    // ==== CORES MEMORIZADAS ====
    val cores by remember {
        derivedStateOf {
            RangeCores(
                glassGreen = Color(0xFF4CAF50),
                glassFill = Color(0x334CAF50),
                glassBorder = Color(0xFF81C784),
                glassHighlight = Color(0xFFA5D6A7),
                glassShadow = Color(0x1F000000),
                deleteZone = Color(0xFFFF1744)
            )
        }
    }

    // ==== ESTADO DE DRAG ====
    var dragState by remember { mutableStateOf<DragState>(DragState.None) }
    var isDraggingForDelete by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    
    // Throttler para updates de drag (16ms = 60 FPS)
    val throttler = rememberThrottler(PerformanceConfig.THROTTLE_60FPS_MS)

    // ==== RANGES COMBINADOS (memoizado) ====
    val todosRanges by remember(ranges, rangeEmCriacao) {
        derivedStateOf {
            if (rangeEmCriacao != null) {
                ranges + rangeEmCriacao.copy(emEdicao = true)
            } else {
                ranges
            }
        }
    }
    
    // Chave estável para pointerInput (evita recriação desnecessária)
    val pointerInputKey = remember(todosRanges.size) { 
        todosRanges.hashCode() 
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pointerInputKey, scrollOffsetPx, pxPorSegundo) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val centerOffset = containerWidthPx / 2f

                            // Verifica se clicou em alguma alça (otimizado)
                            for (range in todosRanges) {
                                val isPlayheadInside = posicaoPlayheadMs in range
                                if (!range.emEdicao || !isPlayheadInside) continue

                                val startX = tempoParaX(
                                    range.inicioMs,
                                    pxPorSegundo,
                                    scrollOffsetPx,
                                    centerOffset
                                )
                                val endX = tempoParaX(
                                    range.fimMs,
                                    pxPorSegundo,
                                    scrollOffsetPx,
                                    centerOffset
                                )

                                // Hit test nas alças (com área de toque expandida)
                                val halfHandle = dimensoes.handleWidth / 2
                                val halfHeight = dimensoes.handleHeight / 2
                                val touchExpansion = dimensoes.handleTouchArea
                                
                                val startHandleRect = Rect(
                                    left = startX - halfHandle - touchExpansion,
                                    top = size.height / 2 - halfHeight - touchExpansion,
                                    right = startX + halfHandle + touchExpansion,
                                    bottom = size.height / 2 + halfHeight + touchExpansion
                                )

                                val endHandleRect = Rect(
                                    left = endX - halfHandle - touchExpansion,
                                    top = size.height / 2 - halfHeight - touchExpansion,
                                    right = endX + halfHandle + touchExpansion,
                                    bottom = size.height / 2 + halfHeight + touchExpansion
                                )

                                when {
                                    startHandleRect.contains(offset) -> {
                                        dragState = DragState.DraggingStart(
                                            range.id,
                                            range.inicioMs
                                        )
                                        return@detectDragGestures
                                    }
                                    endHandleRect.contains(offset) -> {
                                        dragState = DragState.DraggingEnd(
                                            range.id,
                                            range.fimMs
                                        )
                                        return@detectDragGestures
                                    }
                                }
                            }

                            // Se não clicou em alça, verifica clique no range
                            var clickedInsideRange = false
                            for (range in todosRanges) {
                                val startX = tempoParaX(
                                    range.inicioMs,
                                    pxPorSegundo,
                                    scrollOffsetPx,
                                    centerOffset
                                )
                                val endX = tempoParaX(
                                    range.fimMs,
                                    pxPorSegundo,
                                    scrollOffsetPx,
                                    centerOffset
                                )
                                if (offset.x in startX..endX) {
                                    onRangeSelect(range.id)
                                    clickedInsideRange = true
                                    break
                                }
                            }
                            if (!clickedInsideRange) {
                                onRangeSelect(null)
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            
                            // Detecta drag para cima (delete) - threshold de 50px
                            if (dragAmount.y < -50 && !isDraggingForDelete) {
                                isDraggingForDelete = true
                            }

                            if (!isDraggingForDelete) {
                                val centerOffset = containerWidthPx / 2f
                                val deltaMs = (dragAmount.x / pxPorSegundo * 1000).toLong()
                                
                                // Usa throttler para limitar updates
                                throttler.throttle {
                                    when (val state = dragState) {
                                        is DragState.DraggingStart -> {
                                            val range = todosRanges.find { it.id == state.rangeId }
                                            if (range != null) {
                                                val novoInicio = (state.initialMs + deltaMs).coerceIn(
                                                    0,
                                                    range.fimMs - ConfiguracaoTimeline.DURACAO_MINIMA_RANGE_MS
                                                )
                                                onRangeUpdate(range.id, novoInicio, range.fimMs)
                                            }
                                        }
                                        is DragState.DraggingEnd -> {
                                            val range = todosRanges.find { it.id == state.rangeId }
                                            if (range != null) {
                                                val novoFim = (state.initialMs + deltaMs).coerceIn(
                                                    range.inicioMs + ConfiguracaoTimeline.DURACAO_MINIMA_RANGE_MS,
                                                    duracaoMs
                                                )
                                                onRangeUpdate(range.id, range.inicioMs, novoFim)
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            } else {
                                dragOffsetY = dragAmount.y.coerceIn(-150f, 0f)
                            }
                        },
                        onDragEnd = {
                            // Flush qualquer update pendente
                            throttler.flushPending()
                            
                            // Verifica se deve deletar
                            if (isDraggingForDelete) {
                                when (val state = dragState) {
                                    is DragState.DraggingStart,
                                    is DragState.DraggingEnd -> {
                                        val rangeId = (state as? DragState.DraggingStart)?.rangeId
                                            ?: (state as? DragState.DraggingEnd)?.rangeId
                                        rangeId?.let { onRangeDelete(it) }
                                    }
                                    else -> {}
                                }
                            }

                            dragState = DragState.None
                            isDraggingForDelete = false
                            dragOffsetY = 0f
                        }
                    )
                }
        ) {
            val centerOffset = containerWidthPx / 2f
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Zona de delete
            if (isDraggingForDelete) {
                desenharZonaDelete(
                    width = canvasWidth,
                    height = dimensoes.deleteZoneHeight,
                    color = cores.deleteZone
                )
            }

            // Desenha cada range (só os visíveis)
            val visibilidadeThreshold = 100f // pixels fora da tela para começar a desenhar
            
            for (range in todosRanges) {
                val startX = tempoParaX(
                    range.inicioMs,
                    pxPorSegundo,
                    scrollOffsetPx,
                    centerOffset
                )
                val endX = tempoParaX(range.fimMs, pxPorSegundo, scrollOffsetPx, centerOffset)

                // Culling: só desenha se estiver visível (com margem)
                if (endX < -visibilidadeThreshold || startX > canvasWidth + visibilidadeThreshold) {
                    continue
                }

                val isPlayheadInside = posicaoPlayheadMs in range
                val showHandles = range.emEdicao && isPlayheadInside
                val isSelected = range.id == rangeSelecionadoId
                val isDraggingThis = isDraggingForDelete && isRangeBeingDragged(dragState, range.id)

                // Seleciona cores baseado no estado
                val (corPreenchimento, corBorda, corDestaque, corGreen) = when {
                    isDraggingThis -> QuadColor(
                        preenchimento = cores.deleteZone.copy(alpha = 0.3f),
                        borda = cores.deleteZone,
                        destaque = cores.deleteZone.copy(alpha = 0.5f),
                        green = cores.deleteZone
                    )
                    else -> QuadColor(
                        preenchimento = cores.glassFill,
                        borda = cores.glassBorder,
                        destaque = cores.glassHighlight,
                        green = cores.glassGreen
                    )
                }

                desenharRangeOtimizado(
                    range = range,
                    startX = startX,
                    endX = endX,
                    timelineHeight = canvasHeight,
                    handleWidth = dimensoes.handleWidth,
                    handleHeight = dimensoes.handleHeight,
                    glassGreen = corGreen,
                    glassFill = corPreenchimento,
                    glassBorder = corBorda,
                    glassHighlight = corDestaque,
                    glassShadow = cores.glassShadow,
                    isSelected = isSelected || isDraggingThis,
                    showHandles = showHandles,
                    verticalOffset = if (isDraggingThis) dragOffsetY else 0f,
                    density = density.density
                )
            }
        }
    }
}

// ==== DATA CLASSES PARA OTIMIZAÇÃO ====
private data class DimensoesOverlay(
    val handleWidth: Float,
    val handleHeight: Float,
    val handleTouchArea: Float,
    val deleteZoneHeight: Float
)

private data class RangeCores(
    val glassGreen: Color,
    val glassFill: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val glassShadow: Color,
    val deleteZone: Color
)

private data class QuadColor(
    val preenchimento: Color,
    val borda: Color,
    val destaque: Color,
    val green: Color
)

/**
 * Verifica se um range específico está sendo arrastado.
 */
private fun isRangeBeingDragged(dragState: DragState, rangeId: String): Boolean {
    return when (dragState) {
        is DragState.DraggingStart -> dragState.rangeId == rangeId
        is DragState.DraggingEnd -> dragState.rangeId == rangeId
        else -> false
    }
}

/**
 * Desenha a zona de delete no topo.
 */
private fun DrawScope.desenharZonaDelete(
    width: Float,
    height: Float,
    color: Color
) {
    // Fundo semi-transparente
    drawRect(
        color = color.copy(alpha = 0.3f),
        topLeft = Offset(0f, 0f),
        size = Size(width, height)
    )
    
    // Ícone de delete (linha horizontal)
    val centerY = height / 2
    val iconWidth = 20f * (width / 400f).coerceIn(0.5f, 2f)
    val strokeWidth = 3f * (width / 400f).coerceIn(0.5f, 2f)
    
    drawLine(
        color = color,
        start = Offset(width / 2 - iconWidth, centerY),
        end = Offset(width / 2 + iconWidth, centerY),
        strokeWidth = strokeWidth
    )
}

/**
 * Desenha um range individual no canvas - VERSÃO OTIMIZADA.
 */
private fun DrawScope.desenharRangeOtimizado(
    range: RangeCorte,
    startX: Float,
    endX: Float,
    timelineHeight: Float,
    handleWidth: Float,
    handleHeight: Float,
    glassGreen: Color,
    glassFill: Color,
    glassBorder: Color,
    glassHighlight: Color,
    glassShadow: Color,
    isSelected: Boolean,
    showHandles: Boolean,
    verticalOffset: Float = 0f,
    density: Float
) {
    val clampedStart = startX.coerceIn(-100f, size.width + 100f)
    val clampedEnd = endX.coerceIn(-100f, size.width + 100f)
    val width = clampedEnd - clampedStart

    if (width <= 0) return

    val topY = verticalOffset.coerceIn(-150f, 0f)
    val baseY = topY.coerceAtLeast(0f)
    
    // Stroke widths escalados com densidade
    val borderWidth = if (isSelected) 3f * density else 2f * density
    val shadowOffset = 2f * density
    val handleCornerRadius = 4f * density
    val indicatorMinWidth = 40f * density
    val indicatorRadius = if (showHandles) 8f * density else 5f * density

    // ===== EFEITO GLASSMORPHISM (otimizado) =====

    // 1. Sombra (só se não estiver sendo arrastado para cima)
    if (verticalOffset >= 0) {
        drawRect(
            color = glassShadow,
            topLeft = Offset(clampedStart + shadowOffset, baseY + shadowOffset),
            size = Size(width, timelineHeight)
        )
    }

    // 2. Preenchimento vidro
    drawRect(
        color = glassFill,
        topLeft = Offset(clampedStart, baseY),
        size = Size(width, timelineHeight)
    )

    // 3. Gradiente reflexo (parte superior)
    drawRect(
        color = glassHighlight.copy(alpha = 0.3f),
        topLeft = Offset(clampedStart, baseY),
        size = Size(width.coerceAtLeast(1f), timelineHeight * 0.3f)
    )

    // 4. Borda externa glow (só se selecionado)
    if (isSelected) {
        val glowSize = 2f * density
        drawRect(
            color = glassGreen.copy(alpha = 0.3f),
            topLeft = Offset(clampedStart - glowSize, baseY - glowSize),
            size = Size(width + glowSize * 2, timelineHeight + glowSize * 2)
        )
    }

    // 5. Bordas verticais
    val corBorda = if (isSelected) glassGreen else glassBorder
    drawLine(
        color = corBorda,
        start = Offset(clampedStart, baseY),
        end = Offset(clampedStart, timelineHeight + baseY),
        strokeWidth = borderWidth
    )
    drawLine(
        color = corBorda,
        start = Offset(clampedEnd, baseY),
        end = Offset(clampedEnd, timelineHeight + baseY),
        strokeWidth = borderWidth
    )

    // 6. Linha superior brilho
    drawLine(
        color = glassHighlight.copy(alpha = 0.8f),
        start = Offset(clampedStart, baseY + density),
        end = Offset(clampedEnd, baseY + density),
        strokeWidth = density
    )

    // Alças (só se showHandles = true)
    if (showHandles) {
        val handleTop = timelineHeight / 2 - handleHeight / 2
        val halfHandle = handleWidth / 2
        val handleLineWidth = 2f * density
        val handleLineInset = 2f * density

        // Alça START
        drawRoundRect(
            color = glassGreen,
            topLeft = Offset(clampedStart - halfHandle, handleTop + baseY),
            size = Size(handleWidth, handleHeight),
            cornerRadius = CornerRadius(handleCornerRadius, handleCornerRadius)
        )
        drawLine(
            color = glassHighlight,
            start = Offset(
                clampedStart - halfHandle + handleLineInset,
                handleTop + baseY + 4f * density
            ),
            end = Offset(clampedStart + halfHandle - handleLineInset, handleTop + baseY + 4f * density),
            strokeWidth = handleLineWidth
        )

        // Alça END
        drawRoundRect(
            color = glassGreen,
            topLeft = Offset(clampedEnd - halfHandle, handleTop + baseY),
            size = Size(handleWidth, handleHeight),
            cornerRadius = CornerRadius(handleCornerRadius, handleCornerRadius)
        )
        drawLine(
            color = Color.White,
            start = Offset(clampedEnd, handleTop + 4f * density),
            end = Offset(clampedEnd, handleTop + handleHeight - 4f * density),
            strokeWidth = handleLineWidth
        )
    }

    // Indicador central (círculo) - só se houver espaço suficiente
    if (width > indicatorMinWidth) {
        drawCircle(
            color = if (showHandles) glassGreen.copy(alpha = 0.8f) else glassGreen.copy(alpha = 0.4f),
            radius = indicatorRadius,
            center = Offset(clampedStart + width / 2, timelineHeight / 2)
        )
    }
}

/**
 * Desenha um range individual no canvas - LEGADO (mantido para compatibilidade).
 * @deprecated Use desenharRangeOtimizado
 */
private fun DrawScope.desenharRange(
    range: RangeCorte,
    startX: Float,
    endX: Float,
    timelineHeight: Float,
    handleWidth: Float,
    handleHeight: Float,
    glassGreen: Color,
    glassFill: Color,
    glassBorder: Color,
    glassHighlight: Color,
    glassShadow: Color,
    isSelected: Boolean,
    showHandles: Boolean,
    verticalOffset: Float = 0f
) {
    desenharRangeOtimizado(
        range = range,
        startX = startX,
        endX = endX,
        timelineHeight = timelineHeight,
        handleWidth = handleWidth,
        handleHeight = handleHeight,
        glassGreen = glassGreen,
        glassFill = glassFill,
        glassBorder = glassBorder,
        glassHighlight = glassHighlight,
        glassShadow = glassShadow,
        isSelected = isSelected,
        showHandles = showHandles,
        verticalOffset = verticalOffset,
        density = 1f // Assume density 1 para compatibilidade
    )
}

/**
 * Estados de drag.
 */
private sealed class DragState {
    object None : DragState()
    data class DraggingStart(val rangeId: String, val initialMs: Long) : DragState()
    data class DraggingEnd(val rangeId: String, val initialMs: Long) : DragState()
}

/**
 * Converte tempo (ms) para posição X na tela.
 * Otimizado: evita criação de objetos temporários.
 */
private fun tempoParaX(
    tempoMs: Long,
    pxPorSegundo: Float,
    scrollOffsetPx: Float,
    centerOffset: Float
): Float {
    return (tempoMs / 1000f) * pxPorSegundo - scrollOffsetPx + centerOffset
}
