package com.chopcut

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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

class TimelineV2ViewModel : ViewModel() {
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

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

    // Precise 60 FPS animation loop using withFrameNanos
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

    // Auto-play immediately when screen is loaded
    LaunchedEffect(Unit) {
        viewModel.play()
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

            // Timeline Area
            TimelineV2(
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Play / Pause Button
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    .clickable { viewModel.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun TimelineV2(
    currentPositionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val thumbWidthPx = with(density) { 80.dp.toPx() }
    val thumbHeightPx = with(density) { 45.dp.toPx() }
    val timelineTopPx = with(density) { 24.dp.toPx() }
    val tickHeightPx = with(density) { 6.dp.toPx() }

    Canvas(
        modifier = modifier
    ) {
        val width = size.width
        val height = size.height
        val centerX = width / 2f
        
        // Total timeline length in pixels (59 thumbnails of 80dp)
        val totalTimelineWidthPx = 59 * thumbWidthPx
        val scrollOffsetPx = (currentPositionMs.toFloat() / durationMs.toFloat()) * totalTimelineWidthPx
        
        // 1. Draw top line for ticks reference
        drawLine(
            color = Color.White.copy(alpha = 0.15f),
            start = Offset(0f, timelineTopPx),
            end = Offset(width, timelineTopPx),
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
                    start = Offset(thumbLeft, timelineTopPx),
                    end = Offset(thumbRight, timelineTopPx + thumbHeightPx)
                )
                
                // Draw thumbnail fill
                drawRect(
                    brush = brush,
                    topLeft = Offset(thumbLeft, timelineTopPx),
                    size = Size(thumbWidthPx, thumbHeightPx)
                )
                
                // Draw thin thumbnail border
                drawRect(
                    color = Color.White.copy(alpha = 0.15f),
                    topLeft = Offset(thumbLeft, timelineTopPx),
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
                    start = Offset(tickX, timelineTopPx - tickHeightPx),
                    end = Offset(tickX, timelineTopPx),
                    strokeWidth = with(density) { 1.dp.toPx() }
                )
                
                // Sub-ticks (half seconds)
                val halfTickX = tickX + (thumbWidthPx / 2f)
                if (halfTickX >= -10f && halfTickX <= width + 10f && i < 58) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.2f),
                        start = Offset(halfTickX, timelineTopPx - (tickHeightPx / 2f)),
                        end = Offset(halfTickX, timelineTopPx),
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
                    topLeft = Offset(tickX - textLayoutResult.size.width / 2f, 2.dp.toPx())
                )
            }
        }
        
        // Final tick mark at 59 seconds
        val endTickX = centerX + (59 * thumbWidthPx) - scrollOffsetPx
        if (endTickX >= -10f && endTickX <= width + 10f) {
            drawLine(
                color = Color.White.copy(alpha = 0.4f),
                start = Offset(endTickX, timelineTopPx - tickHeightPx),
                end = Offset(endTickX, timelineTopPx),
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
                topLeft = Offset(endTickX - textLayoutResult.size.width / 2f, 2.dp.toPx())
            )
        }

        // 3. Draw premium Cyan Playhead (#00E5FF) exactly at the center
        drawLine(
            color = Color(0xFF00E5FF),
            start = Offset(centerX, timelineTopPx - tickHeightPx),
            end = Offset(centerX, timelineTopPx + thumbHeightPx + 4.dp.toPx()),
            strokeWidth = with(density) { 2.5.dp.toPx() }
        )
        
        // Playhead top cap (small circle)
        drawCircle(
            color = Color(0xFF00E5FF),
            radius = with(density) { 5.dp.toPx() },
            center = Offset(centerX, timelineTopPx - tickHeightPx)
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
