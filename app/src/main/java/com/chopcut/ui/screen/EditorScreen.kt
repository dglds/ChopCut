package com.chopcut.ui.screen

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chopcut.data.thumbnail.ThumbnailExtractor
import com.chopcut.ui.components.VideoPreview
import com.chopcut.ui.components.VideoTimeline
import com.chopcut.ui.components.TrimRange
import com.chopcut.ui.preview.PreviewManager
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Editor screen for video editing
 *
 * @param videoUri Initial video URI to edit
 * @param onNavigateBack Callback when user wants to go back
 * @param onExportComplete Callback when export is complete (result Uri)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    videoUri: Uri,
    onNavigateBack: () -> Unit = {},
    onExportComplete: (Uri) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Managers
    val previewManager = remember { PreviewManager(context) }
    val thumbnailExtractor = remember { ThumbnailExtractor(context) }

    // State
    var trimRange by remember { mutableStateOf<TrimRange?>(null) }
    var currentVideoUri by remember { mutableStateOf(videoUri) }
    var videoDurationMs by remember { mutableLongStateOf(0L) }

    // ViewModel
    val editorViewModel: EditorViewModel = viewModel(
        factory = EditorViewModelFactory(
            context = context,
            videoUri = currentVideoUri
        )
    )

    val videoInfo by editorViewModel.videoInfo.collectAsStateWithLifecycle()
    val exportResult by editorViewModel.exportResult.collectAsStateWithLifecycle()
    val isExporting by editorViewModel.isExporting.collectAsStateWithLifecycle()

    // Load video metadata
    LaunchedEffect(currentVideoUri) {
        editorViewModel.loadVideoMetadata(currentVideoUri)
    }

    // Update duration when videoInfo changes
    LaunchedEffect(videoInfo) {
        val info = videoInfo
        if (info != null) {
            videoDurationMs = info.durationMs
            Timber.d("Video duration loaded: ${videoDurationMs}ms")
            if (videoDurationMs > 0 && trimRange == null) {
                trimRange = TrimRange(0L, videoDurationMs)
                Timber.d("Initial trim range: 0 - ${videoDurationMs}ms")
            }
        }
    }

    // Handle export result
    LaunchedEffect(exportResult) {
        val result = exportResult
        if (result != null) {
            result.getOrNull()?.let { outputUri ->
                onExportComplete(outputUri)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Editor") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val range = trimRange
                            if (range != null) {
                                editorViewModel.exportTrimmedVideo(range)
                            }
                        },
                        enabled = !isExporting && trimRange != null
                    ) {
                        if (isExporting) {
                            Text("Exporting...")
                        } else {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Export")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Video Preview
            VideoPreview(
                uri = currentVideoUri,
                previewManager = previewManager,
                modifier = Modifier.fillMaxWidth(),
                onPositionChanged = { positionMs ->
                    // Update current position display if needed
                    Timber.d("Position: ${positionMs}ms")
                }
            )

            Spacer(Modifier.height(16.dp))

            // Playback controls
            PlaybackControls(
                previewManager = previewManager,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // Video Timeline
            if (videoDurationMs > 0) {
                VideoTimeline(
                    uri = currentVideoUri,
                    durationMs = videoDurationMs,
                    thumbnailExtractor = thumbnailExtractor,
                    trimRange = trimRange,
                    onTrimRangeChange = { newRange ->
                        trimRange = newRange
                    },
                    onPositionClick = { positionMs ->
                        previewManager.seekTo(positionMs)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // Trim info
                val range = trimRange
                if (range != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Start: ${formatTime(range.startMs)}",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Duration: ${formatTime(range.endMs - range.startMs)}",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "End: ${formatTime(range.endMs)}",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Video info
            val info = videoInfo
            if (info != null) {
                Spacer(Modifier.height(16.dp))
                VideoInfoDisplay(info)
            }
        }
    }
}

/**
 * Playback controls with play/pause button
 */
@Composable
fun PlaybackControls(
    previewManager: PreviewManager,
    modifier: Modifier = Modifier
) {
    val isPlaying by previewManager.isPlaying.collectAsStateWithLifecycle()
    val isReady by previewManager.isReady.collectAsStateWithLifecycle()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingActionButton(
            onClick = {
                if (isPlaying) {
                    previewManager.pause()
                } else {
                    previewManager.play()
                }
            },
            modifier = Modifier.size(64.dp)
        ) {
            if (isPlaying) {
                // Pause icon not available in base icons, using text
                Text(
                    text = "||",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/**
 * Display video information
 */
@Composable
fun VideoInfoDisplay(videoInfo: com.chopcut.data.model.VideoInfo) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Video Information",
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Resolution: ${videoInfo.width}x${videoInfo.height}",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Duration: ${formatTime(videoInfo.durationMs)}",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Frame Rate: ${videoInfo.frameRate} fps",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Bitrate: ${videoInfo.bitrate / 1_000_000} Mbps",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Codec: ${videoInfo.videoCodec ?: "Unknown"}",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
        )
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
