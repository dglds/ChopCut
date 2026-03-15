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
import androidx.compose.foundation.background
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
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
import com.chopcut.data.thumbnail.v3.FastFrameExtractor
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filter

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
 * LruCache de Bitmaps que notifica o Compose sobre mudanças.
 *
 * Bitmaps evicted são reciclados para liberar memória nativa.
 */
private class ThumbnailLruCache(maxEntries: Int) {
    private val cache = object : LruCache<Int, Bitmap>(maxEntries) {
        override fun sizeOf(key: Int, value: Bitmap) = 1
        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted) {
                oldValue.recycle()
                timber.log.Timber.d("LRU evicted frame %d", key)
            }
        }
    }
    private val version = mutableIntStateOf(0)

    operator fun get(key: Int): Bitmap? {
        version.intValue
        return cache.get(key)
    }

    operator fun set(key: Int, bitmap: Bitmap) {
        cache.put(key, bitmap)
        version.intValue++
    }

    fun containsKey(key: Int): Boolean {
        version.intValue
        return cache.get(key) != null
    }

    fun size(): Int {
        version.intValue
        return cache.size()
    }

    fun releaseAll() {
        cache.snapshot().values.forEach { it.recycle() }
        cache.evictAll()
        timber.log.Timber.d("LRU released all (%d frames)", cache.putCount())
    }
}

/**
 * Extrai um frame e armazena no cache.
 */
private suspend fun extractAndCache(
    extractor: FastFrameExtractor,
    cache: ThumbnailLruCache,
    index: Int,
    onExtracted: () -> Unit
) {
    val timeUs = index * 1_000_000L
    val bitmap = extractor.getFrameAt(timeUs)
    if (bitmap != null) {
        cache[index] = bitmap
        onExtracted()
    }
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

    // Guard sub-second videos
    val totalSeconds = (durationMs / 1000).toInt().coerceAtLeast(1)

    // Optimized Extractor
    val fastExtractor = remember(videoUri) { FastFrameExtractor(context, videoUri) }
    var isExtractorReady by remember { mutableStateOf(false) }

    // Prepare extractor — sem dependência de videoWidth/Height
    LaunchedEffect(fastExtractor, thumbWidth, thumbHeight, durationMs) {
        if (durationMs > 0) {
            isExtractorReady = false
            isExtractorReady = fastExtractor.prepare(width = thumbWidth, height = thumbHeight)
        }
    }

    DisposableEffect(fastExtractor) {
        onDispose {
            fastExtractor.release()
        }
    }

    // Cache de thumbnails
    val thumbnails = remember(videoUri) {
        ThumbnailLruCache(maxEntries = Int.MAX_VALUE)
    }

    // Cadência fixa de carregamento (ms entre frames visualmente aparecendo)
    val cadenceMs = 80L
    var extractionCount by remember { mutableIntStateOf(0) }

    DisposableEffect(videoUri) {
        onDispose {
            thumbnails.releaseAll()
        }
    }

    // LazyRow scroll state
    val listState = rememberLazyListState()

    // Coroutine de loading: sempre sequencial (0, 1, 2, 3...)
    // Scroll não influencia a ordem — carregamento é linear e previsível
    LaunchedEffect(isExtractorReady, thumbWidth, durationMs) {
        if (!isExtractorReady) return@LaunchedEffect
        extractionCount = 0

        val totalSecs = (durationMs / 1000).toInt().coerceAtLeast(0)
        if (totalSecs == 0) return@LaunchedEffect

        for (index in 0 until totalSecs) {
            ensureActive()
            if (thumbnails.containsKey(index)) continue

            val frameStart = System.nanoTime()
            extractAndCache(fastExtractor, thumbnails, index) { extractionCount++ }

            // Ritmo fixo: se a extração foi mais rápida que o cadence, espera
            val elapsedMs = (System.nanoTime() - frameStart) / 1_000_000
            val remaining = cadenceMs - elapsedMs
            if (remaining > 0) {
                kotlinx.coroutines.delay(remaining)
            }
        }
    }

    // Scroll → seek callback (user arrasta a timeline)
    LaunchedEffect(listState, durationMs) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .filter { listState.isScrollInProgress }
            .collect { (firstIndex, _) ->
                if (durationMs > 0 && totalSeconds > 0) {
                    onSeek((firstIndex.toLong() * durationMs) / totalSeconds)
                }
            }
    }

    // Scroll programático: player → timeline sync
    LaunchedEffect(currentPositionMs, durationMs) {
        if (durationMs > 0 && !listState.isScrollInProgress) {
            val target = ((currentPositionMs * totalSeconds) / durationMs).toInt()
                .coerceIn(0, (totalSeconds - 1).coerceAtLeast(0))
            listState.animateScrollToItem(target)
        }
    }

    // UI
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(thumbHeight.dp)
    ) {
        // Debug stats (apenas em debug builds)
        if (BuildConfig.DEBUG && totalSeconds > 0) {
            Text(
                text = "Thumbs: ${thumbnails.size()} / $totalSeconds",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
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
            items(totalSeconds) { index ->
                val bitmap = thumbnails[index]

                // Animação sincopada: progress 0→1 triggered quando bitmap aparece no cache
                val progress = remember(videoUri, index) { Animatable(0f) }

                LaunchedEffect(bitmap != null) {
                    if (bitmap != null && progress.value == 0f) {
                        val variant = index % 3
                        when (variant) {
                            0 -> progress.animateTo(
                                1f,
                                animationSpec = spring(
                                    dampingRatio = 0.6f,
                                    stiffness = 800f
                                )
                            )
                            1 -> progress.animateTo(
                                1f,
                                animationSpec = tween(
                                    durationMillis = 350,
                                    easing = FastOutSlowInEasing
                                )
                            )
                            2 -> progress.animateTo(
                                1f,
                                animationSpec = tween(
                                    durationMillis = 500,
                                    easing = EaseOutQuint
                                )
                            )
                        }
                    }
                }

                // Derivar scale, alpha e offsetY do progress
                val variant = index % 3
                val p = progress.value
                val scale = when (variant) {
                    0 -> 0.8f + 0.2f * p    // pop: 0.8→1.0
                    1 -> 0.92f + 0.08f * p   // ease: 0.92→1.0
                    else -> 0.95f + 0.05f * p // drift: 0.95→1.0
                }
                val alpha = p
                val offsetY = when (variant) {
                    0 -> 6f * (1f - p)    // pop: +6dp→0
                    1 -> 3f * (1f - p)    // ease: +3dp→0
                    else -> 2f * (1f - p) // drift: +2dp→0
                }

                Box(
                    modifier = Modifier
                        .size(width = thumbWidth.dp, height = thumbHeight.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .offset(y = offsetY.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
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
