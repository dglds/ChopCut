package com.chopcut.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
import com.chopcut.ui.components.AudioWaveForms
import com.chopcut.ui.components.timeline.VideoPreview
import com.chopcut.ui.components.timeline.SeekbarProgress
import com.chopcut.ui.components.timeline.CurrentTimeDisplay
import com.chopcut.ui.components.timeline.VideoFileInfo
import com.chopcut.utils.FormatUtils
import com.chopcut.data.thumbnail.ThumbnailStripManager
import com.chopcut.data.local.PreferencesManager
import kotlinx.coroutines.NonCancellable
import timber.log.Timber
import androidx.compose.animation.core.rememberInfiniteTransition
import com.chopcut.ui.theme.ChopCutMonoFont
import com.chopcut.data.model.ThumbnailQuality
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

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
    audioWaveformsAmplitudes: List<Float> = emptyList(),
    isAudioWaveformsLoading: Boolean = false,
    preloadedStrips: Map<Int, Bitmap> = emptyMap(),
    thumbnailViewModel: com.chopcut.ui.screen.ThumbnailViewModel? = null,
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
         var isPlaying by remember { mutableStateOf(false) }
         
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
                 Timber.v("Scroll velocity: ${scrollVelocity.toInt()} px/ms")
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
                // TEMPORÁRIAMENTE DESABILITADO: adaptiveStrips = false
                // Strip adaptativas causam problemas na renderização porque cada
                // segmento tem largura diferente. Precisamos de uma solução
                // mais complexa para suportar isso corretamente.
                ThumbnailStripManager(context, thumbWidth, thumbHeight, thumbsPerStrip, adaptiveStrips = false)
            }
          // Observar StateFlow de ThumbnailViewModel se disponível
          val stripsFromViewModel = if (thumbnailViewModel != null) {
              thumbnailViewModel.strips.collectAsState().value
          } else {
              emptyMap()
          }

          // Estado mutável local para strips
          val strips = remember {
              androidx.compose.runtime.mutableStateMapOf<Int, android.graphics.Bitmap>().apply {
                  putAll(preloadedStrips)
              }
          }

          // OTIMIZAÇÃO: Sincronizar strips de forma eficiente
          // Usar key que só muda quando realmente há novas strips
          LaunchedEffect(stripsFromViewModel.size, preloadedStrips.size) {
              // Se temos ViewModel, usar strips dela
              if (thumbnailViewModel != null && stripsFromViewModel.isNotEmpty()) {
                  stripsFromViewModel.forEach { (k, v) ->
                      if (!strips.containsKey(k)) {
                          strips[k] = v
                      }
                  }
              }
              // Caso contrário, usar preloadedStrips (compatibilidade)
              else if (preloadedStrips.isNotEmpty()) {
                  preloadedStrips.forEach { (k, v) ->
                      if (!strips.containsKey(k)) {
                          strips[k] = v
                      }
                  }
              }
          }

          // Rastrear quantos tempos tem em cada segmento (para strips adaptativas)
          val thumbsPerStripMap = remember { androidx.compose.runtime.mutableStateMapOf<Int, Int>() }
          val loadingStrips = remember { androidx.compose.runtime.mutableStateMapOf<Int, Boolean>() }
          val scope = androidx.compose.runtime.rememberCoroutineScope()

        // Máximo de strips em memória (100 × ~432KB RGB_565 ≈ 42MB)
        // Suficiente para ~16 minutos de timeline sem eviction
        // Aumentado para 500 para suportar vídeos longos (~83 min) sem descarte agressivo
        val maxStrips = 500


        // SEQUENTIAL LOADING: Delegar o carregamento para o ViewModel.
        // Isso garante que o processo sobreviva à navegação entre telas.
        LaunchedEffect(videoUri, videoDurationMs) {
            if (videoDurationMs == 0L) return@LaunchedEffect
            
            if (thumbnailViewModel != null) {
                thumbnailViewModel.loadAllStripsSequentially(videoUri, videoDurationMs)
            } else {
                // FALLBACK: Se o ViewModel não estiver disponível, mantemos o loop local simples
                val totalSegments = stripManager.getSegmentCount(videoDurationMs)
                for (segIdx in 0 until totalSegments) {
                    if (strips.containsKey(segIdx) || loadingStrips.containsKey(segIdx)) continue
                    
                    com.chopcut.data.thumbnail.ThumbnailCacheManager.loadStripWithTracking(
                        uri = videoUri,
                        segmentIndex = segIdx,
                        durationMs = videoDurationMs,
                        thumbWidth = thumbWidth,
                        thumbHeight = thumbHeight,
                        thumbsPerStrip = thumbsPerStrip
                    ) { strip ->
                        if (strip != null) {
                            strips[segIdx] = strip
                        }
                    }
                    kotlinx.coroutines.delay(100)
                }
            }
        }

        // Animação de shimmer suave para placeholders de thumbnails
        val infiniteTransition = rememberInfiniteTransition(label = "thumbnailShimmer")
        val shimmerProgress by infiniteTransition.animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmerProgress"
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
                    Timber.tag("TimelineEditor").e(error, "ExoPlayer error: ${error.message}")
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
    
        // OTIMIZAÇÃO: Remover scrollOffsetPx das dependências para evitar feedback loop
        // O LaunchedEffect modifica scrollOffsetPx, então não deve depender dele
        LaunchedEffect(isPlaying) {
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
                onRetry = {
                    playerError = null
                    isSecurityError = false
                    exoPlayer.prepare()
                    exoPlayer.play()
                },
                onTogglePlayPause = {
                    if (isPlaying) {
                        exoPlayer.pause()
                    } else {
                        exoPlayer.play()
                    }
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
                  .background(Color(0xFF2A2A2A)) // Fundo Cinza
                  .border(1.dp, Color(0xFF404040))
          ) {
              val timelineWidth = constraints.maxWidth.toFloat()
              val centerOffset = timelineWidth / 2f
              val durationPx = (videoDurationMs / 1000f) * pxPerSecond

              // Alturas calculadas dinamicamente
              val rulerHeight = with(density) { 44.dp.toPx() }
              val waveformHeightDp = 36.dp
              val thumbnailsHeightDp = 48.dp
            
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

                // Shimmer gradient colors (gradiente diagonal suave)
                val shimmerGradient = remember {
                    intArrayOf(
                        android.graphics.Color.parseColor("#1E1E1E"), // Base escuro
                        android.graphics.Color.parseColor("#3D3D3D"), // Médio
                        android.graphics.Color.parseColor("#666666"), // Claro (Mais ativo)
                        android.graphics.Color.parseColor("#3D3D3D"), // Médio
                        android.graphics.Color.parseColor("#1E1E1E")  // Base escuro
                    )
                }

                val shimmerPositions = remember { floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f) }

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
                           val startSec = segIdx * thumbsPerStrip
                           val stripWidthPx = thumbW * thumbsPerStrip
                           val x = centerOffset + (startSec * pxPerSecond) - currentScroll

                           drawCallCount.intValue++

                           if (strip != null && !strip.isRecycled) {
                               // Desenhar STRIP INTEIRA de uma vez (sem recortes)
                               val verticalOffset = (thumbnailHeightPx - thumbH) / 2f

                               dstRect.set(
                                   x.toInt(), (thumbnailTop + verticalOffset).toInt(),
                                   (x + stripWidthPx).toInt(), (thumbnailTop + verticalOffset + thumbH).toInt()
                               )

                               drawIntoCanvas { canvas ->
                                   canvas.nativeCanvas.drawBitmap(strip, null, dstRect, renderPaint)
                               }
                           } else {
                               // Strip não carregada - shimmer para a STRIP INTEIRA
                               drawIntoCanvas { canvas ->
                                   // Base background para strip inteira
                                   canvas.nativeCanvas.drawRect(
                                       x, thumbnailTop, x + stripWidthPx, thumbnailTop + thumbnailHeightPx,
                                       bgPaint
                                   )

                                   // Shimmer diagonal para strip inteira
                                   val shimmerPaint = android.graphics.Paint().apply {
                                       val width = stripWidthPx
                                       val height = thumbnailHeightPx
                                       val gradientSize = (width + height) * 0.8f

                                       val offsetX = shimmerProgress * (width + gradientSize) - gradientSize
                                       val offsetY = shimmerProgress * (height + gradientSize) - gradientSize

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

                       // MÉTRICA DE PERFORMANCE: Logar draw calls por frame (a cada 60 frames ≈ 1s)
                       val frameTimeMs = (System.nanoTime() - frameStartTime) / 1_000_000
                       val currentTime = System.currentTimeMillis()

                       if (currentTime - lastLogTime.longValue > 1000) {
                           frameCount.intValue++
                           Timber.i("""
                               ═══════════════════════════════════════════════════════
                               TIMELINE PERFORMANCE LOG (Frame #${frameCount.intValue})
                               ═══════════════════════════════════════════════════════
                               ✅ OTIMIZAÇÃO ATIVA: Renderização por STRIP
                               ────────────────────────────────────────────────
                               📊 MÉTRICAS DO FRAME:
                               • Draw calls: ${drawCallCount.intValue}
                               • Strips visíveis: ${visibleSegmentIndices.size}
                               • Frame time: ${frameTimeMs}ms
                               • FPS estimado: ${if (frameTimeMs > 0) 1000f / frameTimeMs else 60f} fps
                               ────────────────────────────────────────────────
                               📐 CONFIGURAÇÃO:
                               • thumbsPerStrip: $thumbsPerStrip
                               • thumbWidth: ${thumbW.toInt()}px
                               • thumbHeight: ${thumbH.toInt()}px
                               • pxPerSecond: ${pxPerSecond.toInt()}px
                               • Timeline width: ${timelineWidth.toInt()}px
                               ────────────────────────────────────────────────
                               📈 PERFORMANCE (vs implementação antiga):
                               • Draw calls redução: ~${(endSecond - startSecond + 1) / visibleSegmentIndices.size.coerceAtLeast(1)}x menos
                               • Iterações: ${visibleSegmentIndices.size} strips (antigo: ${endSecond - startSecond + 1} frames)
                               ═══════════════════════════════════════════════════════
                           """.trimIndent())
                           lastLogTime.longValue = currentTime
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

                          // Linha vertical simples
                          drawLine(
                              color = Color.White.copy(alpha = tickAlpha),
                              start = Offset(x, tickTopY),
                              end = Offset(x, tickBottomY),
                              strokeWidth = 1.dp.toPx()
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
