package com.chopcut.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.chopcut.ui.timeline.model.Thumbnail

/**
 * Componente principal da Timeline com playhead fixo no centro.
 * As thumbnails rolam horizontalmente sob o playhead.
 *
 * @param viewModel ViewModel da Timeline
 * @param thumbnails Lista de thumbnails do vídeo
 * @param modifier Modifier
 * @param showControls Se deve mostrar controles de range
 * @param thumbnailWidth Largura de cada thumbnail (calculada baseado no aspect ratio)
 * @param onScrubStart Callback quando scrubbing inicia
 * @param onScrubEnd Callback quando scrubbing termina
 */
@Composable
fun Timeline(
    viewModel: TimelineViewModel,
    thumbnails: List<Thumbnail>,
    modifier: Modifier = Modifier,
    showControls: Boolean = true,
    thumbnailWidth: androidx.compose.ui.unit.Dp = 80.dp,
    onScrubStart: () -> Unit = {},
    onScrubEnd: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val density = LocalDensity.current

    Column(modifier = modifier) {
        // Timeline com playhead fixo no centro
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(Color.Black.copy(alpha = 0.1f))
        ) {
            val centerX = constraints.maxWidth.toFloat() / 2
            val durationMs = remember(state.totalDurationMs) { state.totalDurationMs.coerceAtLeast(1L) }
            val handleWidthPx = remember(density) { with(density) { 16.dp.toPx() } }

            // 1. Thumbnail Strip com scroll automático baseado no playhead
            ThumbnailStrip(
                thumbnails = thumbnails,
                modifier = Modifier.fillMaxSize(),
                thumbnailWidth = thumbnailWidth,
                playheadPositionMs = state.playheadPositionMs,
                totalDurationMs = durationMs,
                onThumbnailClick = { timeMs ->
                    viewModel.updatePlayheadPosition(timeMs)
                    onScrubStart()
                    onScrubEnd()
                }
            )

            // 2. Playhead FIXO no centro
            Playhead(
                positionPx = centerX,
                onPositionChanged = { newPx ->
                    // O playhead não se move mais, apenas atualizamos a posição
                    // baseado em onde o usuário toca na timeline
                    val widthPx = constraints.maxWidth.toFloat()
                    val clickProgress = (newPx / widthPx).coerceIn(0f, 1f)
                    val newTimeMs = (clickProgress * durationMs).toLong()
                    viewModel.updatePlayheadPosition(newTimeMs)
                },
                onDragStart = onScrubStart,
                onDragEnd = onScrubEnd
            )

            // 3. Indicadores visuais dos ranges (sobreposição no centro)
            state.ranges.forEach { range ->
                val startProgress = range.startMs.toFloat() / durationMs
                val endProgress = range.endMs.toFloat() / durationMs
                val rangeWidth = ((endProgress - startProgress) * constraints.maxWidth).coerceAtLeast(handleWidthPx)

                // Offset baseado na posição do range em relação ao playhead
                val playheadProgress = state.playheadPositionMs.toFloat() / durationMs
                val centerProgress = 0.5f  // Playhead está sempre no centro (50%)
                val rangeCenterProgress = (startProgress + endProgress) / 2
                val offsetFromCenter = (rangeCenterProgress - playheadProgress) * constraints.maxWidth

                // Desenhar range apenas se estiver visível
                if (offsetFromCenter > -constraints.maxWidth && offsetFromCenter < constraints.maxWidth) {
                    Box(
                        modifier = Modifier
                            .height(80.dp)
                            .width(with(density) { rangeWidth.toDp() })
                            .align(Alignment.CenterStart)
                            .then(
                                Modifier.offset(x = with(density) {
                                    (centerX + offsetFromCenter - rangeWidth / 2).toDp()
                                })
                            )
                            .background(
                                if (range.isSelected)
                                    Color(0xFF2196F3).copy(alpha = 0.3f)
                                else
                                    Color(0xFF4CAF50).copy(alpha = 0.3f)
                            )
                    )
                }

                // Handles apenas do range selecionado
                if (range.isSelected) {
                    val startOffsetFromCenter = (startProgress - playheadProgress) * constraints.maxWidth
                    val endOffsetFromCenter = (endProgress - playheadProgress) * constraints.maxWidth

                    // Handle de início
                    Box(
                        modifier = Modifier
                            .width(with(density) { handleWidthPx.toDp() })
                            .height(80.dp)
                            .align(Alignment.CenterStart)
                            .offset(x = with(density) {
                                (centerX + startOffsetFromCenter).toDp()
                            })
                            .border(
                                width = if (range.isSelected) 2.dp else 1.dp,
                                color = if (range.isSelected) Color(0xFF2196F3) else Color.White
                            )
                    )

                    // Handle de fim
                    Box(
                        modifier = Modifier
                            .width(with(density) { handleWidthPx.toDp() })
                            .height(80.dp)
                            .align(Alignment.CenterStart)
                            .offset(x = with(density) {
                                (centerX + endOffsetFromCenter - handleWidthPx).toDp()
                            })
                            .border(
                                width = if (range.isSelected) 2.dp else 1.dp,
                                color = if (range.isSelected) Color(0xFF2196F3) else Color.White
                            )
                    )
                }
            }
        }

        // Controles e lista de ranges
        if (showControls) {
            RangeControls(
                onAddRange = { viewModel.addRange() },
                onRemoveRange = { viewModel.removeSelectedRange() },
                hasSelectedRange = state.selectedRange != null,
                modifier = Modifier.fillMaxWidth()
            )

            // Lista de ranges
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                RangesList(
                    ranges = state.ranges,
                    onRangeClick = { rangeId -> viewModel.selectRange(rangeId) }
                )
            }
        }
    }
}
