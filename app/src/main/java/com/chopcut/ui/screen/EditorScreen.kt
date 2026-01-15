package com.chopcut.ui.screen

import android.net.Uri
import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.chopcut.data.model.EditOperation
import com.chopcut.data.model.FilterType
import com.chopcut.data.thumbnail.ThumbnailExtractor
import com.chopcut.ui.components.ExportDialog
import com.chopcut.ui.components.TrimRange
import com.chopcut.ui.components.VideoPreview
import com.chopcut.ui.components.VideoTimeline
import com.chopcut.ui.components.WaveForm
import com.chopcut.ui.preview.PreviewManager
import com.chopcut.ui.filter.FilterDialog
import com.chopcut.ui.filter.SpeedDialog
import com.chopcut.ui.filter.VolumeDialog
import kotlinx.coroutines.launch
import timber.log.Timber

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

    val previewManager = remember { PreviewManager(context) }
    val thumbnailExtractor = remember { ThumbnailExtractor(context) }

    var trimRange by remember { mutableStateOf<TrimRange?>(null) }
    var currentVideoUri by remember { mutableStateOf(videoUri) }
    var videoDurationMs by remember { mutableLongStateOf(0L) }

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
    var showFilterDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showVolumeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(edits) {
        previewManager.applyEffects(edits)
    }

    LaunchedEffect(Unit) {
        editorViewModel.messageFlow.collect { message ->
            val toast = android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT)
            toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 200)
            toast.show()
        }
    }

    LaunchedEffect(project) {
        project?.let {
            currentVideoUri = Uri.parse(it.sourceVideoUri)
        }
    }

    LaunchedEffect(videoInfo) {
        val info = videoInfo
        if (info != null) {
            videoDurationMs = info.durationMs
            if (videoDurationMs > 0 && trimRange == null) {
                trimRange = TrimRange(0L, videoDurationMs)
            }
        }
    }

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
            val totalRotation = edits.filterIsInstance<EditOperation.Rotation>()
                .sumOf { it.degrees }
                .toFloat() % 360f
            
            val activeFilter = edits.filterIsInstance<EditOperation.Filter>().lastOrNull()?.filterType

            VideoPreview(
                uri = currentVideoUri,
                previewManager = previewManager,
                modifier = Modifier.fillMaxWidth(),
                rotationDegrees = totalRotation,
                activeFilter = activeFilter,
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

            if (edits.isNotEmpty()) {
                Text(
                    text = "Histórico",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(edits.size) { i ->
                        val op = edits.reversed()[i]
                        val index = edits.size - i
                        
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
                                        is EditOperation.Filter -> "Filter"
                                        is EditOperation.Speed -> "Speed"
                                        is EditOperation.Volume -> "Vol"
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

            // Barra de ferramentas principal
            EditorToolbar(
                isExporting = isExporting,
                videoInfo = videoInfo != null,
                trimRange = trimRange,
                currentFilter = edits.filterIsInstance<EditOperation.Filter>().lastOrNull(),
                currentSpeed = edits.filterIsInstance<EditOperation.Speed>().lastOrNull(),
                currentVolume = edits.filterIsInstance<EditOperation.Volume>().lastOrNull(),
                onExportClick = { showExportDialog = true },
                onTrimClick = { range ->
                    if (range != null) editorViewModel.applyTrim(range)
                },
                onRotateClick = { editorViewModel.testOperation("rotate") },
                onFilterClick = { showFilterDialog = true },
                onSpeedClick = { showSpeedDialog = true },
                onVolumeClick = { showVolumeDialog = true }
            )

            Spacer(Modifier.height(16.dp))

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
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatTime(range.endMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

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

                Spacer(Modifier.height(16.dp))
                VideoInfoDisplay(videoInfo!!)
            }
        }

        if (showExportDialog) {
            ExportDialog(
                presets = presets,
                onPresetSelected = { preset ->
                    showExportDialog = false
                    val range = trimRange
                    editorViewModel.exportVideo(range, preset)
                },
                onDismiss = { showExportDialog = false }
            )
        }

        // Diálogo de filtros
        if (showFilterDialog) {
            val currentFilter = edits.filterIsInstance<EditOperation.Filter>().lastOrNull()
            FilterDialog(
                currentFilter = currentFilter,
                onConfirm = { filter ->
                    showFilterDialog = false
                    if (filter != null) {
                        editorViewModel.addOperation(filter)
                    }
                },
                onDismiss = { showFilterDialog = false }
            )
        }

        // Diálogo de velocidade
        if (showSpeedDialog) {
            val currentSpeed = edits.filterIsInstance<EditOperation.Speed>().lastOrNull()
            SpeedDialog(
                currentSpeed = currentSpeed,
                onConfirm = { speed ->
                    showSpeedDialog = false
                    editorViewModel.addOperation(speed)
                },
                onDismiss = { showSpeedDialog = false }
            )
        }

        // Diálogo de volume
        if (showVolumeDialog) {
            val currentVolume = edits.filterIsInstance<EditOperation.Volume>().lastOrNull()
            VolumeDialog(
                currentVolume = currentVolume,
                onConfirm = { volume ->
                    showVolumeDialog = false
                    editorViewModel.addOperation(volume)
                },
                onDismiss = { showVolumeDialog = false }
            )
        }

        if (isExporting) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { },
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

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * Barra de ferramentas principal do editor com botões organizados por categoria.
 */
@Composable
private fun EditorToolbar(
    isExporting: Boolean,
    videoInfo: Boolean,
    trimRange: TrimRange?,
    currentFilter: EditOperation.Filter?,
    currentSpeed: EditOperation.Speed?,
    currentVolume: EditOperation.Volume?,
    onExportClick: () -> Unit,
    onTrimClick: (TrimRange?) -> Unit,
    onRotateClick: () -> Unit,
    onFilterClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onVolumeClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Linha 1: Ações principais (Export, Trim, Rotate)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Exportar (destaque)
            Button(
                onClick = onExportClick,
                enabled = !isExporting && videoInfo,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Exportar", style = MaterialTheme.typography.labelMedium)
            }

            // Trim
            Button(
                onClick = { onTrimClick(trimRange) },
                enabled = !isExporting && trimRange != null,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Trim", style = MaterialTheme.typography.labelSmall)
            }

            // Rotacionar
            Button(
                onClick = onRotateClick,
                enabled = !isExporting,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Rotacionar", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Linha 2: Efeitos (Filtros, Velocidade, Volume)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Filtros
            FilterButton(
                currentFilter = currentFilter,
                onClick = onFilterClick,
                enabled = !isExporting
            )

            // Velocidade
            SpeedButton(
                currentSpeed = currentSpeed,
                onClick = onSpeedClick,
                enabled = !isExporting
            )

            // Volume
            VolumeButton(
                currentVolume = currentVolume,
                onClick = onVolumeClick,
                enabled = !isExporting
            )
        }
    }
}

/**
 * Botão de filtros com indicador de filtro ativo.
 */
@Composable
private fun FilterButton(
    currentFilter: EditOperation.Filter?,
    onClick: () -> Unit,
    enabled: Boolean
) {
    val isActive = currentFilter != null
    val filterName = when (currentFilter?.filterType) {
        FilterType.GRAYSCALE -> "P&B"
        FilterType.SEPIA -> "Sépia"
        FilterType.BRIGHTNESS -> "Brilho"
        FilterType.CONTRAST -> "Contraste"
        FilterType.SATURATION -> "Saturação"
        else -> "Filtros"
    }

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            Icons.Default.Settings,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(4.dp))
        Text(filterName, style = MaterialTheme.typography.labelSmall)
        if (isActive) {
            Spacer(Modifier.width(4.dp))
            Text(
                String.format("%.0f%%", currentFilter.intensity * 100),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Botão de velocidade com indicador de velocidade atual.
 */
@Composable
private fun SpeedButton(
    currentSpeed: EditOperation.Speed?,
    onClick: () -> Unit,
    enabled: Boolean
) {
    val isActive = currentSpeed != null && currentSpeed.speed != 1.0f
    val speedText = if (isActive) String.format("%.1fx", currentSpeed!!.speed) else "Velocidade"

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            if (isActive && currentSpeed!!.speed < 1f) Icons.Default.ArrowBack else Icons.Default.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (isActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(4.dp))
        Text(speedText, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * Botão de volume com indicador de volume atual.
 */
@Composable
private fun VolumeButton(
    currentVolume: EditOperation.Volume?,
    onClick: () -> Unit,
    enabled: Boolean
) {
    val isActive = currentVolume != null && currentVolume.volume != 1.0f
    val volumePercent = currentVolume?.volume?.let { (it * 100).toInt() } ?: 100
    val volumeText = if (isActive) "$volumePercent%" else "Volume"

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = when {
                currentVolume?.volume == 0f -> MaterialTheme.colorScheme.errorContainer
                isActive -> MaterialTheme.colorScheme.primaryContainer
                else -> Color.Transparent
            }
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            when {
                currentVolume?.volume == 0f -> Icons.Default.Close
                else -> Icons.Default.Notifications
            },
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(4.dp))
        Text(volumeText, style = MaterialTheme.typography.labelSmall)
    }
}
