package com.chopcut.ui.screen

import android.net.Uri
import android.view.Gravity
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.chopcut.data.model.EditOperation
import com.chopcut.data.model.ExportPreset
import com.chopcut.data.model.FilterType
import com.chopcut.data.thumbnail.ThumbnailExtractor
import com.chopcut.ui.components.TrimRange
import com.chopcut.ui.components.VideoPreview
import com.chopcut.ui.components.VideoTimeline
import com.chopcut.ui.components.WaveForm
import com.chopcut.ui.preview.PreviewManager
import com.chopcut.ui.filter.FilterContent
import com.chopcut.ui.filter.SpeedContent
import com.chopcut.ui.filter.AudioControlContent
import com.chopcut.ui.filter.rememberFilterState
import com.chopcut.ui.filter.rememberSpeedState
import com.chopcut.ui.filter.rememberAudioState
import timber.log.Timber

enum class EditorTool {
    NONE,
    TRIM,
    FILTER,
    SPEED,
    VOLUME
}

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
    val exportProgress by editorViewModel.exportProgress.collectAsStateWithLifecycle()
    val edits by editorViewModel.edits.collectAsStateWithLifecycle()
    val canUndo by editorViewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by editorViewModel.canRedo.collectAsStateWithLifecycle()
    val saveStatus by editorViewModel.saveStatus.collectAsStateWithLifecycle()
    val presets by editorViewModel.availablePresets.collectAsStateWithLifecycle(initialValue = emptyList())

    // Active Tool State
    var activeTool by remember { mutableStateOf(EditorTool.NONE) }

    // Estado da tela de exportação unificada
    var exportScreenState by remember { mutableStateOf(ExportScreenState.SELECTING_PRESET) }
    var showExportScreen by remember { mutableStateOf(false) }
    var lastExportUri by remember { mutableStateOf<Uri?>(null) }
    var lastExportName by remember { mutableStateOf<String?>(null) }
    var lastExportError by remember { mutableStateOf<String?>(null) }

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

    // Gerenciar estado de exportação baseado no resultado
    LaunchedEffect(exportResult) {
        val result = exportResult
        if (result != null) {
            result.getOrNull()?.let { outputUri ->
                lastExportUri = outputUri
                lastExportName = outputUri.lastPathSegment?.substringAfterLast('/')
                    ?: "ChopCut_${System.currentTimeMillis()}.mp4"
                exportScreenState = ExportScreenState.SUCCESS
            } ?: run {
                lastExportError = result.exceptionOrNull()?.message ?: "Erro desconhecido"
                exportScreenState = ExportScreenState.ERROR
            }
        }
    }

    // Atualizar estado para EXPORTING quando isExporting muda
    LaunchedEffect(isExporting) {
        if (isExporting && showExportScreen) {
            exportScreenState = ExportScreenState.EXPORTING
        }
    }

    // Estados ativos das features
    val hasTrim by remember { derivedStateOf { edits.any { it is EditOperation.Trim } } }
    val hasFilter by remember { derivedStateOf { 
        val last = edits.filterIsInstance<EditOperation.Filter>().lastOrNull()
        last != null && last.filterType != FilterType.NONE
    } }
    val hasSpeed by remember { derivedStateOf { edits.any { it is EditOperation.Speed && it.speed != 1.0f } } }
    val hasVolume by remember { derivedStateOf {
        edits.any { it is EditOperation.Volume && it.volume != 1.0f } }
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { editorViewModel.undo() }, enabled = canUndo) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Desfazer")
                    }
                    IconButton(onClick = { editorViewModel.redo() }, enabled = canRedo) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Refazer")
                    }
                    // Botão Exportar
                    IconButton(
                        onClick = {
                            exportScreenState = ExportScreenState.SELECTING_PRESET
                            showExportScreen = true
                        },
                        enabled = videoInfo != null && !isExporting
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Exportar")
                    }
                }
            )
        },
        bottomBar = {
            EditorBottomBar(
                activeTool = activeTool,
                onToolChange = { tool -> activeTool = tool },
                isExporting = isExporting,
                hasTrim = hasTrim,
                hasFilter = hasFilter,
                hasSpeed = hasSpeed,
                hasVolume = hasVolume,
                videoInfo = videoInfo,
                trimRange = trimRange,
                edits = edits,
                onApplyEdit = { op ->
                    editorViewModel.addOperation(op)
                    activeTool = EditorTool.NONE
                },
                onTrimClick = { range ->
                    if (range != null) editorViewModel.applyTrim(range)
                },
                onRotateClick = { editorViewModel.testOperation("rotate") }
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

            if (edits.isNotEmpty()) {
                val opNames = edits.reversed().map { op ->
                    when (op) {
                        is EditOperation.Trim -> "Trim"
                        is EditOperation.Rotation -> "Rotation"
                        is EditOperation.Resize -> "Resize"
                        is EditOperation.Crop -> "Crop"
                        is EditOperation.Filter -> "Filter"
                        is EditOperation.Speed -> "Speed"
                        is EditOperation.Volume -> "Volume"
                        is EditOperation.Fade -> "Fade"
                        else -> "Op"
                    }
                }

                Text(
                    text = "Histórico: ${opNames.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 8.sp
                )
                Spacer(Modifier.height(8.dp))
            }

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
                    // Extract fade settings
                    val fadeOp = edits.filterIsInstance<EditOperation.Fade>().lastOrNull()
                    val fadeInMs = fadeOp?.fadeInMs ?: 0L
                    val fadeOutMs = fadeOp?.fadeOutMs ?: 0L
                    val duration = videoInfo?.durationMs ?: 0L

                    WaveForm(
                        amplitudes = waveformData.amplitudes,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        fadeInMs = fadeInMs,
                        fadeOutMs = fadeOutMs,
                        durationMs = duration
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
            }
        }
    }

    // Tela unificada de Exportação e Resultado
    if (showExportScreen) {
        ExportResultScreen(
            state = exportScreenState,
            presets = presets,
            inputData = ExportInputData(
                trimRange = trimRange,
                originalWidth = videoInfo?.width ?: 0,
                originalHeight = videoInfo?.height ?: 0,
                originalBitrate = (videoInfo?.bitrate ?: 0).toInt()
            ),
            resultUri = lastExportUri,
            resultName = lastExportName,
            exportProgress = exportProgress,
            exportError = lastExportError,
            onPresetSelected = { preset ->
                exportScreenState = ExportScreenState.EXPORTING
                editorViewModel.exportVideo(trimRange, preset)
            },
            onDismiss = {
                showExportScreen = false
                // Resetar estado ao fechar
                if (!isExporting) {
                    exportScreenState = ExportScreenState.SELECTING_PRESET
                }
            },
            onBackToEditor = {
                showExportScreen = false
                exportScreenState = ExportScreenState.SELECTING_PRESET
            },
            onRetry = {
                // Tentar novamente com o mesmo preset
                exportScreenState = ExportScreenState.SELECTING_PRESET
            }
        )
    }
}

