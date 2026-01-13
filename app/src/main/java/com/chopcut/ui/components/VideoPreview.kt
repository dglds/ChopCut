package com.chopcut.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.chopcut.ui.preview.PreviewManager
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber

import androidx.compose.foundation.clickable

/**
 * Video preview component with ExoPlayer integration
 *
 * @param uri Video URI to preview
 * @param previewManager PreviewManager instance
 * @param modifier Modifier for the container
 * @param onPositionChanged Callback when video position changes (in ms)
 * @param onVideoClick Callback when the video surface is clicked
 */
@Composable
fun VideoPreview(
    uri: android.net.Uri,
    previewManager: PreviewManager,
    modifier: Modifier = Modifier,
    onPositionChanged: (Long) -> Unit = {},
    onVideoClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isPlaying by previewManager.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by previewManager.currentPosition.collectAsStateWithLifecycle()
    val duration by previewManager.duration.collectAsStateWithLifecycle()
    val isReady by previewManager.isReady.collectAsStateWithLifecycle()

    // Track if user is currently dragging the slider
    var isUserSeeking by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

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
                .clickable { onVideoClick() },
            contentAlignment = Alignment.Center
        ) {
            // Only show PlayerView when player is ready (triggers recomposition)
            if (isReady) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false  // Timeline controls playback
                            controllerShowTimeoutMs = 0
                            player = previewManager.exoPlayer
                            Timber.d("PlayerView created with player: ${previewManager.exoPlayer}")
                        }
                    },
                    update = { view ->
                        // Ensure the view is always attached to the current player
                        view.player = previewManager.exoPlayer
                    },
                    modifier = Modifier.fillMaxSize()
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

        // Controls area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Seek bar
            if (isReady && duration > 0) {
                Slider(
                    value = sliderPosition,
                    onValueChange = { newValue ->
                        sliderPosition = newValue
                        isUserSeeking = true
                    },
                    onValueChangeFinished = {
                        isUserSeeking = false
                        val positionMs = (sliderPosition * duration).toLong()
                        previewManager.seekTo(positionMs)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Time display
                Text(
                    text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
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

/**
 * Video preview controls with play/pause button
 *
 * @param previewManager PreviewManager instance
 * @param modifier Modifier for the container
 */
@Composable
fun VideoPreviewControls(
    previewManager: PreviewManager,
    modifier: Modifier = Modifier
) {
    val isPlaying by previewManager.isPlaying.collectAsStateWithLifecycle()
    val isReady by previewManager.isReady.collectAsStateWithLifecycle()

    // TODO: Create play/pause button component
    // This will be integrated with the main VideoPreview component
}
