package com.chopcut.ui.components.timeline

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutQuint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.chopcut.BuildConfig
import com.chopcut.ui.screen.VideoTimelineViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filter
import timber.log.Timber

/**
 * Presets de qualidade para thumbnails da timeline.
 *
 * MIN_QUALITY é o default — avarento em memória, cache e prefetch.
 */
private enum class ThumbnailQuality(
    val thumbHeight: Int
) {
    MIN_QUALITY(thumbHeight = 36)
}

/**
 * Timeline de thumbnails com extração MediaCodec via FastFrameExtractor.
 *
 * Responsabilidades:
 * - Extrair e exibir thumbnails (1 por segundo de vídeo)
 * - Mostrar playhead na posição atual
 * - Sincronizar scroll ↔ posição do player via callbacks
 *
 * NÃO é responsabilidade desta composable:
 * - Player de vídeo (TrimScreen tem VideoPreview)
 * - Lifecycle do player (TrimViewModel/PlayerManager)
 * - Seleção de qualidade (fixo em MIN_QUALITY)
 */
@Composable
fun VideoTimeline(
    modifier: Modifier = Modifier,
    videoUri: Uri,
    durationMs: Long,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    trimRanges: List<Pair<Long, Long>> = emptyList()
) {
    val context = LocalContext.current

    val thumbHeight = ThumbnailQuality.MIN_QUALITY.thumbHeight
    val thumbWidth = (thumbHeight * 4f / 3f).toInt()

    // Total de frames (1 frame por segundo)
    val totalFrames = (durationMs / 1000).toInt().coerceAtLeast(1)

    // VideoTimelineViewModel gerencia cache de sprites
    // Persiste entre navegações usando factory
    val viewModel: VideoTimelineViewModel = viewModel(
        factory = VideoTimelineViewModel.VideoTimelineViewModelFactory(LocalContext.current.applicationContext as android.app.Application)
    )
    val sprites by viewModel.sprites.collectAsStateWithLifecycle()
    val extractionProgress by viewModel.extractionProgress.collectAsStateWithLifecycle()
    val isReady by viewModel.isReady.collectAsStateWithLifecycle()

    // Carregar sprites ao mudar vídeo ou duração
    LaunchedEffect(videoUri, durationMs, thumbWidth, thumbHeight) {
        Timber.d("VideoTimeline LaunchedEffect: durationMs=$durationMs, thumbWidth=$thumbWidth, thumbHeight=$thumbHeight")
        if (durationMs > 0) {
            Timber.d("VideoTimeline: iniciando loadSprites para $videoUri")
            viewModel.loadSprites(videoUri, durationMs, thumbWidth, thumbHeight)
        } else {
            Timber.w("VideoTimeline: durationMs é 0, não carregando sprites")
        }
    }

    // LazyRow scroll state
    val listState = rememberLazyListState()

    // Scroll → seek callback (user arrasta a timeline)
    LaunchedEffect(listState, durationMs) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .filter { listState.isScrollInProgress }
            .collect { (firstIndex, _) ->
                if (durationMs > 0 && totalFrames > 0) {
                    onSeek((firstIndex.toLong() * durationMs) / totalFrames)
                }
            }
    }

    // Scroll programático: player → timeline sync
    LaunchedEffect(currentPositionMs, durationMs) {
        if (durationMs > 0 && !listState.isScrollInProgress) {
            val target = ((currentPositionMs * totalFrames) / durationMs).toInt()
                .coerceIn(0, (totalFrames - 1).coerceAtLeast(0))
            listState.animateScrollToItem(target)
        }
    }

    // UI
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(thumbHeight.dp)
    ) {
        val totalSprites = (totalFrames + 6) / 7
        
        // Contador de sprites
        val spritesLoaded = sprites.size
        val spritesRemaining = totalSprites - spritesLoaded
        val progressPercent = ((spritesLoaded.toFloat() / totalSprites.toFloat()) * 100).toInt()
        
        if (totalFrames > 0) {
            Text(
                text = "Sprites: $spritesLoaded/$totalSprites | Faltam: $spritesRemaining | $progressPercent%",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp)
            )
        }
        
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(totalFrames) { frameIndex ->
                val sprite = viewModel.getSprite(frameIndex)
                
                val isFromCache = sprite != null && viewModel.isFrameFromCache(frameIndex)
                
                Box(
                    modifier = Modifier
                        .size(width = thumbWidth.dp, height = thumbHeight.dp)
                        .clip(RoundedCornerShape(0.dp))
                        .background(Color.DarkGray)
                        .then(
                            if (isFromCache) {
                                Modifier.border(1.dp, Color.Cyan, RoundedCornerShape(0.dp))
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (sprite != null) {
                        val thumbBitmap = remember(frameIndex) {
                            val spriteIndex = frameIndex / 7
                            val col = (frameIndex % 7) % 7
                            val row = (frameIndex % 7) / 7
                            Bitmap.createBitmap(
                                sprite,
                                col * thumbWidth, row * thumbHeight,
                                thumbWidth, thumbHeight
                            )
                        }
                        Image(
                            bitmap = thumbBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Shimmer placeholder
                        val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
                        val shimmerAlpha by shimmerTransition.animateFloat(
                            initialValue = 0.15f,
                            targetValue = 0.3f,
                            animationSpec = infiniteRepeatable(
                                tween(800),
                                RepeatMode.Reverse
                            ),
                            label = "shimmerAlpha"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White.copy(alpha = shimmerAlpha))
                        )
                    }
                }
            }
        }

        // Playhead overlay (linha branca vertical)
        if (durationMs > 0) {
            val fraction = currentPositionMs.toFloat() / durationMs
            Box(
                Modifier
                    .offset { IntOffset((fraction * constraints.maxWidth).toInt(), 0) }
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(Color.White)
            )
        }
    }
}
