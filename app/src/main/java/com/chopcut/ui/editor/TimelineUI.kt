package com.chopcut

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.collection.LruCache
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber


// --- Merged from TimelineControls.kt ---


@Composable
fun SeekbarProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    // SEEKBAR NEON LUXO
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
            fontWeight = FontWeight.Bold,
            style = TextStyle(
                shadow = Shadow(
                    color = neonColor.copy(alpha = 0.5f),
                    offset = Offset(0f, 0f),
                    blurRadius = 8f
                )
            )
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

// --- Merged from TimelineEditor.kt ---


@Composable
fun TimelineEditor(
    videoUri: Uri,
    trimPosition: TrimPosition,
    currentPosition: Long,
    waveformData: WaveformData = WaveformData.empty(),
    isWaveformLoading: Boolean = false,
    waveformError: String? = null,
    waveformStyle: WaveformStyle = WaveformStyle(),
    audioWaveformsAmplitudes: List<Float> = emptyList(),
    isAudioWaveformsLoading: Boolean = false,
    preloadedStrips: Map<Int, Bitmap> = emptyMap(),
    thumbnailViewModel: ThumbnailViewModel? = null,
    aspectRatio: Float = 16f / 9f,
    onPositionChange: (Long) -> Unit,
    onAddPosition: () -> Unit,
    onRequestNewMedia: (() -> Unit)? = null,
    onVideoDurationChange: ((Long) -> Unit)? = null,
    extraContent: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    showWaveform: Boolean = false
) {
         val context = androidx.compose.ui.platform.LocalContext.current
         val density = LocalDensity.current
         val pxPerSecond = remember { with(density) { 60.dp.toPx() } }
         var scrollOffsetPx by remember { mutableFloatStateOf(0f) }
         var videoDurationMs by remember { mutableLongStateOf(0L) }
         // isPlaying agora vem do PlayerManager (definido após exoPlayer)
         
         // Detecção de velocidade do scroll para pre-fetching adaptativo
         var scrollVelocity by remember { mutableFloatStateOf(0f) }
         var lastScrollOffset by remember { mutableFloatStateOf(0f) }
         var lastScrollTime by remember { mutableLongStateOf(0L) }

         LaunchedEffect(videoDurationMs) {
             if (videoDurationMs > 0 && onVideoDurationChange != null) {
                 onVideoDurationChange(videoDurationMs)
             }
         }
         
         // Detecção de velocidade do scroll para pre-fetching adaptativo
         // Resolve Problema 11: Pre-fetching não é adaptativo
         LaunchedEffect(scrollOffsetPx) {
             val now = System.currentTimeMillis()
             val deltaTime = (now - lastScrollTime).toFloat().coerceAtLeast(1f)
             val deltaOffset = scrollOffsetPx - lastScrollOffset
             
             scrollVelocity = kotlin.math.abs(deltaOffset / deltaTime)
             lastScrollOffset = scrollOffsetPx
             lastScrollTime = now
             
             // Log apenas quando a velocidade mudar significativamente
             if (scrollVelocity > 1000f || scrollVelocity < 100f) {
             }
         }
     
          // Strip-based Thumbnails State
            // Dimensões density-aware: match exato do display = pixel-perfect, sem distorção
            val thumbWidth = remember(density) { with(density) { 60.dp.roundToPx() } }
            val thumbHeight = remember(thumbWidth, aspectRatio) { 
                if (aspectRatio > 0) (thumbWidth / aspectRatio).toInt().coerceAtLeast(1) 
                else with(density) { 40.dp.roundToPx() } 
            }
            val thumbsPerStrip = 10 // Padrão fixo sem PreferencesManager
            val stripManager = remember(thumbWidth, thumbHeight, thumbsPerStrip) {
                // Strip adaptativas ativadas para otimizar carregamento
                // Strips iniciais têm 5 thumbs (rápido), crescendo até 10 thumbs (detalhe)
                ThumbnailStripManager(context, thumbWidth, thumbHeight, thumbsPerStrip, adaptiveStrips = true)
            }
          // Observar StateFlow de ThumbnailViewModel se disponível
          val stripsFromViewModel = if (thumbnailViewModel != null) {
              thumbnailViewModel.strips.collectAsState().value
          } else {
              emptyMap()
          }

          // Usar strips do ViewModel diretamente (reativo via collectAsState)
          // Fallback para preloadedStrips se ViewModel não disponível
          val strips: Map<Int, android.graphics.Bitmap> = if (thumbnailViewModel != null) {
              stripsFromViewModel
          } else {
              preloadedStrips
          }

          // Rastrear quantos tempos tem em cada segmento (para strips adaptativas)
          val thumbsPerStripMap = remember { androidx.compose.runtime.mutableStateMapOf<Int, Int>() }
          val loadingStrips = remember { androidx.compose.runtime.mutableStateMapOf<Int, Boolean>() }
          val scope = androidx.compose.runtime.rememberCoroutineScope()

        // Máximo de strips em memória (100 × ~432KB RGB_565 ≈ 42MB)
        // Suficiente para ~16 minutos de timeline sem eviction
        // Aumentado para 500 para suportar vídeos longos (~83 min) sem descarte agressivo
        val maxStrips = 500

        // Total de segmentos para o vídeo atual (usado em culling e renderização)
        val totalSegments = remember(videoDurationMs) {
            if (videoDurationMs > 0) stripManager.getSegmentCount(videoDurationMs) else 0
        }

        // Log inicial da timeline
        androidx.compose.runtime.LaunchedEffect(videoUri, totalSegments) {
            Timber.tag("Timeline").i("Timeline inicializada: URI=$videoUri, durationMs=$videoDurationMs, totalSegments=$totalSegments")
        }

        // Animação de shimmer suave - v4.1 (Accelerated)
        // Mais rápida para feedback visual imediato
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

        val playerManager = remember(videoUri) {
            PlayerManager(
                context = context,
                videoUri = videoUri,
                onDurationReady = { duration -> videoDurationMs = duration }
            )
        }
        val exoPlayer = playerManager.exoPlayer
        val isPlaying by playerManager.isPlaying.collectAsState()
        val playerError by playerManager.playerError.collectAsState()
        val isSecurityError by playerManager.isSecurityError.collectAsState()

        var currentTimeMs by remember { mutableLongStateOf(0L) }

        val isInsideRange = remember(trimPosition, currentTimeMs) {
            trimPosition.isPositionInRange(currentTimeMs)
        }

        DisposableEffect(playerManager) {
            onDispose { 
                Timber.tag("TimelineComplete").i("Timeline session finalizada: URI=$videoUri, strips carregadas=${strips.size}, total segments=$totalSegments")
                playerManager.release() 
            }
        }

        // Auto-launch recovery if security error
        LaunchedEffect(isSecurityError) {
            // Mensagem de erro já é informativa no PlayerManager
        }

        // OTIMIZAÇÃO: Remover scrollOffsetPx das dependências para evitar feedback loop
        LaunchedEffect(isPlaying) {
            if (!isPlaying) return@LaunchedEffect
            while (isActive) {
                val pos = playerManager.currentPosition
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
                if (kotlin.math.abs(playerManager.currentPosition - playerPos) > 30) {
                    playerManager.seekTo(playerPos.toLong())
                }
            }
        }
    
        // Cleanup: NÃO reciclar bitmaps — são compartilhados com ThumbnailCacheManager
        // A gestão de memória é feita pelo cache LRU e trimMemory() do ViewModel

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF121212)),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VideoFileInfo(
                fileInfo = FormatUtils.getFileInfo(context, videoUri, videoDurationMs)
            )

            VideoPreview(
                exoPlayer = exoPlayer,
                isPlaying = isPlaying,
                isInsideRange = isInsideRange,
                playerError = playerError,
                isSecurityError = isSecurityError,
                onRequestNewMedia = onRequestNewMedia,
                onRetry = { playerManager.retry() },
                onTogglePlayPause = {
                    if (isPlaying) playerManager.pause() else playerManager.play()
                }
            )

        // 2. PASSIVE SEEKBAR (Visual Playback Bar) - Custom Sharp
        val progress = if (videoDurationMs > 0) currentTimeMs.toFloat() / videoDurationMs.toFloat() else 0f
        SeekbarProgress(progress = progress)

        // 2.5. CURRENT TIME DISPLAY (Outside timeline container, below video)
        CurrentTimeDisplay(
            currentTimeMs = currentTimeMs,
            isInsideRange = isInsideRange
        )

          // 3. TIMELINE RULER (Moved up, Gray BG, Pause on Scroll)
           Spacer(modifier = Modifier.height(10.dp))
           BoxWithConstraints(
               modifier = Modifier
                   .fillMaxWidth()
                   .height(150.dp)
                   .background(Color(0xFF2A2A2A))
                   .border(1.dp, Color(0xFF404040))
           ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    
                    val filmHeight = 12.dp.toPx()
                    val holeWidth = 6.dp.toPx()
                    val holeHeight = 4.dp.toPx()
                    val frameSpacing = 20.dp.toPx()
                    val frameNumber = 8.dp.toPx()
                    
                    val amberDark = Color(0xFF4A2C0A)
                    val amberMid = Color(0xFF6B4423)
                    val amberLight = Color(0xFF8B5A2B)
                    val metalSheen = Color(0xFFE8DCC8)
                    
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                amberDark.copy(alpha = 0.9f),
                                amberMid.copy(alpha = 0.85f),
                                amberLight.copy(alpha = 0.9f),
                                amberMid.copy(alpha = 0.85f),
                                amberDark.copy(alpha = 0.9f)
                            )
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(width, filmHeight)
                    )
                    
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                amberDark.copy(alpha = 0.9f),
                                amberMid.copy(alpha = 0.85f),
                                amberLight.copy(alpha = 0.9f),
                                amberMid.copy(alpha = 0.85f),
                                amberDark.copy(alpha = 0.9f)
                            )
                        ),
                        topLeft = Offset(0f, height - filmHeight),
                        size = Size(width, filmHeight)
                    )
                    
                     val startX = ((scrollOffsetPx * -1) % frameSpacing)
                     for (x in startX.toInt() - frameSpacing.toInt()..(width.toInt() + frameSpacing.toInt()) step frameSpacing.toInt()) {
                         if (x < 0) continue
                         
                         val holeYTop = 4.dp.toPx()
                         val holeYBottom = height - filmHeight + holeYTop
                         
                         drawRoundRect(
                             color = Color.Black.copy(alpha = 0.7f),
                             topLeft = Offset(x.toFloat() - holeWidth / 2, holeYTop),
                             size = Size(holeWidth, holeHeight),
                             cornerRadius = androidx.compose.ui.geometry.CornerRadius(holeWidth / 2)
                         )
                         
                         drawRoundRect(
                             color = Color.Black.copy(alpha = 0.7f),
                             topLeft = Offset(x.toFloat() - holeWidth / 2, holeYBottom),
                             size = Size(holeWidth, holeHeight),
                             cornerRadius = androidx.compose.ui.geometry.CornerRadius(holeWidth / 2)
                         )
                         
                         val frameNum = ((x + scrollOffsetPx) / frameSpacing).toInt()
                         if (frameNum >= 0) {
                             val text = frameNum.toString().padStart(3, '0')
                             drawIntoCanvas { canvas ->
                                 val paint = android.graphics.Paint().apply {
                                     color = metalSheen.copy(alpha = 0.6f).hashCode()
                                     textSize = 8.dp.toPx()
                                     typeface = android.graphics.Typeface.create(
                                        android.graphics.Typeface.MONOSPACE,
                                        android.graphics.Typeface.BOLD
                                    )
                                    isAntiAlias = true
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }
                                canvas.nativeCanvas.drawText(
                                    text,
                                    x.toFloat(),
                                    filmHeight - 2.dp.toPx(),
                                    paint
                                )
                                canvas.nativeCanvas.drawText(
                                    text,
                                    x.toFloat(),
                                    height - 2.dp.toPx(),
                                    paint
                                )
                            }
                        }
                        
                        drawRect(
                            color = metalSheen.copy(alpha = 0.15f),
                            topLeft = Offset(x.toFloat() - 1.dp.toPx(), 0f),
                            size = Size(0.5.dp.toPx(), filmHeight)
                        )
                        drawRect(
                            color = metalSheen.copy(alpha = 0.15f),
                            topLeft = Offset(x.toFloat() - 1.dp.toPx(), height - filmHeight),
                            size = Size(0.5.dp.toPx(), filmHeight)
                        )
                    }
                    
                    drawLine(
                        color = metalSheen.copy(alpha = 0.3f),
                        start = Offset(0f, filmHeight),
                        end = Offset(width, filmHeight),
                        strokeWidth = 0.5.dp.toPx()
                    )
                    drawLine(
                        color = metalSheen.copy(alpha = 0.3f),
                        start = Offset(0f, height - filmHeight),
                        end = Offset(width, height - filmHeight),
                        strokeWidth = 0.5.dp.toPx()
                    )
                }

                 val timelineWidth = constraints.maxWidth.toFloat()
                val centerOffset = timelineWidth / 2f
               val durationPx = (videoDurationMs / 1000f) * pxPerSecond

               // ON-DEMAND LOADING: Carrega strips visíveis + buffer conforme usuário rola
               androidx.compose.runtime.LaunchedEffect(videoUri, videoDurationMs, scrollOffsetPx, timelineWidth) {
                   if (videoDurationMs == 0L || thumbnailViewModel == null) return@LaunchedEffect
                   
                   // Calcular posição atual em segundos
                   val currentSecond = ((scrollOffsetPx - centerOffset) / pxPerSecond).toInt()
                       .coerceAtLeast(0)
                   
                   // Calcular largura da viewport em segundos
                   val viewportWidthSeconds = (timelineWidth / pxPerSecond).toInt()
                   
                   Timber.tag("TimelineViewport").d("Viewport mudou: second=$currentSecond, width=${viewportWidthSeconds}s, scroll=$scrollOffsetPx")
                   
                   // Carregar strips visíveis + buffer
                   thumbnailViewModel.loadVisibleStripsWithBuffer(
                       uri = videoUri,
                       durationMs = videoDurationMs,
                       currentSecond = currentSecond,
                       viewportWidthSeconds = viewportWidthSeconds,
                       bufferSize = 6
                   )
               }

               // Alturas calculadas dinamicamente
              val rulerHeight = with(density) { 44.dp.toPx() }
              val waveformHeightDp = 36.dp
              val thumbnailsHeightDp = 48.dp
            
            // OTIMIZAÇÃO: Precisão over Velocidade
            // Aplicamos um multiplicador de 0.7f (dampening) para tornar o scroll mais "pesado" e preciso.
            val scrollableState = androidx.compose.foundation.gestures.rememberScrollableState { delta ->
                // PAUSE ON MANIPULATION
                if (isPlaying) {
                    playerManager.pause()
                }
                
                // Aplicar amortecimento (30% de redução na velocidade/sensibilidade)
                val dampenedDelta = delta * 0.7f
                
                val newOffset = (scrollOffsetPx - dampenedDelta).coerceIn(0f, durationPx)
                val consumed = scrollOffsetPx - newOffset
                scrollOffsetPx = newOffset
                
                // Retornar quanto delta foi "consumido" (em termos do delta original)
                // Se o offset mudou, consideramos que o delta foi consumido proporcionalmente
                if (delta == 0f) 0f else (consumed / 0.7f)
            }

            // FlingBehavior customizado para parar mais rápido (Reduzimos a velocidade inicial da inércia em 50%)
            val defaultFling = androidx.compose.foundation.gestures.ScrollableDefaults.flingBehavior()
            val flingBehavior = remember(defaultFling) {
                object : androidx.compose.foundation.gestures.FlingBehavior {
                    override suspend fun androidx.compose.foundation.gestures.ScrollScope.performFling(initialVelocity: Float): Float {
                        // Reduzir a inércia para dar mais controle (Parar mais rápido)
                        return with(defaultFling) {
                            performFling(initialVelocity * 0.5f)
                        }
                    }
                }
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
                 }
                 } // Fim do if (showWaveform)

                 // PAINT ALLOCATIONS MOVED OUTSIDE LOOP (Optimization)
                 // Pré-alocar objetos fora do draw loop (zero allocations por frame durante scroll)
                 // srcRect REMOVIDO: não mais necessário com renderização por strip inteira
                 // val srcRect = remember { android.graphics.Rect() } // Remember para sobreviver a recomposições
                 val dstRect = remember { android.graphics.Rect() }

                // Paint com bilinear filtering para render suave da strip→tela
                val renderPaint = remember {
                   android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                       isAntiAlias = true
                       isDither = true
                   }
                }

                // Paint para background do placeholder (base)
                val bgPaint = remember {
                    android.graphics.Paint().apply {
                       color = android.graphics.Color.parseColor("#1E1E1E")
                       style = android.graphics.Paint.Style.FILL
                    }
                }

                // Shimmer gradient colors (v4.0: Ambient Flow - Neutral & Professional)
                // Usando cinzas profundos com transições muito largas para suavidade máxima
                val shimmerGradient = remember {
                    intArrayOf(
                        android.graphics.Color.parseColor("#121212"), // Charcoal Base
                        android.graphics.Color.parseColor("#1E1E1E"), // Deep Gray
                        android.graphics.Color.parseColor("#2A2A2A"), // Ambient Light
                        android.graphics.Color.parseColor("#3D3D3D"), // Subtle Glow
                        android.graphics.Color.parseColor("#2A2A2A"), // Ambient Light
                        android.graphics.Color.parseColor("#1E1E1E"), // Deep Gray
                        android.graphics.Color.parseColor("#121212")  // Charcoal Base
                    )
                }

                val shimmerPositions = remember { floatArrayOf(0f, 0.1f, 0.35f, 0.5f, 0.65f, 0.9f, 1f) }

                  // Paint para timestamps da régua (pré-alocado, fora do draw loop)
                  val robotoMonoTypeface = remember {
                      androidx.core.content.res.ResourcesCompat.getFont(context, com.chopcut.R.font.roboto_mono)
                  }
                  val timestampPaint = remember(robotoMonoTypeface) {
                      android.graphics.Paint().apply {
                          color = android.graphics.Color.parseColor("#808080")
                          textSize = with(density) { 12.dp.toPx() }
                          textAlign = android.graphics.Paint.Align.CENTER
                          typeface = robotoMonoTypeface ?: android.graphics.Typeface.MONOSPACE
                          isAntiAlias = true
                          letterSpacing = 0f
                       }
                   }

                   // MÉTRICA DE PERFORMANCE: Variáveis para tracking (antes do Canvas)
                   val drawCallCount = remember { androidx.compose.runtime.mutableIntStateOf(0) }
                   val frameCount = remember { androidx.compose.runtime.mutableIntStateOf(0) }
                   val lastLogTime = remember { androidx.compose.runtime.mutableLongStateOf(0L) }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Régua: ticks a cada 0.1s, timestamps a cada 5s
                        val tickSpacingSeconds = 0.1f
                        val tickSpacingPx = pxPerSecond * tickSpacingSeconds
                        val startTickIndex = ((currentScroll - centerOffset) / tickSpacingPx).toInt() - 1
                        val endTickIndex = ((currentScroll - centerOffset + timelineWidth) / tickSpacingPx).toInt() + 2
                        
                        // Largura dos ticks baseada no zoom - mais fino quando zoomed out
                        val tickWidth = (1f / density.density).coerceAtMost(1f)
                        val tickWidthSecond = (1.5f / density.density).coerceAtMost(1.5f)


                     val rulerTopY = 0f
                     val rulerThumbGap = 6.dp.toPx()

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
                       // OTIMIZAÇÃO: Culling agressivo - sem buffer para renderizar ~30% menos strips
                       val endSecond = ((currentScroll - centerOffset + timelineWidth) / pxPerSecond).toInt()

                        // OTIMIZAÇÃO: Calcular segmentos visíveis para buscar strips uma vez
                       val visibleSegmentIndices = mutableSetOf<Int>()
                       for (sec in startSecond..endSecond) {
                           val segIdx = sec / thumbsPerStrip
                           visibleSegmentIndices.add(segIdx)
                       }

                       // MÉTRICA DE PERFORMANCE: Iniciar contagem
                       drawCallCount.intValue = 0
                       val frameStartTime = System.nanoTime()


                       // ═══════════════════════════════════════════════════════════════
                       // ✅ NOVO: Renderização por STRIP (OTIMIZADO - 30x menos draw calls)
                       // ═══════════════════════════════════════════════════════════════
                        for (segIdx in visibleSegmentIndices) {
                             val strip = strips[segIdx]
                             val thumbsPerStripForThisSeg = stripManager.getThumbsPerStripForSegment(segIdx, totalSegments)
                              val startSec = stripManager.getSegmentStartSecond(segIdx, totalSegments)
                              val stripWidthPx = pxPerSecond * thumbsPerStripForThisSeg
                              val x = centerOffset + (startSec * pxPerSecond) - currentScroll

                             drawCallCount.intValue++

                             // 1. Desenhar a STRIP se disponível (sem shimmer por baixo)
                             if (strip != null && !strip.isRecycled) {
                                 val verticalOffset = (thumbnailHeightPx - thumbH) / 2f
                                 
                                 // OTIMIZAÇÃO CRÍTICA: Calcular largura baseada na duração real do bitmap
                                 // Se o bitmap tem 1 frame (overview), ele deve ocupar apenas pxPerSecond.
                                 // Se tem 20 frames (detailed), ele ocupa pxPerSecond * 20.
                                 val actualDurationSeconds = strip.width.toFloat() / thumbW
                                 val actualStripVisualWidth = actualDurationSeconds * pxPerSecond

                                 dstRect.set(
                                     x.toInt(), (thumbnailTop + verticalOffset).toInt(),
                                     (x + actualStripVisualWidth).toInt(), (thumbnailTop + verticalOffset + thumbH).toInt()
                                 )

                                 drawIntoCanvas { canvas ->
                                     canvas.nativeCanvas.drawBitmap(strip, null, dstRect, renderPaint)
                                 }
                             } else {
                                 // 2. Desenhar Shimmer apenas quando não há strip disponível
                                 drawIntoCanvas { canvas ->
                                     // Base background
                                     canvas.nativeCanvas.drawRect(
                                         x, thumbnailTop, x + stripWidthPx, thumbnailTop + thumbnailHeightPx,
                                         bgPaint
                                     )

                                     // Shimmer diagonal suave - SEQÜENCIAL (Phase-Shift por segIdx)
                                     val shimmerPaint = android.graphics.Paint().apply {
                                         val width = stripWidthPx
                                         val height = thumbnailHeightPx
                                         val gradientSize = (width + height) * 0.8f

                                         // Aplicar deslocamento de fase baseado no índice do segmento
                                         // v4.0: Flow Sutil (Quase imperceptível, mas orgânico)
                                         val phaseShift = segIdx * 0.02f 
                                         var adjustedProgress = shimmerProgress - phaseShift
                                         
                                         // Manter no range -1..2 circulando (wrapping)
                                         val range = 3f
                                         while (adjustedProgress < -1f) adjustedProgress += range
                                         while (adjustedProgress > 2f) adjustedProgress -= range

                                         val offsetX = adjustedProgress * (width + gradientSize) - gradientSize
                                         val offsetY = adjustedProgress * (height + gradientSize) - gradientSize

                                         shader = android.graphics.LinearGradient(
                                             x + offsetX, thumbnailTop + offsetY,
                                             x + offsetX + gradientSize, thumbnailTop + offsetY + gradientSize,
                                             shimmerGradient, shimmerPositions,
                                             android.graphics.Shader.TileMode.CLAMP
                                         )
                                     }
                                     canvas.nativeCanvas.drawRect(
                                         x, thumbnailTop, x + stripWidthPx, thumbnailTop + thumbnailHeightPx,
                                         shimmerPaint
                                     )
                                 }
                             }
                        }

                       // ═══════════════════════════════════════════════════════════════
                       // ❌ ANTIGO: Renderização por SEGUNDO (SUBÓTIMO - mantido para referência)
                       // ═══════════════════════════════════════════════════════════════
                       // Comentado pois causa ~30x mais draw calls
                       /*
                       for (sec in startSecond..endSecond) {
                           val timeMs = sec * 1000L
                           if (timeMs >= videoDurationMs) break

                           val segIdx = sec / thumbsPerStrip
                           val frameInStrip = sec % thumbsPerStrip
                           val x = centerOffset + (sec * pxPerSecond) - currentScroll

                           if (x > size.width || x + pxPerSecond < 0) continue

                           val strip = strips[segIdx]
                            if (strip != null && !strip.isRecycled) {
                                // Recortar o frame correto da strip (mantendo aspect ratio 1:1)
                                // srcRect.set(
                                //     (frameInStrip * thumbW).toInt(), 0,
                                //     ((frameInStrip + 1) * thumbW).toInt(), thumbH.toInt()
                                // )
                                // Manter largura original do thumb para evitar distorção
                                val thumbDisplayWidth = thumbW
                                val thumbDisplayHeight = thumbH

                                // Centralizar verticalmente na área de thumbnails (48dp)
                                val verticalOffset = (thumbnailHeightPx - thumbDisplayHeight) / 2f

                                // dstRect.set(
                                //     x.toInt(), (thumbnailTop + verticalOffset).toInt(),
                                //     (x + thumbDisplayWidth).toInt(), (thumbnailTop + verticalOffset + thumbDisplayHeight).toInt()
                                // )

                                // 🔍 RENDER Strip #$segIdx

                                drawIntoCanvas { canvas ->
                                    // canvas.nativeCanvas.drawBitmap(strip, srcRect, dstRect, renderPaint)
                                }
                             } else {
                                // Strip não carregada - shimmer effect diagonal suave
                                drawIntoCanvas { canvas ->
                                    // Draw base background
                                    canvas.nativeCanvas.drawRect(
                                        x, thumbnailTop, x + pxPerSecond, thumbnailTop + thumbnailHeightPx,
                                        bgPaint
                                    )

                                    // Draw shimmer com gradiente diagonal
                                    val shimmerPaint = android.graphics.Paint().apply {
                                        // Gradiente diagonal (45°) que se move suavemente
                                        val width = pxPerSecond
                                        val height = thumbnailHeightPx
                                        val gradientSize = (width + height) * 0.8f

                                        // Calcular posição do gradiente baseado no shimmerProgress
                                        val offsetX = shimmerProgress * (width + gradientSize) - gradientSize
                                        val offsetY = shimmerProgress * (height + gradientSize) - gradientSize

                                        shader = android.graphics.LinearGradient(
                                            x + offsetX,
                                            thumbnailTop + offsetY,
                                            x + offsetX + gradientSize,
                                            thumbnailTop + offsetY + gradientSize,
                                            shimmerGradient,
                                            shimmerPositions,
                                            android.graphics.Shader.TileMode.CLAMP
                                        )
                                    }

                                         canvas.nativeCanvas.drawRect(
                                              x, thumbnailTop, x + pxPerSecond, thumbnailTop + thumbnailHeightPx,
                                              shimmerPaint
                                          )
                                      }
                                  }
                            }
                       */
                       // ═══════════════════════════════════════════════════════════════
                      drawContext.canvas.restore()

                      // DRAW AUDIO WAVEFORMS (sincronizado com thumbnails)
                      // ⚠️ TEMPORARIAMENTE DESATIVADO para testes de thumbnail
                      if (showWaveform && audioWaveformsAmplitudes.isNotEmpty()) {
                          timber.log.Timber.d("TimelineEditor: Drawing waveform with ${audioWaveformsAmplitudes.size} amplitudes")
                          val waveformWidth = (videoDurationMs / 1000f) * pxPerSecond
                         val waveformStartX = centerOffset - currentScroll
                         val waveformHeightPx = waveformHeightDp.toPx()
                         val waveformTopY = thumbnailTop + thumbnailHeightPx // Logo abaixo das thumbnails


                         val barSlotWidth = waveformWidth / audioWaveformsAmplitudes.size.coerceAtLeast(1)
                         val barWidthPx = (barSlotWidth * 0.8f).coerceAtLeast(1f)

                         // Clip para não desenhar fora da área
                         drawContext.canvas.save()
                         val waveformClipEnd = centerOffset + (videoDurationMs / 1000f * pxPerSecond) - currentScroll
                         drawContext.canvas.clipRect(0f, waveformTopY, waveformClipEnd, waveformTopY + waveformHeightPx)

                         // OTIMIZAÇÃO: Path Batching para Waveforms
                         val wavePath = androidx.compose.ui.graphics.Path()

                         audioWaveformsAmplitudes.forEachIndexed { index, amplitude ->
                             val x = waveformStartX + (index * barSlotWidth)

                             // Só adicionar ao Path se estiver visível
                             if (x + barSlotWidth >= 0f && x <= size.width) {
                                 val normalizedAmp = amplitude.coerceAtLeast(0.01f).coerceAtMost(1.0f)
                                 val barHeight = normalizedAmp * waveformHeightPx

                                 // Desenhar barra centralizada verticalmente na área do waveform
                                 val y = waveformTopY + (waveformHeightPx - barHeight) / 2f
                                 val barStartX = x + (barSlotWidth - barWidthPx) / 2

                                 wavePath.addRect(
                                     androidx.compose.ui.geometry.Rect(
                                         left = barStartX,
                                         top = y,
                                         right = barStartX + barWidthPx,
                                         bottom = y + barHeight
                                     )
                                 )
                             }
                         }
                         
                         drawPath(
                             path = wavePath,
                             color = Color(0xFF00D9FF)
                         )
                         
                        drawContext.canvas.restore()

                       // MÉTRICA DE PERFORMANCE: Logar draw calls por frame (a cada 60 frames ≈ 1s)
                       val frameTimeMs = (System.nanoTime() - frameStartTime) / 1_000_000
                       val currentTime = System.currentTimeMillis()

                      } else {
                          if (showWaveform) {
                              timber.log.Timber.w("TimelineEditor: Waveform not drawn - audioWaveformsAmplitudes.size=${audioWaveformsAmplitudes.size}")
                          }
                      }

                         // Dimming removed to ensure vibrant colors and because elements are now stacked non-overlapping.

                   // Layout da régua: texto no topo, ticks embaixo apontando para as thumbs
                        val tickZoneTop = 20.dp.toPx()
                        val tickZoneHeight = rulerHeight - tickZoneTop
                        val textBaselineY = tickZoneTop - 2.dp.toPx()
                        val tickCenterY = tickZoneTop + (tickZoneHeight / 2)

                        for (i in startTickIndex..endTickIndex) {
                          val tickTimeSec = i * tickSpacingSeconds
                          if (tickTimeSec < 0f || tickTimeSec > videoDurationMs / 1000f) continue

                          val x = centerOffset + (tickTimeSec * pxPerSecond) - currentScroll
                          if (x < -20f || x > size.width + 20f) continue

                          // Hierarquia de 2 níveis: 1s (M), 0.1s (P)
                          val totalSec = (tickTimeSec * 10).toInt() // Multiplicar por 10 para converter 0.1s para int
                          val isSecond = (totalSec % 10 == 0)
                          val isDecim = !isSecond

                          // Alturas proporcionais: M=0.6, P=0.3
                          val tickHeightRatio = when {
                              isSecond -> 0.6f
                              else -> 0.3f
                          }

                          val tickAlpha = when {
                              isSecond -> 0.6f
                              else -> 0.3f
                          }

                           val tickHeight = tickZoneHeight * tickHeightRatio * 0.95f
                           val tickTopY = tickCenterY - (tickHeight / 2)
                           val tickBottomY = tickCenterY + (tickHeight / 2)

                           // Linha vertical com largura baseada no zoom
                           val strokeWidth = if (isSecond) tickWidthSecond else tickWidth
                           drawLine(
                               color = Color.White.copy(alpha = tickAlpha),
                               start = Offset(x, tickTopY),
                               end = Offset(x, tickBottomY),
                               strokeWidth = strokeWidth
                           )

                          // Timestamp acima dos ticks a cada 5 segundos
                          if (isSecond && totalSec % 50 == 0) {
                              val totalSec = (tickTimeSec * 10).toInt() / 10
                              val min = totalSec / 60
                              val sec = totalSec % 60
                              val label = String.format("%02d:%02d", min, sec)
                              drawIntoCanvas { canvas ->
                                  canvas.nativeCanvas.drawText(
                                      label,
                                      x,
                                      textBaselineY,
                                      timestampPaint
                                  )
                              }
                          }
                      }

                      // DRAW RANGES (at the top border, above everything)
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

        Spacer(modifier = Modifier.height(10.dp))

        // 5. RANGE LIST (Moved down)
        extraContent()
        
        Spacer(modifier = Modifier.weight(1f))

    }
}

