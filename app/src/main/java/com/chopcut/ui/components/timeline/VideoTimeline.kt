package com.chopcut.ui.components.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import com.chopcut.ui.components.atoms.formatDuration
import com.chopcut.ui.theme.ChopCutMonoFont
import com.chopcut.ui.theme.ChopCutSpacing
import com.chopcut.ui.theme.Playhead
import com.chopcut.ui.theme.SelectionOverlay
import com.chopcut.ui.theme.Surface
import com.chopcut.ui.theme.TimelineBackground
import com.chopcut.ui.theme.TimelineTrack
import com.chopcut.ui.theme.TrimHandle

/**
 * Timeline de vídeo para editor
 *
 * Componente principal para visualização e manipulação de vídeo
 *
 * @param durationMs Duração total do vídeo em milissegundos
 * @param currentPosition Posição atual do playhead em milissegundos
 * @param trimStart Início do corte em milissegundos (0 = início do vídeo)
 * @param trimEnd Fim do corte em milissegundos (duration = fim do vídeo)
 * @param onPositionChange Callback quando posição do playhead muda
 * @param onTrimChange Callback quando o corte muda (start, end)
 * @param modifier Modificador
 */
@Composable
fun VideoTimeline(
    durationMs: Long,
    currentPosition: Long,
    trimStart: Long,
    trimEnd: Long,
    onPositionChange: (Long) -> Unit = {},
    onTrimChange: (Long, Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var isDraggingPlayhead by remember { mutableStateOf(false) }
    var isDraggingTrimStart by remember { mutableStateOf(false) }
    var isDraggingTrimEnd by remember { mutableStateOf(false) }
    var timelineWidth by remember { mutableFloatStateOf(0f) }

    // Converter milissegundos para posição (0-1)
    val positionRatio = currentPosition.toFloat() / durationMs.toFloat()
    val trimStartRatio = trimStart.toFloat() / durationMs.toFloat()
    val trimEndRatio = trimEnd.toFloat() / durationMs.toFloat()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ChopCutSpacing.md)
    ) {
        // Duração total
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "0:00",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                fontFamily = ChopCutMonoFont
            )
            Text(
                text = formatDuration(durationMs),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                fontFamily = ChopCutMonoFont
            )
        }

        // Timeline principal
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChopCutSpacing.timelineHeight)
                .background(TimelineBackground, RoundedCornerShape(8.dp))
                .onGloballyPositioned { coordinates ->
                    timelineWidth = coordinates.size.width.toFloat()
                }
        ) {
            // Trilha de vídeo (track)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(8.dp)
                    .background(TimelineTrack, RoundedCornerShape(4.dp))
            )

            // Área selecionada (trim)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(8.dp)
                    .align(Alignment.CenterStart)
                    .then(
                        if (timelineWidth > 0) {
                            Modifier
                                .width((timelineWidth * (trimEndRatio - trimStartRatio)).dp)
                                .padding(start = (timelineWidth * trimStartRatio).dp)
                        } else {
                            Modifier
                        }
                    )
                    .background(SelectionOverlay.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            )

            // Playhead
            PlayheadIndicator(
                ratio = positionRatio,
                isDragging = isDraggingPlayhead
            )

            // Trim handles
            TrimHandleStart(
                ratio = trimStartRatio,
                isDragging = isDraggingTrimStart
            )

            TrimHandleEnd(
                ratio = trimEndRatio,
                isDragging = isDraggingTrimEnd
            )

            // Área de toque para drag
            TimelineTouchArea(
                timelineWidth = timelineWidth,
                playheadRatio = positionRatio,
                trimStartRatio = trimStartRatio,
                trimEndRatio = trimEndRatio,
                onPlayheadDragStart = { isDraggingPlayhead = true },
                onPlayheadDrag = { ratio ->
                    onPositionChange((ratio * durationMs).toLong())
                },
                onPlayheadDragEnd = { isDraggingPlayhead = false },
                onTrimStartDragStart = { isDraggingTrimStart = true },
                onTrimStartDrag = { ratio ->
                    val newStart = (ratio * durationMs).toLong()
                        .coerceAtMost(trimEnd - 100) // Mínimo 100ms
                    onTrimChange(newStart, trimEnd)
                },
                onTrimStartDragEnd = { isDraggingTrimStart = false },
                onTrimEndDragStart = { isDraggingTrimEnd = true },
                onTrimEndDrag = { ratio ->
                    val newEnd = (ratio * durationMs).toLong()
                        .coerceAtLeast(trimStart + 100) // Mínimo 100ms
                    onTrimChange(trimStart, newEnd)
                },
                onTrimEndDragEnd = { isDraggingTrimEnd = false }
            )
        }

        // Informações do corte
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = ChopCutSpacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Início: ${formatDuration(trimStart)}",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                fontFamily = ChopCutMonoFont
            )
            Text(
                text = "Duração: ${formatDuration(trimEnd - trimStart)}",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                fontFamily = ChopCutMonoFont,
                color = Playhead
            )
            Text(
                text = "Fim: ${formatDuration(trimEnd)}",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                fontFamily = ChopCutMonoFont
            )
        }
    }
}

