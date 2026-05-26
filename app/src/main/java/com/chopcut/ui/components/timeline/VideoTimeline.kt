package com.chopcut.ui.components.timeline

import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chopcut.ui.viewmodel.VideoTimelineViewModel
import com.chopcut.ui.components.waveform.WaveformRenderer
import com.chopcut.ui.state.TimelineScrollMode
import com.chopcut.util.TimeUtils
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.runtime.mutableLongStateOf

@Composable
fun VideoTimeline(
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
    videoHeight: Int = 0
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

    val thumbWidthPx = pxPerSecond

    val viewModel: VideoTimelineViewModel = viewModel(
        factory = VideoTimelineViewModel.VideoTimelineViewModelFactory(context.applicationContext as android.app.Application)
    )
    val sprites by viewModel.sprites.collectAsStateWithLifecycle()
    val isReady by viewModel.isReady.collectAsStateWithLifecycle()

    val shimmerTransition = rememberInfiniteTransition(label = "skeleton")
    val shimmerAlpha by shimmerTransition.animateFloat(
        initialValue = 0.04f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    LaunchedEffect(videoUri, durationMs) {
        if (durationMs > 0) {
            val h = with(density) { 56.dp.roundToPx() }
            val w = (h * aspectRatio).toInt()
            viewModel.loadSprites(videoUri, durationMs, w, h)
        }
    }

    val waveColor = remember { Color.White.copy(alpha = 0.55f) }

    var localPositionMs by remember { mutableLongStateOf(currentPositionMs) }
    var isScrubbingLocal by remember { mutableStateOf(false) }

    val scrollMode by remember {
        derivedStateOf {
            when {
                isScrubbingLocal -> TimelineScrollMode.MANUAL
                isPlaying -> TimelineScrollMode.AUTO
                else -> TimelineScrollMode.IDLE
            }
        }
    }

    val smoothPositionMs = remember { mutableFloatStateOf(currentPositionMs.toFloat()) }

    when (scrollMode) {
        TimelineScrollMode.IDLE -> {
            LaunchedEffect(currentPositionMs) {
                smoothPositionMs.floatValue = currentPositionMs.toFloat()
            }
        }
        TimelineScrollMode.AUTO -> {
            LaunchedEffect(isPlaying) {
                if (!isPlaying) return@LaunchedEffect
                val startPositionMs = currentPositionMs
                val startTimeNanos = System.nanoTime()
                while (true) {
                    withFrameNanos { frameTimeNanos ->
                        val elapsedMs = (frameTimeNanos - startTimeNanos) / 1_000_000f
                        smoothPositionMs.floatValue = (startPositionMs + elapsedMs).coerceIn(0f, durationMs.toFloat())
                    }
                }
            }
        }
        TimelineScrollMode.MANUAL -> {
            LaunchedEffect(localPositionMs) {
                smoothPositionMs.floatValue = localPositionMs.toFloat()
            }
        }
    }

    val scrollableState = androidx.compose.foundation.gestures.rememberScrollableState { delta ->
        if (!isScrubbingLocal) {
            isScrubbingLocal = true
            onScrubStart()
        }
        val deltaMs = (delta / pxPerSecond * 1000).toLong()
        val newPos = (localPositionMs - deltaMs).coerceIn(0, durationMs)
        localPositionMs = newPos
        delta
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeightDp)
            .background(Color.Black.copy(alpha = 0.2f))
            .scrollable(
                state = scrollableState,
                orientation = Orientation.Horizontal
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LegacyTimelineRuler(
                smoothPositionState = smoothPositionMs,
                durationMs = durationMs,
                pixelPerSecond = pxPerSecond,
                modifier = Modifier.fillMaxWidth().height(rulerHeightDp)
            )

            TimelineThumbnails(
                smoothPositionState = smoothPositionMs,
                durationMs = durationMs,
                pixelPerSecond = pxPerSecond,
                thumbHeightPx = thumbHeightPx,
                viewModel = viewModel,
                isReady = isReady,
                shimmerAlpha = shimmerAlpha,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            )

            if (showWaveform && audioAmplitudes.isNotEmpty()) {
                LegacyTimelineWaveform(
                    amplitudes = audioAmplitudes,
                    durationMs = durationMs,
                    height = waveformHeightDp,
                    waveColor = waveColor
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .fillMaxHeight()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.White, Color.Transparent)
                    )
                )
        )
    }
}

