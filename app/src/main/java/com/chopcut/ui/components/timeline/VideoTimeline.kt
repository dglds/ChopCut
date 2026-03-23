package com.chopcut.ui.components.timeline

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chopcut.ui.screen.VideoTimelineViewModel
import timber.log.Timber

@Composable
fun VideoTimeline(
    modifier: Modifier = Modifier,
    videoUri: Uri,
    durationMs: Long,
    currentPositionMs: Long,
    onSeek: (Long) -> Unit,
    trimRanges: List<Pair<Long, Long>> = emptyList(),
    audioAmplitudes: List<Float> = emptyList(),
    showWaveform: Boolean = true,
    videoWidth: Int = 0,
    videoHeight: Int = 0
) {
    val context = LocalContext.current
    val thumbHeight = 56
    
    // Calcular largura baseada na proporção real do vídeo (fallback 16:9)
    val aspectRatio = if (videoWidth > 0 && videoHeight > 0) {
        videoWidth.toFloat() / videoHeight
    } else {
        16f / 9f
    }
    val thumbWidth = (thumbHeight * aspectRatio).toInt()
    val totalFrames = (durationMs / 1000).toInt().coerceAtLeast(1)
    
    val totalHeightDp = thumbHeight + (if (showWaveform) 40 else 0) + 8
    val amplitudesPerSecond = if (totalFrames > 0) (audioAmplitudes.size.toFloat() / totalFrames).coerceAtLeast(0f) else 0f

    val viewModel: VideoTimelineViewModel = viewModel(
        factory = VideoTimelineViewModel.VideoTimelineViewModelFactory(context.applicationContext as android.app.Application)
    )
    val sprites by viewModel.sprites.collectAsStateWithLifecycle()
    
    val listState = rememberLazyListState()

    LaunchedEffect(videoUri, durationMs, thumbWidth, thumbHeight) {
        if (durationMs > 0) {
            viewModel.loadSprites(videoUri, durationMs, thumbWidth, thumbHeight)
        }
    }

    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var lastTargetIndex by remember { mutableStateOf(-1) }

    LaunchedEffect(currentPositionMs, durationMs, isDragged) {
        if (isDragged) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .collect { firstIndex ->
                    if (durationMs > 0) {
                        onSeek((firstIndex.toLong() * durationMs) / totalFrames)
                    }
                }
        } else if (durationMs > 0 && !listState.isScrollInProgress) {
            val target = ((currentPositionMs * totalFrames) / durationMs).toInt()
                .coerceIn(0, (totalFrames - 1).coerceAtLeast(0))
            if (target != lastTargetIndex) {
                lastTargetIndex = target
                listState.scrollToItem(target)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeightDp.dp)
            .padding(vertical = 4.dp)
    ) {
        val totalSprites = (totalFrames + 6) / 7
        val spritesLoaded = sprites.size
        
        Text(
            text = "HD: $spritesLoaded/$totalSprites",
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp)
        )
        
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(totalFrames) { frameIndex ->
                val sprite = viewModel.getSprite(frameIndex)
                val isFromCache = sprite != null && viewModel.isFrameFromCache(frameIndex)
                
                val alphaAnim = remember { Animatable(0f) }
                LaunchedEffect(sprite != null) {
                    if (sprite != null) alphaAnim.animateTo(1f, tween(500))
                }

                Column(
                    modifier = Modifier.width(thumbWidth.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = thumbWidth.dp, height = thumbHeight.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF121212))
                            .border(
                                width = 1.dp,
                                color = if (isFromCache) Color.Cyan.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(6.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (sprite != null) {
                            val thumbBitmap = remember(frameIndex, sprite) {
                                val THUMBS_PER_SPRITE = 3
                                val col = frameIndex % THUMBS_PER_SPRITE
                                val sw = sprite.width / THUMBS_PER_SPRITE
                                val sh = sprite.height
                                val sx = (col * sw).coerceIn(0, (sprite.width - sw).coerceAtLeast(0))
                                Bitmap.createBitmap(sprite, sx, 0, sw.coerceAtLeast(1), sh.coerceAtLeast(1))
                            }
                            Image(
                                bitmap = thumbBitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().graphicsLayer(alpha = alphaAnim.value)
                            )
                        } else {
                            val shimmerAlpha by rememberInfiniteTransition(label = "").animateFloat(
                                initialValue = 0.05f, targetValue = 0.12f,
                                animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = ""
                            )
                            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = shimmerAlpha)))
                        }
                    }
                    
                    if (showWaveform) {
                        if (audioAmplitudes.isNotEmpty()) {
                            val startIndex = (frameIndex * amplitudesPerSecond).toInt().coerceIn(0, audioAmplitudes.size - 1)
                            val endIndex = ((frameIndex + 1) * amplitudesPerSecond).toInt().coerceAtMost(audioAmplitudes.size)
                            val frameAmps = if (startIndex < endIndex) audioAmplitudes.subList(startIndex, endIndex) else listOf(audioAmplitudes[startIndex])
                            
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier
                                    .width(thumbWidth.dp)
                                    .height(64.dp)
                                    .padding(top = 2.dp)
                                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                            ) {
                                if (frameAmps.isNotEmpty()) {
                                    val barSpacing = size.width / frameAmps.size.coerceAtLeast(1)
                                    val midY = size.height / 2f
                                    
                                    frameAmps.forEachIndexed { idx, amp ->
                                        val h = (amp * size.height).coerceIn(1f, size.height)
                                        drawRoundRect(
                                            color = Color(0xFF00E5FF),
                                            topLeft = androidx.compose.ui.geometry.Offset(idx * barSpacing + barSpacing * 0.15f, midY - h / 2f),
                                            size = androidx.compose.ui.geometry.Size((barSpacing * 0.7f).coerceAtLeast(1f), h),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
                                            alpha = 0.8f
                                        )
                                    }
                                }
                            }
                        } else {
                            // PLACEHOLDER PULSANTE (SHIMMER) ENQUANTO EXTRAI
                            val shimmerAlpha by rememberInfiniteTransition(label = "audio-shimmer").animateFloat(
                                initialValue = 0.05f, targetValue = 0.15f,
                                animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "audio-shimmer"
                            )
                            
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier
                                    .width(thumbWidth.dp)
                                    .height(64.dp)
                                    .padding(top = 2.dp)
                                    .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                            ) {
                                val barCount = 8
                                val barWidth = size.width / barCount
                                val midY = size.height / 2f
                                val placeholderH = 8f // Barras baixinhas
                                
                                for (i in 0 until barCount) {
                                    drawRoundRect(
                                        color = Color.White,
                                        topLeft = androidx.compose.ui.geometry.Offset(i * barWidth + barWidth * 0.2f, midY - placeholderH / 2f),
                                        size = androidx.compose.ui.geometry.Size(barWidth * 0.6f, placeholderH),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f, 1f),
                                        alpha = shimmerAlpha
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

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
