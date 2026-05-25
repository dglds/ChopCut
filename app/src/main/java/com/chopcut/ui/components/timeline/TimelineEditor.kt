package com.chopcut.ui.components.timeline

import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.chopcut.ui.components.waveform.AudioWaveForms
import com.chopcut.ui.components.timeline.VideoPreview
import com.chopcut.ui.components.timeline.SeekbarProgress
import com.chopcut.ui.components.timeline.CurrentTimeDisplay
import com.chopcut.ui.components.timeline.VideoFileInfo
import com.chopcut.util.FormatUtils
import com.chopcut.data.thumbnail.ThumbnailStripManager
import com.chopcut.data.local.PreferencesManager
import kotlinx.coroutines.NonCancellable
import androidx.compose.animation.core.rememberInfiniteTransition
import com.chopcut.ui.theme.ChopCutMonoFont
import com.chopcut.data.model.ThumbnailQuality
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import com.chopcut.ui.components.trim.TrimPosition
import com.chopcut.ui.components.waveform.WaveformData
import com.chopcut.ui.components.waveform.WaveformStyle

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
    thumbnailViewModel: com.chopcut.ui.viewmodel.ThumbnailViewModel? = null,
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
            val thumbsPerStrip = remember { PreferencesManager(context).thumbsPerStrip }
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
            com.chopcut.ui.components.player.PlayerManager(
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