@Composable
fun VideoInfoDisplay(videoInfo: com.chopcut.data.model.VideoInfo) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Video Information",
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Resolution: ${videoInfo.width}x${videoInfo.height}",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Duration: ${formatTime(videoInfo.durationMs)}",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Frame Rate: ${videoInfo.frameRate} fps",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Bitrate: ${videoInfo.bitrate / 1_000_000} Mbps",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Codec: ${videoInfo.videoCodec ?: "Unknown"}",
            style = MaterialTheme.typography.bodySmall
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
 * BottomBar do editor com botões de features e informações do vídeo.
 */
@Composable
private fun EditorBottomBar(
    activeTool: EditorTool,
    onToolChange: (EditorTool) -> Unit,
    isExporting: Boolean,
    hasTrim: Boolean,
    hasFilter: Boolean,
    hasSpeed: Boolean,
    hasVolume: Boolean,
    videoInfo: com.chopcut.data.model.VideoInfo?,
    trimRange: TrimRange?,
    edits: List<EditOperation>,
    onApplyEdit: (EditOperation) -> Unit,
    onTrimClick: (TrimRange?) -> Unit,
    onRotateClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WindowInsets.navigationBars.asPaddingValues())
        ) {
            if (activeTool == EditorTool.NONE) {
                // Default Menu
                
                // Informações do vídeo (compactas)
                if (videoInfo != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VideoInfoBadge(
                            icon = Icons.Default.PlayArrow,
                            text = formatTime(videoInfo.durationMs)
                        )
                        VideoInfoBadge(
                            icon = Icons.Default.Edit,
                            text = "${videoInfo.width}x${videoInfo.height}"
                        )
                        VideoInfoBadge(
                            icon = Icons.Default.Notifications,
                            text = "${videoInfo.frameRate} fps"
                        )
                        VideoInfoBadge(
                            icon = Icons.Default.Share,
                            text = "${videoInfo.bitrate / 1_000_000}M"
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }

                // Botões de features
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Trim
                    FeatureButton(
                        icon = Icons.Default.Check,
                        label = "Trim",
                        isActive = hasTrim,
                        enabled = !isExporting,
                        onClick = { onTrimClick(null) }
                    )

                    // Rotate
                    FeatureButton(
                        icon = Icons.Default.Refresh,
                        label = "Girar",
                        isActive = false,
                        enabled = !isExporting,
                        onClick = onRotateClick
                    )

                    // Filter
                    FeatureButton(
                        icon = Icons.Default.Settings,
                        label = "Filtro",
                        isActive = hasFilter,
                        enabled = !isExporting,
                        onClick = { onToolChange(EditorTool.FILTER) }
                    )

                    // Speed
                    FeatureButton(
                        icon = Icons.Default.PlayArrow,
                        label = "Veloc",
                        isActive = hasSpeed,
                        enabled = !isExporting,
                        onClick = { onToolChange(EditorTool.SPEED) }
                    )

                    // Volume
                    FeatureButton(
                        icon = Icons.Default.Notifications,
                        label = "Volume",
                        isActive = hasVolume,
                        enabled = !isExporting,
                        onClick = { onToolChange(EditorTool.VOLUME) }
                    )
                }
            } else {
                // Tool Content
                Box(
                    modifier = Modifier.fillMaxWidth().animateContentSize()
                ) {
                    when (activeTool) {
                        EditorTool.FILTER -> {
                            val currentFilter = edits.filterIsInstance<EditOperation.Filter>().lastOrNull()
                            val state = rememberFilterState(currentFilter)
                            FilterContent(
                                filterState = state,
                                onConfirm = { op -> 
                                    if (op != null) {
                                        onApplyEdit(op) 
                                    } else {
                                        // Apply NONE filter to clear
                                        onApplyEdit(EditOperation.Filter(FilterType.NONE, 1.0f))
                                    }
                                    onToolChange(EditorTool.NONE)
                                },
                                onDismiss = { onToolChange(EditorTool.NONE) }
                            )
                        }
                        EditorTool.SPEED -> {
                            val currentSpeed = edits.filterIsInstance<EditOperation.Speed>().lastOrNull()
                            val state = rememberSpeedState(currentSpeed)
                            SpeedContent(
                                speedState = state,
                                onConfirm = { op -> 
                                    onApplyEdit(op)
                                    onToolChange(EditorTool.NONE)
                                },
                                onDismiss = { onToolChange(EditorTool.NONE) },
                                getDisplayColor = { 
                                    if (state.selectedValue < 1.0f) Color(0xFF4CAF50)
                                    else if (state.selectedValue > 1.0f) Color(0xFFFF9800)
                                    else MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                        EditorTool.VOLUME -> {
                            val currentVolume = edits.filterIsInstance<EditOperation.Volume>().lastOrNull()
                            val currentFade = edits.filterIsInstance<EditOperation.Fade>().lastOrNull()
                            val state = rememberAudioState(currentVolume, currentFade)
                            AudioControlContent(
                                audioState = state,
                                onConfirm = { ops -> 
                                    ops.forEach { op -> onApplyEdit(op) }
                                    onToolChange(EditorTool.NONE)
                                },
                                onDismiss = { onToolChange(EditorTool.NONE) }
                            )
                        }
                        else -> { /* No content */ }
                    }
                }
            }
        }
    }
}

/**
 * Botão de feature para a BottomBar.
 */
@Composable
private fun FeatureButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (isActive) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = if (enabled) {
                    if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) {
                if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * Badge compacto para informações do vídeo na BottomBar.
 */
@Composable
private fun VideoInfoBadge(
    icon: ImageVector,
    text: String
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
