package com.chopcut.ui.timelinev5

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.chopcut.ui.timelinev5.model.Thumbnail

/**
 * Componente principal da TimelineV5 que integra a faixa de thumbnails,
 * seletores de intervalo (trim) e o playhead.
 */
@Composable
fun TimelineV5(
    viewModel: TimelineV5ViewModel,
    thumbnails: List<Thumbnail>,
    modifier: Modifier = Modifier,
    onScrubStart: () -> Unit = {},
    onScrubEnd: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val density = LocalDensity.current
    
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color.Black.copy(alpha = 0.1f))
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val durationMs = remember(state.totalDurationMs) { state.totalDurationMs.coerceAtLeast(1L) }
        
        // Helpers memoizados para conversão
        val timeToPx: (Long) -> Float = remember(widthPx, durationMs) {
            { timeMs -> (timeMs.toFloat() / durationMs) * widthPx }
        }
        val pxToTime: (Float) -> Long = remember(widthPx, durationMs) {
            { px -> ((px / widthPx) * durationMs).toLong().coerceIn(0, durationMs) }
        }

        val handleWidthPx = remember(density) { with(density) { 16.dp.toPx() } }

        // 1. Thumbnail Strip
        ThumbnailStrip(
            thumbnails = thumbnails,
            modifier = Modifier.fillMaxWidth(),
            thumbnailWidth = remember(maxWidth, thumbnails.size) { 
                (maxWidth / thumbnails.size.coerceAtLeast(1)).coerceAtLeast(1.dp)
            }
        )

        // 2. Overlay de sombreamento fora do intervalo selecionado
        val startWeight = remember(state.selectedStartMs, durationMs) {
            state.selectedStartMs.toFloat() / durationMs
        }
        val endWeight = remember(state.selectedEndMs, durationMs) {
            1f - (state.selectedEndMs.toFloat() / durationMs)
        }

        Box(
            modifier = Modifier
                .height(80.dp)
                .fillMaxWidth(startWeight.coerceIn(0f, 1f))
                .background(Color.Black.copy(alpha = 0.5f))
                .align(Alignment.CenterStart)
        )
        
        Box(
            modifier = Modifier
                .height(80.dp)
                .fillMaxWidth(endWeight.coerceIn(0f, 1f))
                .background(Color.Black.copy(alpha = 0.5f))
                .align(Alignment.CenterEnd)
        )

        // 3. Trim Handles
        val startPx = remember(state.selectedStartMs, timeToPx) { timeToPx(state.selectedStartMs) }
        val endPx = remember(state.selectedEndMs, timeToPx) { timeToPx(state.selectedEndMs) }

        TrimHandle(
            positionPx = startPx,
            onPositionChanged = { newPx ->
                viewModel.updateSelectedStart(pxToTime(newPx))
            },
            isStart = true,
            onDragStart = onScrubStart,
            onDragEnd = onScrubEnd
        )

        TrimHandle(
            positionPx = endPx - handleWidthPx,
            onPositionChanged = { newPx ->
                viewModel.updateSelectedEnd(pxToTime(newPx + handleWidthPx))
            },
            isStart = false,
            onDragStart = onScrubStart,
            onDragEnd = onScrubEnd
        )

        // 4. Playhead
        val playheadPx = remember(state.playheadPositionMs, timeToPx) { 
            timeToPx(state.playheadPositionMs) 
        }
        
        Playhead(
            positionPx = playheadPx,
            onPositionChanged = { newPx ->
                viewModel.updatePlayheadPosition(pxToTime(newPx))
            },
            onDragStart = onScrubStart,
            onDragEnd = onScrubEnd
        )
    }
}
