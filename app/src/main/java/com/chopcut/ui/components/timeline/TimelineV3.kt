package com.chopcut.ui.components.timeline

import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.unit.dp

import android.net.Uri
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

import androidx.compose.runtime.LaunchedEffect
import android.graphics.Bitmap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutQuint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale

import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.ensureActive
import android.util.LruCache
import androidx.compose.runtime.mutableIntStateOf
import com.chopcut.data.thumbnail.v3.FastFrameExtractor

/**
 * Presets de qualidade para thumbnails da timeline.
 *
 * MIN_QUALITY é o default — avarento em memória, cache e prefetch.
 * Todos usam FastExtractor (MediaCodec); a diferença está nos recursos alocados.
 */
private enum class ThumbnailQuality(
    val label: String,
    val thumbHeight: Int
) {
    MIN_QUALITY(
        label = "Performance",
        thumbHeight = 36        // ~3.4KB/frame RGB_565
    ),
    RECOMMENDED(
        label = "Balanced",
        thumbHeight = 56        // ~8.4KB/frame
    ),
    MAX_QUALITY(
        label = "Max Quality",
        thumbHeight = 90        // ~21KB/frame
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineV3(
    modifier: Modifier = Modifier,
    exoPlayer: ExoPlayer,
    videoUri: Uri
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedQuality by remember { mutableStateOf(ThumbnailQuality.MIN_QUALITY) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // State for video duration and dimensions
    var durationMs by remember { mutableStateOf(0L) }
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }

    // Thumbnail dimensions: ratio fixo 4:3 (w:h) para consistência na timeline
    val thumbHeight = selectedQuality.thumbHeight
    val thumbWidth = (thumbHeight * 4f / 3f).toInt()

    // Optimized Extractor
    val fastExtractor = remember(videoUri) { FastFrameExtractor(context, videoUri) }
    var isExtractorReady by remember { mutableStateOf(false) }

    // Prepare extractor when video dimensions or quality change
    LaunchedEffect(fastExtractor, videoWidth, videoHeight, selectedQuality) {
        if (videoWidth > 0 && videoHeight > 0) {
            isExtractorReady = false
            isExtractorReady = fastExtractor.prepare(width = thumbWidth, height = thumbHeight)
        }
    }

    // Reset player ao trocar qualidade
    LaunchedEffect(selectedQuality) {
        exoPlayer.seekTo(0)
        exoPlayer.play()
    }

    DisposableEffect(fastExtractor) {
        onDispose {
            fastExtractor.release()
        }
    }

    // Observer to get duration and video dimensions when player is ready
    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
            }

            override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
                if (size.width > 0 && size.height > 0) {
                    videoWidth = size.width
                    videoHeight = size.height
                }
            }
        }
        exoPlayer.addListener(listener)
        // Initial check in case it's already ready
        if (exoPlayer.playbackState == androidx.media3.common.Player.STATE_READY) {
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
        }
        // Initial check for video size
        val currentSize = exoPlayer.videoSize
        if (currentSize.width > 0 && currentSize.height > 0) {
            videoWidth = currentSize.width
            videoHeight = currentSize.height
        }
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Banco de caches: um por quality, todos persistem enquanto o vídeo for o mesmo
    val cacheBank = remember(videoUri) {
        mutableMapOf<ThumbnailQuality, ThumbnailLruCache>()
    }
    val thumbnails = cacheBank.getOrPut(selectedQuality) {
        ThumbnailLruCache(maxEntries = Int.MAX_VALUE)
    }

    // Cadência fixa de carregamento (ms entre frames visualmente aparecendo)
    val cadenceMs = 80L
    var extractionCount by remember { mutableIntStateOf(0) }

    DisposableEffect(videoUri) {
        onDispose {
            cacheBank.values.forEach { it.releaseAll() }
            cacheBank.clear()
        }
    }

    // LazyRow scroll state
    val listState = rememberLazyListState()

    // Coroutine de loading: sempre sequencial (0, 1, 2, 3...)
    // Scroll não influencia a ordem — carregamento é linear e previsível
    // Cadência fixa: cada frame aparece a cada ~cadenceMs, criando ritmo visual
    LaunchedEffect(isExtractorReady, selectedQuality, thumbWidth, durationMs) {
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> exoPlayer.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Quality Selector
        var dropdownExpanded by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Thumbnail Quality",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium
            )
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
                modifier = Modifier.width(180.dp)
            ) {
                OutlinedTextField(
                    value = selectedQuality.label,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    textStyle = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    ThumbnailQuality.entries.forEach { quality ->
                        DropdownMenuItem(
                            text = { Text(quality.label) },
                            onClick = {
                                selectedQuality = quality
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Video Player
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        controllerShowTimeoutMs = 3000
                        controllerAutoShow = true
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Horizontal Thumbnail Scroll
        val totalSeconds = (durationMs / 1000).toInt().coerceAtLeast(0)

        // Stats de extração
        if (totalSeconds > 0) {
            Text(
                text = "Thumbs: ${thumbnails.size()} / $totalSeconds",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
        ) {
            items(totalSeconds) { index ->
                val bitmap = thumbnails[index]

                // Animação sincopada: progress 0→1 triggered quando bitmap aparece no cache
                val progress = remember(videoUri, selectedQuality, index) { Animatable(0f) }

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
                    contentAlignment = androidx.compose.ui.Alignment.Center
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
    }
}