@Composable
private fun LegacyTimelineRuler(
    smoothPositionState: State<Float>,
    durationMs: Long,
    pixelPerSecond: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    val rulerTickColor = remember { Color.White.copy(alpha = 0.4f) }
    val tickEndY = remember(density) { with(density) { 8.dp.toPx() } }
    val rulerTextY = remember(density) { with(density) { 20.dp.toPx() } }
    val rulePaint = remember(density) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            alpha = 100
            textSize = with(density) { 10.dp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    val totalSeconds = remember(durationMs) { (durationMs / 1000).toInt() }
    val showAllLabels = totalSeconds < 30
    val timeLabels = remember(durationMs) {
        (0..totalSeconds).associateWith { sec ->
            TimeUtils.formatDuration(sec * 1000L)
        }
    }

    Canvas(modifier = modifier.fillMaxWidth()) {
        val centerOffset = size.width / 2f
        val currentScrollPx = (smoothPositionState.value / 1000f) * pixelPerSecond
        val startX = centerOffset - currentScrollPx
        val canvasWidth = size.width

        for (sec in 0..totalSeconds) {
            val tickX = startX + (sec * pixelPerSecond)
            if (tickX < -50 || tickX > canvasWidth + 50) continue

            drawLine(
                color = rulerTickColor,
                start = androidx.compose.ui.geometry.Offset(tickX, 0f),
                end = androidx.compose.ui.geometry.Offset(tickX, tickEndY),
                strokeWidth = 1.dp.toPx()
            )

            if (showAllLabels || sec % 5 == 0) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        timeLabels[sec] ?: "",
                        tickX,
                        rulerTextY,
                        rulePaint
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineThumbnails(
    smoothPositionState: State<Float>,
    durationMs: Long,
    pixelPerSecond: Float,
    thumbHeightPx: Float,
    viewModel: VideoTimelineViewModel,
    isReady: Boolean,
    shimmerAlpha: Float,
    modifier: Modifier = Modifier
) {
    val srcRect = remember { android.graphics.Rect() }
    val dstRect = remember { android.graphics.Rect() }
    val totalFrames = remember(durationMs) { kotlin.math.ceil(durationMs / 1000f).toInt() }
    val THUMBS_PER_SPRITE = 3

    Canvas(modifier = modifier.fillMaxWidth()) {
        val centerOffset = size.width / 2f
        val currentScrollPx = (smoothPositionState.value / 1000f) * pixelPerSecond
        val startX = centerOffset - currentScrollPx
        val canvasWidth = size.width

        for (f in 0 until totalFrames) {
            val x = startX + (f * pixelPerSecond)
            val isLast = f == totalFrames - 1
            val remainderMs = durationMs % 1000
            val currentThumbWidth = if (isLast && remainderMs > 0) {
                pixelPerSecond * (remainderMs / 1000f)
            } else {
                pixelPerSecond
            }

            if (x + currentThumbWidth < 0 || x > canvasWidth) continue

            val sprite = viewModel.getSprite(f)
            if (sprite != null && !sprite.isRecycled) {
                val col = f % THUMBS_PER_SPRITE
                val sw = sprite.width / THUMBS_PER_SPRITE
                val sh = sprite.height
                val sx = col * sw

                val sourceFactor = if (isLast && remainderMs > 0) remainderMs / 1000f else 1f
                val currentSourceWidth = (sw * sourceFactor).toInt().coerceAtLeast(1)

                srcRect.set(sx, 0, sx + currentSourceWidth, sh)
                dstRect.set(
                    x.toInt(), 0,
                    (x + currentThumbWidth).toInt(), thumbHeightPx.toInt()
                )

                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawBitmap(sprite, srcRect, dstRect, null)
                }
            } else if (!isReady) {
                if (shimmerAlpha > 0f) {
                    drawRect(
                        color = Color.White.copy(alpha = shimmerAlpha),
                        topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                        size = androidx.compose.ui.geometry.Size(currentThumbWidth, thumbHeightPx)
                    )
                }
            }
        }
    }
}

@Composable
private fun LegacyTimelineWaveform(
    amplitudes: FloatArray,
    durationMs: Long,
    height: Dp,
    waveColor: Color = Color.White.copy(alpha = 0.55f),
    modifier: Modifier = Modifier
) {
    if (amplitudes.isEmpty()) return

    WaveformRenderer(
        amplitudes = amplitudes,
        modifier = modifier.height(height),
        barWidth = 2.5.dp,
        barGap = 1.dp,
        minHeight = 1.5.dp,
        color = waveColor,
        mirrored = true,
        baseline = com.chopcut.ui.components.waveform.WaveformBaseline.Center,
        animate = false
    )
}