// --- Merged from TimelineOverlay.kt ---


@Composable
fun TimelineOverlay(
    ranges: List<Pair<Long, Long>>,
    draftStart: Long?,
    currentPosition: Long,
    pxPerSecond: Float,
    scrollOffsetPx: Float,
    timelineWidth: Float,
    modifier: Modifier = Modifier
) {
    val draftColor = Color(0xFF2196F3)  // Azul para draft
    val rangeColor = Color(0xFFE91E63)    // Rosa para ranges confirmados

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerOffset = timelineWidth / 2f

        fun timeToX(timeMs: Long): Float {
            return (timeMs / 1000f) * pxPerSecond - scrollOffsetPx + centerOffset
        }

        // Desenha ranges confirmados (rosa)
        ranges.forEach { (start, end) ->
            val startX = timeToX(start)
            val endX = timeToX(end)
            if (endX >= 0 && startX <= size.width) {
                drawLine(
                    color = rangeColor,
                    start = Offset(startX, 0f),
                    end = Offset(startX, size.height),
                    strokeWidth = 3.dp.toPx()
                )
                drawLine(
                    color = rangeColor,
                    start = Offset(endX, 0f),
                    end = Offset(endX, size.height),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }

        // Desenha draft start (azul)
        draftStart?.let { start ->
            val startX = timeToX(start)
            if (startX >= 0 && startX <= size.width) {
                drawLine(
                    color = draftColor,
                    start = Offset(startX, 0f),
                    end = Offset(startX, size.height),
                    strokeWidth = 4.dp.toPx()
                )
            }
        }
    }
}

// --- Merged from TrimSlider.kt ---


/**
 * Slider de trim para seleção de range de vídeo
 *
 * Componente simplificado para seleção de início e fim
 *
 * @param startPosition Posição inicial (0.0 a 1.0)
 * @param endPosition Posição final (0.0 a 1.0)
 * @param onPositionChange Callback quando as posições mudam (start, end)
 * @param modifier Modificador
 * @param enabled Se o slider está habilitado
 */
@Composable
fun TrimSlider(
    startPosition: Float,
    endPosition: Float,
    onPositionChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var isDraggingStart by remember { mutableStateOf(false) }
    var isDraggingEnd by remember { mutableStateOf(false) }
    var sliderWidth by remember { mutableFloatStateOf(0f) }

    // Garantir ordem correta
    val actualStart = startPosition.coerceAtMost(endPosition)
    val actualEnd = endPosition.coerceAtLeast(startPosition)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = ChopCutSpacing.md)
    ) {
        // Slider visual
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .align(Alignment.Center)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = ChopCutSpacing.trimHandleSize / 2)
            ) {
                val width = size.width
                val height = size.height
                val centerY = height / 2
                sliderWidth = width

                // Linha de fundo (track)
                drawLine(
                    color = TimelineTrack,
                    start = Offset(0f, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = 4.dp.toPx()
                )

                // Linha de seleção (entre os handles)
                drawLine(
                    color = Playhead.copy(alpha = 0.5f),
                    start = Offset(width * actualStart, centerY),
                    end = Offset(width * actualEnd, centerY),
                    strokeWidth = 4.dp.toPx()
                )
            }

            // Handle inicial
            TrimHandle(
                position = actualStart,
                isDragging = isDraggingStart,
                onDragStart = { isDraggingStart = true },
                onDrag = { delta ->
                    val newPosition = actualStart + delta / sliderWidth
                    val clamped = newPosition.coerceIn(0f, actualEnd - 0.05f)
                    onPositionChange(clamped, actualEnd)
                },
                onDragEnd = { isDraggingStart = false },
                enabled = enabled
            )

            // Handle final
            TrimHandle(
                position = actualEnd,
                isDragging = isDraggingEnd,
                onDragStart = { isDraggingEnd = true },
                onDrag = { delta ->
                    val newPosition = actualEnd + delta / sliderWidth
                    val clamped = newPosition.coerceIn(actualStart + 0.05f, 1f)
                    onPositionChange(actualStart, clamped)
                },
                onDragEnd = { isDraggingEnd = false },
                enabled = enabled
            )
        }
    }
}

