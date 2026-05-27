package com.chopcut

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import android.view.ViewGroup
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause

@Composable
fun SeekbarProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .padding(horizontal = 16.dp)
            .clip(RectangleShape)
            .background(Color.White.copy(alpha = 0.05f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF00E5FF).copy(alpha = 0.6f),
                            Color(0xFF00E5FF)
                        )
                    )
                )
                .then(
                    Modifier.border(0.5.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), RectangleShape)
                )
        )
    }
}

@Composable
fun CurrentTimeDisplay(
    currentTimeMs: Long,
    isInsideRange: Boolean,
    modifier: Modifier = Modifier
) {
    val neonColor = if (isInsideRange) Color(0xFFFF5252) else Color(0xFF00E5FF)
    
    Box(
        modifier = modifier
            .padding(vertical = 8.dp)
            .clip(RectangleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .border(2.dp, Color.Green, RectangleShape)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            text = TimeUtils.formatTimeWithMillis(currentTimeMs),
            color = neonColor,
            fontSize = 22.sp,
            fontFamily = ChopCutMonoFont,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun VideoFileInfo(
    fileInfo: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RectangleShape)
            .background(Color.White.copy(alpha = 0.03f))
            .border(
                width = 0.5.dp, 
                color = Color.White.copy(alpha = 0.08f), 
                shape = RectangleShape
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RectangleShape)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.4f))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = fileInfo,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TimelineEditor(
    modifier: Modifier = Modifier,
    videoUri: Uri,
    durationMs: Long,
    currentPositionMs: Long,
    isPlaying: Boolean = false,
    onSeek: (Long) -> Unit,
    onScrubStart: () -> Unit = {},
    onScrubStop: (Long) -> Unit = {},
    trimRanges: List<Pair<Long, Long>> = emptyList(),
    audioAmplitudes: FloatArray = floatArrayOf(),
    showWaveform: Boolean = true,
    videoWidth: Int = 0,
    videoHeight: Int = 0,
    thumbnailViewModel: ThumbnailViewModel? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val pxPerSecond = with(density) { 60.dp.toPx() }
    val thumbHeightPx = with(density) { 56.dp.toPx() }
    val rulerHeightDp = 24.dp
    val rulerHeightPx = with(density) { rulerHeightDp.toPx() }
    val waveformHeightDp = 40.dp
    val waveformHeightPx = with(density) { waveformHeightDp.toPx() }

    val aspectRatio = if (videoWidth > 0 && videoHeight > 0) {
        videoWidth.toFloat() / videoHeight
    } else {
        16f / 9f
    }

    val thumbWidth = with(density) { 60.dp.roundToPx() }
    val thumbHeight = if (aspectRatio > 0) (thumbWidth / aspectRatio).toInt().coerceAtLeast(1) else with(density) { 40.dp.roundToPx() }
    val thumbsPerStrip = 10
    val stripManager = remember(thumbWidth, thumbHeight, thumbsPerStrip) {
        ThumbnailStripManager(context, thumbWidth, thumbHeight, thumbsPerStrip, adaptiveStrips = true)
    }

    // Observar tiras do ViewModel reativamente
    val stripsState = if (thumbnailViewModel != null) {
        thumbnailViewModel.strips.collectAsStateWithLifecycle()
    } else {
        remember { mutableStateOf(emptyMap<Int, Bitmap>()) }
    }
    val strips = stripsState.value

    val totalSegments = remember(durationMs) {
        if (durationMs > 0) stripManager.getSegmentCount(durationMs) else 0
    }

    // Shimmer sutil para miniaturas pendentes
    val infiniteTransition = rememberInfiniteTransition(label = "thumbnailShimmer")
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    var localPositionMs by remember { mutableLongStateOf(currentPositionMs) }
    var isScrubbingLocal by remember { mutableStateOf(false) }
    val smoothPositionMs = remember { mutableFloatStateOf(currentPositionMs.toFloat()) }

    // Sincronizar posição reativamente quando não estiver arrastando
    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            snapshotFlow { currentPositionMs }
                .collect { pos ->
                    smoothPositionMs.floatValue = pos.toFloat()
                    localPositionMs = pos
                }
        } else {
            smoothPositionMs.floatValue = currentPositionMs.toFloat()
            localPositionMs = currentPositionMs
        }
    }

    // Loop de scroll a 60 FPS
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var lastTimeNanos = 0L
            var accumulatedMs = 0f
            while (true) {
                withFrameNanos { frameTimeNanos ->
                    if (lastTimeNanos == 0L) {
                        lastTimeNanos = frameTimeNanos
                    } else {
                        val elapsedMs = (frameTimeNanos - lastTimeNanos) / 1_000_000f
                        lastTimeNanos = frameTimeNanos
                        accumulatedMs += elapsedMs
                        if (accumulatedMs >= 16.67f) {
                            val currentPos = smoothPositionMs.floatValue
                            val newSmoothPos = (currentPos + accumulatedMs).coerceIn(0f, durationMs.toFloat())
                            smoothPositionMs.floatValue = newSmoothPos
                            localPositionMs = newSmoothPos.toLong()
                            accumulatedMs = 0f
                        }
                    }
                }
            }
        }
    }

    // Sincronizar arraste manual
    LaunchedEffect(localPositionMs) {
        if (isScrubbingLocal) {
            smoothPositionMs.floatValue = localPositionMs.toFloat()
            onSeek(localPositionMs)
        }
    }

    val durationPx = (durationMs / 1000f) * pxPerSecond
    val scrollOffsetPx = (smoothPositionMs.floatValue / 1000f) * pxPerSecond

    // Amortecimento de arraste (30% mais preciso)
    val scrollableState = androidx.compose.foundation.gestures.rememberScrollableState { delta ->
        if (!isScrubbingLocal) {
            isScrubbingLocal = true
            onScrubStart()
        }
        val dampenedDelta = delta * 0.7f
        val deltaMs = (dampenedDelta / pxPerSecond * 1000).toLong()
        val newPos = (localPositionMs - deltaMs).coerceIn(0, durationMs)
        localPositionMs = newPos
        delta
    }

    // FlingBehavior suavizado (50% menos inércia para parar rápido)
    val defaultFling = androidx.compose.foundation.gestures.ScrollableDefaults.flingBehavior()
    val flingBehavior = remember(defaultFling) {
        object : androidx.compose.foundation.gestures.FlingBehavior {
            override suspend fun androidx.compose.foundation.gestures.ScrollScope.performFling(initialVelocity: Float): Float {
                return with(defaultFling) {
                    performFling(initialVelocity * 0.5f)
                }
            }
        }
    }

    LaunchedEffect(scrollableState) {
        snapshotFlow { scrollableState.isScrollInProgress }
            .collect { isScrolling ->
                if (!isScrolling && isScrubbingLocal) {
                    isScrubbingLocal = false
                    onScrubStop(localPositionMs)
                }
            }
    }

    val totalHeightDp = rulerHeightDp + 56.dp + (if (showWaveform && audioAmplitudes.isNotEmpty()) waveformHeightDp else 0.dp) + 8.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeightDp)
            .background(Color(0xFF161616))
            .border(width = 0.5.dp, color = Color.White.copy(alpha = 0.08f), shape = RectangleShape)
    ) {
        val timelineWidth = constraints.maxWidth.toFloat()
        val centerOffset = timelineWidth / 2f

        // Pré-carregamento de strips sob demanda baseado na viewport
        LaunchedEffect(videoUri, durationMs, scrollOffsetPx, timelineWidth) {
            if (durationMs == 0L || thumbnailViewModel == null) return@LaunchedEffect
            val currentSecond = ((scrollOffsetPx - centerOffset) / pxPerSecond).toInt().coerceAtLeast(0)
            val viewportWidthSeconds = (timelineWidth / pxPerSecond).toInt()
            thumbnailViewModel.loadVisibleStripsWithBuffer(
                uri = videoUri,
                durationMs = durationMs,
                currentSecond = currentSecond,
                viewportWidthSeconds = viewportWidthSeconds,
                bufferSize = 6
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .scrollable(
                    state = scrollableState,
                    orientation = Orientation.Horizontal,
                    flingBehavior = flingBehavior
                )
        ) {
            // 1. Régua de Ticks
            Canvas(modifier = Modifier.fillMaxWidth().height(rulerHeightDp)) {
                val width = size.width
                val tickSpacingSeconds = 1.0f
                val tickSpacingPx = pxPerSecond * tickSpacingSeconds
                val startTickIndex = ((scrollOffsetPx - centerOffset) / tickSpacingPx).toInt() - 1
                val endTickIndex = ((scrollOffsetPx - centerOffset + width) / tickSpacingPx).toInt() + 2

                for (t in startTickIndex..endTickIndex) {
                    val tickTimeMs = (t * tickSpacingSeconds * 1000).toLong()
                    if (tickTimeMs in 0..durationMs) {
                        val x = centerOffset + (t * tickSpacingPx) - scrollOffsetPx
                        val isMajor = t % 5 == 0
                        val tickHeight = if (isMajor) 10.dp.toPx() else 5.dp.toPx()
                        
                        drawLine(
                            color = Color.White.copy(alpha = if (isMajor) 0.35f else 0.15f),
                            start = Offset(x, 0f),
                            end = Offset(x, tickHeight),
                            strokeWidth = 1.dp.toPx()
                        )
                        
                        if (isMajor) {
                            drawIntoCanvas { canvas ->
                                val paint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    alpha = 95
                                    textSize = 8.dp.toPx()
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }
                                canvas.nativeCanvas.drawText(
                                    formatTime(tickTimeMs),
                                    x,
                                    tickHeight + 9.dp.toPx(),
                                    paint
                                )
                            }
                        }
                    }
                }
            }

            // 2. Ondas de Áudio Waveform (Ativas)
            if (showWaveform && audioAmplitudes.isNotEmpty()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(waveformHeightDp)
                        .padding(top = rulerHeightDp + 56.dp + 4.dp)
                ) {
                    val width = size.width
                    val ampCount = audioAmplitudes.size
                    val totalWidthMs = durationMs

                    val startAmpIndex = (((scrollOffsetPx - centerOffset) / pxPerSecond) * 1000f / (totalWidthMs.toFloat() / ampCount)).toInt().coerceIn(0, ampCount - 1)
                    val endAmpIndex = (((scrollOffsetPx - centerOffset + width) / pxPerSecond) * 1000f / (totalWidthMs.toFloat() / ampCount)).toInt().coerceIn(0, ampCount - 1)

                    for (i in startAmpIndex..endAmpIndex) {
                        val sampleTimeMs = (i * totalWidthMs) / ampCount
                        val x = centerOffset + (sampleTimeMs / 1000f * pxPerSecond) - scrollOffsetPx
                        val amplitude = audioAmplitudes[i]
                        val barHeight = amplitude * waveformHeightPx * 0.7f
                        val barWidth = 2.dp.toPx()
                        val yOffset = (waveformHeightPx - barHeight) / 2f
                        
                        drawRect(
                            color = Color.White.copy(alpha = 0.45f),
                            topLeft = Offset(x - barWidth / 2f, yOffset),
                            size = Size(barWidth, barHeight)
                        )
                    }
                }
            }

            // 3. Linha do Tempo (Thumbnails reais)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(top = rulerHeightDp + 2.dp)
            ) {
                val width = size.width

                val renderPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                    isAntiAlias = true
                    isDither = true
                }
                val bgPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#121212")
                    style = android.graphics.Paint.Style.FILL
                }
                val shimmerGradient = intArrayOf(
                    android.graphics.Color.parseColor("#121212"),
                    android.graphics.Color.parseColor("#1E1E1E"),
                    android.graphics.Color.parseColor("#2A2A2A"),
                    android.graphics.Color.parseColor("#3D3D3D"),
                    android.graphics.Color.parseColor("#2A2A2A"),
                    android.graphics.Color.parseColor("#1E1E1E"),
                    android.graphics.Color.parseColor("#121212")
                )
                val shimmerPositions = floatArrayOf(0f, 0.1f, 0.35f, 0.5f, 0.65f, 0.9f, 1f)
                val dstRect = android.graphics.Rect()

                drawContext.canvas.save()
                val clipEnd = centerOffset + (durationMs / 1000f * pxPerSecond) - scrollOffsetPx
                drawContext.canvas.clipRect(0f, 0f, clipEnd, thumbHeightPx)

                val startSecond = ((scrollOffsetPx - centerOffset) / pxPerSecond).toInt().coerceAtLeast(0)
                val endSecond = ((scrollOffsetPx - centerOffset + width) / pxPerSecond).toInt()

                val visibleSegmentIndices = mutableSetOf<Int>()
                for (sec in startSecond..endSecond) {
                    val segIdx = sec / thumbsPerStrip
                    visibleSegmentIndices.add(segIdx)
                }

                for (segIdx in visibleSegmentIndices) {
                    val strip = strips[segIdx]
                    val thumbsPerStripForThisSeg = stripManager.getThumbsPerStripForSegment(segIdx, totalSegments)
                    val startSec = stripManager.getSegmentStartSecond(segIdx, totalSegments)
                    val stripWidthPx = pxPerSecond * thumbsPerStripForThisSeg
                    val x = centerOffset + (startSec * pxPerSecond) - scrollOffsetPx

                    if (strip != null && !strip.isRecycled) {
                        val verticalOffset = (thumbHeightPx - thumbHeight) / 2f
                        val actualDurationSeconds = strip.width.toFloat() / thumbWidth
                        val actualStripVisualWidth = actualDurationSeconds * pxPerSecond
                        dstRect.set(
                            x.toInt(), verticalOffset.toInt(),
                            (x + actualStripVisualWidth).toInt(), (verticalOffset + thumbHeight).toInt()
                        )
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawBitmap(strip, null, dstRect, renderPaint)
                        }
                    } else {
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawRect(
                                x, 0f, x + stripWidthPx, thumbHeightPx,
                                bgPaint
                           )
                           val shimmerPaint = android.graphics.Paint().apply {
                               val w = stripWidthPx
                               val h = thumbHeightPx
                               val gradientSize = (w + h) * 0.8f
                               val phaseShift = segIdx * 0.02f
                               var adjustedProgress = shimmerProgress - phaseShift
                               val r = 3f
                               while (adjustedProgress < -1f) adjustedProgress += r
                               while (adjustedProgress > 2f) adjustedProgress -= r
                               val offsetX = adjustedProgress * (w + gradientSize) - gradientSize
                               val offsetY = adjustedProgress * (h + gradientSize) - gradientSize
                               shader = android.graphics.LinearGradient(
                                   x + offsetX, offsetY,
                                   x + offsetX + gradientSize, offsetY + gradientSize,
                                   shimmerGradient, shimmerPositions,
                                   android.graphics.Shader.TileMode.CLAMP
                               )
                           }
                           canvas.nativeCanvas.drawRect(
                               x, 0f, x + stripWidthPx, thumbHeightPx,
                               shimmerPaint
                           )
                       }
                   }
               }
               drawContext.canvas.restore()
           }
       }

       // Playhead Cyan centralizado
       Box(
           modifier = Modifier
               .align(Alignment.Center)
               .width(2.5.dp)
               .fillMaxHeight()
               .background(Color(0xFF00E5FF))
       )
   }
}

