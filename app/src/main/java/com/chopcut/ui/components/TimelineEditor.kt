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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import com.chopcut.ui.components.AudioWaveForms
import com.chopcut.ui.components.AudioWaveFormsConfig
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
        modifier: Modifier = Modifier
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
     
        // Thumbnails State
        val thumbnails = remember { androidx.compose.runtime.mutableStateMapOf<Long, android.graphics.Bitmap>() }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        
        // Fetch thumbnails based on scroll
        LaunchedEffect(scrollOffsetPx, videoDurationMs) {
            if (videoDurationMs == 0L) return@LaunchedEffect
            
            val visibleDurationPx = with(density) { 
                // Assuming screen width roughly, or the timeline width. 
                // Better to fetch a bit more than visible.
                1000.dp.toPx() 
            }
            
            val startTimeSec = (scrollOffsetPx / pxPerSecond).toLong()
            val endTimeSec = ((scrollOffsetPx + visibleDurationPx) / pxPerSecond).toLong()
            
            for (sec in startTimeSec..endTimeSec) {
                val timeMs = sec * 1000
                if (timeMs > videoDurationMs) break
                if (!thumbnails.containsKey(timeMs)) {
                    launch(Dispatchers.IO) {
                        val bmp = ThumbnailUtils.getThumbnail(context, videoUri, timeMs)
                        if (bmp != null) {
                            // Update state on Main thread to ensure proper recomposition and safety
                            withContext(Dispatchers.Main) {
                                thumbnails[timeMs] = bmp
                            }
                        }
                    }
                }
            }
        }
    
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
            val rulerHeight = with(density) { 24.dp.toPx() }
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

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#BDBDBD")
                        textSize = 10.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.DEFAULT
                        isAntiAlias = true
                    }

                    val tickSpacing = pxPerSecond / 10f
                    val startTickIndex = (scrollOffsetPx / tickSpacing).toInt() - (centerOffset / tickSpacing).toInt() - 2
                    val endTickIndex = startTickIndex + (timelineWidth / tickSpacing).toInt() + 4
                    

                    val rulerTopY = 0f

                    // DRAW THUMBNAILS
                    // Draw thumbnails below ruler, above waveform
                     val thumbnailHeightPx = thumbnailsHeightDp.toPx()
                     val thumbnailTop = rulerHeight
                     val thumbnailWidth = pxPerSecond // 1 second width
                     
                     // Clip the thumbnails to the video duration so they don't overshoot visually
                     drawContext.canvas.save()
                     val clipEnd = centerOffset + (videoDurationMs / 1000f * pxPerSecond) - currentScroll
                     // Limit clip rect to thumbnail area
                     drawContext.canvas.clipRect(0f, thumbnailTop, clipEnd, thumbnailTop + thumbnailHeightPx)
                     
                     for (i in startTickIndex..endTickIndex) {
                         // We only draw thumbnails at integer seconds (0, 1, 2...)
                         // Mapping tick index (0.1s) to second
                         if (i % 10 == 0) {
                             val second = i / 10
                             val timeMs = second * 1000L
                             
                             if (timeMs in 0 until videoDurationMs) {
                                 val x = centerOffset + (second * pxPerSecond) - currentScroll
                                 
                                 thumbnails[timeMs]?.let { bmp ->
                                     drawIntoCanvas { canvas ->
                                         // Implement CenterCrop logic to avoid distortion
                                         val viewWidth = thumbnailWidth
                                         val viewHeight = thumbnailHeightPx
                                         val bmpWidth = bmp.width.toFloat()
                                         val bmpHeight = bmp.height.toFloat()

                                         val scale: Float
                                         var dx = 0f
                                         var dy = 0f

                                         // Calculate scale to cover the destination area (CenterCrop)
                                         if (bmpWidth * viewHeight > viewWidth * bmpHeight) {
                                             scale = viewHeight / bmpHeight
                                             dx = (viewWidth - bmpWidth * scale) * 0.5f
                                         } else {
                                             scale = viewWidth / bmpWidth
                                             dy = (viewHeight - bmpHeight * scale) * 0.5f
                                         }
                                         
                                         canvas.save()
                                         canvas.translate(x, thumbnailTop)
                                         canvas.clipRect(0f, 0f, viewWidth, viewHeight)
                                         canvas.translate(dx, dy)
                                         canvas.scale(scale, scale)
                                         canvas.nativeCanvas.drawBitmap(bmp, 0f, 0f, null)
                                         canvas.restore()
                                     }
                                 }
                             }
                         }
                     }
                     drawContext.canvas.restore()

                     // DRAW AUDIO WAVEFORMS (sincronizado com thumbnails)
                     if (audioWaveformsAmplitudes.isNotEmpty()) {
                         val waveformWidth = (videoDurationMs / 1000f) * pxPerSecond
                         val waveformStartX = centerOffset - currentScroll
                         val waveformHeightPx = waveformHeightDp.toPx()
                         val waveformTopY = rulerHeight + thumbnailHeightPx // Logo abaixo das thumbnails

                         android.util.Log.d("TimelineEditor", "Drawing AudioWaveforms: ${audioWaveformsAmplitudes.size} bars, startX=$waveformStartX, width=$waveformWidth")

                         val barSlotWidth = waveformWidth / audioWaveformsAmplitudes.size.coerceAtLeast(1)
                         val barWidthPx = (barSlotWidth * 0.8f).coerceAtLeast(1f)

                         // Clip para não desenhar fora da área
                         drawContext.canvas.save()
                         val clipEnd = centerOffset + (videoDurationMs / 1000f * pxPerSecond) - currentScroll
                         drawContext.canvas.clipRect(0f, waveformTopY, clipEnd, waveformTopY + waveformHeightPx)

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
                        
                        
                        // Ticks White
                        val tickColor = Color.White
                        val stroke = if (isSecond) 2.dp.toPx() else 1.dp.toPx()
                        
                        // Ticks drawn in the top ruler area
                        val tickStartY = 0f
                        val tickEndY = tickHeight.coerceAtMost(rulerHeight)

                        drawLine(
                            tickColor,
                            Offset(x, tickStartY),
                            Offset(x, tickEndY),
                            stroke
                        )
                        
                        // TEXT REMOVED per user request
                    }

                    trimPosition.completeRanges.forEach { (start, end) ->
                        val startX = centerOffset + (start / 1000f) * pxPerSecond - currentScroll
                        val endX = centerOffset + (end / 1000f) * pxPerSecond - currentScroll
                        if (endX >= 0 && startX <= size.width) {
                            val rangeY = 4.dp.toPx()
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
                            
                            val rangeY = 4.dp.toPx()
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



        Spacer(modifier = Modifier.height(10.dp))

        // 5. RANGE LIST (Moved down)
        extraContent()
        
        Spacer(modifier = Modifier.weight(1f))

    }
}
