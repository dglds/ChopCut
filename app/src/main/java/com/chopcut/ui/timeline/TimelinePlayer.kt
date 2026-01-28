package com.chopcut.ui.timeline

import android.net.Uri
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.chopcut.R
import timber.log.Timber

/**
 * Componente unificado de controle de edição e visualização (Timeline Player).
 * Integra o Player (ExoPlayer), a Timeline (Strip) e a lógica de sincronização.
 *
 * @param uri URI do vídeo a ser editado.
 * @param previewManager Gerenciador do player (injetado da tela pai).
 * @param modifier Modificador de layout.
 */
@Composable
fun TimelinePlayer(
    uri: Uri,
    previewManager: PreviewManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // ===== 1. ESTADOS DO PLAYER (Fonte da Verdade: PreviewManager) =====
    val currentPosition by previewManager.currentPosition.collectAsStateWithLifecycle()
    val duration by previewManager.duration.collectAsStateWithLifecycle()
    val isReady by previewManager.isReady.collectAsStateWithLifecycle()
    val isPlaying by previewManager.isPlaying.collectAsStateWithLifecycle()

    // ===== 2. VIEWMODEL DA TIMELINE (Estado de Edição) =====
    // Instanciamos aqui para controlar os Ranges e o Playhead lógico
    val timelineViewModel = remember(duration) {
        TimelineViewModel(initialDurationMs = duration)
    }
    val timelineState by timelineViewModel.state.collectAsState()

    // ===== 3. ESTADOS LOCAIS DE UI =====
    var isScrubbing by remember { mutableStateOf(false) }
    var isScrolling by remember { mutableStateOf(false) }
    
    // Lista de frames (TimelineStrip)
    val listState = rememberLazyListState()

    // Progresso visual da barra simples (0f a 1f)
    val sliderPosition by remember {
        derivedStateOf {
            if (duration > 0) currentPosition.toFloat() / duration else 0f
        }
    }
    
    // Dados derivados para a Strip
    val frameWidthPx = with(density) { 80.dp.toPx() }
    val frameCount by remember(duration) {
        derivedStateOf {
            if (duration <= 0) 0
            else {
                val framesPerSecond = duration / 1000
                framesPerSecond.coerceAtLeast(10).coerceAtMost(60)
            }
        }
    }

    // ===== 4. SINCRONIZAÇÃO (A "Cola") =====

    // A. Inicialização
    LaunchedEffect(uri) {
        previewManager.prepare(context, uri, coroutineScope)
    }

    LaunchedEffect(duration) {
        if (duration > 0) {
            timelineViewModel.updateTotalDuration(duration)
        }
    }

    // B. Player <-> TimelineViewModel (Bi-direcional, protegido por isScrubbing)
    
    // Player -> ViewModel (Playback normal)
    LaunchedEffect(currentPosition, duration, isScrubbing) {
        if (!isScrubbing && duration > 0) {
            timelineViewModel.updatePlayheadPosition(currentPosition)
        }
    }

    // ViewModel -> Player (Seek commands - quando usuário mexe na timeline)
    LaunchedEffect(timelineState.playheadPositionMs, isScrubbing, duration) {
        if (isScrubbing && duration > 0) {
            Timber.v("Seek Triggered: ${timelineState.playheadPositionMs}ms")
            previewManager.seekTo(timelineState.playheadPositionMs)
        }
    }

    // Estado Scrubbing -> Player (Para pausar/otimizar durante drag)
    LaunchedEffect(isScrubbing) {
        previewManager.setScrubbing(isScrubbing)
    }

    // C. TimelineViewModel <-> Scroll da Lista (Sincronia Visual da Fita)

    // Sistema -> Scroll (Auto-scroll durante Play, Reset no Stop)
    LaunchedEffect(timelineState.playheadPositionMs, frameCount, duration, isScrolling) {
        if (!isScrolling && frameCount > 0 && duration > 0) {
            val totalScrollableWidth = frameCount * frameWidthPx
            val progress = timelineState.playheadPositionMs.toFloat() / duration
            val currentScrollPx = progress * totalScrollableWidth

            val targetIndex = (currentScrollPx / frameWidthPx).toInt()
            val targetOffset = (currentScrollPx % frameWidthPx).toInt()

            listState.scrollToItem(
                index = targetIndex,
                scrollOffset = targetOffset
            )
        }
    }
    
    // Monitoramento de Scroll Manual (Auxiliar)
    LaunchedEffect(listState.isScrollInProgress) {
        isScrolling = listState.isScrollInProgress
    }

    // Scroll Manual -> ViewModel (Scrubbing via Fita)
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
        .collect { (index, offset) ->
            if (listState.isScrollInProgress && duration > 0 && frameCount > 0) {
                val msPerFrame = duration / frameCount.toFloat()
                
                val baseTimeMs = index * msPerFrame
                val offsetTimeMs = (offset / frameWidthPx) * msPerFrame
                val newTimeMs = (baseTimeMs + offsetTimeMs).toLong().coerceIn(0, duration)
                
                Timber.v("Scroll Sync: index=$index offset=$offset time=$newTimeMs")
                timelineViewModel.updatePlayheadPosition(newTimeMs)
                isScrubbing = true 
            } else if (!listState.isScrollInProgress && isScrubbing) {
                // Usuário soltou o dedo da lista
                Timber.d("Scroll Sync: Ended")
                isScrubbing = false
            }
        }
    }
    
    // Cleanup
    DisposableEffect(previewManager) {
        onDispose {
            // Não damos release aqui pois o PreviewManager vem de fora (EditorScreen)
            // Mas podemos limpar listeners se houver
        }
    }

    // ===== 5. LAYOUT UI =====
    Column(modifier = modifier) {
        
        // --- Timer (Topo) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
        }

        // --- Preview do Vídeo ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .drawBehind {
                     // LED de Estado (Visual Feedback)
                    val ledSize = size.height * 0.04f
                    val ledPadding = size.height * 0.03f
                    val ledRadius = ledSize / 2
                    val centerX = size.width - ledPadding - ledRadius
                    val centerY = ledPadding + ledRadius
                    val ledColor = when {
                        !isReady -> Color(0xFFD32F2F)
                        !isPlaying -> Color(0xFFF57C00)
                        else -> Color(0xFF388E3C)
                    }
                    drawCircle(color = ledColor, radius = ledRadius, center = Offset(centerX, centerY))
                }
                .clickable { 
                     previewManager.togglePlayPause() 
                },
            contentAlignment = Alignment.Center
        ) {
            if (isReady) {
                AndroidView(
                    factory = { ctx ->
                        val view = LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView
                        view.apply {
                            controllerShowTimeoutMs = 0
                            player = previewManager.exoPlayer
                            useController = false
                        }
                    },
                    update = { view ->
                        (view as PlayerView).player = previewManager.exoPlayer
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "Carregando...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // --- Barra de Progresso Simples (Visual) ---
        if (isReady && duration > 0) {
            val inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest
            val activeColor = MaterialTheme.colorScheme.primary

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .drawBehind {
                        drawRect(color = inactiveColor)
                        drawRect(
                            color = activeColor,
                            topLeft = Offset(0f, 0f),
                            size = androidx.compose.ui.geometry.Size(size.width * sliderPosition, size.height)
                        )
                    }
            )
        }

        // --- Controles de Playback ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { previewManager.togglePlayPause() },
                    enabled = isReady,
                    modifier = Modifier.size(48.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    if (isPlaying) {
                        Text(text = "⏸", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Icon(Icons.Default.PlayArrow, "Play", Modifier.size(24.dp))
                    }
                }

                Button(
                    onClick = { previewManager.stop() },
                    enabled = isReady,
                    modifier = Modifier.size(48.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(text = "■", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        
        // --- Controles de Range (Add/Remove) ---
        // TODO: Extrair RangeControls para arquivo próprio se necessário, ou usar inline
        // Por hora, simplificando: A UI de ranges pode ser parte do próprio strip ou externa.
        // Vamos manter o básico aqui.
        if (duration > 0) {
             Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { timelineViewModel.addRange() },
                    enabled = timelineState.selectedRange == null
                ) {
                    Text("Adicionar Corte")
                }
                
                if (timelineState.selectedRange != null) {
                    Button(
                        onClick = { timelineViewModel.removeSelectedRange() },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Remover Corte")
                    }
                }
            }
        }

        // --- Fita de Timeline (TimelineStrip) ---
        if (duration > 0) {
            TimelineStrip(
                durationMs = duration,
                playheadPositionMs = timelineState.playheadPositionMs,
                ranges = timelineState.ranges,
                listState = listState,
                onSeek = { ms ->
                    isScrubbing = true
                    timelineViewModel.updatePlayheadPosition(ms)
                },
                onRangeSelect = { id -> timelineViewModel.selectRange(id) },
                onRangeStartChange = { ms -> timelineViewModel.updateSelectedRangeStart(ms) },
                onRangeEndChange = { ms -> timelineViewModel.updateSelectedRangeEnd(ms) },
                onScrubStart = { isScrubbing = true },
                onScrubEnd = { 
                    isScrubbing = false
                    previewManager.seekTo(timelineState.playheadPositionMs) 
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Formata milissegundos para MM:SS:m.
 */
private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val tenths = (ms % 1000) / 100
    return "%02d:%02d:%d".format(minutes, seconds, tenths)
}
