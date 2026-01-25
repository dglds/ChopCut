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
import com.chopcut.ui.preview.PreviewManager
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

    // Estado para drag de handles
    var handleDragType by remember { mutableStateOf(HandleDragType.NONE) }
    var dragStartX by remember { mutableFloatStateOf(0f) }
    var dragStartValue by remember { mutableLongStateOf(0L) }

    // Estado derivado: progresso (0f a 1f)
    val sliderPosition by remember {
        derivedStateOf {
            if (duration > 0) currentPosition.toFloat() / duration else 0f
        }
    }

    // Inicializar Player
    LaunchedEffect(uri) {
        previewManager.prepare(context, uri, coroutineScope)
    }

    // Atualizar duração no ViewModel
    LaunchedEffect(duration) {
        if (duration > 0) {
            timelineViewModel.updateTotalDuration(duration)
        }
    }

    // Sincronização Player ↔ Timeline
    LaunchedEffect(currentPosition, duration, isScrubbing) {
        if (!isScrubbing && duration > 0) {
            timelineViewModel.updatePlayheadPosition(currentPosition)
        }
    }

    // Sincronização Timeline → Player
    LaunchedEffect(timelineState.playheadPositionMs, isScrubbing, duration) {
        if (isScrubbing && duration > 0) {
            previewManager.seekTo(timelineState.playheadPositionMs)
        }
    }

    // Atualizar estado de scrubbing no player
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
        // ===== PLAYER PREVIEW =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
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
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Posição atual / Duração total
            Text(
                text = formatTime(currentPosition),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Botões de playback
            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { previewManager.togglePlayPause() },
                    enabled = isReady,
                    modifier = Modifier.size(40.dp),
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
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Button(
                    onClick = { previewManager.stop() },
                    enabled = isReady,
                    modifier = Modifier.size(40.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(
                        text = "■",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Duração total
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ===== CONTROLES DE RANGES =====
        if (duration > 0) {
            RangeControls(
                onAddRange = { timelineViewModel.addRange() },
                onRemoveRange = { timelineViewModel.removeSelectedRange() },
                hasSelectedRange = timelineState.selectedRange != null,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ===== TIMELINE COM THUMBNAILS =====
        if (duration > 0) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(Color.Black.copy(alpha = 0.1f))
            ) {
                val containerWidth = constraints.maxWidth.toFloat()
                val centerX = containerWidth / 2f
                val handleWidthPx = with(density) { 16.dp.toPx() }

                // Extrair thumbnails
                LaunchedEffect(uri, duration) {
                    if (duration > 0) {
                        val thumbnailProvider = ThumbnailProvider(context)
                        val timelineHeightPx = with(density) { 80.dp.toPx().toInt() }
                        val thumbnailWidthPx = (timelineHeightPx * 1f).toInt() // 1:1 aspect ratio

                        thumbnailProvider.extractThumbnails(
                            uri = uri,
                            durationMs = duration,
                            count = 10,
                            width = thumbnailWidthPx,
                            height = timelineHeightPx
                        ).collect { fetchedThumbnails ->
                            thumbnails = fetchedThumbnails
                        }
                    }
                }

                // Thumbnail Strip com scroll automático
                val listState = rememberLazyListState()
                val centerIndex by remember(timelineState.playheadPositionMs, duration, thumbnails.size) {
                    derivedStateOf {
                        if (thumbnails.isEmpty()) 0
                        else {
                            val progress = timelineState.playheadPositionMs.toFloat() / duration
                            ((progress * thumbnails.size).toInt().coerceIn(0, thumbnails.size - 1))
                        }
                    }
                }

                LaunchedEffect(centerIndex) {
                    if (thumbnails.isNotEmpty()) {
                        listState.animateScrollToItem(centerIndex)
                    }
                }

                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items = thumbnails, key = { it.timeMs }) { thumb ->
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .fillMaxHeight()
                                .background(Color.DarkGray)
                        ) {
                            thumb.bitmap?.let { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }

                // Playhead fixo no centro
                Playhead(
                    positionPx = centerX,
                    containerWidth = containerWidth,
                    durationMs = duration,
                    onPositionChanged = { newPx ->
                        val clickProgress = (newPx / containerWidth).coerceIn(0f, 1f)
                        val newTimeMs = (clickProgress * duration).toLong()
                        isScrubbing = true
                        timelineViewModel.updatePlayheadPosition(newTimeMs)
                    },
                    onDragStart = { isScrubbing = true },
                    onDragEnd = {
                        isScrubbing = false
                        previewManager.seekTo(timelineState.playheadPositionMs)
                    }
                )

                // Ranges
                timelineState.ranges.forEach { range ->
                    val startProgress = range.startMs.toFloat() / duration
                    val endProgress = range.endMs.toFloat() / duration
                    val rangeWidth = ((endProgress - startProgress) * containerWidth).coerceAtLeast(handleWidthPx)

                    val playheadProgress = timelineState.playheadPositionMs.toFloat() / duration
                    val rangeCenterProgress = (startProgress + endProgress) / 2
                    val offsetFromCenter = (rangeCenterProgress - playheadProgress) * containerWidth

                    if (offsetFromCenter > -containerWidth && offsetFromCenter < containerWidth) {
                        Box(
                            modifier = Modifier
                                .height(80.dp)
                                .width(with(density) { rangeWidth.toDp() })
                                .align(Alignment.CenterStart)
                                .offset {
                                    IntOffset(
                                        (centerX + offsetFromCenter - rangeWidth / 2).roundToInt(),
                                        0
                                    )
                                }
                                .background(
                                    if (range.isSelected)
                                        Color(0xFF2196F3).copy(alpha = 0.3f)
                                    else
                                        Color(0xFF4CAF50).copy(alpha = 0.3f)
                                )
                                .border(
                                    width = if (range.isSelected) 2.dp else 1.dp,
                                    color = if (range.isSelected) Color(0xFF2196F3) else Color(0xFF4CAF50)
                                )
                                .clickable { timelineViewModel.selectRange(range.id) }
                        )

                        // Handles do range selecionado
                        if (range.isSelected) {
                            val startOffsetFromCenter = (startProgress - playheadProgress) * containerWidth
                            val endOffsetFromCenter = (endProgress - playheadProgress) * containerWidth

                            // Handle esquerdo
                            Box(
                                modifier = Modifier
                                    .width(with(density) { handleWidthPx.toDp() })
                                    .height(80.dp)
                                    .align(Alignment.CenterStart)
                                    .offset {
                                        IntOffset(
                                            (centerX + startOffsetFromCenter).roundToInt(),
                                            0
                                        )
                                    }
                                    .background(Color(0xFF2196F3))
                                    .pointerInput(range.id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                handleDragType = HandleDragType.LEFT
                                                dragStartX = it.x
                                                dragStartValue = range.startMs
                                            },
                                            onDragEnd = { handleDragType = HandleDragType.NONE }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            if (handleDragType == HandleDragType.LEFT) {
                                                val deltaX = dragAmount.x
                                                val deltaMs = ((deltaX / containerWidth) * duration).toLong()
                                                val newStart = (dragStartValue + deltaMs)
                                                    .coerceIn(0, range.endMs - 1)
                                                timelineViewModel.updateSelectedRangeStart(newStart)
                                            }
                                        }
                                    }
                            )

                            // Handle direito
                            Box(
                                modifier = Modifier
                                    .width(with(density) { handleWidthPx.toDp() })
                                    .height(80.dp)
                                    .align(Alignment.CenterStart)
                                    .offset {
                                        IntOffset(
                                            (centerX + endOffsetFromCenter - handleWidthPx).roundToInt(),
                                            0
                                        )
                                    }
                                    .background(Color(0xFF2196F3))
                                    .pointerInput(range.id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                handleDragType = HandleDragType.RIGHT
                                                dragStartX = it.x
                                                dragStartValue = range.endMs
                                            },
                                            onDragEnd = { handleDragType = HandleDragType.NONE }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            if (handleDragType == HandleDragType.RIGHT) {
                                                val deltaX = dragAmount.x
                                                val deltaMs = ((deltaX / containerWidth) * duration).toLong()
                                                val newEnd = (dragStartValue + deltaMs)
                                                    .coerceIn(range.startMs + 1, duration)
                                                timelineViewModel.updateSelectedRangeEnd(newEnd)
                                            }
                                        }
                                    }
                            )
                        }
                    }
                }
            }
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

// ===== COMPONENTES INTERNOS =====

/**
 * Playhead fixo no centro com suporte a drag.
 */
@Composable
private fun Playhead(
    positionPx: Float,
    containerWidth: Float,
    durationMs: Long,
    onPositionChanged: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    color: Color = Color.Red,
    width: Dp = 2.dp
) {
    Canvas(
        modifier = Modifier
            .offset { IntOffset(positionPx.roundToInt(), 0) }
            .width(width)
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newPx = positionPx + dragAmount.x
                        onPositionChanged(newPx)
                    }
                )
            }
    ) {
        // Linha vertical
        drawLine(
            color = color,
            start = Offset(size.width / 2, 0f),
            end = Offset(size.width / 2, size.height),
            strokeWidth = width.toPx()
        )

        // Triângulo no topo
        val triangleWidth = 10.dp.toPx()
        val triangleHeight = 10.dp.toPx()
        val path = Path().apply {
            moveTo(size.width / 2 - triangleWidth / 2, 0f)
            lineTo(size.width / 2 + triangleWidth / 2, 0f)
            lineTo(size.width / 2, triangleHeight)
            close()
        }
        drawPath(path, color)
    }
}

/**
 * Lista visual dos ranges.
 */
@Composable
private fun RangesList(
    ranges: List<com.chopcut.ui.timeline.model.VideoRange>,
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
 * Formata milissegundos para MM:SS.
 */
private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

/**
 * Tipo de drag em andamento no handle.
 */
private enum class HandleDragType { LEFT, RIGHT, NONE }
