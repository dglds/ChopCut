package com.chopcut.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chopcut.ui.timeline.TimelineScrollController
import kotlinx.coroutines.flow.collect
import java.util.Locale

/**
 * Configurações da timeline V2 simplificada
 */
object TimelineConfigV2 {
    /** Altura da timeline em dp (thumbs quadradas) */
    val TIMELINE_HEIGHT = 64.dp

    /** Largura da linha do playhead em dp */
    val PLAYHEAD_LINE_WIDTH = 2.dp

    /** Cor do playhead */
    val PLAYHEAD_COLOR = Color.White
    
    /** Cor da borda dos frames */
    val FRAME_BORDER_COLOR = Color(0xFFFFA500) // Orange

    /** Cor do texto do frame */
    val FRAME_TEXT_COLOR = Color.White.copy(alpha = 0.7f)

    /** Cada thumb representa exatamente 1 segundo */
    const val THUMB_DURATION_MS: Long = 1000L
}

/**
 * Video timeline component V2 simplificado (Visualização apenas)
 * Renderiza blocos representando frames (segundos) com borda laranja.
 *
 * @param durationMs Video duration in milliseconds
 * @param currentPositionMs Current playback position in milliseconds for the timer
 * @param onSeek Callback when user scrubs/seeks
 * @param onScrubStart Callback when user starts dragging
 * @param onScrubEnd Callback when user stops dragging
 * @param modifier Modifier for the container
 */
@Composable
fun VideoTimelineV2(
    durationMs: Long,
    currentPositionMs: Long = 0L,
    frameRate: Int = 30,
    onSeek: (Long) -> Unit = {},
    onScrubStart: () -> Unit = {},
    onScrubEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Layout & Scrolling
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    var screenWidthPx by remember { mutableStateOf(0) }

    // Interaction State
    var isUserInteracting by remember { mutableStateOf(false) }

    // Thumbs QUADRADAS: largura = altura
    val thumbSizeDp = TimelineConfigV2.TIMELINE_HEIGHT
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }.toInt()
    
    // Controller para lógica de scrubbing
    val scrollController = remember(durationMs, frameRate, onSeek) {
        TimelineScrollController(
            durationMs = durationMs, 
            frameRate = frameRate, 
            onSeek = onSeek
        )
    }

    // Sincronizar Scroll com Player (Apenas se usuário NÃO estiver interagindo)
    LaunchedEffect(currentPositionMs, screenWidthPx, thumbSizePx, isUserInteracting) {
        if (!isUserInteracting && screenWidthPx > 0 && thumbSizePx > 0 && durationMs > 0) {
            val paddingPx = screenWidthPx / 2
            val (index, offset) = TimelineCalculator.calculateLazyListScroll(
                currentPositionMs,
                thumbSizePx,
                TimelineConfigV2.THUMB_DURATION_MS,
                paddingPx
            )
            // Usar scrollToItem para movimento imediato/sincronizado
            listState.scrollToItem(index, offset)
        }
    }

    // Monitorar Scroll do Usuário -> Atualizar Player
    LaunchedEffect(listState, screenWidthPx, thumbSizePx, isUserInteracting) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> // Usando collect para não perder frames
                if (isUserInteracting) {
                    scrollController.onScrollChanged(
                        index = index,
                        offset = offset,
                        thumbSizePx = thumbSizePx,
                        screenWidthPx = screenWidthPx
                    )
                }
            }
    }

    // Calcular número de blocos: 1 por segundo
    val count = if (durationMs > 0) {
         (durationMs.toFloat() / 1000f).toInt() + 1
    } else {
        0
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TimelineConfigV2.TIMELINE_HEIGHT)
            .background(Color.Black) // Fundo escuro para vídeo
            .onSizeChanged { screenWidthPx = it.width }
    ) {
        if (screenWidthPx > 0) {
            val paddingPx = screenWidthPx / 2
            val paddingDp = with(density) { paddingPx.toDp() }
            
            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            // User touched the timeline
                            isUserInteracting = true
                            onScrubStart()
                            
                            try {
                                waitForUpOrCancellation()
                            } finally {
                                // User released
                                isUserInteracting = false
                                onScrubEnd()
                            }
                        }
                    }
            ) {
                // Start Spacer (Half Screen) - para centralizar o inicio (0s)
                item { Spacer(Modifier.width(paddingDp)) }
                
                // Visual Blocks (Frames)
                items(count) { index ->
                    Box(
                        modifier = Modifier
                            .width(thumbSizeDp)
                            .height(thumbSizeDp)
                            .background(Color.DarkGray.copy(alpha = 0.3f))
                            .border(0.5.dp, TimelineConfigV2.FRAME_BORDER_COLOR),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = index.toString(),
                            color = TimelineConfigV2.FRAME_TEXT_COLOR,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp
                        )
                    }
                }
                
                // End Spacer (Half Screen) - para centralizar o fim
                item { Spacer(Modifier.width(paddingDp)) }
            }

            // Playhead (Center Fixed)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(TimelineConfigV2.PLAYHEAD_LINE_WIDTH)
                    .fillMaxHeight()
                    .background(TimelineConfigV2.PLAYHEAD_COLOR)
            )
        }
    }
}
