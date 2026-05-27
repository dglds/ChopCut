package com.chopcut

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class MarkerInterval(
    val id: Int,
    val startMs: Long,
    val endMs: Long
)

class TimelineV2ViewModel : ViewModel() {
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _markerIntervals = MutableStateFlow<List<MarkerInterval>>(emptyList())
    val markerIntervals: StateFlow<List<MarkerInterval>> = _markerIntervals.asStateFlow()

    private val _activeMarkerStartMs = MutableStateFlow<Long?>(null)
    val activeMarkerStartMs: StateFlow<Long?> = _activeMarkerStartMs.asStateFlow()

    val durationMs = 59_000L

    fun play() {
        if (_currentPositionMs.value >= durationMs) {
            _currentPositionMs.value = 0L
        }
        _isPlaying.value = true
    }

    fun pause() {
        _isPlaying.value = false
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    fun updatePosition(ms: Long) {
        _currentPositionMs.value = ms.coerceIn(0L, durationMs)
    }

    fun toggleMarker(currentPositionMs: Long) {
        val start = _activeMarkerStartMs.value
        if (start == null) {
            // Start a new marker interval at current playhead
            _activeMarkerStartMs.value = currentPositionMs
        } else {
            // Finish active marker interval
            val end = currentPositionMs
            val minTime = Math.min(start, end)
            val maxTime = Math.max(start, end)
            
            val newInterval = MarkerInterval(
                id = 0, // Assigned dynamically after sorting/merging
                startMs = minTime,
                endMs = maxTime
            )
            
            _markerIntervals.value = mergeIntervals(_markerIntervals.value + newInterval)
            _activeMarkerStartMs.value = null
        }
    }

    fun deleteInterval(intervalId: Int) {
        _markerIntervals.value = _markerIntervals.value.filter { it.id != intervalId }
        _markerIntervals.value = reindexIntervals(_markerIntervals.value)
    }

    private fun reindexIntervals(list: List<MarkerInterval>): List<MarkerInterval> {
        return list.mapIndexed { index, interval ->
            interval.copy(id = index + 1)
        }
    }

    private fun mergeIntervals(list: List<MarkerInterval>): List<MarkerInterval> {
        if (list.isEmpty()) return emptyList()
        
        // Sort intervals by startMs
        val sorted = list.sortedWith(compareBy({ it.startMs }, { it.endMs }))
        val merged = mutableListOf<MarkerInterval>()
        var current = sorted[0]
        
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.startMs <= current.endMs) {
                // Merge overlapping or continuous intervals
                current = current.copy(endMs = Math.max(current.endMs, next.endMs))
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        
        return reindexIntervals(merged)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineV2Screen(
    onNavigateBack: () -> Unit,
    viewModel: TimelineV2ViewModel = viewModel()
) {
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPositionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs = viewModel.durationMs

    val markerIntervals by viewModel.markerIntervals.collectAsStateWithLifecycle()
    val activeMarkerStartMs by viewModel.activeMarkerStartMs.collectAsStateWithLifecycle()

    // Target position for manual scrubbing smoothing
    var targetPositionMs by remember { mutableStateOf(currentPositionMs.toFloat()) }

    // Synchronize targetPositionMs during auto-play
    LaunchedEffect(currentPositionMs, isPlaying) {
        if (isPlaying) {
            targetPositionMs = currentPositionMs.toFloat()
        }
    }

    // Precise 60 FPS animation loop using withFrameNanos (Auto-play/Auto-scroll)
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var lastNanos = System.nanoTime()
            var accumulatedMs = currentPositionMs.toFloat()
            while (isPlaying) {
                withFrameNanos { nanos ->
                    val deltaNanos = nanos - lastNanos
                    val deltaMs = deltaNanos / 1_000_000f
                    lastNanos = nanos

                    accumulatedMs = (accumulatedMs + deltaMs).coerceAtMost(durationMs.toFloat())
                    viewModel.updatePosition(accumulatedMs.toLong())

                    if (accumulatedMs >= durationMs) {
                        viewModel.pause()
                    }
                }
            }
        }
    }

    // Smooth interpolation loop when paused (exponential decay for premium fluidity during scrubbing)
    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            var lastNanos = System.nanoTime()
            while (!isPlaying) {
                withFrameNanos { nanos ->
                    val deltaNanos = nanos - lastNanos
                    val deltaMs = deltaNanos / 1_000_000f
                    lastNanos = nanos

                    val current = viewModel.currentPositionMs.value.toFloat()
                    val target = targetPositionMs
                    val diff = target - current

                    if (Math.abs(diff) > 0.5f) {
                        val tau = 80f // Time constant in ms (premium fluid damping coefficient)
                        val factor = (1f - Math.exp(-deltaMs.toDouble() / tau.toDouble())).toFloat()
                        val next = current + diff * factor
                        viewModel.updatePosition(next.toLong())
                    } else if (current != target) {
                        viewModel.updatePosition(target.toLong())
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "TimelineV2 Demo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Millisecond Counter placed directly above the Playhead (centered on screen)
            Text(
                text = formatMs(currentPositionMs),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Timeline Area (using the calibrated ideal sensitivity of 1.60x)
            TimelineV2(
                targetPositionMs = targetPositionMs,
                onTargetPositionChanged = { targetPositionMs = it },
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                sensitivity = 1.60f,
                markerIntervals = markerIntervals,
                activeMarkerStartMs = activeMarkerStartMs,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(114.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Controls Row (Play/Pause & Marker Button side by side)
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Pause Button
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .clickable { viewModel.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Marker Button (with generic name, pulsing red active indicators, and tiny labels)
                val isRecording = activeMarkerStartMs != null
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseAlpha by if (isRecording) {
                    infiniteTransition.animateFloat(
                        initialValue = 0.08f,
                        targetValue = 0.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = FastOutLinearInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )
                } else {
                    remember { mutableStateOf(0.08f) }
                }

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = if (isRecording) Color.Red.copy(alpha = pulseAlpha) else Color.White.copy(alpha = 0.08f),
                            shape = CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = if (isRecording) Color.Red else Color.White.copy(alpha = 0.15f),
                            shape = CircleShape
                        )
                        .clickable { viewModel.toggleMarker(currentPositionMs) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (isRecording) Color.Red else Color.White,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isRecording) "CORTE" else "MARCAR",
                            color = if (isRecording) Color.Red else Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Minimalist Marker List at the bottom
            if (markerIntervals.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "INTERVALOS MARCADOS",
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp)
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(markerIntervals, key = { it.id }) { interval ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format(Locale.US, "#%02d: %dms - %dms", interval.id, interval.startMs, interval.endMs),
                                style = TextStyle(
                                    color = Color.Yellow,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Excluir marcação",
                                tint = Color.Red.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { viewModel.deleteInterval(interval.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineV2(
    targetPositionMs: Float,
    onTargetPositionChanged: (Float) -> Unit,
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    sensitivity: Float,
    markerIntervals: List<MarkerInterval>,
    activeMarkerStartMs: Long?,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val targetPositionState = rememberUpdatedState(targetPositionMs)
    val onTargetPositionChangedState = rememberUpdatedState(onTargetPositionChanged)
    val sensitivityState = rememberUpdatedState(sensitivity)

    // Playhead color animation when active marker is running (rapid flashing between Cyan and bright Amber/Yellow for alert)
    val isRecording = activeMarkerStartMs != null
    val infiniteTransition = rememberInfiniteTransition(label = "playheadTransition")
    val playheadColor by if (isRecording) {
        infiniteTransition.animateColor(
            initialValue = Color(0xFF00E5FF), // Cyan
            targetValue = Color(0xFFFFC107),  // Amber/Yellow Warning Alert
            animationSpec = infiniteRepeatable(
                animation = tween(250, easing = FastOutLinearInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "playheadColor"
        )
    } else {
        remember { mutableStateOf(Color(0xFF00E5FF)) }
    }

    // Original, elegant visual dimensions
    val thumbWidthPx = with(density) { 80.dp.toPx() }
    val thumbHeightPx = with(density) { 45.dp.toPx() }
    val timelineTopPx = with(density) { 24.dp.toPx() }
    val tickHeightPx = with(density) { 6.dp.toPx() }
    val tickGapPx = with(density) { 4.dp.toPx() } // Elegant 4dp space between ruler and thumbnails

    Canvas(
        modifier = modifier
            .pointerInput(isPlaying) {
                if (!isPlaying) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val totalTimelineWidthPx = 59 * thumbWidthPx
                            val msPerPx = durationMs.toFloat() / totalTimelineWidthPx
                            val deltaMs = -dragAmount * msPerPx
                            val newTarget = (targetPositionState.value + deltaMs * sensitivityState.value)
                                .coerceIn(0f, durationMs.toFloat())
                            onTargetPositionChangedState.value(newTarget)
                        }
                    )
                }
            }
    ) {
        val width = size.width
        val height = size.height
        val centerX = width / 2f
        
        // Calculate vertical offset to center the 69dp visual content inside the 114dp touch container
        val contentHeightPx = timelineTopPx + thumbHeightPx
        val verticalOffsetPx = (height - contentHeightPx) / 2f
        
        // Total timeline length in pixels (59 thumbnails of 80dp)
        val totalTimelineWidthPx = 59 * thumbWidthPx
        val scrollOffsetPx = (currentPositionMs.toFloat() / durationMs.toFloat()) * totalTimelineWidthPx
        
        // 1. Draw top line for ticks reference (with gap offset)
        drawLine(
            color = Color.White.copy(alpha = 0.15f),
            start = Offset(0f, verticalOffsetPx + timelineTopPx - tickGapPx),
            end = Offset(width, verticalOffsetPx + timelineTopPx - tickGapPx),
            strokeWidth = 1f
        )
        
        // 2. Draw Ticks & Labels & Mocked Thumbnails
        for (i in 0 until 59) {
            val thumbLeft = centerX + (i * thumbWidthPx) - scrollOffsetPx
            val thumbRight = thumbLeft + thumbWidthPx
            
            // Viewport culling (only render what's visible on screen)
            if (thumbRight >= -50f && thumbLeft <= width + 50f) {
                // Diagonal premium gradients
                val colorStart: Color
                val colorEnd: Color
                
                when {
                    i % 5 == 0 -> {
                        // High accent gradient for scene cuts
                        colorStart = Color(0xFFE94560)
                        colorEnd = Color(0xFF0F3460)
                    }
                    i % 2 == 0 -> {
                        colorStart = Color(0xFF1A1A2E)
                        colorEnd = Color(0xFF16213E)
                    }
                    else -> {
                        colorStart = Color(0xFF0F3460)
                        colorEnd = Color(0xFF1A1A2E)
                    }
                }
                
                val brush = Brush.linearGradient(
                    colors = listOf(colorStart, colorEnd),
                    start = Offset(thumbLeft, verticalOffsetPx + timelineTopPx),
                    end = Offset(thumbRight, verticalOffsetPx + timelineTopPx + thumbHeightPx)
                )
                
                // Draw thumbnail fill
                drawRect(
                    brush = brush,
                    topLeft = Offset(thumbLeft, verticalOffsetPx + timelineTopPx),
                    size = Size(thumbWidthPx, thumbHeightPx)
                )
                
                // Draw thin thumbnail border
                drawRect(
                    color = Color.White.copy(alpha = 0.15f),
                    topLeft = Offset(thumbLeft, verticalOffsetPx + timelineTopPx),
                    size = Size(thumbWidthPx, thumbHeightPx),
                    style = Stroke(width = with(density) { 0.5.dp.toPx() })
                )
            }
            
            // Draw tick mark and label at each second boundary
            val tickX = centerX + (i * thumbWidthPx) - scrollOffsetPx
            if (tickX >= -10f && tickX <= width + 10f) {
                // Standard second tick
                drawLine(
                    color = Color.White.copy(alpha = 0.4f),
                    start = Offset(tickX, verticalOffsetPx + timelineTopPx - tickGapPx - tickHeightPx),
                    end = Offset(tickX, verticalOffsetPx + timelineTopPx - tickGapPx),
                    strokeWidth = with(density) { 1.dp.toPx() }
                )
                
                // Sub-ticks (half seconds)
                val halfTickX = tickX + (thumbWidthPx / 2f)
                if (halfTickX >= -10f && halfTickX <= width + 10f && i < 58) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.2f),
                        start = Offset(halfTickX, verticalOffsetPx + timelineTopPx - tickGapPx - (tickHeightPx / 2f)),
                        end = Offset(halfTickX, verticalOffsetPx + timelineTopPx - tickGapPx),
                        strokeWidth = with(density) { 0.5.dp.toPx() }
                    )
                }
                
                // Ticks Label (Roboto Mono or standard Monospace)
                val textLayoutResult = textMeasurer.measure(
                    text = "${i}s",
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(tickX - textLayoutResult.size.width / 2f, verticalOffsetPx + 2.dp.toPx())
                )
            }
        }
        
        // 3. Draw Yellow Marker Intervals (Transparent overlay + solid borders)
        markerIntervals.forEach { interval ->
            val startX = centerX + (interval.startMs.toFloat() / durationMs.toFloat()) * totalTimelineWidthPx - scrollOffsetPx
            val endX = centerX + (interval.endMs.toFloat() / durationMs.toFloat()) * totalTimelineWidthPx - scrollOffsetPx
            val drawWidth = endX - startX
            
            if (endX >= 0f && startX <= width) {
                // Fill semi-transparent yellow
                drawRect(
                    color = Color.Yellow.copy(alpha = 0.22f),
                    topLeft = Offset(startX, verticalOffsetPx + timelineTopPx),
                    size = Size(drawWidth, thumbHeightPx)
                )
                
                // Left & Right solid border
                drawLine(
                    color = Color.Yellow,
                    start = Offset(startX, verticalOffsetPx + timelineTopPx),
                    end = Offset(startX, verticalOffsetPx + timelineTopPx + thumbHeightPx),
                    strokeWidth = with(density) { 1.5.dp.toPx() }
                )
                drawLine(
                    color = Color.Yellow,
                    start = Offset(endX, verticalOffsetPx + timelineTopPx),
                    end = Offset(endX, verticalOffsetPx + timelineTopPx + thumbHeightPx),
                    strokeWidth = with(density) { 1.5.dp.toPx() }
                )
            }
        }

        // 4. Draw In-Progress Active Marker Selection (Real-time expanding/contracting visual feedback)
        if (activeMarkerStartMs != null) {
            val minMs = Math.min(activeMarkerStartMs, currentPositionMs)
            val maxMs = Math.max(activeMarkerStartMs, currentPositionMs)
            
            val startX = centerX + (minMs.toFloat() / durationMs.toFloat()) * totalTimelineWidthPx - scrollOffsetPx
            val endX = centerX + (maxMs.toFloat() / durationMs.toFloat()) * totalTimelineWidthPx - scrollOffsetPx
            val drawWidth = endX - startX
            
            if (endX >= 0f && startX <= width) {
                // Fill semi-transparent active yellow
                drawRect(
                    color = Color.Yellow.copy(alpha = 0.15f),
                    topLeft = Offset(startX, verticalOffsetPx + timelineTopPx),
                    size = Size(drawWidth, thumbHeightPx)
                )
                
                // Active solid borders
                drawLine(
                    color = Color.Yellow.copy(alpha = 0.7f),
                    start = Offset(startX, verticalOffsetPx + timelineTopPx),
                    end = Offset(startX, verticalOffsetPx + timelineTopPx + thumbHeightPx),
                    strokeWidth = with(density) { 1.5.dp.toPx() }
                )
                drawLine(
                    color = Color.Yellow.copy(alpha = 0.7f),
                    start = Offset(endX, verticalOffsetPx + timelineTopPx),
                    end = Offset(endX, verticalOffsetPx + timelineTopPx + thumbHeightPx),
                    strokeWidth = with(density) { 1.5.dp.toPx() }
                )
            }
        }

        // Final tick mark at 59 seconds
        val endTickX = centerX + (59 * thumbWidthPx) - scrollOffsetPx
        if (endTickX >= -10f && endTickX <= width + 10f) {
            drawLine(
                color = Color.White.copy(alpha = 0.4f),
                start = Offset(endTickX, verticalOffsetPx + timelineTopPx - tickGapPx - tickHeightPx),
                end = Offset(endTickX, verticalOffsetPx + timelineTopPx - tickGapPx),
                strokeWidth = with(density) { 1.dp.toPx() }
            )
            val textLayoutResult = textMeasurer.measure(
                text = "59s",
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(endTickX - textLayoutResult.size.width / 2f, verticalOffsetPx + 2.dp.toPx())
            )
        }

        // 5. Draw premium Playhead (alternating Cyan/Amber in rapid alert flash when active, else Cyan) exactly at the center
        val playheadWidth = if (isRecording) 3.5.dp else 2.5.dp
        val capRadius = if (isRecording) 7.dp else 5.dp

        if (isRecording) {
            // Draw a semi-transparent pulsing alert glow behind the playhead cap
            drawCircle(
                color = playheadColor.copy(alpha = 0.25f),
                radius = with(density) { (capRadius + 4.dp).toPx() },
                center = Offset(centerX, verticalOffsetPx + timelineTopPx - tickGapPx - tickHeightPx)
            )
        }

        drawLine(
            color = playheadColor,
            start = Offset(centerX, verticalOffsetPx + timelineTopPx - tickGapPx - tickHeightPx),
            end = Offset(centerX, verticalOffsetPx + timelineTopPx + thumbHeightPx + 4.dp.toPx()),
            strokeWidth = with(density) { playheadWidth.toPx() }
        )
        
        // Playhead top cap (small circle)
        drawCircle(
            color = playheadColor,
            radius = with(density) { capRadius.toPx() },
            center = Offset(centerX, verticalOffsetPx + timelineTopPx - tickGapPx - tickHeightPx)
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val milliseconds = ms % 1000
    return String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, milliseconds)
}
