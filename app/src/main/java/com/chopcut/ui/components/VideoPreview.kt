package com.chopcut.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

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

    val currentPosition by previewManager.currentPosition.collectAsStateWithLifecycle()
    val duration by previewManager.duration.collectAsStateWithLifecycle()
    val isReady by previewManager.isReady.collectAsStateWithLifecycle()

    var sliderPosition by remember { mutableFloatStateOf(0f) }
    
    // Track double tap for pause/stop
    var lastTapTime by remember { mutableStateOf(0L) }
    val doubleTapTimeout = 300L // ms between taps to consider it a double tap

    // Initialize Player - IMPORTANT: Must happen before PlayerView is created
    LaunchedEffect(uri) {
        Timber.d("LaunchedEffect: Preparing player with URI: $uri")
        previewManager.prepare(context, uri, coroutineScope)
    }

    // Update progress position
    LaunchedEffect(currentPosition, duration) {
        if (duration > 0) {
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
                        onTap = {
                            val currentTime = System.currentTimeMillis()
                            val timeSinceLastTap = currentTime - lastTapTime

                            if (timeSinceLastTap < doubleTapTimeout) {
                                // Double tap detected - stop video (optional behavior, keeping from original)
                                // previewManager.stop() 
                                // Timber.d("Double tap: Stopped video")
                                lastTapTime = 0 
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
                            useController = false // Disable built-in controls
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

        // Barra de progresso visual (sem interação, colada ao player)
        if (isReady && duration > 0) {
            SimpleProgressBar(
                progress = sliderPosition,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Barra de progresso visual simples (sem interação)
 */
@Composable
private fun SimpleProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val activeColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .height(8.dp) // Altura aumentada conforme solicitado
            .drawBehind {
                // Track de fundo (inativo) - cantos retos
                drawRect(
                    color = inactiveColor,
                    size = size
                )

                // Track ativo (posição atual) - cantos retos
                drawRect(
                    color = activeColor,
                    size = androidx.compose.ui.geometry.Size(size.width * progress, size.height)
                )
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
    // Unused
}
