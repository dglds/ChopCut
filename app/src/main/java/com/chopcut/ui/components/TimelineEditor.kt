package com.chopcut.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(UnstableApi::class)
@Composable
fun TimelineEditor(
    videoUri: Uri,
    trimPosition: TrimPosition,
    currentPosition: Long,
    onPositionChange: (Long) -> Unit,
    onAddPosition: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = LocalDensity.current
    val pxPerSecond = remember { with(density) { 60.dp.toPx() } }
    var scrollOffsetPx by remember { mutableFloatStateOf(0f) }
    var videoDurationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = false
        }
    }

    var currentTimeMs by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    videoDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying, scrollOffsetPx) {
        if (!isPlaying) return@LaunchedEffect
        while (isActive) {
            val pos = exoPlayer.currentPosition
            scrollOffsetPx = (pos / 1000f) * pxPerSecond
            currentTimeMs = if (pxPerSecond > 0) {
                ((scrollOffsetPx / pxPerSecond) * 1000).toLong().coerceIn(0, videoDurationMs)
            } else 0L
            onPositionChange(currentTimeMs)
            delay(16)
        }
    }

    LaunchedEffect(scrollOffsetPx) {
        if (!isPlaying) {
            currentTimeMs = if (pxPerSecond > 0) {
                ((scrollOffsetPx / pxPerSecond) * 1000).toLong().coerceIn(0, videoDurationMs)
            } else 0L
            onPositionChange(currentTimeMs)
            val playerPos = (scrollOffsetPx / pxPerSecond) * 1000
            if (kotlin.math.abs(exoPlayer.currentPosition - playerPos) > 30) {
                exoPlayer.seekTo(playerPos.toLong())
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Text(
                text = String.format("%02d:%02d.%03d",
                    currentTimeMs / 60000,
                    (currentTimeMs % 60000) / 1000,
                    currentTimeMs % 1000),
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(Color(0xFFE0E0E0))
        ) {
            val timelineWidth = constraints.maxWidth.toFloat()
            val centerOffset = timelineWidth / 2f
            val durationPx = (videoDurationMs / 1000f) * pxPerSecond

            val scrollableState = androidx.compose.foundation.gestures.rememberScrollableState { delta ->
                val newOffset = (scrollOffsetPx - delta).coerceIn(0f, durationPx)
                val consumed = scrollOffsetPx - newOffset
                scrollOffsetPx = newOffset
                consumed
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollable(scrollableState, Orientation.Horizontal)
            ) {
                val currentScroll = scrollOffsetPx
                val videoStartX = -currentScroll + centerOffset
                val videoEndX = videoStartX + durationPx

                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (videoStartX > 0) {
                        drawRect(Color(0xFFD6D6D6), Offset(0f, 0f), Size(videoStartX, size.height))
                    }
                    if (videoEndX < size.width) {
                        drawRect(Color(0xFFD6D6D6), Offset(videoEndX, 0f), Size(size.width - videoEndX, size.height))
                    }

                    val tickSpacing = pxPerSecond
                    var x = videoStartX
                    var tickMs = 0L
                    while (x < size.width && tickMs <= videoDurationMs) {
                        val color = if (tickMs % 10000 == 0L) Color(0xFF424242) else Color(0xFF757575)
                        val height = if (tickMs % 10000 == 0L) 16.dp.toPx() else 8.dp.toPx()
                        drawLine(color, Offset(x, size.height - height), Offset(x, size.height), 2f)
                        x += tickSpacing
                        tickMs += 1000
                    }

                    val timelineCenterY = size.height / 2

                    trimPosition.completeRanges.forEach { (start, end) ->
                        val startX = centerOffset + (start / 1000f) * pxPerSecond - currentScroll
                        val endX = centerOffset + (end / 1000f) * pxPerSecond - currentScroll
                        if (endX >= 0 && startX <= size.width) {
                            val rangeWidth = endX - startX
                            if (rangeWidth > 0) {
                                drawRect(
                                    Color(0xFFE91E63).copy(alpha = 0.3f),
                                    Offset(startX, timelineCenterY - 8.dp.toPx()),
                                    Size(rangeWidth, 16.dp.toPx())
                                )
                            }
                            drawLine(
                                Color(0xFFE91E63),
                                Offset(startX, 0f),
                                Offset(endX, 0f),
                                strokeWidth = 4.dp.toPx()
                            )
                        }
                    }

                    if (trimPosition.isDraftMode) {
                        trimPosition.draftPosition?.let { startPos ->
                            val startX = centerOffset + (startPos / 1000f) * pxPerSecond - currentScroll
                            val playheadX = centerOffset
                            val minX = minOf(startX, playheadX)
                            val maxX = maxOf(startX, playheadX)
                            val draftWidth = maxX - minX

                            if (draftWidth > 0) {
                                drawRect(
                                    Color(0xFFFF9800).copy(alpha = 0.4f),
                                    Offset(minX, timelineCenterY - 8.dp.toPx()),
                                    Size(draftWidth, 16.dp.toPx())
                                )
                                drawLine(
                                    Color(0xFFFF9800),
                                    Offset(minX, 0f),
                                    Offset(maxX, 0f),
                                    strokeWidth = 4.dp.toPx()
                                )
                            }
                        }
                    }

                    val playheadColor = when {
                        trimPosition.isPositionInRange(currentPosition) -> Color(0xFFE91E63)
                        trimPosition.isDraftMode -> Color(0xFFFF9800)
                        else -> Color(0xFF4CAF50)
                    }

                    drawLine(
                        playheadColor,
                        Offset(centerOffset, 0f),
                        Offset(centerOffset, size.height),
                        strokeWidth = 3.dp.toPx()
                    )
                }
            }
        }
    }
}
