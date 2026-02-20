package com.chopcut.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import com.chopcut.ui.components.AudioWaveForms
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.chopcut.data.thumbnail.ThumbnailStripManager
import com.chopcut.data.thumbnail.ThumbnailStripManager.Companion.SEGMENT_SECONDS
import kotlinx.coroutines.NonCancellable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode

@OptIn(UnstableApi::class)
@Composable
fun TimelineEditor(
    videoUri: Uri,
    trimPosition: TrimPosition,
    currentPosition: Long,
    waveformData: WaveformData = WaveformData.empty(),
    isWaveformLoading: Boolean = false,
    waveformError: String? = null,
    waveformStyle: WaveformStyle = WaveformStyle(),
    // Novos parâmetros para AudioWaveForms
    audioWaveformsAmplitudes: List<Float> = emptyList(),
    isAudioWaveformsLoading: Boolean = false,
    onPositionChange: (Long) -> Unit,
        onAddPosition: () -> Unit,
        onRequestNewMedia: (() -> Unit)? = null,
        onVideoDurationChange: ((Long) -> Unit)? = null,
        extraContent: @Composable () -> Unit = {},
        modifier: Modifier = Modifier,
        // ⚠️ Flag para desativar waveform temporariamente
        showWaveform: Boolean = false
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val density = LocalDensity.current
        val pxPerSecond = remember { with(density) { 60.dp.toPx() } }
        var scrollOffsetPx by remember { mutableFloatStateOf(0f) }
        var videoDurationMs by remember { mutableLongStateOf(0L) }
        var isPlaying by remember { mutableStateOf(false) }

        LaunchedEffect(videoDurationMs) {
            if (videoDurationMs > 0 && onVideoDurationChange != null) {
                onVideoDurationChange(videoDurationMs)
            }
        }
     
        // Strip-based Thumbnails State
        // Dimensões density-aware: match exato do display = pixel-perfect, sem distorção
        val thumbWidth = remember(density) { with(density) { 60.dp.roundToPx() } }
        val thumbHeight = remember(density) { with(density) { 40.dp.roundToPx() } }
        val stripManager = remember(thumbWidth, thumbHeight) {
            ThumbnailStripManager(context, thumbWidth, thumbHeight)
        }
        val strips = remember { androidx.compose.runtime.mutableStateMapOf<Int, android.graphics.Bitmap>() }
        val loadingStrips = remember { androidx.compose.runtime.mutableStateMapOf<Int, Boolean>() }
        val scope = androidx.compose.runtime.rememberCoroutineScope()

        // Máximo de strips em memória (100 × ~432KB RGB_565 ≈ 42MB)
        // Suficiente para ~16 minutos de timeline sem eviction
        val maxStrips = 100

        // STRIP LOADING: Carregar segmentos visíveis + adjacentes por prioridade
        // IMPORTANTE: usa scope.launch (não launch) para que as coroutines
        // sobrevivam ao restart do LaunchedEffect quando scrollOffsetPx muda
        LaunchedEffect(scrollOffsetPx, videoDurationMs) {
            if (videoDurationMs == 0L) return@LaunchedEffect

            val totalSegments = stripManager.getSegmentCount(videoDurationMs)
            val currentSecond = (scrollOffsetPx / pxPerSecond).toInt().coerceAtLeast(0)
            val visibleSegment = currentSecond / SEGMENT_SECONDS

            val segmentsToLoad = listOf(
                visibleSegment,       // prioridade 1: visível agora
                visibleSegment - 1,   // prioridade 2: anterior
                visibleSegment + 1,   // prioridade 3: próximo
                visibleSegment - 2,   // prioridade 4: pre-fetch
                visibleSegment + 2
            ).filter { it in 0 until totalSegments }
             .filter { !strips.containsKey(it) && loadingStrips[it] != true }

            segmentsToLoad.forEach { segIdx ->
                loadingStrips[segIdx] = true
                // scope.launch sobrevive ao restart do LaunchedEffect
                scope.launch(Dispatchers.IO) {
                    try {
                        val strip = stripManager.extractSegment(videoUri, segIdx, videoDurationMs)
                        withContext(Dispatchers.Main) {
                            if (strip != null) {
                                strips[segIdx] = strip
                            }
                            // LRU eviction: remover strip mais distante do visível
                            while (strips.size > maxStrips) {
                                val currSeg = (scrollOffsetPx / pxPerSecond).toInt().coerceAtLeast(0) / SEGMENT_SECONDS
                                val toEvict = strips.keys
                                    .maxByOrNull { kotlin.math.abs(it - currSeg) }
                                    ?: break
                                strips.remove(toEvict)?.let { bmp ->
                                    if (!bmp.isRecycled) bmp.recycle()
                                }
                            }
                        }
                    } finally {
                        // Garantir cleanup mesmo se cancelada/exception
                        withContext(Dispatchers.Main + NonCancellable) {
                            loadingStrips.remove(segIdx)
                        }
                    }
                }
            }
        }

        // BACKGROUND LOADING: Pré-carregar até maxStrips segmentos sequencialmente
        LaunchedEffect(videoUri, videoDurationMs) {
            if (videoDurationMs == 0L) return@LaunchedEffect
            val totalSegments = stripManager.getSegmentCount(videoDurationMs)
            val toPreload = minOf(totalSegments, maxStrips)

            android.util.Log.d("ThumbnailStrip", "Background: $totalSegments segments, pre-loading $toPreload")

            for (segIdx in 0 until toPreload) {
                // Parar se já atingiu o limite (scroll-based pode ter preenchido)
                if (strips.size >= maxStrips) break
                if (!strips.containsKey(segIdx) && loadingStrips[segIdx] != true) {
                    loadingStrips[segIdx] = true
                    try {
                        val strip = withContext(Dispatchers.IO) {
                            stripManager.extractSegment(videoUri, segIdx, videoDurationMs)
                        }
                        if (strip != null) strips[segIdx] = strip
                    } finally {
                        loadingStrips.remove(segIdx)
                    }
                }
            }

            android.util.Log.d("ThumbnailStrip", "Background: Complete (${strips.size} strips loaded)")
        }

        // Animação de spinner para thumbnails carregando
        val infiniteTransition = rememberInfiniteTransition(label = "thumbnailSpinner")
        val spinnerAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "spinnerRotation"
        )

        val exoPlayer = remember(videoUri) {
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
    
        var playerError by remember(videoUri) { mutableStateOf<String?>(null) }
        var isSecurityError by remember(videoUri) { mutableStateOf(false) }
    
        DisposableEffect(exoPlayer) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        videoDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                        playerError = null
                        isSecurityError = false
                    }
                }
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    android.util.Log.e("TimelineEditor", "ExoPlayer error: ${error.message}", error)
                    val cause = error.cause
                    // Check for SecurityException (Permission denied)
                    val isPermError = cause?.toString()?.contains("SecurityException") == true || 
                                      cause?.cause?.toString()?.contains("SecurityException") == true
                    
                    isSecurityError = isPermError
                    playerError = if (isPermError) {
                        "Permissão do arquivo expirou. Toque em 'Re-Localizar' para corrigir."
                    } else {
                        "Erro ao reproduzir: ${error.message ?: "Desconhecido"}"
                    }
                    
                    if (isPermError && onRequestNewMedia != null) {
                       // Auto-trigger handled in LaunchedEffect to avoid side-effects in listener
                    }
                }
            }
            exoPlayer.addListener(listener)
            onDispose {
                exoPlayer.removeListener(listener)
                exoPlayer.release()
            }
        }
        
        // Auto-launch recovery if security error
        LaunchedEffect(isSecurityError) {
            if (isSecurityError && onRequestNewMedia != null) {
                // Optional: Add a small delay or check strict conditions to avoid loops
                // For now, we rely on the parent to handle "one-time" logic if needed, 
                // or just let the user click.
                // User requested "funcione de cara", so let's TRY to auto-launch?
                // Actually, auto-launching a system picker without user click might be blocked or jarring.
                // Let's stick to clear UI, but maybe the user meant "Don't show generic error".
                // We improved the message.
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
    
        // Cleanup: reciclar strips quando o composable sai da composição
        DisposableEffect(videoUri) {
            onDispose {
                strips.values.forEach { bitmap ->
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
                strips.clear()
            }
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF121212)),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Video Filename
            val fileName = videoUri.lastPathSegment?.substringAfterLast('/') ?: "unknown"
            Text(
                text = fileName,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // 1. VIDEO PREVIEW
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(Color.Black)
            ) {
                if (playerError != null) {
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
                            text = playerError!!,
                            color = Color.White,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                        
                        if (isSecurityError && onRequestNewMedia != null) {
                             androidx.compose.material3.Button(
                                onClick = onRequestNewMedia,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer,
                                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
                                )
                             ) {
                                Text("Re-Localizar Arquivo (Necessário)")
                            }
                        } else {
                            androidx.compose.material3.Button(onClick = { 
                                playerError = null
                                isSecurityError = false
                                exoPlayer.prepare()
                                exoPlayer.play()
                            }) {
                                Text("Tentar Novamente")
                            }
                        }

                        if (!isSecurityError && onRequestNewMedia != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            androidx.compose.material3.OutlinedButton(onClick = onRequestNewMedia) {
                                Text("Localizar Arquivo")
                            }
                        }
                    }
                } else {
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

        // 2.5. CURRENT TIME DISPLAY (Outside timeline container, below video)
        Text(
            text = String.format("%02d:%02d.%03d",
                (currentTimeMs / 60000),
                (currentTimeMs % 60000) / 1000,
                (currentTimeMs % 1000)),
            color = if (isInsideRange) Color.Red else Color.White,
            fontSize = 24.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // 3. TIMELINE RULER (Moved up, Gray BG, Pause on Scroll)
        Spacer(modifier = Modifier.height(10.dp))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp) // Increased height to accommodate WaveForm below Thumbs
                .background(Color(0xFF2A2A2A)) // Fundo Cinza
        ) {
            val timelineWidth = constraints.maxWidth.toFloat()
            val centerOffset = timelineWidth / 2f
            val durationPx = (videoDurationMs / 1000f) * pxPerSecond
            val rulerHeight = with(density) { 30.dp.toPx() }
            val waveformHeightDp = 36.dp
            val thumbnailsHeightDp = 40.dp
            
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

                // WAVEFORM LAYER (Bottom) - AudioWaveForms
                // ⚠️ TEMPORARIAMENTE DESATIVADO para testes de thumbnail
                if (showWaveform) {
                    if (isAudioWaveformsLoading) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter).height(waveformHeightDp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                         androidx.compose.material3.CircularProgressIndicator(
                             modifier = Modifier.size(20.dp),
                             color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                             strokeWidth = 2.dp
                         )
                    }
                } else if (audioWaveformsAmplitudes.isNotEmpty()) {
                     // Marca que temos dados de audio waveforms para desenhar no Canvas
                     Box(modifier = Modifier.align(Alignment.BottomCenter).height(waveformHeightDp).fillMaxWidth()) {}
                } else if (isWaveformLoading) {
                    // Fallback para waveform antigo durante carregamento
                    Box(modifier = Modifier.align(Alignment.BottomCenter).height(waveformHeightDp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                         androidx.compose.material3.CircularProgressIndicator(
                             modifier = Modifier.size(20.dp),
                             color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                             strokeWidth = 2.dp
                         )
                    }
                } else if (waveformError != null) {
                     Box(modifier = Modifier.align(Alignment.BottomCenter).height(waveformHeightDp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                         Text(text = "❌", fontSize = 10.sp)
                    }
                } else if (waveformData.amplitudes.isNotEmpty()) {
                     // Fallback para waveform antigo
                     val audioDurationMs = if (waveformData.durationMs > 0) waveformData.durationMs else videoDurationMs
                     val waveformWidth = (audioDurationMs / 1000f) * pxPerSecond
                     val waveformStartOffset = centerOffset - currentScroll

                     WaveForm(
                         amplitudes = waveformData.amplitudes,
                         modifier = Modifier
                             .height(waveformHeightDp)
                             .align(Alignment.BottomStart)
                             .width(with(density) { waveformWidth.toDp() })
                             .graphicsLayer {
                                 translationX = waveformStartOffset
                             },
                         maxAmp = 1.0f,
                         style = waveformStyle
                     )
                }
                } // Fim do if (showWaveform)

                // PAINT ALLOCATIONS MOVED OUTSIDE LOOP (Optimization)
                // Pré-alocar objetos fora do draw loop (zero allocations por frame durante scroll)
                val srcRect = remember { android.graphics.Rect() } // Remember para sobreviver a recomposições
                val dstRect = remember { android.graphics.Rect() }

                // Paint com bilinear filtering para render suave da strip→tela
                val renderPaint = remember {
                   android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                       isAntiAlias = true
                       isDither = true
                   }
                }

                // Paint para spinner (pré-alocado)
                val arcPaint = remember {
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#64B5F6")
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 3f * 2.625f // 3dp approx in px, hardcoded for now or pass density
                        isAntiAlias = true
                        strokeCap = android.graphics.Paint.Cap.ROUND
                    }
                }
                val arcRect = remember { android.graphics.RectF() }

                // Paint para BG placeholder
                val bgPaint = remember {
                    android.graphics.Paint().apply {
                       color = android.graphics.Color.parseColor("#1A1A1A")
                       style = android.graphics.Paint.Style.FILL
                    }
                }

                 // Paint para timestamps da régua (pré-alocado, fora do draw loop)
                 val timestampPaint = remember {
                     android.graphics.Paint().apply {
                         color = android.graphics.Color.parseColor("#808080")
                         textSize = with(density) { 8.dp.toPx() }
                         textAlign = android.graphics.Paint.Align.CENTER
                         typeface = android.graphics.Typeface.MONOSPACE
                         isAntiAlias = true
                         letterSpacing = 0.03f
                     }
                 }

                 Canvas(modifier = Modifier.fillMaxSize()) {
                    // Régua: ticks a cada 1s, timestamps a cada 5s
                    val tickSpacingSeconds = 1f
                    val tickSpacingPx = pxPerSecond * tickSpacingSeconds
                    val startTickIndex = ((currentScroll - centerOffset) / tickSpacingPx).toInt() - 1
                    val endTickIndex = ((currentScroll - centerOffset + timelineWidth) / tickSpacingPx).toInt() + 2


                    val rulerTopY = 0f
                    val rulerThumbGap = 8.dp.toPx()

                    // DRAW THUMBNAIL STRIPS
                     val thumbnailHeightPx = thumbnailsHeightDp.toPx()
                     val thumbnailTop = rulerHeight + rulerThumbGap
                     val thumbW = stripManager.thumbWidth.toFloat()
                     val thumbH = stripManager.thumbHeight.toFloat()


                     // Clip para não ultrapassar a duração do vídeo
                     drawContext.canvas.save()
                     val clipEnd = centerOffset + (videoDurationMs / 1000f * pxPerSecond) - currentScroll
                     drawContext.canvas.clipRect(0f, thumbnailTop, clipEnd, thumbnailTop + thumbnailHeightPx)

                     // Range de segundos visíveis na tela
                     // Otimização: calcular apenas os visíveis + buffer pequeno (segurança visual)
                     val startSecond = ((currentScroll - centerOffset) / pxPerSecond).toInt().coerceAtLeast(0)
                     val endSecond = ((currentScroll - centerOffset + timelineWidth) / pxPerSecond).toInt() + 1 // +1 buffer

                     for (sec in startSecond..endSecond) {
                         val timeMs = sec * 1000L
                         if (timeMs >= videoDurationMs) break

                         val segIdx = sec / SEGMENT_SECONDS
                         val frameInStrip = sec % SEGMENT_SECONDS
                         val x = centerOffset + (sec * pxPerSecond) - currentScroll

                         // Cull check: Pular se fora da tela (redundante com start/endSecond mas bom pra garantir)
                         if (x > size.width || x + pxPerSecond < 0) continue

                         val strip = strips[segIdx]
                         if (strip != null && !strip.isRecycled) {
                             // Recortar o frame correto da strip (1:1 pixel mapping)
                             srcRect.set(
                                 (frameInStrip * thumbW).toInt(), 0,
                                 ((frameInStrip + 1) * thumbW).toInt(), thumbH.toInt()
                             )
                             dstRect.set(
                                 x.toInt(), thumbnailTop.toInt(),
                                 (x + pxPerSecond).toInt(), (thumbnailTop + thumbnailHeightPx).toInt()
                             )
                             drawIntoCanvas { canvas ->
                                 canvas.nativeCanvas.drawBitmap(strip, srcRect, dstRect, renderPaint)
                             }
                         } else {
                             // Strip não carregada - fundo escuro + spinner animado
                             drawIntoCanvas { canvas ->
                                 // Draw BG
                                 canvas.nativeCanvas.drawRect(
                                     x, thumbnailTop, x + pxPerSecond, thumbnailTop + thumbnailHeightPx,
                                     bgPaint
                                 )
                                 
                                 // Draw Spinner
                                 val cx = x + pxPerSecond / 2
                                 val cy = thumbnailTop + thumbnailHeightPx / 2
                                 val r = 8.dp.toPx()
                                 arcRect.set(cx - r, cy - r, cx + r, cy + r)
                                 canvas.nativeCanvas.drawArc(arcRect, spinnerAngle, 270f, false, arcPaint)
                             }
                         }
                     }
                     drawContext.canvas.restore()

                     // DRAW AUDIO WAVEFORMS (sincronizado com thumbnails)
                     // ⚠️ TEMPORARIAMENTE DESATIVADO para testes de thumbnail
                     if (showWaveform && audioWaveformsAmplitudes.isNotEmpty()) {
                         val waveformWidth = (videoDurationMs / 1000f) * pxPerSecond
                         val waveformStartX = centerOffset - currentScroll
                         val waveformHeightPx = waveformHeightDp.toPx()
                         val waveformTopY = thumbnailTop + thumbnailHeightPx // Logo abaixo das thumbnails

                         android.util.Log.d("TimelineEditor", "Drawing AudioWaveforms: ${audioWaveformsAmplitudes.size} bars, startX=$waveformStartX, width=$waveformWidth")

                         val barSlotWidth = waveformWidth / audioWaveformsAmplitudes.size.coerceAtLeast(1)
                         val barWidthPx = (barSlotWidth * 0.8f).coerceAtLeast(1f)

                         // Clip para não desenhar fora da área
                         drawContext.canvas.save()
                         val waveformClipEnd = centerOffset + (videoDurationMs / 1000f * pxPerSecond) - currentScroll
                         drawContext.canvas.clipRect(0f, waveformTopY, waveformClipEnd, waveformTopY + waveformHeightPx)

                         audioWaveformsAmplitudes.forEachIndexed { index, amplitude ->
                             val x = waveformStartX + (index * barSlotWidth)

                             // Só desenhar se estiver visível
                             if (x + barSlotWidth >= 0f && x <= size.width) {
                                 val normalizedAmp = amplitude.coerceAtLeast(0.01f).coerceAtMost(1.0f)
                                 val barHeight = normalizedAmp * waveformHeightPx

                                 // Desenhar barra centralizada verticalmente na área do waveform
                                 val y = waveformTopY + (waveformHeightPx - barHeight) / 2f

                                 drawRect(
                                     color = Color(0xFF00D9FF),
                                     topLeft = androidx.compose.ui.geometry.Offset(x + (barSlotWidth - barWidthPx) / 2, y),
                                     size = androidx.compose.ui.geometry.Size(barWidthPx, barHeight)
                                 )
                             }
                         }
                         drawContext.canvas.restore()
                     }

                     // Dimming removed to ensure vibrant colors and because elements are now stacked non-overlapping.

                 // Layout da régua: texto no topo, ticks embaixo apontando para as thumbs
                     val tickZoneTop = 30.dp.toPx()
                     val tickZoneHeight = rulerHeight - tickZoneTop

                     for (i in startTickIndex..endTickIndex) {
                        val tickTimeSec = i * tickSpacingSeconds
                        if (tickTimeSec < 0f || tickTimeSec > videoDurationMs / 1000f) continue

                        val x = centerOffset + (tickTimeSec * pxPerSecond) - currentScroll
                        if (x < -20f || x > size.width + 20f) continue

                        // Hierarquia de 3 níveis: 10s (super), 5s (major), 1s (minor)
                        val isTenSecond = (i % 10 == 0)
                        val isFiveSecond = (i % 5 == 0) && !isTenSecond

                        val dotRadius = when {
                            isTenSecond -> 3.dp.toPx()
                            isFiveSecond -> 2.dp.toPx()
                            else -> 1.2.dp.toPx()
                        }

                        val dotAlpha = when {
                            isTenSecond -> 0.9f
                            isFiveSecond -> 0.50f
                            else -> 0.22f
                        }

                        val dotY = tickZoneTop + tickZoneHeight * 0.45f

                        // Bolinhas com hierarquia de tamanho
                        drawCircle(
                            color = Color.White.copy(alpha = dotAlpha),
                            radius = dotRadius,
                            center = Offset(x, dotY)
                        )

                        // Timestamp acima dos ticks (zona de texto no topo da régua)
                        if (isFiveSecond || isTenSecond) {
                            val totalSec = tickTimeSec.toInt()
                            val min = totalSec / 60
                            val sec = totalSec % 60
                            val label = String.format("%d:%02d", min, sec)
                            drawIntoCanvas { canvas ->
                                canvas.nativeCanvas.drawText(
                                    label,
                                    x,
                                    tickZoneTop - 3.dp.toPx(),
                                    timestampPaint
                                )
                            }
                        }
                    }

                     trimPosition.completeRanges.forEach { (start, end) ->
                         val startX = centerOffset + (start / 1000f) * pxPerSecond - currentScroll
                         val endX = centerOffset + (end / 1000f) * pxPerSecond - currentScroll
                         if (endX >= 0 && startX <= size.width) {
                             val rangeY = 14.dp.toPx()
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

                             val rangeY = 14.dp.toPx()
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

        Spacer(modifier = Modifier.height(10.dp))

        // 5. RANGE LIST (Moved down)
        extraContent()
        
        Spacer(modifier = Modifier.weight(1f))

    }
}