/**
 * Indicador do playhead (posição atual)
 */
@Composable
private fun PlayheadIndicator(
    ratio: Float,
    isDragging: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .then(
                if (ratio > 0f) {
                    Modifier.padding(start = (ratio * 1000000).dp) // Hack temporário
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // Linha vertical
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .background(Playhead)
        )

        // Círculo no topo
        Box(
            modifier = Modifier
                .padding(start = 9.dp) // Metade da largura - metade da linha
                .size(12.dp)
                .background(Playhead, CircleShape)
                .then(
                    if (isDragging) {
                        Modifier.padding(2.dp)
                    } else {
                        Modifier
                    }
                )
        )
    }
}

/**
 * Alça de corte no início
 */
@Composable
private fun TrimHandleStart(
    ratio: Float,
    isDragging: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .padding(top = 8.dp, bottom = 8.dp)
            .then(
                if (ratio > 0f) {
                    Modifier.padding(start = (ratio * 1000000).dp)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = 8.dp) // Ajuste para alinhar com a trilha
                .size(ChopCutSpacing.trimHandleSize)
                .background(TrimHandle, CircleShape)
                .then(
                    if (isDragging) {
                        Modifier.border(2.dp, Playhead, CircleShape)
                    } else {
                        Modifier
                    }
                )
        )
    }
}

/**
 * Alça de corte no fim
 */
@Composable
private fun TrimHandleEnd(
    ratio: Float,
    isDragging: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .padding(top = 8.dp, bottom = 8.dp)
            .then(
                if (ratio > 0f) {
                    Modifier.padding(start = (ratio * 1000000).dp)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .size(ChopCutSpacing.trimHandleSize)
                .background(TrimHandle, CircleShape)
                .then(
                    if (isDragging) {
                        Modifier.border(2.dp, Playhead, CircleShape)
                    } else {
                        Modifier
                    }
                )
        )
    }
}

/**
 * Área de toque para detecção de drag
 * Simplificada - em produção, usar PointerInput com detectDragGestures
 */
@Composable
private fun TimelineTouchArea(
    timelineWidth: Float,
    playheadRatio: Float,
    trimStartRatio: Float,
    trimEndRatio: Float,
    onPlayheadDragStart: () -> Unit,
    onPlayheadDrag: (Float) -> Unit,
    onPlayheadDragEnd: () -> Unit,
    onTrimStartDragStart: () -> Unit,
    onTrimStartDrag: (Float) -> Unit,
    onTrimStartDragEnd: () -> Unit,
    onTrimEndDragStart: () -> Unit,
    onTrimEndDrag: (Float) -> Unit,
    onTrimEndDragEnd: () -> Unit
) {
    // TODO(human): Implementar gesto de drag usando PointerInput
    // 1. Usar Modifier.pointerInput(Unit) { detectTapGestures { offset -> ... } }
    // 2. Detectar se o toque está perto do playhead ou trim handles
    // 3. Usar detectDragGestures para arrastar
    //
    // Dica: A posição do toque pode ser obtida com offset.x / timelineWidth
    // Verificar se está dentro de 20dp do playhead/handle para iniciar o drag
}