/**
 * Handle do slider
 */
@Composable
private fun TrimHandle(
    position: Float,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    enabled: Boolean
) {
    var offsetX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(if (enabled) {
                Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            onDragStart()
                            offsetX = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            onDrag(offsetX)
                        },
                        onDragEnd = {
                            onDragEnd()
                            offsetX = 0f
                        }
                    )
                }
            } else Modifier),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = (position * 1000000).dp) // Hack: será ajustado no layout real
                .size(ChopCutSpacing.trimHandleSize)
                .background(
                    if (isDragging) Playhead else Surface,
                    RectangleShape
                )
                .then(
                    if (isDragging) {
                        Modifier.border(
                            2.dp,
                            OnSurface,
                            RectangleShape
                        )
                    } else {
                        Modifier.border(
                            2.dp,
                            Playhead.copy(alpha = 0.5f),
                            RectangleShape
                        )
                    }
                )
        )
    }
}

/**
 * Slider de posição simples (playhead)
 *
 * @param position Posição atual (0.0 a 1.0)
 * @param onPositionChange Callback quando posição muda
 * @param modifier Modificador
 */
@Composable
fun PositionSlider(
    position: Float,
    onPositionChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var sliderWidth by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = ChopCutSpacing.md)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .align(Alignment.Center)
        ) {
            val width = size.width
            val height = size.height
            val centerY = height / 2
            sliderWidth = width

            // Track
            drawLine(
                color = TimelineTrack,
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 4.dp.toPx()
            )
        }

        // Thumb
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .align(Alignment.Center)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // TODO(human): Implementar lógica de drag
                            // Calcular nova posição baseada no dragAmount.x
                        },
                        onDragEnd = { isDragging = false }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .padding(start = (position * 1000000).dp) // Hack temporário
                    .size(20.dp)
                    .background(
                        if (isDragging) Playhead else Surface,
                        RectangleShape
                    )
                    .border(
                        2.dp,
                        Playhead,
                        RectangleShape
                    )
            )
        }
    }
}

