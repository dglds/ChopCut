package com.chopcut

import android.os.Parcelable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.parcelize.Parcelize


// --- Merged from RangeList.kt ---


@Composable
fun RangeList(
    ranges: List<Pair<Long, Long>>,
    currentPosition: Long,
    totalDurationMs: Long,
    finalDurationMs: Long,
    isDraftMode: Boolean,
    draftPosition: Long?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Duração Info
        Row(
            modifier = Modifier.padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Total: ${formatTime(totalDurationMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Final: ${formatTime(finalDurationMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF64B5F6),
                fontWeight = FontWeight.Bold
            )
        }

        if (ranges.isEmpty() && !isDraftMode) {
            Text(
                text = "Nenhum range definido",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        } else {
            ranges.forEachIndexed { index, (start, end) ->
                Text(
                    text = "#${index + 1} ${formatTime(start)} → ${formatTime(end)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }

            if (isDraftMode && draftPosition != null) {
                Text(
                    text = "Draft: ${formatTime(draftPosition)} → ${formatTime(currentPosition)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFF9800),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// --- Merged from RangeManager.kt ---


/**
 * Gerenciador de ranges para edição de vídeo.
 *
 * Responsável por:
 * - Manter lista de ranges
 * - Validar sobreposições
 * - Redimensionar ranges
 * - Adicionar/remover ranges
 *
 * @property minRangeDurationMs Duração mínima permitida para um range (padrão: 500ms)
 * @property videoDurationMs Duração total do vídeo em milissegundos
 */
class RangeManager(
    private val minRangeDurationMs: Long = 500L,
    private val videoDurationMs: Long
) {
    private val _ranges = mutableStateListOf<VideoRange>()
    val ranges: SnapshotStateList<VideoRange> = _ranges

    /**
     * Adiciona um novo range nas posições especificadas.
     *
     * @param startMs Posição inicial em milissegundos
     * @param endMs Posição final em milissegundos
     * @return Result.success com o range criado ou Result.failure com mensagem de erro
     */
    fun addRangeAt(startMs: Long, endMs: Long): Result<VideoRange> {
        // Validar limites do vídeo
        if (startMs < 0 || endMs > videoDurationMs) {
            return Result.failure(IllegalArgumentException("Range fora dos limites do vídeo"))
        }

        // Validar ordem das posições
        if (startMs >= endMs) {
            return Result.failure(IllegalArgumentException("Início deve ser menor que fim"))
        }

        // Validar duração mínima
        if (endMs - startMs < minRangeDurationMs) {
            return Result.failure(IllegalArgumentException("Duração mínima: ${minRangeDurationMs}ms"))
        }

        // Validar sobreposição com ranges existentes
        if (!validateNoOverlap(startMs, endMs)) {
            return Result.failure(IllegalArgumentException("Range sobrepõe range existente"))
        }

        // Criar e adicionar o range
        val newRange = VideoRange(
            startMs = startMs,
            endMs = endMs
        )
        _ranges.add(newRange)
        _ranges.sortBy { it.startMs }

        return Result.success(newRange)
    }

    /**
     * Adiciona um range centralizado na posição especificada.
     *
     * @param positionMs Posição central do range em milissegundos
     * @param defaultDurationMs Duração padrão do range (padrão: 2000ms)
     * @return Result.success com o range criado ou Result.failure com mensagem de erro
     */
    fun addCenteredRangeAt(positionMs: Long, defaultDurationMs: Long = 2000L): Result<VideoRange> {
        val halfDuration = defaultDurationMs / 2
        val startMs = max(0L, positionMs - halfDuration)
        val endMs = min(videoDurationMs, positionMs + halfDuration)

        // Ajustar se estiver nos limites
        val adjustedStartMs = if (endMs == videoDurationMs) {
            max(0L, videoDurationMs - defaultDurationMs)
        } else {
            startMs
        }

        val adjustedEndMs = if (startMs == 0L) {
            min(videoDurationMs, defaultDurationMs)
        } else {
            endMs
        }

        return addRangeAt(adjustedStartMs, adjustedEndMs)
    }

    /**
     * Remove um range pelo ID.
     *
     * @param rangeId ID do range a ser removido
     * @return true se o range foi removido, false se não foi encontrado
     */
    fun removeRange(rangeId: String): Boolean {
        return _ranges.removeIf { it.id == rangeId }
    }

    /**
     * Redimensiona um range especificado.
     *
     * @param rangeId ID do range a ser redimensionado
     * @param newStartMs Nova posição inicial (ou null para manter atual)
     * @param newEndMs Nova posição final (ou null para manter atual)
     * @return Result.success com o range atualizado ou Result.failure com mensagem de erro
     */
    fun resizeRange(
        rangeId: String,
        newStartMs: Long? = null,
        newEndMs: Long? = null
    ): Result<VideoRange> {
        val rangeIndex = _ranges.indexOfFirst { it.id == rangeId }
        if (rangeIndex == -1) {
            return Result.failure(IllegalArgumentException("Range não encontrado: $rangeId"))
        }

        val currentRange = _ranges[rangeIndex]
        val startMs = newStartMs ?: currentRange.startMs
        val endMs = newEndMs ?: currentRange.endMs

        // Validar ordem das posições
        if (startMs >= endMs) {
            return Result.failure(IllegalArgumentException("Início deve ser menor que fim"))
        }

        // Validar limites do vídeo
        if (startMs < 0 || endMs > videoDurationMs) {
            return Result.failure(IllegalArgumentException("Range fora dos limites do vídeo"))
        }

        // Validar duração mínima
        if (endMs - startMs < minRangeDurationMs) {
            return Result.failure(IllegalArgumentException("Duração mínima: ${minRangeDurationMs}ms"))
        }

        // Validar sobreposição com outros ranges (excluindo este range)
        if (!validateNoOverlap(startMs, endMs, excludeId = rangeId)) {
            return Result.failure(IllegalArgumentException("Range sobrepõe range existente"))
        }

        // Atualizar o range
        val updatedRange = currentRange.withPosition(startMs, endMs)
        _ranges[rangeIndex] = updatedRange
        _ranges.sortBy { it.startMs }

        return Result.success(updatedRange)
    }

    /**
     * Move um range por um delta em milissegundos.
     *
     * @param rangeId ID do range a ser movido
     * @param deltaMs Delta de movimento em milissegundos (positivo = direita, negativo = esquerda)
     * @return Result.success com o range movido ou Result.failure com mensagem de erro
     */
    fun moveRange(rangeId: String, deltaMs: Long): Result<VideoRange> {
        val range = _ranges.find { it.id == rangeId }
            ?: return Result.failure(IllegalArgumentException("Range não encontrado: $rangeId"))

        val newStartMs = range.startMs + deltaMs
        val newEndMs = range.endMs + deltaMs

        return resizeRange(rangeId, newStartMs, newEndMs)
    }

    /**
     * Seleciona ou desseleciona um range.
     *
     * @param rangeId ID do range
     * @param selected true para selecionar, false para desselecionar
     * @return true se o range foi atualizado, false se não foi encontrado
     */
    fun selectRange(rangeId: String, selected: Boolean): Boolean {
        val rangeIndex = _ranges.indexOfFirst { it.id == rangeId }
        if (rangeIndex == -1) return false

        _ranges[rangeIndex] = _ranges[rangeIndex].withSelection(selected)

        // Desselecionar todos os outros se estiver selecionando este
        if (selected) {
            for (i in _ranges.indices) {
                if (i != rangeIndex && _ranges[i].isSelected) {
                    _ranges[i] = _ranges[i].withSelection(false)
                }
            }
        }

        return true
    }

    /**
     * Desseleciona todos os ranges.
     */
    fun clearSelection() {
        for (i in _ranges.indices) {
            if (_ranges[i].isSelected) {
                _ranges[i] = _ranges[i].withSelection(false)
            }
        }
    }

    /**
     * Retorna o range selecionado atualmente.
     *
     * @return O range selecionado ou null se nenhum estiver selecionado
     */
    fun getSelectedRange(): VideoRange? {
        return _ranges.find { it.isSelected }
    }

    /**
     * Verifica se uma posição está dentro de algum range.
     *
     * @param positionMs Posição em milissegundos
     * @return O range que contém a posição ou null se não houver
     */
    fun findRangeAt(positionMs: Long): VideoRange? {
        return _ranges.find { it.contains(positionMs) }
    }

    /**
     * Valida se um intervalo não sobrepõe ranges existentes.
     *
     * @param startMs Início do intervalo
     * @param endMs Fim do intervalo
     * @param excludeId ID de um range para excluir da validação (útil ao mover/redimensionar)
     * @return true se não há sobreposição, false se há sobreposição
     */
    fun validateNoOverlap(startMs: Long, endMs: Long, excludeId: String? = null): Boolean {
        val otherRanges = if (excludeId != null) {
            _ranges.filterNot { it.id == excludeId }
        } else {
            _ranges
        }

        return otherRanges.none { it.overlapsWith(startMs, endMs) }
    }

    /**
     * Remove todos os ranges.
     */
    fun clear() {
        _ranges.clear()
    }

    /**
     * Retorna o número de ranges.
     */
    fun count(): Int = _ranges.size

    /**
     * Verifica se há ranges selecionados.
     */
    fun hasSelection(): Boolean = _ranges.any { it.isSelected }
}

// --- Merged from RangeOverlay.kt ---


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

// --- Merged from TrimActionButton.kt ---


enum class TrimActionState {
    CUT,
    CONFIRM,
    DELETE
}

@Composable
fun TrimActionButton(
    isDraftMode: Boolean,
    isInsideRange: Boolean,
    onAddPosition: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actionState = when {
        isDraftMode -> TrimActionState.CONFIRM
        isInsideRange -> TrimActionState.DELETE
        else -> TrimActionState.CUT
    }

    TrimActionIconButton(
        actionState = actionState,
        onClick = when (actionState) {
            TrimActionState.CUT -> onAddPosition
            TrimActionState.CONFIRM -> onAddPosition
            TrimActionState.DELETE -> onDelete
        },
        modifier = modifier
    )
}

@Composable
private fun TrimActionIconButton(
    actionState: TrimActionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config = getActionConfig(actionState)
    
    // Animação de cor de fundo suave entre os estados
    val animatedBgColor by animateColorAsState(
        targetValue = config.backgroundColor,
        animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing),
        label = "fab-color-transition"
    )

    // Animação de pulsação apenas no estado CONFIRM (durante o trim)
    val infiniteTransition = rememberInfiniteTransition(label = "fab-pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (actionState == TrimActionState.CONFIRM) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fab-scale"
    )
    
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (actionState == TrimActionState.CONFIRM) 0.8f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fab-border-alpha"
    )

    Box(contentAlignment = Alignment.Center) {
        // Efeito de brilho externo pulsante (apenas para CONFIRM)
        if (actionState == TrimActionState.CONFIRM) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .scale(pulseScale)
                    .background(config.backgroundColor.copy(alpha = 0.4f), RectangleShape)
            )
        }

        IconButton(
            onClick = onClick,
            modifier = modifier
                .size(64.dp)
                .background(animatedBgColor, RectangleShape)
                .then(
                    if (actionState == TrimActionState.CONFIRM) {
                        Modifier.border(2.dp, Color.White.copy(alpha = borderAlpha), RectangleShape)
                    } else Modifier
                ),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = Color.White
            )
        ) {
            Crossfade(
                targetState = actionState,
                animationSpec = tween(durationMillis = 300),
                label = "trim-action-icon"
            ) { state ->
                val iconConfig = getActionConfig(state)
                Icon(
                    imageVector = iconConfig.icon,
                    contentDescription = iconConfig.contentDescription,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

private data class TrimActionConfig(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val contentDescription: String,
    val backgroundColor: Color
)

@Composable
private fun getActionConfig(actionState: TrimActionState): TrimActionConfig {
    return when (actionState) {
        TrimActionState.CUT -> TrimActionConfig(
            icon = Icons.Default.ContentCut,
            contentDescription = "Iniciar corte",
            backgroundColor = MaterialTheme.colorScheme.primary
        )
        TrimActionState.CONFIRM -> TrimActionConfig(
            icon = Icons.Default.Check,
            contentDescription = "Confirmar corte",
            backgroundColor = Color(0xFFFF9800) // Amarelo/Laranja vibrante
        )
        TrimActionState.DELETE -> TrimActionConfig(
            icon = Icons.Default.Delete,
            contentDescription = "Excluir range",
            backgroundColor = MaterialTheme.colorScheme.error
        )
    }
}

// --- Merged from TrimButtons.kt ---


@Composable
fun TrimButtons(
    isDraftMode: Boolean,
    isInsideRange: Boolean,
    onAddPosition: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        TrimActionButton(
            isDraftMode = isDraftMode,
            isInsideRange = isInsideRange,
            onAddPosition = onAddPosition,
            onDelete = onDelete
        )
    }
}

// --- Merged from TrimPosition.kt ---

data class TrimPosition(
    val positions: List<Long> = emptyList()
) {
    companion object {
        val Empty = TrimPosition()
        const val MIN_RANGE_DURATION_MS = 500L
    }

    val draftPosition: Long?
        get() = if (positions.size % 2 == 1) positions.last() else null

    val completeRanges: List<Pair<Long, Long>>
        get() = mergeRanges(positions)

    val isDraftMode: Boolean
        get() = positions.size % 2 == 1

    fun isPositionInList(pos: Long): Boolean = pos in positions

    fun isPositionInRange(pos: Long): Boolean = completeRanges.any { (s, e) -> pos in s..e }

    fun withPosition(pos: Long): TrimPosition {
        return if (pos in positions) this else copy(positions = positions + pos)
    }

    fun removeRangeAt(pos: Long): TrimPosition {
        val rangeToRemove = completeRanges.find { (s, e) -> pos in s..e }
        return if (rangeToRemove != null) {
            val newRanges = completeRanges.filterNot { it == rangeToRemove }
            val newPositions = newRanges.flatMap { listOf(it.first, it.second) }.toMutableList()
            if (isDraftMode) {
                newPositions.add(positions.last())
            }
            copy(positions = newPositions)
        } else {
            this
        }
    }

    fun updateRangeAt(rangeIndex: Int, newStartMs: Long, newEndMs: Long): TrimPosition {
        val ranges = completeRanges.toMutableList()
        if (rangeIndex !in ranges.indices) return this
        val clampedEnd = maxOf(newEndMs, newStartMs + MIN_RANGE_DURATION_MS)
        val clampedStart = minOf(newStartMs, clampedEnd - MIN_RANGE_DURATION_MS)
        ranges[rangeIndex] = clampedStart to clampedEnd
        val newPositions = ranges.flatMap { listOf(it.first, it.second) }.toMutableList()
        if (isDraftMode) newPositions.add(positions.last())
        return copy(positions = newPositions)
    }

    private fun mergeRanges(positions: List<Long>): List<Pair<Long, Long>> {
        if (positions.size < 2) return emptyList()

        val rawRanges = positions.chunked(2).mapNotNull { chunk ->
            if (chunk.size == 2) minOf(chunk[0], chunk[1]) to maxOf(chunk[0], chunk[1])
            else null
        }

        return rawRanges.sortedBy { it.first }
            .fold(emptyList<Pair<Long, Long>>()) { acc, range ->
                if (acc.isEmpty() || range.first > acc.last().second) {
                    acc + range
                } else {
                    acc.dropLast(1) + (acc.last().first to maxOf(acc.last().second, range.second))
                }
            }
    }
}

// --- Merged from TrimRange.kt ---

/**
 * Representa um intervalo de tempo para trim/recorte de vídeo
 * 
 * @property startMs Tempo de início em milissegundos (inclusivo)
 * @property endMs Tempo de fim em milissegundos (exclusivo)
 */
data class TrimRange(
    val startMs: Long,
    val endMs: Long
) {
    companion object {
        /** Cria um TrimRange com validação automática */
        fun create(startMs: Long, endMs: Long): TrimRange {
            require(startMs >= 0) { "startMs must be non-negative, was $startMs" }
            require(endMs >= startMs) { "endMs ($endMs) must be >= startMs ($startMs)" }
            return TrimRange(startMs, endMs)
        }
        
        /** TrimRange vazio (duração zero) */
        val Empty = TrimRange(0, 0)
    }
    
    /** Duração do intervalo em milissegundos */
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0)
    
    /** Verifica se o intervalo é válido (início < fim) */
    val isValid: Boolean
        get() = startMs >= 0 && endMs > startMs
    
    /** Verifica se o intervalo é vazio (duração zero) */
    val isEmpty: Boolean
        get() = durationMs == 0L
    
    /** Verifica se um tempo está dentro deste intervalo */
    fun contains(timeMs: Long): Boolean {
        return timeMs in startMs..endMs
    }
    
    /** Normaliza o intervalo para garantir que startMs <= endMs */
    fun normalize(): TrimRange {
        return if (startMs <= endMs) this else copy(startMs = endMs, endMs = startMs)
    }
    
    /** Interpola um valor baseado em uma duração total */
    fun scaledTo(totalDurationMs: Long): TrimRange {
        return copy(
            startMs = (startMs * totalDurationMs / durationMs).coerceAtLeast(0),
            endMs = (endMs * totalDurationMs / durationMs).coerceAtMost(totalDurationMs)
        )
    }
    
    override fun toString(): String {
        return "TrimRange(${formatTime(startMs)} - ${formatTime(endMs)})"
    }
    
    private fun formatTime(ms: Long): String {
        val seconds = ms / 1000
        val decis = (ms % 1000) / 100
        return "${seconds}s${decis}"
    }
}

// --- Merged from TrimRangeData.kt ---


/**
 * Representa um range de trim (área a ser removida) com ID único.
 *
 * @property id Identificador único do range.
 * @property startMs Ponto de início em milissegundos.
 * @property endMs Ponto de fim em milissegundos.
 * @property isSelected Se true, o range está selecionado na UI.
 * @property isDraft Se true, o range está em edição (alças visíveis).
 * @property isConfirmed Se true, o range já foi salvo.
 * @property isDefining Se true, o range está sendo definido (Mark A definido, Mark B seguindo o playhead).
 */
@Parcelize
data class TrimRangeData(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val isSelected: Boolean = false,
    val isDraft: Boolean = true,
    val isConfirmed: Boolean = false,
    val isDefining: Boolean = false
) : Parcelable {
    val durationMs: Long get() = Math.abs(endMs - startMs)

    fun contains(timeMs: Long): Boolean {
        val min = Math.min(startMs, endMs)
        val max = Math.max(startMs, endMs)
        return timeMs in min..max
    }

    fun overlaps(other: TrimRangeData): Boolean {
        val thisMin = Math.min(startMs, endMs)
        val thisMax = Math.max(startMs, endMs)
        val otherMin = Math.min(other.startMs, other.endMs)
        val otherMax = Math.max(other.startMs, other.endMs)
        return thisMin < otherMax && thisMax > otherMin
    }
}

// --- Merged from TrimSaveDialog.kt ---


data class SaveDialogState(
    val isSaving: Boolean = false,
    val progress: Int = 0,
    val isCompleted: Boolean = false,
    val error: String? = null
) {
    val canDismiss: Boolean
        get() = !isSaving

    val title: String
        get() = when {
            isCompleted -> "Vídeo Salvo!"
            error != null -> "Erro ao Salvar"
            isSaving -> "Exportando..."
            else -> "Remover Trechos"
        }
}

@Composable
fun TrimSaveDialog(
    state: SaveDialogState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (state.canDismiss) {
                onDismiss()
            }
        },
        title = {
            Text(
                state.title,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                when {
                    state.isCompleted -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Vídeo salvo com sucesso!")
                    }
                    state.error != null -> {
                        Text(state.error)
                    }
                    state.isSaving -> {
                        if (state.progress > 0) {
                            CircularProgressIndicator(
                                progress = { state.progress / 100f },
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("${state.progress}%")
                    }
                    else -> {
                        Text("Deseja remover os trechos selecionados e salvar o vídeo?")
                    }
                }
            }
        },
        confirmButton = {
            when {
                state.isCompleted -> {
                    TextButton(onClick = {
                        onDismiss()
                        onNavigateBack()
                    }) {
                        Text("Ir para Início")
                    }
                }
                state.error != null -> {
                    TextButton(onClick = {
                        onDismiss()
                    }) {
                        Text("Fechar")
                    }
                }
                state.isSaving -> {
                }
                else -> {
                    TextButton(onClick = onSave) {
                        Text("Salvar")
                    }
                }
            }
        },
        dismissButton = {
            if (!state.isSaving && !state.isCompleted && state.error == null) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        }
    )
}
