package com.chopcut.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.chopcut.R
import com.chopcut.ui.preview.PreviewManager
import timber.log.Timber
import android.view.LayoutInflater
import androidx.compose.material3.ExperimentalMaterial3Api

/**
 * Video preview component with ExoPlayer integration
 *
 * @param uri Video URI to preview
 * @param previewManager PreviewManager instance
 * @param modifier Modifier for the container
 * @param rotationDegrees Rotation to apply to the video view
 * @param onPositionChanged Callback when video position changes (in ms)
 * @param onVideoClick Callback when the video surface is clicked
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPreview(
    uri: android.net.Uri,
    previewManager: PreviewManager,
    modifier: Modifier = Modifier,
    rotationDegrees: Float = 0f,
    onPositionChanged: (Long) -> Unit = {},
    onVideoClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isPlaying by previewManager.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by previewManager.currentPosition.collectAsStateWithLifecycle()
    val duration by previewManager.duration.collectAsStateWithLifecycle()
    val isReady by previewManager.isReady.collectAsStateWithLifecycle()
    val currentVolume by previewManager.currentVolume.collectAsStateWithLifecycle()

    // Track if user is currently dragging the slider
    var isUserSeeking by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    // Track double tap for pause/stop
    var lastTapTime by remember { mutableStateOf(0L) }
    val doubleTapTimeout = 300L // ms between taps to consider it a double tap

    // Initialize Player - IMPORTANT: Must happen before PlayerView is created
    LaunchedEffect(uri) {
        Timber.d("LaunchedEffect: Preparing player with URI: $uri")
        previewManager.prepare(context, uri, coroutineScope)
    }

    // Update slider position only when user is NOT seeking
    LaunchedEffect(currentPosition, duration, isUserSeeking) {
        if (!isUserSeeking && duration > 0) {
            sliderPosition = currentPosition.toFloat() / duration.toFloat()
        }
        onPositionChanged(currentPosition)
    }

    // Cleanup on dispose
    DisposableEffect(previewManager) {
        onDispose {
            Timber.d("VideoPreview disposed")
        }
    }

    Column(modifier = modifier) {
        // Video Surface - standard 16:9 aspect ratio container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val currentTime = System.currentTimeMillis()
                            val timeSinceLastTap = currentTime - lastTapTime

                            if (timeSinceLastTap < doubleTapTimeout) {
                                // Double tap detected - pause video
                                if (isPlaying) {
                                    previewManager.pause()
                                    Timber.d("Double tap: Paused video")
                                }
                                lastTapTime = 0 // Reset to avoid triple tap
                            } else {
                                // Single tap - call callback
                                onVideoClick()
                                lastTapTime = currentTime
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Only show PlayerView when player is ready (triggers recomposition)
            if (isReady) {
                AndroidView(
                    factory = { ctx ->
                        // Inflate from XML to use TextureView (supports rotation)
                        val view = LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView
                        view.apply {
                            controllerShowTimeoutMs = 0
                            player = previewManager.exoPlayer
                            Timber.d("PlayerView created from XML with TextureView")
                        }
                    },
                    update = { view ->
                        // Ensure the view is always attached to the current player
                        (view as PlayerView).player = previewManager.exoPlayer
                    },
                    modifier = Modifier.fillMaxSize().rotate(rotationDegrees)
                )
            }

            if (!isReady) {
                Text(
                    text = "Loading...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Seek bar (cantos retos, sem thumb/playhead)
        if (isReady && duration > 0) {
            SeekBar(
                progress = sliderPosition,
                durationMs = duration,
                onProgressChange = { newProgress ->
                    sliderPosition = newProgress
                    val positionMs = (newProgress * duration).toLong()
                        .coerceIn(0L, duration)
                    previewManager.seekTo(positionMs)
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // Sound bar (medidor de volume em tempo real)
            SoundBar(
                volume = currentVolume,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Format time in milliseconds to MM:SS
 */
