package com.chopcut.ui.timeline

import android.net.Uri
import android.view.LayoutInflater
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.chopcut.R
import com.chopcut.ui.timeline.model.Thumbnail
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Timeline unificada com preview de vídeo integrado.
 * Combina player de vídeo e timeline de edição em um único componente.
 *
 * @param uri URI do vídeo
 * @param previewManager Gerenciador do player
 * @param modifier Modifier
 * @param onVideoClick Callback ao clicar no vídeo
 */
@Composable
fun Timeline(
    uri: Uri,
    previewManager: PreviewManager,
    modifier: Modifier = Modifier,
    onVideoClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Estados do Player
    val currentPosition by previewManager.currentPosition.collectAsStateWithLifecycle()
    val duration by previewManager.duration.collectAsStateWithLifecycle()
    val isReady by previewManager.isReady.collectAsStateWithLifecycle()
    val isPlaying by previewManager.isPlaying.collectAsStateWithLifecycle()

    // ViewModel da Timeline
    val timelineViewModel = remember(duration) {
        TimelineViewModel(initialDurationMs = duration)
    }
    val timelineState by timelineViewModel.state.collectAsState()

    // Estado de scrubbing
    var isScrubbing by remember { mutableStateOf(false) }

    // Estado para thumbnails
    var thumbnails by remember { mutableStateOf<List<Thumbnail>>(emptyList()) }

    // Estado derivado: progresso (0f a 1f)
    val sliderPosition by remember {
        derivedStateOf {
            if (duration > 0) currentPosition.toFloat() / duration else 0f
        }
    }

    // 1. SETUP & METADATA
    LaunchedEffect(uri) {
        previewManager.prepare(context, uri, coroutineScope)
    }

    LaunchedEffect(duration) {
        if (duration > 0) {
            timelineViewModel.updateTotalDuration(duration)
        }
    }

    // 2. PLAYER SYNCHRONIZATION (Two-way bind protected by isScrubbing)
    
    // Player -> Timeline (Playback updates)
    LaunchedEffect(currentPosition, duration, isScrubbing) {
        if (!isScrubbing && duration > 0) {
            timelineViewModel.updatePlayheadPosition(currentPosition)
        }
    }

    // Timeline -> Player (Seek commands)
    LaunchedEffect(timelineState.playheadPositionMs, isScrubbing, duration) {
        if (isScrubbing && duration > 0) {
            previewManager.seekTo(timelineState.playheadPositionMs)
        }
    }

    // Scrubbing State Sync
    LaunchedEffect(isScrubbing) {
        previewManager.setScrubbing(isScrubbing)
    }

    // Cleanup
    DisposableEffect(previewManager) {
        onDispose {
            Timber.d("Timeline disposed")
        }
    }

    Column(modifier = modifier) {
        // ===== TIMER NO TOPO =====
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

        // ===== PLAYER PREVIEW =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .drawBehind {
                    // LED indicador de estado
                    val ledSize = size.height * 0.04f
                    val ledPadding = size.height * 0.03f
                    val ledRadius = ledSize / 2

                    // Posição do LED (top-right)
                    val centerX = size.width - ledPadding - ledRadius
                    val centerY = ledPadding + ledRadius

                    // Cor do LED baseada no estado
                    val ledColor = when {
                        !isReady -> Color(0xFFD32F2F) // Estado inicial/stop - vermelho
                        !isPlaying -> Color(0xFFF57C00) // Pause - amarelo/laranja
                        else -> Color(0xFF388E3C) // Playing - verde
                    }

                    // Glow multicamadas para efeito impressionante
                    // Camada externa mais suave
                    drawCircle(
                        color = ledColor,
                        radius = ledRadius * 4f,
                        center = Offset(centerX, centerY),
                        alpha = 0.08f
                    )
                    // Camada média
                    drawCircle(
                        color = ledColor,
                        radius = ledRadius * 2.5f,
                        center = Offset(centerX, centerY),
                        alpha = 0.15f
                    )
                    // Camada interna brilhante
                    drawCircle(
                        color = ledColor,
                        radius = ledRadius * 1.3f,
                        center = Offset(centerX, centerY),
                        alpha = 0.4f
                    )

                    // LED principal com center brilhante
                    drawCircle(
                        color = ledColor,
                        radius = ledRadius,
                        center = Offset(centerX, centerY)
                    )
                    // Center hotspot (brilho intenso no centro)
                    drawCircle(
                        color = Color.White,
                        radius = ledRadius * 0.4f,
                        center = Offset(centerX, centerY),
                        alpha = 0.6f
                    )
                }
                .clickable { onVideoClick() },
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
                    text = "Loading...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Barra de progresso
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

        // ===== CONTROLES DE PLAYBACK =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botões de playback
            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { previewManager.togglePlayPause() },
                    enabled = isReady,
                    modifier = Modifier.size(48.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    if (isPlaying) {
                        Text(
                            text = "⏸",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Button(
                    onClick = { previewManager.stop() },
                    enabled = isReady,
                    modifier = Modifier.size(48.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(
                        text = "■",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        // ===== CONTROLES DE RANGES =====
        if (duration > 0) {
            RangeControls(
                onAddRange = { timelineViewModel.addRange() },
                onRemoveRange = { 
                    timelineState.selectedRange?.let { timelineViewModel.removeRange(it.id) }
                },
                hasSelectedRange = timelineState.selectedRange != null,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ===== TIMELINE COM THUMBNAILS =====
        if (duration > 0) {
            val listState = rememberLazyListState()

            // Restaurando estados necessários para a lógica de sync
            val frameCount by remember(duration) {
                derivedStateOf {
                    if (duration <= 0) 0
                    else {
                        val framesPerSecond = duration / 1000
                        framesPerSecond.coerceAtLeast(10).coerceAtMost(60)
                    }
                }
            }
            var isScrolling by remember { mutableStateOf(false) }

            // Reset scroll quando a posição volta a 0 (Stop)
            // System Output: Time -> Scroll (Playback, Stop, External Seeks)
            LaunchedEffect(timelineState.playheadPositionMs, frameCount, duration, isScrolling) {
                if (!isScrolling && frameCount > 0 && duration > 0) {
                    val frameWidthPx = with(density) { 80.dp.toPx() }
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
            
            // Detectar estado de scroll manual (auxiliar)
            LaunchedEffect(listState.isScrollInProgress) {
                isScrolling = listState.isScrollInProgress
            }

            // User Input: Scroll -> Time (Scrubbing via list)
            LaunchedEffect(listState) {
                androidx.compose.runtime.snapshotFlow {
                    listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                }
                .collect { (index, offset) ->
                    if (listState.isScrollInProgress && duration > 0 && frameCount > 0) {
                         val frameWidthPx = with(density) { 80.dp.toPx() } // Mesmo valor fixo usado no Strip
                         val msPerFrame = duration / frameCount.toFloat()
                        
                        val baseTimeMs = index * msPerFrame
                        val offsetTimeMs = (offset / frameWidthPx) * msPerFrame
                        val newTimeMs = (baseTimeMs + offsetTimeMs).toLong().coerceIn(0, duration)
                        
                        timelineViewModel.updatePlayheadPosition(newTimeMs)
                        isScrubbing = true 
                    } else if (!listState.isScrollInProgress && isScrubbing) {
                        isScrubbing = false
                    }
                }
            }

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
                onRangeUpdate = { id, start, end -> timelineViewModel.updateRange(id, start, end) },
                onScrubStart = { isScrubbing = true },
                onScrubEnd = { 
                    isScrubbing = false
                    previewManager.seekTo(timelineState.playheadPositionMs) 
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ===== LISTA DE RANGES =====
        if (timelineState.ranges.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(horizontal = 8.dp)
            ) {
                RangesList(
                    ranges = timelineState.ranges,
                    onRangeClick = { rangeId -> timelineViewModel.selectRange(rangeId) }
                )
            }
        }
    }
}

// ===== COMPONENTES INTERNOS =====

/**
 * Controles de adicionar/remover ranges.
 */
@Composable
private fun RangeControls(
    onAddRange: () -> Unit,
    onRemoveRange: () -> Unit,
    hasSelectedRange: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onAddRange,
            enabled = !hasSelectedRange
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Adicionar Range",
                modifier = Modifier.padding(end = 4.dp)
            )
            Text("Adicionar Range")
        }

        Spacer(modifier = Modifier.weight(1f))

        if (hasSelectedRange) {
            Button(
                onClick = onRemoveRange,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Excluir Range",
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text("Excluir Range")
            }
        }
    }
}

/**
 * Lista visual dos ranges.
 */
@Composable
private fun RangesList(
    ranges: List<com.chopcut.ui.components.TrimRangeData>,
    onRangeClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "Ranges (${ranges.size})",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            ranges.forEachIndexed { index, range ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (range.isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                Color.Transparent
                        )
                        .clickable { onRangeClick(range.id) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "#${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(0.15f)
                    )

                    Text(
                        text = "${formatTime(range.startMs)} → ${formatTime(range.endMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(0.7f)
                    )

                    Text(
                        text = "${(range.endMs - range.startMs) / 1000}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.15f)
                    )
                }
            }
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
