package com.chopcut.ui.screen

import android.net.Uri
import android.view.Gravity
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import com.chopcut.data.model.EditOperation
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
import com.chopcut.ui.components.WaveForm
import com.chopcut.ui.components.TrimRange
import com.chopcut.ui.components.ExportDialog
import com.chopcut.ui.preview.PreviewManager
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.media3.common.util.UnstableApi

/**
 * Editor screen for video editing
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    videoUri: Uri,
    projectId: String? = null,
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
            videoUri = videoUri,
            projectId = projectId
        )
    )

    val project by editorViewModel.project.collectAsStateWithLifecycle()
    val videoInfo by editorViewModel.videoInfo.collectAsStateWithLifecycle()
    val waveformData by editorViewModel.waveformData.collectAsStateWithLifecycle()
    val exportResult by editorViewModel.exportResult.collectAsStateWithLifecycle()
    val isExporting by editorViewModel.isExporting.collectAsStateWithLifecycle()
    val edits by editorViewModel.edits.collectAsStateWithLifecycle()
    val canUndo by editorViewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by editorViewModel.canRedo.collectAsStateWithLifecycle()
    val saveStatus by editorViewModel.saveStatus.collectAsStateWithLifecycle()
    val presets by editorViewModel.availablePresets.collectAsStateWithLifecycle(initialValue = emptyList())

    var showExportDialog by remember { mutableStateOf(false) }

    // Apply effects when edits change
    LaunchedEffect(edits) {
        previewManager.applyEffects(edits)
    }

    // Handle UI messages
    LaunchedEffect(Unit) {
        editorViewModel.messageFlow.collect { message ->
            val toast = android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT)
            toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 200)
            toast.show()
        }
    }

    // Update currentVideoUri when project loads
    LaunchedEffect(project) {
        project?.let {
            currentVideoUri = Uri.parse(it.sourceVideoUri)
        }
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
                val toast = android.widget.Toast.makeText(
                    context,
                    "Salvo na galeria! \n$outputUri",
                    android.widget.Toast.LENGTH_LONG
                )
                toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 200)
                toast.show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Video Editor")
                        Text(
                            text = when(saveStatus) {
                                EditorViewModel.SaveStatus.SAVED -> "Salvo"
                                EditorViewModel.SaveStatus.SAVING -> "Salvando..."
                                EditorViewModel.SaveStatus.UNSAVED -> "Não salvo"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { editorViewModel.undo() }, enabled = canUndo) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Desfazer")
                    }
                    IconButton(onClick = { editorViewModel.redo() }, enabled = canRedo) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Refazer")
                    }
                    IconButton(
                        onClick = { editorViewModel.saveProject(manual = true) },
                        enabled = videoInfo != null
                    ) {
                        Icon(Icons.Default.Done, contentDescription = "Salvar")
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
                .verticalScroll(rememberScrollState())
        ) {
            // Calculate total rotation
            val totalRotation = edits.filterIsInstance<EditOperation.Rotation>()
                .sumOf { it.degrees }
                .toFloat() % 360f

            // Video Preview
            VideoPreview(
                uri = currentVideoUri,
                previewManager = previewManager,
                modifier = Modifier.fillMaxWidth(),
                rotationDegrees = totalRotation,
                onPositionChanged = { positionMs ->
                    Timber.d("Position: ${positionMs}ms")
                },
                onVideoClick = {
                    if (previewManager.isPlaying.value) {
                        previewManager.pause()
                    } else {
                        previewManager.play()
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            // 1. Applied Operations (Compact Horizontal List)
            if (edits.isNotEmpty()) {
                Text(
                    text = "Histórico",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(edits.reversed()) { index, op ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (op) {
                                        is EditOperation.Trim -> "Trim"
                                        is EditOperation.Rotation -> "Rot"
                                        is EditOperation.Resize -> "Size"
                                        is EditOperation.Crop -> "Crop"
                                        else -> "Op"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // 2. Operations Buttons (Trim, Test, etc)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Export Button (Starts the real process)
                Button(
                    onClick = { showExportDialog = true },
                    enabled = !isExporting && videoInfo != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Exportar", style = MaterialTheme.typography.labelSmall)
                }

                // Trim Button (Applies logic)
                Button(
                    onClick = {
                        val range = trimRange
                        if (range != null) {
                            editorViewModel.applyTrim(range)
                        }
                    },
                    enabled = !isExporting && trimRange != null,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Trim", style = MaterialTheme.typography.labelSmall)
                }

                Button(
                    onClick = { editorViewModel.testOperation("rotate") },
                    enabled = !isExporting,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Rot", style = MaterialTheme.typography.labelSmall)
                }

                Button(
                    onClick = { editorViewModel.testOperation("resize") },
                    enabled = !isExporting,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Size", style = MaterialTheme.typography.labelSmall)
                }

                Button(
                    onClick = { editorViewModel.testOperation("crop") },
                    enabled = !isExporting,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Crop", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(16.dp))

            // 3. Timeline Row
            if (videoInfo == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // Timeline
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
                            previewManager.pause()
                            previewManager.seekTo(positionMs)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Trim Info (Below timeline)
                val range = trimRange
                if (range != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(range.startMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatTime(range.endMs - range.startMs),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = formatTime(range.endMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Waveform
                Spacer(Modifier.height(16.dp))
                if (waveformData.amplitudes.isNotEmpty()) {
                    WaveForm(
                        amplitudes = waveformData.amplitudes,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Gerando forma de onda...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(modifier = Modifier.width(120.dp))
                    }
                }

                // Video info
                Spacer(Modifier.height(16.dp))
                VideoInfoDisplay(videoInfo!!)
            }
        }

        // Export Presets Dialog
        if (showExportDialog) {
            ExportDialog(
                presets = presets,
                onPresetSelected = { preset ->
                    showExportDialog = false
                    val range = trimRange
                    // Pass current trim range (or null for full video) and selected preset
                    editorViewModel.exportVideo(range, preset)
                },
                onDismiss = { showExportDialog = false }
            )
        }

        // Exporting Progress Dialog
        if (isExporting) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { /* Prevent dismiss during export */ },
                title = { Text("Exportando Vídeo") },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Processando...", style = MaterialTheme.typography.bodyMedium)
                    }
                },
                confirmButton = {}
            )
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