// --- Merged from VideoPreview.kt ---


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
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
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

// --- Merged from VideoTimeline.kt ---


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

// --- Merged from WaveformView.kt ---


/**
 * Visualizador de waveform (forma de onda do áudio)
 *
 * Exibe a representação visual do áudio do vídeo
 *
 * @param samples Amostras do áudio (valores de 0 a 1, normalizados)
 * @param highlightedRange Range destacado (startRatio a endRatio, 0-1)
 * @param modifier Modificador
 */
@Composable
fun WaveformView(
    samples: List<Float>,
    highlightedRange: ClosedRange<Float>? = null, // 0.0 a 1.0
    modifier: Modifier = Modifier
) {
    val backgroundColor = TimelineBackground
    val waveformColor = Waveform
    val highlightColor = Playhead

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(ChopCutSpacing.waveformHeight)
            .background(backgroundColor, RectangleShape)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        if (samples.isEmpty()) {
            // Desenhar linha central se não há amostras
            drawLine(
                color = waveformColor.copy(alpha = 0.3f),
                start = androidx.compose.ui.geometry.Offset(0f, centerY),
                end = androidx.compose.ui.geometry.Offset(width, centerY),
                strokeWidth = 1.dp.toPx()
            )
            return@Canvas
        }

        // Número de barras baseado na largura
        val barCount = samples.size.coerceAtMost(width.toInt() / 4)
        val barWidth = width / barCount
        val step = samples.size.toFloat() / barCount.toFloat()

        // Desenhar waveform
        for (i in 0 until barCount) {
            val startIndex = (i * step).toInt().coerceAtMost(samples.size - 1)
            val endIndex = ((i + 1) * step).toInt().coerceAtMost(samples.size)
            val sample = samples.subList(startIndex, endIndex).maxOrNull() ?: 0f

            val x = i * barWidth
            val barHeight = (sample * height * 0.8f).coerceAtLeast(2.dp.toPx())

            // Verificar se está na área destacada
            val ratio = i.toFloat() / barCount.toFloat()
            val isHighlighted = highlightedRange?.contains(ratio) == true

            val color = if (isHighlighted) highlightColor else waveformColor

            // Desenhar barra (centrada verticalmente)
            drawRoundRect(
                color = color.copy(alpha = if (isHighlighted) 1f else 0.6f),
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = x + 1.dp.toPx(),
                    y = centerY - barHeight / 2
                ),
                size = androidx.compose.ui.geometry.Size(
                    width = (barWidth - 2.dp.toPx()).coerceAtLeast(1.dp.toPx()),
                    height = barHeight
                ),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }

        // Desenhar linha central
        drawLine(
            color = waveformColor.copy(alpha = 0.2f),
            start = androidx.compose.ui.geometry.Offset(0f, centerY),
            end = androidx.compose.ui.geometry.Offset(width, centerY),
            strokeWidth = 1.dp.toPx()
        )
    }
}

