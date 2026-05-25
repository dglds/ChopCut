package com.chopcut.ui.components.timeline

import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chopcut.ui.viewmodel.VideoTimelineViewModel
import com.chopcut.ui.components.waveform.WaveformRenderer
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
    onSeek: (Long) -> Unit,
    onScrubStart: () -> Unit = {},
    onScrubStop: (Long) -> Unit = {},
    trimRanges: List<Pair<Long, Long>> = emptyList(),
    audioAmplitudes: List<Float> = emptyList(),
    showWaveform: Boolean = true,
    videoWidth: Int = 0,
    videoHeight: Int = 0
) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current

    timber.log.Timber.d("VideoTimeline: received ${audioAmplitudes.size} audio amplitudes, showWaveform=$showWaveform, durationMs=$durationMs")
    
    // ESCALA: 60dp por segundo (referência da tela antiga)
    val pxPerSecond = with(density) { 60.dp.toPx() }
    val thumbHeightPx = with(density) { 56.dp.toPx() }
    val rulerHeightPx = with(density) { 24.dp.toPx() }
    val waveformHeightPx = with(density) { 40.dp.toPx() }
    
    // Calcular largura da thumbnail baseada na proporção real do vídeo
    val aspectRatio = if (videoWidth > 0 && videoHeight > 0) {
        videoWidth.toFloat() / videoHeight
    } else {
        16f / 9f
    }
    
    // Na escala de 60dp/s, cada segundo ocupa pxPerSecond. 
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

    // Objetos reutilizáveis entre frames — evitam alocação por frame no draw scope
    val textSizePx = with(density) { 10.dp.toPx() }
    val rulerTextY  = with(density) { 20.dp.toPx() }
    val rulePaint = remember(textSizePx) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            alpha = 100
            textSize = textSizePx
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    val srcRect = remember { android.graphics.Rect() }
    val dstRect = remember { android.graphics.Rect() }

    // Waveform — estilo CapCut/InShot: barras finas espelhadas, cor única neutra,
    // só a altura comunica amplitude (sem variação de alpha).
    val waveBarWidthPx = with(density) { 2.5.dp.toPx() }
    val waveBarGapPx = with(density) { 2.dp.toPx() }
    val waveBarStepPx = waveBarWidthPx + waveBarGapPx
    val waveMinHeightPx = with(density) { 1.5.dp.toPx() }
    val waveColor = remember { Color.White.copy(alpha = 0.55f) }
    val waveCornerRadius = remember(waveBarWidthPx) {
        androidx.compose.ui.geometry.CornerRadius(waveBarWidthPx / 2f, waveBarWidthPx / 2f)
    }

    // Posição local usada durante o arraste — evita depender do parâmetro externo
    // (que pode ser sobrescrito pelo poll do ExoPlayer a cada 100ms)
    var localPositionMs by remember { mutableLongStateOf(currentPositionMs) }
    var isScrubbing by remember { mutableStateOf(false) }

    // Sincronizar posição externa apenas quando não estiver arrastando
    LaunchedEffect(currentPositionMs) {
        if (!isScrubbing) {
            localPositionMs = currentPositionMs
        }
    }

    // O cálculo de windowPeak deve ser estável. 
    val globalPeak = remember(audioAmplitudes) {
        var max = 0.01f
        for (i in audioAmplitudes.indices) {
            if (audioAmplitudes[i] > max) max = audioAmplitudes[i]
        }
        max
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height((rulerHeightPx / density.density + thumbHeightPx / density.density + (if (showWaveform) waveformHeightPx / density.density else 0f) + 8f).dp)
            .background(Color.Black.copy(alpha = 0.2f))
    ) {
        val width = constraints.maxWidth.toFloat()
        val centerOffset = width / 2f
        val currentScrollPx = (localPositionMs / 1000f) * pxPerSecond

        // Scroll manual com estado local — desacoplado do ExoPlayer durante o arraste.
        // onSeek é omitido aqui: atualizar o ViewModel a cada frame causaria recomposição
        // em cascata no EditorScreen. A posição é propagada uma única vez via onScrubStop.
        val scrollableState = androidx.compose.foundation.gestures.rememberScrollableState { delta ->
            if (!isScrubbing) {
                isScrubbing = true
                onScrubStart()
            }
            val deltaMs = (delta / pxPerSecond * 1000).toLong()
            val newPos = (localPositionMs - deltaMs).coerceIn(0, durationMs)
            localPositionMs = newPos
            delta
        }

        // Detectar fim do gesto: quando isScrollInProgress vira false, disparar onScrubStop
        LaunchedEffect(scrollableState) {
            snapshotFlow { scrollableState.isScrollInProgress }
                .collect { isScrolling ->
                    if (!isScrolling && isScrubbing) {
                        isScrubbing = false
                        onScrubStop(localPositionMs)
                    }
                }
        }

        // Thumbnails + Ruler Canvas
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scrollable(
                    state = scrollableState,
                    orientation = Orientation.Horizontal
                )
        ) {
            val startX = centerOffset - currentScrollPx
            val totalSeconds = (durationMs / 1000).toInt()
            
            // 1. DESENHAR RÉGUA (Ticks e Segundos)
            for (sec in 0..totalSeconds) {
                val tickX = startX + (sec * pxPerSecond)
                if (tickX < -100 || tickX > width + 100) continue
                
                // Tick principal
                drawLine(
                    color = Color.White.copy(alpha = 0.4f),
                    start = androidx.compose.ui.geometry.Offset(tickX, 0f),
                    end = androidx.compose.ui.geometry.Offset(tickX, 8.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
                
                // Texto do tempo
                if (sec % 5 == 0 || totalSeconds < 30) {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(
                            TimeUtils.formatDuration(sec * 1000L),
                            tickX,
                            rulerTextY,
                            rulePaint
                        )
                    }
                }
            }

            // 2. DESENHAR THUMBNAILS (Strips)
            val thumbTop = rulerHeightPx + 4.dp.toPx()
            val totalFrames = kotlin.math.ceil(durationMs / 1000f).toInt()
            val THUMBS_PER_SPRITE = 3
            
            for (f in 0 until totalFrames) {
                val isLast = f == totalFrames - 1
                val remainderMs = durationMs % 1000
                val currentThumbWidth = if (isLast && remainderMs > 0) {
                    pxPerSecond * (remainderMs / 1000f)
                } else {
                    pxPerSecond
                }

                val x = startX + (f * pxPerSecond)
                if (x + currentThumbWidth < 0 || x > width) continue
                
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
                        x.toInt(), thumbTop.toInt(),
                        (x + currentThumbWidth).toInt(), (thumbTop + thumbHeightPx).toInt()
                    )

                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawBitmap(sprite, srcRect, dstRect, null)
                    }
                } else {
                    val alpha = if (isReady) 0f else shimmerAlpha
                    if (alpha > 0f) {
                        drawRect(
                            color = Color.White.copy(alpha = alpha),
                            topLeft = androidx.compose.ui.geometry.Offset(x, thumbTop),
                            size = androidx.compose.ui.geometry.Size(currentThumbWidth, thumbHeightPx)
                        )
                    }
                }
            }

            // Waveform agora é renderizado externamente via WaveformRenderer
        }

        // PLAYHEAD FIXO NO CENTRO
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

        // Waveform como overlay acima das thumbnails
        if (showWaveform && audioAmplitudes.isNotEmpty()) {
            timber.log.Timber.d("VideoTimeline: rendering waveform with ${audioAmplitudes.size} amplitudes")
            TimelineWaveform(
                amplitudes = audioAmplitudes,
                durationMs = durationMs,
                height = with(density) { waveformHeightPx.toDp() }
            )
        }
    }
}

@Composable
private fun TimelineWaveform(
    amplitudes: List<Float>,
    durationMs: Long,
    height: Dp
) {
    if (amplitudes.isEmpty()) return

    val floatArray = remember(amplitudes) {
        FloatArray(amplitudes.size) { amplitudes[it] }
    }

    WaveformRenderer(
        amplitudes = floatArray,
        modifier = Modifier.height(height),
        barWidth = 2.5.dp,
        barGap = 1.dp,
        minHeight = 1.5.dp,
        color = Color.White.copy(alpha = 0.55f),
        mirrored = true,
        baseline = com.chopcut.ui.components.waveform.WaveformBaseline.Center,
        animate = false
    )
}