@OptIn(UnstableApi::class)
class PlayerManager(
    context: Context,
    videoUri: Uri,
    private val onDurationReady: (Long) -> Unit
) {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(videoUri))
        prepare()
        repeatMode = Player.REPEAT_MODE_OFF
        playWhenReady = false
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    private val _isSecurityError = MutableStateFlow(false)
    val isSecurityError: StateFlow<Boolean> = _isSecurityError.asStateFlow()

    val currentPosition: Long get() = exoPlayer.currentPosition

    val currentPositionFlow: Flow<Long> = flow {
        while (true) {
            emit(exoPlayer.currentPosition)
            delay(100)
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                val duration = exoPlayer.duration.coerceAtLeast(0L)
                onDurationReady(duration)
                _playerError.value = null
                _isSecurityError.value = false
            }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
        }

        override fun onPlayerError(error: PlaybackException) {
            val cause = error.cause
            val isPermError = cause?.toString()?.contains("SecurityException") == true ||
                    cause?.cause?.toString()?.contains("SecurityException") == true

            _isSecurityError.value = isPermError
            _playerError.value = if (isPermError) {
                "Permissão do arquivo expirou. Toque em 'Re-Localizar' para corrigir."
            } else {
                "Erro ao reproduzir: ${error.message ?: "Desconhecido"}"
            }
        }
    }

    init {
        exoPlayer.addListener(listener)
    }

    fun play() {
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    fun retry() {
        _playerError.value = null
        _isSecurityError.value = false
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun release() {
        exoPlayer.removeListener(listener)
        exoPlayer.release()
    }
}

@Composable
fun VideoPreview(
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    isInsideRange: Boolean,
    playerError: String?,
    isSecurityError: Boolean,
    currentTimeMs: Long = 0L,
    onRequestNewMedia: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onTogglePlayPause: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RectangleShape)
            .background(Color.Black)
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RectangleShape)
    ) {
        if (playerError != null) {
            VideoErrorState(
                error = playerError,
                isSecurityError = isSecurityError,
                onRequestNewMedia = onRequestNewMedia,
                onRetry = onRetry
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isInsideRange) {
                TrimRangeOverlay()
            }

            // Timer Overlay (Garantido estar no topo do vídeo)
            Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.TopCenter) {
                CurrentTimeDisplay(
                    currentTimeMs = currentTimeMs,
                    isInsideRange = isInsideRange
                )
            }

            VideoControls(
                isInsideRange = isInsideRange,
                isPlaying = isPlaying,
                onTogglePlayPause = onTogglePlayPause
            )
        }
    }
}