/**
 * Visualizador de waveform simples (linha contínua)
 *
 * Alternativa ao estilo de barras, mais leve para renderizar
 *
 * @param samples Amostras do áudio (valores de 0 a 1, normalizados)
 * @param modifier Modificador
 */
@Composable
fun SimpleWaveformView(
    samples: List<Float>,
    modifier: Modifier = Modifier
) {
    val waveformColor = Waveform
    val backgroundColor = TimelineBackground

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(ChopCutSpacing.waveformHeight)
            .background(backgroundColor, RectangleShape)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        if (samples.isEmpty()) {
            drawLine(
                color = waveformColor.copy(alpha = 0.3f),
                start = androidx.compose.ui.geometry.Offset(0f, centerY),
                end = androidx.compose.ui.geometry.Offset(width, centerY),
                strokeWidth = 1.dp.toPx()
            )
            return@Canvas
        }

        val path = Path()
        val step = width / samples.size.toFloat()

        path.moveTo(0f, centerY)

        for ((i, sample) in samples.withIndex()) {
            val x = i * step
            val y = centerY - (sample * height * 0.4f)
            path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = waveformColor,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

// --- Merged from PlayerManager.kt ---


/**
 * Encapsula a criação e gerenciamento do ExoPlayer.
 *
 * Expõe o estado do player como StateFlows reativos e métodos
 * de controle (play/pause/seek/retry/release).
 *
 * @param onDurationReady chamado quando o player resolve a duração do vídeo
 */
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

    /** Emite a posição atual do player a cada 100ms para evitar congestionamento da UI thread e obter rolagem autônoma ultra-suave. */
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

// --- Merged from VideoFileInfo.kt ---

/**
 * Informações de arquivo de vídeo.
 *
 * Classe de dados para armazenar informações de arquivo.
 */
data class VideoFileInfo(
    val fileInfo: String
)

// --- Merged from VideoTimelineViewModel.kt ---


/**
 * ViewModel especializada para gerenciar sprites de thumbnails da timeline.
 *
 * Responsabilidades:
 * - Gerenciar cache de sprites em memória (StateFlow reativo)
 * - Cache LRU persistente entre navegações
 * - Extrair sprites sequencialmente sem delay (a todo vapor)
 * - Render-first: emitir sprite para state IMEDIATAMENTE, depois cachear
 *
 * Escopo: Activity (compartilhada entre navegações)
 */
class VideoTimelineViewModel(
    application: Application
) : AndroidViewModel(application) {
    
    // ========== CONSTANTES ==========
    
    private companion object {
        const val SPRITE_COLS = 3
        const val SPRITE_ROWS = 1
        const val THUMBS_PER_SPRITE = SPRITE_COLS * SPRITE_ROWS  // 3
        const val FRAME_INTERVAL_US = 1_000_000L  // 1 FPS (1 frame a cada 1000ms)
        const val CACHE_PERCENTAGE = 0.10f  // 10% do total
        const val MAX_CACHE_SPRITES = 900
        const val BATCH_SIZE = 3
        
        // Multi-threading: 2 threads fixas
        const val MAX_CONCURRENT_EXTRACTIONS = 2
    }
    
    // ========== ESTADO REATIVO (sprites prontos para renderizar) ==========
    
    private val _sprites = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val sprites: StateFlow<Map<Int, Bitmap>> = _sprites.asStateFlow()
    
    // ========== CACHE LRU PERSISTENTE (fallback) ==========
    
    private var spriteCache: LruCache<Int, Bitmap>? = null
    
    // ========== ESTADO DE EXTRAÇÃO ==========
    
    private val _extractionProgress = MutableStateFlow<Float>(0f)
    val extractionProgress: StateFlow<Float> = _extractionProgress.asStateFlow()
    
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()
    
    // ========== DEPENDÊNCIAS ==========
    
    private var extractor: FastFrameExtractor? = null
    private var activeUri: Uri? = null
    private var activeDurationMs = 0L
    private var loadingJob: Job? = null
    
    // ========== DIMENSÕES ==========
    
    private var thumbWidth = 0
    private var thumbHeight = 0
    
    // ========== MÉTODOS PÚBLICOS ==========
    
    /**
     * Carrega sprites para um vídeo com render-first.
     *
     * Processo:
     * 1. Verifica se já carregou (cache hit)
     * 2. Prepara FastFrameExtractor
     * 3. Extrai sprites sequencialmente sem delay
     * 4. Para cada sprite: emitir para state (render-first) + cachear no LRU
     *
     * @param uri URI do vídeo
     * @param durationMs Duração do vídeo em ms
     * @param width Largura da thumbnail
     * @param height Altura da thumbnail
     */
    fun loadSprites(uri: Uri, durationMs: Long, width: Int, height: Int) {
        Timber.d("loadSprites desativada temporariamente para teste de stress")
        _isReady.value = true
        _extractionProgress.value = 1f
        return
    }
    
    /**
     * Obtém um sprite baseado no índice do frame.
     *
     * Prioridade: 1. State reativo (pronto para renderizar), 2. Cache LRU (persistente)
     *
     * @param frameIndex Índice do frame
     * @return Sprite Bitmap ou null se não disponível
     */
    fun getSprite(frameIndex: Int): Bitmap? {
        val spriteIndex = frameIndex / THUMBS_PER_SPRITE
        
        // Tentar do state (pronto para renderizar)
        val fromState = _sprites.value[spriteIndex]
        if (fromState != null && !fromState.isRecycled) {
            return fromState
        }
        
        // Tentar do cache LRU (persistente)
        val fromCache = spriteCache?.get(spriteIndex)
        if (fromCache != null && !fromCache.isRecycled) {
            return fromCache
        }
        
        return null
    }
    
    /**
     * Verifica se um frame específico está pronto.
     *
     * @param frameIndex Índice do frame
     * @return true se frame está pronto (sprite disponível), false caso contrário
     */
    fun isFrameReady(frameIndex: Int): Boolean {
        return getSprite(frameIndex) != null
    }

    /**
     * Verifica se um frame específico veio do cache.
     *
     * @param frameIndex Índice do frame
     * @return true se frame veio do cache (não extraído agora), false caso contrário
     */
    fun isFrameFromCache(frameIndex: Int): Boolean {
        val spriteIndex = frameIndex / THUMBS_PER_SPRITE
        return spriteCache?.get(spriteIndex) != null
    }
    
    /**
     * Obtém o número total de frames para a duração atual.
     *
     * @return Total de frames
     */
    fun getTotalFrames(): Int {
        return (activeDurationMs / 1000).toInt().coerceAtLeast(1)
    }
    
    /**
     * Limpa todo o cache e estado.
     *
     * IMPORTANTE: Recicla os bitmaps para liberar memória nativa.
     */
    fun clear() {
        Timber.d("Limpando cache de sprites")
        
        // Reciclar sprites no state
        _sprites.value.values.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        
        // Evict cache LRU
        spriteCache?.evictAll()
        
        // Liberar extractor
        extractor?.release()
        extractor = null
        
        // Limpar estado
        _sprites.value = emptyMap()
        _extractionProgress.value = 0f
        _isReady.value = false
        activeUri = null
        activeDurationMs = 0L
        
        Timber.d("Cache de sprites limpo")
    }
    
    // ========== MÉTODOS PRIVADOS ==========
    
    /**
     * Extrai um sprite contendo 100 frames.
     *
     * Processo:
     * 1. Criar sprite bitmap (10×10 grid)
     * 2. Extrair 100 frames consecutivos usando FastFrameExtractor
     * 3. Desenhar cada frame na posição correta do grid
     * 4. Reciclar frames temporários (importante para evitar OOM)
     *
     * @param spriteIndex Índice do sprite (0, 1, 2, ...)
     * @param totalFrames Total de frames no vídeo
     * @return Sprite Bitmap ou null se falhar
     */
    private suspend fun extractSprite(spriteIndex: Int, totalFrames: Int): Bitmap? {
        val extractor = extractor ?: return null
        
        val startFrame = spriteIndex * THUMBS_PER_SPRITE
        val endFrame = min(startFrame + THUMBS_PER_SPRITE, totalFrames)
        
        // Criar sprite bitmap
        val spriteW = SPRITE_COLS * thumbWidth
        val spriteH = SPRITE_ROWS * thumbHeight
        val sprite = Bitmap.createBitmap(spriteW, spriteH, Bitmap.Config.RGB_565)
        val canvas = Canvas(sprite)
        val paint = Paint()
        
        // Extrair e desenhar frames no sprite
        for (i in 0 until (endFrame - startFrame)) {
            val frameIndex = startFrame + i
            val timeUs = frameIndex * FRAME_INTERVAL_US
            
            val frame = extractor.getFrameAt(timeUs)
            if (frame != null) {
                val col = i % SPRITE_COLS
                val row = i / SPRITE_COLS
                canvas.drawBitmap(
                    frame,
                    col * thumbWidth.toFloat(),
                    row * thumbHeight.toFloat(),
                    paint
                )
                frame.recycle()  // IMPORTANTE: liberar frame temporário
            }
        }
        
        return sprite
    }
    
    // ========== CLEANUP ==========
    
    override fun onCleared() {
        super.onCleared()
        clear()
    }
    
    // ========== FACTORY ==========
    
    class VideoTimelineViewModelFactory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VideoTimelineViewModel::class.java)) {
                return VideoTimelineViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.simpleName}")
        }
    }
}
