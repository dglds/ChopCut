package com.chopcut.ui.timeline

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chopcut.ui.timeline.model.Thumbnail

/**
 * Renderiza uma faixa contínua de thumbnails do vídeo com scroll horizontal.
 * O scroll é controlado externamente baseado na posição do playhead.
 */
@Composable
fun ThumbnailStrip(
    thumbnails: List<Thumbnail>,
    modifier: Modifier = Modifier,
    thumbnailWidth: Dp = 60.dp,
    playheadPositionMs: Long = 0L,
    totalDurationMs: Long = 1L,
    onThumbnailClick: ((Long) -> Unit)? = null
) {
    val listState = rememberLazyListState()

    // Calcular qual thumbnail deve estar no centro baseado na posição do playhead
    val centerIndex by remember(playheadPositionMs, totalDurationMs, thumbnails.size) {
        derivedStateOf {
            if (thumbnails.isEmpty()) 0
            else {
                val progress = playheadPositionMs.toFloat() / totalDurationMs
                ((progress * thumbnails.size).toInt().coerceIn(0, thumbnails.size - 1))
            }
        }
    }

    // Rolar automaticamente para manter o thumbnail do playhead no centro
    LaunchedEffect(centerIndex) {
        if (thumbnails.isNotEmpty()) {
            listState.animateScrollToItem(centerIndex)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        items(items = thumbnails, key = { it.timeMs }) { thumb ->
            Box(
                modifier = Modifier
                    .width(thumbnailWidth)
                    .fillMaxHeight()
                    .background(Color.DarkGray)
                    .then(
                        if (onThumbnailClick != null) {
                            Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { onThumbnailClick(thumb.timeMs) }
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                thumb.bitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}
