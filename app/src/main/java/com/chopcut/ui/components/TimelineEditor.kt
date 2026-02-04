package com.chopcut.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    extraContent: @Composable () -> Unit = {},
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
    
    val isInsideRange = remember(trimPosition, currentTimeMs) {
        trimPosition.isPositionInRange(currentTimeMs)
    }

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
            .background(Color(0xFF121212)),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. VIDEO PREVIEW
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(Color.Black)
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
            
            if (isInsideRange) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, Color.Red.copy(alpha = 0.5f))
                        .background(Color.Red.copy(alpha = 0.1f))
                )
            }

            // Play/Pause Button Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White
                    )
                }
            }
        }

        // 2. PASSIVE SEEKBAR (Visual Playback Bar) - Custom Sharp
        val progress = if (videoDurationMs > 0) currentTimeMs.toFloat() / videoDurationMs.toFloat() else 0f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color(0xFF424242))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(Color(0xFF64B5F6))
            )
        }

        // 3. TIMELINE RULER (Moved up, Gray BG, Pause on Scroll)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(Color(0xFF2A2A2A)) // Fundo Cinza (não preto)
        ) {
            val timelineWidth = constraints.maxWidth.toFloat()
            val centerOffset = timelineWidth / 2f
            val durationPx = (videoDurationMs / 1000f) * pxPerSecond

            val scrollableState = androidx.compose.foundation.gestures.rememberScrollableState { delta ->
                // PAUSE ON MANIPULATION
                if (isPlaying) {
                    isPlaying = false
                    exoPlayer.pause()
                }
                
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

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#BDBDBD") // Texto um pouco mais escuro que branco puro
                        textSize = 10.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.DEFAULT
                        isAntiAlias = true
                    }

                    val tickSpacing = pxPerSecond / 10f
                    val startTickIndex = (scrollOffsetPx / tickSpacing).toInt() - (centerOffset / tickSpacing).toInt() - 2
                    val endTickIndex = startTickIndex + (timelineWidth / tickSpacing).toInt() + 4
                    
                    val rulerTopY = 30.dp.toPx()

                    for (i in startTickIndex..endTickIndex) {
                        val tickTimeSec = i * 0.1f
                        if (tickTimeSec < 0 || tickTimeSec > videoDurationMs / 1000f) continue

                        val x = centerOffset + (tickTimeSec * pxPerSecond) - currentScroll
                        
                        val isSecond = i % 10 == 0
                        val isHalfSecond = i % 5 == 0 && !isSecond
                        
                        val tickHeight = when {
                            isSecond -> (size.height - rulerTopY) * 0.5f
                            isHalfSecond -> (size.height - rulerTopY) * 0.35f
                            else -> (size.height - rulerTopY) * 0.2f
                        }
                        
                        // Ticks mais escuros conforme solicitado
                        val tickColor = if (isSecond) Color(0xFF616161) else Color(0xFF424242)
                        val stroke = if (isSecond) 2.dp.toPx() else 1.dp.toPx()
                        
                        drawLine(
                            tickColor,
                            Offset(x, rulerTopY),
                            Offset(x, rulerTopY + tickHeight),
                            stroke
                        )

                        if (isSecond) {
                            drawIntoCanvas { canvas ->
                                canvas.nativeCanvas.drawText(
                                    (i / 10).toString(),
                                    x,
                                    rulerTopY + tickHeight + 12.dp.toPx(),
                                    textPaint
                                )
                            }
                        }
                    }

                    trimPosition.completeRanges.forEach { (start, end) ->
                        val startX = centerOffset + (start / 1000f) * pxPerSecond - currentScroll
                        val endX = centerOffset + (end / 1000f) * pxPerSecond - currentScroll
                        if (endX >= 0 && startX <= size.width) {
                            val rangeY = rulerTopY / 2
                            val isActive = currentTimeMs >= start && currentTimeMs <= end
                            
                            val rangeColor = if (isActive) Color.Red else Color(0xFFE91E63)
                            
                            drawLine(rangeColor, Offset(startX, rangeY), Offset(endX, rangeY), 8.dp.toPx())
                        }
                    }

                    if (trimPosition.isDraftMode) {
                        trimPosition.draftPosition?.let { startPos ->
                            val startX = centerOffset + (startPos / 1000f) * pxPerSecond - currentScroll
                            val playheadX = centerOffset
                            val minX = minOf(startX, playheadX)
                            val maxX = maxOf(startX, playheadX)
                            
                            val rangeY = rulerTopY / 2
                            drawLine(Color(0xFFFF9800), Offset(minX, rangeY), Offset(maxX, rangeY), 8.dp.toPx())
                        }
                    }

                    drawLine(
                        Color(0xFF64B5F6),
                        Offset(centerOffset, 0f),
                        Offset(centerOffset, size.height),
                        strokeWidth = 2.dp.toPx()
                    )

                    val gradientWidth = 60.dp.toPx()
                    // Left Gradient (Matching BG)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF2A2A2A), Color.Transparent),
                            startX = 0f,
                            endX = gradientWidth
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(gradientWidth, size.height)
                    )
                    // Right Gradient (Matching BG)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, Color(0xFF2A2A2A)),
                            startX = size.width - gradientWidth,
                            endX = size.width
                        ),
                        topLeft = Offset(size.width - gradientWidth, 0f),
                        size = Size(gradientWidth, size.height)
                    )
                }
            }
        }

        // 4. TIME DISPLAY
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = String.format("%02d:%02d.%02d", 
                    currentTimeMs / 60000, 
                    (currentTimeMs % 60000) / 1000, 
                    (currentTimeMs % 1000) / 10),
                color = if (isInsideRange) Color.Red else Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. RANGE LIST (Moved down)
        extraContent()
        
        Spacer(modifier = Modifier.weight(1f))

    }
}
