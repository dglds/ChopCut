package com.chopcut.ui.screen

import android.net.Uri
import android.view.Gravity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.chopcut.ui.components.TrimRange
import com.chopcut.ui.components.VideoTimeline
import com.chopcut.ui.filter.TrimContent
import com.chopcut.ui.filter.CropContent
import com.chopcut.ui.components.EditorSplitLayout
import com.chopcut.ui.components.ToolPanelContainer
import com.chopcut.ui.preview.PreviewManager
import com.chopcut.ui.preview.PlayerState
import com.chopcut.ui.filter.FilterContent
import com.chopcut.ui.filter.RotationContent
import com.chopcut.ui.filter.SpeedContent
import com.chopcut.ui.filter.rememberFilterState
import com.chopcut.ui.filter.rememberSpeedState
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

    val previewManager = remember { PreviewManager(context) }

    var trimRange by remember { mutableStateOf<TrimRange?>(null) }

    val editorViewModel: EditorViewModel = viewModel(
        factory = EditorViewModelFactory(
            context = context,
            videoUri = videoUri,
            projectId = projectId
        )
    )

    val project by editorViewModel.project.collectAsStateWithLifecycle()
    val videoInfo by editorViewModel.videoInfo.collectAsStateWithLifecycle()
    val exportResult by editorViewModel.exportResult.collectAsStateWithLifecycle()
    val isExporting by editorViewModel.isExporting.collectAsStateWithLifecycle()
    val exportProgress by editorViewModel.exportProgress.collectAsStateWithLifecycle()
    val edits by editorViewModel.edits.collectAsStateWithLifecycle()
    val canUndo by editorViewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by editorViewModel.canRedo.collectAsStateWithLifecycle()
    val saveStatus by editorViewModel.saveStatus.collectAsStateWithLifecycle()
    val presets by editorViewModel.availablePresets.collectAsStateWithLifecycle(initialValue = emptyList())

    // Active Tool State from ViewModel
    val activeTool by editorViewModel.activeTool.collectAsStateWithLifecycle()

    // Player state
    val playerState by previewManager.playerState.collectAsStateWithLifecycle()

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

    LaunchedEffect(videoInfo) {
        val info = videoInfo
        if (info != null) {
            val videoDurationMs = info.durationMs
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
    val hasCrop by remember { derivedStateOf { edits.any { it is EditOperation.Crop } } }
    val hasFilter by remember { derivedStateOf {
        val last = edits.filterIsInstance<EditOperation.Filter>().lastOrNull()
        last != null && last.filterType != FilterType.NONE
    } }
    val hasSpeed by remember { derivedStateOf { edits.any { it is EditOperation.Speed && it.speed != 1.0f } } }

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
        }
    ) { paddingValues ->
        // Split Layout Refactoring with Weights (85/15)
        EditorSplitLayout(
            modifier = Modifier.padding(paddingValues),
            topWeight = 0.85f,
            bottomWeight = 0.15f,
            topContent = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    val totalRotation = edits.filterIsInstance<EditOperation.Rotation>()
                        .sumOf { it.degrees }
                        .toFloat() % 360f

                    // Video Info Display (above preview)
                    val info = videoInfo
                    if (info != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VideoInfoBadge(
                                icon = Icons.Default.PlayArrow,
                                text = formatTime(info.durationMs),
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                textTint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            VideoInfoBadge(
                                icon = Icons.Default.Edit,
                                text = "${info.width}x${info.height}",
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                textTint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            VideoInfoBadge(
                                icon = Icons.Default.Notifications,
                                text = "${info.frameRate} fps",
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                textTint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            VideoInfoBadge(
                                icon = Icons.Default.Share,
                                text = "${info.bitrate / 1_000_000}M",
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                textTint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // Player state badge
                            val (playerStateIcon, playerStateColor) = when (playerState) {
                                PlayerState.PLAYING -> Icons.Default.PlayArrow to Color(0xFF4CAF50) // Green
                                PlayerState.PAUSED -> Icons.Default.Close to Color(0xFFFFEB3B) // Yellow
                                PlayerState.STOPPED -> Icons.Default.Close to Color(0xFFF44336) // Red
                            }
                            val playerStateText = when (playerState) {
                                PlayerState.PLAYING -> "Playing"
                                PlayerState.PAUSED -> "Paused"
                                PlayerState.STOPPED -> "Stopped"
                            }
                            VideoInfoBadge(
                                icon = playerStateIcon,
                                text = playerStateText,
                                iconTint = playerStateColor,
                                textTint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // VideoTimeline unificado (Player + Timeline)
                    val currentVideoInfo = videoInfo
                    if (currentVideoInfo != null) {
                        VideoTimeline(
                            uri = videoUri,
                            previewManager = previewManager,
                            modifier = Modifier.fillMaxWidth(),
                            rotationDegrees = totalRotation,
                            onVideoClick = {
                                if (previewManager.isPlaying.value) {
                                    previewManager.pause()
                                } else {
                                    previewManager.play()
                                }
                            },
                            videoWidth = currentVideoInfo.width,
                            videoHeight = currentVideoInfo.height
                        )

                        // Timer Centralizado
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            val currentPos = previewManager.currentPosition.collectAsState().value
                            val seconds = currentPos / 1000
                            val decis = (currentPos % 1000) / 100
                            val timerText = String.format(java.util.Locale.US, "%02d,%d", seconds, decis)

                            Text(
                                text = timerText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    // ===== CONTROLES PRINCIPAIS =====
                    Spacer(Modifier.height(16.dp))

                    // Tool Panel (quando uma ferramenta está ativa)
                    if (activeTool != EditorTool.NONE) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            tonalElevation = 2.dp,
                            shadowElevation = 2.dp
                        ) {
                            ToolPanelContainer(currentState = activeTool) { targetTool ->
                                when (targetTool) {
                                    EditorTool.TRIM -> {
                                        TrimContent(
                                            currentPosition = previewManager.currentPosition.collectAsState().value,
                                            duration = videoInfo?.durationMs ?: 0L,
                                            initialTrim = trimRange,
                                            onConfirm = { range ->
                                                if (range != null) editorViewModel.applyTrim(range)
                                            }
                                        )
                                    }
                                    EditorTool.CROP -> {
                                        val currentCrop = edits.filterIsInstance<EditOperation.Crop>().lastOrNull()
                                        val initialCropRect = currentCrop?.let {
                                            android.graphics.RectF(
                                                it.left / (videoInfo?.width ?: 1).toFloat(),
                                                it.top / (videoInfo?.height ?: 1).toFloat(),
                                                it.right / (videoInfo?.width ?: 1).toFloat(),
                                                it.bottom / (videoInfo?.height ?: 1).toFloat()
                                            )
                                        }

                                        CropContent(
                                            initialCrop = initialCropRect,
                                            videoWidth = videoInfo?.width ?: 0,
                                            videoHeight = videoInfo?.height ?: 0,
                                            onConfirm = { op ->
                                                if (op != null) {
                                                    editorViewModel.addOperation(op)
                                                }
                                            },
                                            onDismiss = { editorViewModel.setActiveTool(EditorTool.NONE) }
                                        )
                                    }
                                    EditorTool.ROTATE -> {
                                        val currentRotations = edits.filterIsInstance<EditOperation.Rotation>()
                                        val totalRotation = currentRotations.sumOf { it.degrees }

                                        RotationContent(
                                            initialRotation = totalRotation,
                                            onConfirm = { op ->
                                                editorViewModel.addOperation(op)
                                            },
                                            onDismiss = { editorViewModel.setActiveTool(EditorTool.NONE) }
                                        )
                                    }
                                    EditorTool.FILTER -> {
                                        val currentFilter = edits.filterIsInstance<EditOperation.Filter>().lastOrNull()
                                        val state = rememberFilterState(currentFilter)
                                        FilterContent(
                                            filterState = state,
                                            onConfirm = { op ->
                                                if (op != null) {
                                                    editorViewModel.addOperation(op)
                                                } else {
                                                    editorViewModel.addOperation(EditOperation.Filter(FilterType.NONE, 1.0f))
                                                }
                                            },
                                            onDismiss = { editorViewModel.setActiveTool(EditorTool.NONE) }
                                        )
                                    }
                                    EditorTool.SPEED -> {
                                        val currentSpeed = edits.filterIsInstance<EditOperation.Speed>().lastOrNull()
                                        val state = rememberSpeedState(currentSpeed)
                                        SpeedContent(
                                            speedState = state,
                                            onConfirm = { op ->
                                                editorViewModel.addOperation(op)
                                            },
                                            onDismiss = { editorViewModel.setActiveTool(EditorTool.NONE) },
                                            getDisplayColor = {
                                                if (state.selectedValue < 1.0f) Color(0xFF4CAF50)
                                                else if (state.selectedValue > 1.0f) Color(0xFFFF9800)
                                                else MaterialTheme.colorScheme.primary
                                            }
                                        )
                                    }
                                    else -> { /* No content */ }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

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
                }
            },
            bottomContent = {
                EditorControlsPanel(
                    activeTool = activeTool,
                    onToolChange = { tool -> editorViewModel.setActiveTool(tool) },
                    currentPosition = previewManager.currentPosition.collectAsState().value,
                    duration = videoInfo?.durationMs ?: 0L,
                    isExporting = isExporting,
                    hasTrim = hasTrim,
                    hasCrop = hasCrop,
                    hasFilter = hasFilter,
                    hasSpeed = hasSpeed,
                    videoInfo = videoInfo,
                    trimRange = trimRange,
                    edits = edits,
                    onApplyEdit = { op ->
                        editorViewModel.addOperation(op)
                    },
                    onTrimClick = { range ->
                        if (range != null) editorViewModel.applyTrim(range)
                    }
                )
            }
        )
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

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * Painel de controles na parte inferior da tela dividida com botões de seleção de features.
 */
@Composable
private fun EditorControlsPanel(
    activeTool: EditorTool,
    onToolChange: (EditorTool) -> Unit,
    currentPosition: Long,
    duration: Long,
    isExporting: Boolean,
    hasTrim: Boolean,
    hasCrop: Boolean,
    hasFilter: Boolean,
    hasSpeed: Boolean,
    videoInfo: com.chopcut.data.model.VideoInfo?,
    trimRange: TrimRange?,
    edits: List<EditOperation>,
    onApplyEdit: (EditOperation) -> Unit,
    onTrimClick: (TrimRange?) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.navigationBars.asPaddingValues())
        ) {
            // Botões de seleção de features
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Trim
                FeatureButton(
                    icon = Icons.Default.Check,
                    label = "Trim",
                    isActive = hasTrim,
                    isToolActive = activeTool == EditorTool.TRIM,
                    enabled = !isExporting,
                    onClick = { onToolChange(EditorTool.TRIM) }
                )
                // Crop
                FeatureButton(
                    icon = Icons.Default.Create,
                    label = "Crop",
                    isActive = hasCrop,
                    isToolActive = activeTool == EditorTool.CROP,
                    enabled = !isExporting,
                    onClick = { onToolChange(EditorTool.CROP) }
                )
                // Rotate
                FeatureButton(
                    icon = Icons.Default.Refresh,
                    label = "Girar",
                    isActive = edits.any { it is EditOperation.Rotation },
                    isToolActive = activeTool == EditorTool.ROTATE,
                    enabled = !isExporting,
                    onClick = { onToolChange(EditorTool.ROTATE) }
                )

                // Filter
                FeatureButton(
                    icon = Icons.Default.Settings,
                    label = "Filtro",
                    isActive = hasFilter,
                    isToolActive = activeTool == EditorTool.FILTER,
                    enabled = !isExporting,
                    onClick = { onToolChange(EditorTool.FILTER) }
                )

                // Speed
                FeatureButton(
                    icon = Icons.Default.PlayArrow,
                    label = "Veloc",
                    isActive = hasSpeed,
                    isToolActive = activeTool == EditorTool.SPEED,
                    enabled = !isExporting,
                    onClick = { onToolChange(EditorTool.SPEED) }
                )
            }
        }
    }
}

/**
 * Botão de feature para controles principais.
 */
@Composable
fun FeatureButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    isToolActive: Boolean = false,
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
                    when {
                        isToolActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        isActive -> MaterialTheme.colorScheme.primaryContainer
                        else -> Color.Transparent
                    },
                    CircleShape
                )
                .then(
                    if (isToolActive) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = if (enabled) {
                    when {
                        isToolActive -> MaterialTheme.colorScheme.primary
                        isActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isToolActive) FontWeight.Bold else FontWeight.Normal,
            color = if (enabled) {
                if (isToolActive || isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * Badge compacto para informações do vídeo.
 */
@Composable
fun VideoInfoBadge(
    icon: ImageVector,
    text: String,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
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
            tint = iconTint
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textTint
        )
    }
}