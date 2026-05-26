package com.chopcut.ui.components.timeline

import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.chopcut.util.TimelineLogger

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
        // Inicializa o logger de telemetria assíncrono para esta sessão
        TimelineLogger.init(context)
        if (durationMs > 0) {
            val h = with(density) { 56.dp.roundToPx() }
            val w = (h * aspectRatio).toInt()
            viewModel.loadSprites(videoUri, durationMs, w, h)
        }
    }

    val waveColor = remember { Color.White.copy(alpha = 0.55f) }

    var localPositionMs by remember { mutableLongStateOf(currentPositionMs) }
    var isScrubbingLocal by remember { mutableStateOf(false) }

    val smoothPositionMs = remember { mutableFloatStateOf(currentPositionMs.toFloat()) }

    // Sincronizar posição de forma inteligente baseando-se no estado de reprodução
    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            // Quando pausado, mantém sincronia em tempo real absoluta via snapshotFlow (reage a seeks, etc.)
            snapshotFlow { currentPositionMs }
                .collect { pos ->
                    smoothPositionMs.floatValue = pos.toFloat()
                    localPositionMs = pos
                }
        } else {
            // Ancoragem Inicial de Reprodução: sincroniza a posição exata apenas na transição de entrada do Play
            smoothPositionMs.floatValue = currentPositionMs.toFloat()
            localPositionMs = currentPositionMs
            
            TimelineLogger.logMovement(
                mode = "AUTO_A",
                positionMs = currentPositionMs,
                reason = "Autoplay initial anchoring triggered (anchored at ${currentPositionMs}ms)"
            )
        }
    }

    // Motor de Rolagem 100% Autônomo e Desacoplado a 60 FPS
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var lastTimeNanos = 0L
            var accumulatedMs = 0f
            while (true) {
                withFrameNanos { frameTimeNanos ->
                    if (lastTimeNanos == 0L) {
                        // Sincroniza a base de tempo inicial diretamente com o Choreographer
                        lastTimeNanos = frameTimeNanos
                    } else {
                        val elapsedMs = (frameTimeNanos - lastTimeNanos) / 1_000_000f
                        lastTimeNanos = frameTimeNanos
                        accumulatedMs += elapsedMs
                        
                        // Limitador estrito a 60 FPS (atualizações visuais a cada ~16.67ms)
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

    // Sincronizar arraste manual do usuário
    LaunchedEffect(localPositionMs) {
        if (isScrubbingLocal) {
            smoothPositionMs.floatValue = localPositionMs.toFloat()
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
        
        val deltaStr = String.format(java.util.Locale.US, "%.2f", delta)
        // Registra telemetria de arraste manual gestual
        TimelineLogger.logMovement(
            mode = "MANUAL",
            positionMs = newPos,
            reason = "Touch gesture scroll delta = ${deltaStr}px (${deltaMs}ms)"
        )
        
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
            .border(2.dp, Color.Green) // Borda verde de depuração e monitoramento!
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

            // Waveform de áudio desativada temporariamente para teste de stress
            /*
            if (showWaveform && audioAmplitudes.isNotEmpty()) {
                LegacyTimelineWaveform(
                    smoothPositionState = smoothPositionMs,
                    amplitudes = audioAmplitudes,
                    durationMs = durationMs,
                    pixelPerSecond = pxPerSecond,
                    height = waveformHeightDp,
                    waveColor = waveColor
                )
            }
            */
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(4.dp)
                .fillMaxHeight()
                .border(1.dp, Color.Green)
                .background(Color.White)
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
    Canvas(modifier = modifier.fillMaxWidth()) {
        // Ticks desligados temporariamente para teste de performance pura
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
    val textPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.GREEN
            textSize = 40f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    val totalFrames = remember(durationMs) { kotlin.math.ceil(durationMs / 1000f).toInt() }

    Canvas(modifier = modifier.fillMaxWidth()) {
        val centerOffset = size.width / 2f
        val currentScrollPx = (smoothPositionState.value / 1000f) * pixelPerSecond
        val startX = centerOffset - currentScrollPx
        val canvasWidth = size.width

        // Culling matemático: calcula o intervalo de frames visíveis na tela
        val firstVisibleFrame = ((-pixelPerSecond - startX) / pixelPerSecond).toInt().coerceIn(0, totalFrames - 1)
        val lastVisibleFrame = ((canvasWidth - startX) / pixelPerSecond).toInt().coerceIn(0, totalFrames - 1)

        for (f in firstVisibleFrame..lastVisibleFrame) {
            val x = startX + (f * pixelPerSecond)
            val isLast = f == totalFrames - 1
            val remainderMs = durationMs % 1000
            val currentThumbWidth = if (isLast && remainderMs > 0) {
                pixelPerSecond * (remainderMs / 1000f)
            } else {
                pixelPerSecond
            }

            // Desenhar uma box cinza escura de fundo
            drawRect(
                color = Color(0xFF202020),
                topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                size = androidx.compose.ui.geometry.Size(currentThumbWidth, thumbHeightPx),
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
            // Desenhar uma borda verde fina em volta
            drawRect(
                color = Color.Green.copy(alpha = 0.5f),
                topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                size = androidx.compose.ui.geometry.Size(currentThumbWidth, thumbHeightPx),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )

            // Desenhar o número do frame f no centro
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    f.toString(),
                    x + currentThumbWidth / 2f,
                    thumbHeightPx / 2f + 15f,
                    textPaint
                )
            }
        }
    }
}

@Composable
private fun LegacyTimelineWaveform(
    smoothPositionState: State<Float>,
    amplitudes: FloatArray,
    durationMs: Long,
    pixelPerSecond: Float,
    height: Dp,
    waveColor: Color = Color.White.copy(alpha = 0.55f),
    modifier: Modifier = Modifier
) {
    if (amplitudes.isEmpty()) return

    val density = LocalDensity.current
    
    // Otimização: obter medidas em pixel fora do Canvas para evitar conversão dentro do loop
    val barWidthPx = remember(density) { with(density) { 2.5.dp.toPx() } }
    val barGapPx = remember(density) { with(density) { 1.dp.toPx() } }
    val minHeightPx = remember(density) { with(density) { 1.5.dp.toPx() } }

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val centerOffset = size.width / 2f
        val currentScrollPx = (smoothPositionState.value / 1000f) * pixelPerSecond
        val startX = centerOffset - currentScrollPx
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerY = canvasHeight / 2f

        val totalWidthPx = (durationMs / 1000f) * pixelPerSecond
        val stepPx = barWidthPx + barGapPx
        
        // Quantidade total de barras que cabem no vídeo
        val totalBars = (totalWidthPx / stepPx).toInt().coerceAtLeast(1)

        // Fator de normalização rápido (calculado uma única vez fora do loop)
        var maxAmp = 0.01f
        for (i in amplitudes.indices) {
            val a = amplitudes[i]
            if (a > maxAmp) maxAmp = a
        }
        val normFactor = if (maxAmp > 0.05f) maxAmp else 1f

        // Culling matemático: calcula o intervalo de barras visíveis na tela
        val firstVisibleBar = ((-barWidthPx - startX) / stepPx).toInt().coerceIn(0, totalBars - 1)
        val lastVisibleBar = ((canvasWidth - startX) / stepPx).toInt().coerceIn(0, totalBars - 1)

        for (i in firstVisibleBar..lastVisibleBar) {
            val x = startX + (i * stepPx)

            // Mapeamento proporcional rápido para o array de amplitudes
            val ampIdx = ((i.toFloat() / totalBars) * amplitudes.size).toInt().coerceIn(0, amplitudes.size - 1)
            val amp = amplitudes[ampIdx]
            
            val normalized = (amp / normFactor).coerceIn(0f, 1f)
            val boosted = kotlin.math.sqrt(normalized.toDouble()).toFloat()
            val barHeight = (boosted * centerY).coerceAtLeast(minHeightPx)

            drawRoundRect(
                color = waveColor,
                topLeft = androidx.compose.ui.geometry.Offset(x, centerY - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidthPx, barHeight * 2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
            )
        }
    }
}