private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView

// ...

/**
 * Seek bar customizada com cantos retos (sem arredondamento)
 */
@Composable
private fun SeekBar(
    progress: Float,
    durationMs: Long,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val activeColor = MaterialTheme.colorScheme.primary
    val view = LocalView.current

    fun snapProgress(rawProgress: Float): Float {
        if (durationMs <= 0) return rawProgress
        
        val currentMs = rawProgress * durationMs
        val secondInMs = 1000f
        val nearestSecond = (currentMs / secondInMs).let { kotlin.math.round(it) } * secondInMs
        
        val diff = kotlin.math.abs(currentMs - nearestSecond)
        val threshold = 50f // Snap if within 50ms
        
        return if (diff < threshold) {
            // Perform haptic feedback only if we snapped and it's a "new" snap (not checking here for simplicity)
             if (diff > 1f) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            nearestSecond / durationMs
        } else {
            rawProgress
        }
    }

    Box(
        modifier = modifier
            .height(24.dp) // Aumentar área de toque
            .drawBehind {
                // Centralizar verticalmente o track
                val trackHeight = 4.dp.toPx()
                val trackTop = (size.height - trackHeight) / 2
                
                // Track de fundo (inativo) - cantos retos
                drawRect(
                    color = inactiveColor,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, trackTop),
                    size = androidx.compose.ui.geometry.Size(size.width, trackHeight)
                )

                // Track ativo (posição atual) - cantos retos
                drawRect(
                    color = activeColor,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, trackTop),
                    size = androidx.compose.ui.geometry.Size(size.width * progress, trackHeight)
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val clickPosition = offset.x / size.width
                    val snapped = snapProgress(clickPosition.coerceIn(0f, 1f))
                    onProgressChange(snapped)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // Calculate new progress based on drag
                    // We need current progress? No, we should track drag position relative to width
                    // Ideally we map touch X to progress
                    val currentX = change.position.x
                    val dragPosition = (currentX / size.width)
                    val snapped = snapProgress(dragPosition.coerceIn(0f, 1f))
                    onProgressChange(snapped)
                }
            }
    )
}

/**
 * Video preview controls with play/pause button
 */
@Composable
fun VideoPreviewControls(
    previewManager: PreviewManager,
    modifier: Modifier = Modifier
) {
    // Unused, can be removed or kept for future
}

/**
 * Sound bar - Medidor de volume em tempo real
 */
@Composable
fun SoundBar(
    volume: Float,
    modifier: Modifier = Modifier
) {
    val barCount = 20
    val activeBars = remember(volume) {
        (volume * barCount).toInt().coerceIn(0, barCount)
    }

    // Determine color based on volume level
    val barColor = when {
        volume == 0f -> Color(0xFFF44336) // Red = Muted
        volume < 0.3f -> Color(0xFFFF9800) // Orange = Low
        volume < 0.7f -> Color(0xFF4CAF50) // Green = Medium
        volume < 1.0f -> Color(0xFF2196F3) // Blue = High
        else -> Color(0xFFF44336) // Red = Over 100%
    }

    Row(
        modifier = modifier
            .height(24.dp)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Icon(
            imageVector = if (volume == 0f) Icons.Default.Close else Icons.Default.Notifications,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (volume == 0f) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.width(6.dp))

        // Volume bars
        repeat(barCount) { index ->
            val isActive = index < activeBars
            val height = when {
                index < barCount / 3 -> 8.dp   // Low
                index < barCount * 2 / 3 -> 14.dp  // Medium
                else -> 20.dp  // High
            }

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height)
                    .background(
                        if (isActive) barColor else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(2.dp)
                    )
            )
        }

        Spacer(Modifier.width(6.dp))

        // Percentage text
        Text(
            text = "${(volume * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = barColor,
            modifier = Modifier.width(32.dp)
        )
    }
}