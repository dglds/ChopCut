package com.chopcut.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
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
fun TimelinePlayer(
    videoUri: Uri,
    modifier: Modifier = Modifier,
    ranges: List<TrimRangeData> = emptyList(),
    selectedRangeId: String? = null,
    onRangesChange: (List<TrimRangeData>) -> Unit = {},
    onRangeSelect: (String?) -> Unit = {},
    onRangeDelete: (String) -> Unit = {}
) {
    val context = LocalContext.current
    
    // Cores para os ranges (usado quando criar novo range)
    val rangeColors = listOf(
        Color(0xFF4CAF50), // Verde
        Color(0xFF2196F3), // Azul
        Color(0xFFFF9800), // Laranja
        Color(0xFF9C27B0), // Roxo
        Color(0xFFF44336), // Vermelho
    )

    // --- Configuration Constants ---
    val density = LocalDensity.current
    val pxPerSecond = remember(density) { with(density) { 60.dp.toPx() } }
    val tickHeightMajor = remember(density) { with(density) { 16.dp.toPx() } } 
    val tickHeightMinor = remember(density) { with(density) { 8.dp.toPx() } }
    val playheadColor = MaterialTheme.colorScheme.primary

    // --- State ---
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = false 
            }
    }

    var videoDurationMs by remember { mutableStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    
    var scrollOffsetPx by remember { mutableFloatStateOf(0f) }

    val currentTimeMs by remember {
        derivedStateOf {
            if (pxPerSecond > 0) {
                ((scrollOffsetPx / pxPerSecond) * 1000).toLong().coerceIn(0, videoDurationMs)
            } else 0L
        }
    }

    // --- Effects ---

    DisposableEffect(videoUri) {
        val mediaItem = MediaItem.fromUri(videoUri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val dur = exoPlayer.duration
                    if (dur > 0) videoDurationMs = dur
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

    // Sync: Player -> Scroll (Playback Loop)
    LaunchedEffect(isDragging, isPlaying) {
        if (!isDragging && isPlaying) {
            while (isActive) {
                val currentPos = exoPlayer.currentPosition
                scrollOffsetPx = (currentPos / 1000f) * pxPerSecond
                delay(16)
            }
        }
    }

    // Sync: Scroll -> Player (Scrubbing)
    LaunchedEffect(currentTimeMs) {
        if (isDragging) {
             val playerTime = exoPlayer.currentPosition
             val diff = kotlin.math.abs(playerTime - currentTimeMs)
             if (diff > 30) { 
                exoPlayer.seekTo(currentTimeMs)
             }
        }
    }

    // --- Layout ---
    Column(modifier = modifier) {
        
        // VIDEO AREA
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
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

            // Play/Pause Overlay
            Box(modifier = Modifier.fillMaxSize()) {
                IconButton(
                    onClick = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.PlayArrow else Icons.Default.PlayArrow, 
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // Big text showing Time with smaller milliseconds
                val annotatedTime = androidx.compose.ui.text.buildAnnotatedString {
                    val min = (currentTimeMs / 60000).toInt()
                    val sec = ((currentTimeMs % 60000) / 1000).toInt()
                    val ms = (currentTimeMs % 1000).toInt()
                    
                    append(String.format("%02d:%02d", min, sec))
                    
                    pushStyle(androidx.compose.ui.text.SpanStyle(
                        fontSize = MaterialTheme.typography.titleMedium.fontSize,
                        baselineShift = androidx.compose.ui.text.style.BaselineShift(0.2f)
                    ))
                    append(String.format(".%03d", ms))
                    pop()
                }

                Text(
                    text = annotatedTime,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                )
            }
        }

        // ESPAÇO entre player e timeline
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .background(Color.Black)
        )

        // TIMELINE AREA COM RANGES
        BoxWithConstraints(
            modifier = Modifier
                .height(120.dp)
                .fillMaxWidth()
                .background(Color(0xFFE0E0E0)) // Slightly darker for contrast against white UI
        ) {
            val timelineWidth = constraints.maxWidth.toFloat()
            val centerOffset = timelineWidth / 2f
            
            // Total width of the video in pixels
            val durationPx = (videoDurationMs / 1000f) * pxPerSecond
            
            val scrollableState = rememberScrollableState { delta ->
                val oldOffset = scrollOffsetPx
                val newOffset = (oldOffset - delta).coerceIn(0f, durationPx)
                scrollOffsetPx = newOffset
                oldOffset - newOffset 
            }

            LaunchedEffect(scrollableState.isScrollInProgress) {
                isDragging = scrollableState.isScrollInProgress
                if (isDragging) {
                    exoPlayer.pause()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollable(
                        orientation = Orientation.Horizontal,
                        state = scrollableState
                    )
            ) {
                val primaryColor = Color(0xFF424242) // Darker Grey for main ticks
                val tickColor = Color(0xFF757575)   // Medium Grey
                val minorTickColor = Color(0xFF9E9E9E) // Light Grey
                
                // Colors for "Out of bounds" areas
                val neutralBgColor = Color(0xFFD6D6D6) 
                val stripeColor = Color(0xFFBDBDBD)

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val currentScroll = scrollOffsetPx
                    
                    val videoStartX = -currentScroll + centerOffset
                    val videoEndX = videoStartX + durationPx

                    // 0. Draw "Low Relief" (Sunken) Effect - Inner Shadows
                    // Top Shadow
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.15f), Color.Transparent),
                            startY = 0f,
                            endY = size.height * 0.2f
                        )
                    )
                    // Bottom Shadow
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.1f)),
                            startY = size.height * 0.8f,
                            endY = size.height
                        )
                    )

                    // 1. Draw Neutral Areas (Striped Background)
                    // Left Neutral Area
                    if (videoStartX > 0) {
                        drawRect(
                            color = neutralBgColor,
                            topLeft = Offset(0f, 0f),
                            size = androidx.compose.ui.geometry.Size(videoStartX, size.height)
                        )
                        clipRect(left = 0f, top = 0f, right = videoStartX, bottom = size.height) {
                            val stripeSpacing = 15.dp.toPx()
                            var x = -size.height 
                            while (x < videoStartX) {
                                drawLine(
                                    color = stripeColor,
                                    start = Offset(x, size.height),
                                    end = Offset(x + size.height, 0f),
                                    strokeWidth = 3.dp.toPx()
                                )
                                x += stripeSpacing
                            }
                        }
                        // Vertical Divider Line for start
                        drawLine(
                            color = Color.Black.copy(alpha = 0.2f),
                            start = Offset(videoStartX, 0f),
                            end = Offset(videoStartX, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Right Neutral Area
                    if (videoEndX < size.width) {
                        drawRect(
                            color = neutralBgColor,
                            topLeft = Offset(videoEndX, 0f),
                            size = androidx.compose.ui.geometry.Size(size.width - videoEndX, size.height)
                        )
                         clipRect(left = videoEndX, top = 0f, right = size.width, bottom = size.height) {
                            val stripeSpacing = 15.dp.toPx()
                            var x = videoEndX - size.height
                            while (x < size.width) {
                                drawLine(
                                    color = stripeColor,
                                    start = Offset(x, size.height),
                                    end = Offset(x + size.height, 0f),
                                    strokeWidth = 3.dp.toPx()
                                )
                                x += stripeSpacing
                            }
                        }
                        // Vertical Divider Line for end
                        drawLine(
                            color = Color.Black.copy(alpha = 0.2f),
                            start = Offset(videoEndX, 0f),
                            end = Offset(videoEndX, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // 2. Draw Ticks (Top Aligned)
                    val startVisibleTimeSec = ((0 - centerOffset + currentScroll) / pxPerSecond).toInt()
                    val endVisibleTimeSec = ((size.width - centerOffset + currentScroll) / pxPerSecond).toInt() + 1
                    
                    val durationSec = (videoDurationMs / 1000).toInt()
                    val startLoop = startVisibleTimeSec.coerceAtLeast(0)
                    val endLoop = endVisibleTimeSec.coerceAtMost(durationSec + 1)

                    for (sec in startLoop..endLoop) {
                        val xPos = (sec * pxPerSecond) - currentScroll + centerOffset
                        
                        // Major Tick
                        drawLine(
                            color = if (sec % 5 == 0) primaryColor else tickColor,
                            start = Offset(xPos, 0f),
                            end = Offset(xPos, tickHeightMajor),
                            strokeWidth = if (sec % 5 == 0) 2.dp.toPx() else 1.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        
                        // Minor Ticks (0.5s)
                        val xPosMid = xPos + (pxPerSecond / 2)
                        val timeMid = sec + 0.5
                        if (timeMid * 1000 <= videoDurationMs) {
                            drawLine(
                                color = minorTickColor,
                                start = Offset(xPosMid, 0f),
                                end = Offset(xPosMid, tickHeightMinor),
                                strokeWidth = 1.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                // Playhead (Fixed Center Line)
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(2.dp)
                        .height(120.dp) 
                        .background(playheadColor)
                )
                
                // RANGES OVERLAY
                val rangesWithSelection = ranges.map { it.copy(isSelected = it.id == selectedRangeId) }
                TimelineRangesOverlay(
                    ranges = rangesWithSelection,
                    currentTimeMs = currentTimeMs,
                    videoDurationMs = videoDurationMs,
                    pxPerSecond = pxPerSecond,
                    scrollOffsetPx = scrollOffsetPx,
                    timelineWidth = timelineWidth,
                    onRangeUpdate = { id, newStart, newEnd ->
                        val updatedRanges = ranges.map { 
                            if (it.id == id) it.copy(startMs = newStart, endMs = newEnd) 
                            else it 
                        }
                        onRangesChange(updatedRanges)
                    },
                    onRangeSelect = { id ->
                        onRangeSelect(id)
                    },
                    onRangeDelete = { id ->
                        onRangeDelete(id)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}