@Composable
private fun VideoErrorState(
    error: String,
    isSecurityError: Boolean,
    onRequestNewMedia: (() -> Unit)?,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = Color.Red,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            color = Color.White,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp)
        )

        if (isSecurityError && onRequestNewMedia != null) {
            Button(
                onClick = onRequestNewMedia,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text("Re-Localizar Arquivo (Necessário)")
            }
        } else {
            Button(onClick = onRetry) {
                Text("Tentar Novamente")
            }
        }

        if (!isSecurityError && onRequestNewMedia != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onRequestNewMedia) {
                Text("Localizar Arquivo")
            }
        }
    }
}

@Composable
private fun TrimRangeOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(Color.Red.copy(alpha = 0.1f))

        val stripeSpacing = 28.dp.toPx()
        val stripeWidth = 6.dp.toPx()
        val stripeColor = Color.Red.copy(alpha = 0.25f)
        val maxDim = size.width + size.height
        var offset = -size.height
        while (offset < maxDim) {
            drawLine(
                color = stripeColor,
                start = Offset(offset, size.height),
                end = Offset(offset + size.height, 0f),
                strokeWidth = stripeWidth
            )
            offset += stripeSpacing
        }

        drawRect(
            color = Color.Red.copy(alpha = 0.5f),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
private fun VideoControls(
    isInsideRange: Boolean,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (isInsideRange) 0.1f else 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        if (isInsideRange) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Trecho será removido",
                tint = Color.Red.copy(alpha = 0.5f),
                modifier = Modifier.size(72.dp)
            )
        } else {
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RectangleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White
                )
            }
        }
    }
}
