package com.chopcut.ui.timeline.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import kotlin.math.roundToInt

/**
 * Componente de overlay de ranges na timeline.
 *
 * Responsabilidades:
 * - Desenhar ranges (áreas de corte) sobre a timeline
 * - Suportar range em criação (visualização dinâmica)
 * - Permitir drag de alças para ajustar início/fim
 * - Detectar clique para seleção
 * - Suportar gesture de delete (arrastar para cima)
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
    val handleWidth = with(density) { ConfiguracaoTimeline.LARGURA_ALCA_DP.toPx() }
    val handleHeight = with(density) { ConfiguracaoTimeline.ALTURA_ALCA_DP.toPx() }
    val handleTouchArea = with(density) { ConfiguracaoTimeline.AREA_TOQUE_ALCA_DP.toPx() }
    val deleteZoneHeight = with(density) { 60.dp.toPx() }

    // Estado de drag
    var dragState by remember { mutableStateOf<DragState>(DragState.None) }
    var isDraggingForDelete by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    // Cores glassmorphism verde
    val glassGreen = Color(0xFF4CAF50)
    val glassFill = Color(0x334CAF50)
    val glassBorder = Color(0xFF81C784)
    val glassHighlight = Color(0xFFA5D6A7)
    val glassShadow = Color(0x1F000000)
    val deleteZoneColor = Color(0xFFFF1744)

    // Todos os ranges para renderizar (confirmados + em criação)
    val todosRanges = remember(ranges, rangeEmCriacao) {
        if (rangeEmCriacao != null) {
            ranges + rangeEmCriacao.copy(emEdicao = true)
        } else ranges
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(todosRanges, scrollOffsetPx, pxPorSegundo) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val centerOffset = containerWidthPx / 2f

                            // Verifica se clicou em alguma alça
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

                                // Hit test nas alças
                                val startHandleRect = Rect(
                                    left = startX - handleWidth / 2,
                                    top = size.height / 2 - handleHeight / 2,
                                    right = startX + handleWidth / 2,
                                    bottom = size.height / 2 + handleHeight / 2
                                )

                                val endHandleRect = Rect(
                                    left = endX - handleWidth / 2,
                                    top = size.height / 2 - handleHeight / 2,
                                    right = endX + handleWidth / 2,
                                    bottom = size.height / 2 + handleHeight / 2
                                )

                                when {
                                    startHandleRect.inflate(handleTouchArea).contains(offset) -> {
                                        dragState = DragState.DraggingStart(
                                            range.id,
                                            range.inicioMs
                                        )
                                        return@detectDragGestures
                                    }
                                    endHandleRect.inflate(handleTouchArea).contains(offset) -> {
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
                            val centerOffset = containerWidthPx / 2f
                            val deltaMs =
                                (dragAmount.x / pxPorSegundo * 1000).toLong()

                            // Detecta drag para cima (delete)
                            if (dragAmount.y < -50 && !isDraggingForDelete) {
                                isDraggingForDelete = true
                            }

                            if (!isDraggingForDelete) {
                                when (val state = dragState) {
                                    is DragState.DraggingStart -> {
                                        val range = todosRanges.find { it.id == state.rangeId }
                                        if (range != null) {
                                            val novoInicio =
                                                (state.initialMs + deltaMs).coerceIn(
                                                    0,
                                                    range.fimMs - ConfiguracaoTimeline.DURACAO_MINIMA_RANGE_MS
                                                )
                                            onRangeUpdate(range.id, novoInicio, range.fimMs)
                                        }
                                    }
                                    is DragState.DraggingEnd -> {
                                        val range = todosRanges.find { it.id == state.rangeId }
                                        if (range != null) {
                                            val novoFim =
                                                (state.initialMs + deltaMs).coerceIn(
                                                    range.inicioMs + ConfiguracaoTimeline.DURACAO_MINIMA_RANGE_MS,
                                                    duracaoMs
                                                )
                                            onRangeUpdate(range.id, range.inicioMs, novoFim)
                                        }
                                    }
                                    else -> {}
                                }
                            } else {
                                dragOffset = Offset(0f, dragAmount.y.coerceIn(-150f, 0f))
                            }
                        },
                        onDragEnd = {
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
                            dragOffset = Offset.Zero
                        }
                    )
                }
        ) {
            val centerOffset = containerWidthPx / 2f

            // Zona de delete
            if (isDraggingForDelete) {
                drawRect(
                    color = deleteZoneColor.copy(alpha = 0.3f),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, deleteZoneHeight)
                )
                // Ícone de delete
                drawLine(
                    color = deleteZoneColor,
                    start = Offset(size.width / 2 - 10.dp.toPx(), deleteZoneHeight / 2),
                    end = Offset(size.width / 2 + 10.dp.toPx(), deleteZoneHeight / 2),
                    strokeWidth = 3.dp.toPx()
                )
            }

            // Desenha cada range
            todosRanges.forEach { range ->
                val startX = tempoParaX(
                    range.inicioMs,
                    pxPorSegundo,
                    scrollOffsetPx,
                    centerOffset
                )
                val endX = tempoParaX(range.fimMs, pxPorSegundo, scrollOffsetPx, centerOffset)

                // Só desenha se estiver visível
                if (endX >= 0 && startX <= size.width) {
                    val isPlayheadInside = posicaoPlayheadMs in range
                    val showHandles = range.emEdicao && isPlayheadInside
                    val isSelected = range.id == rangeSelecionadoId
                    val isDraggingThis = isDraggingForDelete &&
                            when (dragState) {
                                is DragState.DraggingStart,
                                is DragState.DraggingEnd -> true
                                else -> false
                            } &&
                            ((dragState as? DragState.DraggingStart)?.rangeId == range.id ||
                                    (dragState as? DragState.DraggingEnd)?.rangeId == range.id)

                    // Cores
                    val useGreen = !isDraggingThis
                    val corPreenchimento = if (useGreen) glassFill else deleteZoneColor.copy(
                        alpha = 0.3f
                    )
                    val corBorda = if (useGreen) glassBorder else deleteZoneColor
                    val corDestaque = if (useGreen) glassHighlight else deleteZoneColor.copy(
                        alpha = 0.5f
                    )

                    desenharRange(
                        range = range,
                        startX = startX,
                        endX = endX,
                        timelineHeight = size.height,
                        handleWidth = handleWidth,
                        handleHeight = handleHeight,
                        glassGreen = if (useGreen) glassGreen else deleteZoneColor,
                        glassFill = corPreenchimento,
                        glassBorder = corBorda,
                        glassHighlight = corDestaque,
                        glassShadow = glassShadow,
                        isSelected = isSelected || isDraggingThis,
                        showHandles = showHandles,
                        verticalOffset = if (isDraggingThis) dragOffset.y else 0f
                    )
                }
            }
        }
    }
}

/**
 * Desenha um range individual no canvas.
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
    val clampedStart = startX.coerceIn(-100f, size.width + 100f)
    val clampedEnd = endX.coerceIn(-100f, size.width + 100f)
    val width = clampedEnd - clampedStart

    if (width <= 0) return

    val topY = verticalOffset.coerceIn(-150f, 0f)
    val baseY = topY.coerceAtLeast(0f)

    // ===== EFEITO GLASSMORPHISM =====

    // 1. Sombra
    drawRect(
        color = glassShadow,
        topLeft = Offset(clampedStart + 2f, baseY + 2f),
        size = Size(width, timelineHeight)
    )

    // 2. Preenchimento vidro
    drawRect(
        color = glassFill,
        topLeft = Offset(clampedStart, baseY),
        size = Size(width, timelineHeight)
    )

    // 3. Gradiente reflexo
    drawRect(
        color = glassHighlight.copy(alpha = 0.3f),
        topLeft = Offset(clampedStart, baseY),
        size = Size(width.coerceAtLeast(1f), timelineHeight * 0.3f)
    )

    // 4. Borda externa glow
    if (isSelected) {
        drawRect(
            color = glassGreen.copy(alpha = 0.3f),
            topLeft = Offset(clampedStart - 2f, baseY - 2f),
            size = Size(width + 4f, timelineHeight + 4f)
        )
    }

    // 5. Bordas verticais
    val borderWidth = if (isSelected) 3f else 2f
    drawLine(
        color = if (isSelected) glassGreen else glassBorder,
        start = Offset(clampedStart, baseY),
        end = Offset(clampedStart, timelineHeight + baseY),
        strokeWidth = borderWidth
    )
    drawLine(
        color = if (isSelected) glassGreen else glassBorder,
        start = Offset(clampedEnd, baseY),
        end = Offset(clampedEnd, timelineHeight + baseY),
        strokeWidth = borderWidth
    )

    // 6. Linha superior brilho
    drawLine(
        color = glassHighlight.copy(alpha = 0.8f),
        start = Offset(clampedStart, baseY + 1f),
        end = Offset(clampedEnd, baseY + 1f),
        strokeWidth = 1f
    )

    // Alças (só se showHandles = true)
    if (showHandles) {
        val handleTop = timelineHeight / 2 - handleHeight / 2

        // Alça START
        drawRoundRect(
            color = glassGreen,
            topLeft = Offset(clampedStart - handleWidth / 2, handleTop + baseY),
            size = Size(handleWidth, handleHeight),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawLine(
            color = glassHighlight,
            start = Offset(
                clampedStart - handleWidth / 2 + 2f,
                handleTop + baseY + 4f
            ),
            end = Offset(clampedStart + handleWidth / 2 - 2f, handleTop + baseY + 4f),
            strokeWidth = 2f
        )

        // Alça END
        drawRoundRect(
            color = glassGreen,
            topLeft = Offset(clampedEnd - handleWidth / 2, handleTop + baseY),
            size = Size(handleWidth, handleHeight),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawLine(
            color = Color.White,
            start = Offset(clampedEnd, handleTop + 4.dp.toPx()),
            end = Offset(clampedEnd, handleTop + handleHeight - 4.dp.toPx()),
            strokeWidth = 2f
        )
    }

    // Indicador central (círculo)
    if (width > 40.dp.toPx()) {
        drawCircle(
            color = if (showHandles) glassGreen.copy(alpha = 0.8f) else glassGreen.copy(
                alpha = 0.4f
            ),
            radius = if (showHandles) 8.dp.toPx() else 5.dp.toPx(),
            center = Offset(clampedStart + width / 2, timelineHeight / 2)
        )
    }
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
 */
private fun tempoParaX(
    tempoMs: Long,
    pxPorSegundo: Float,
    scrollOffsetPx: Float,
    centerOffset: Float
): Float {
    return (tempoMs / 1000f) * pxPorSegundo - scrollOffsetPx + centerOffset
}

/**
 * Infla um Rect pela quantidade especificada.
 */
private fun Rect.inflate(amount: Float): Rect {
    return Rect(
        left - amount,
        top - amount,
        right + amount,
        bottom + amount
    )
}
