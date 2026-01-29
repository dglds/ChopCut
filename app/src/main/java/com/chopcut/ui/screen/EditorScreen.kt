package com.chopcut.ui.screen

import android.net.Uri
import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.chopcut.core.designsystem.atoms.*
import com.chopcut.core.designsystem.organisms.*
import com.chopcut.core.designsystem.organisms.TimelineRange
import com.chopcut.core.designsystem.templates.ScreenTemplate
import com.chopcut.core.designsystem.tokens.SpacingTokens
import com.chopcut.data.model.EditOperation
import com.chopcut.data.model.ExportPreset
import com.chopcut.data.model.FilterType
import com.chopcut.ui.components.RangeManager
import com.chopcut.ui.components.TrimRange
import com.chopcut.ui.components.TimelinePlayer
import com.chopcut.ui.filter.*
import com.chopcut.ui.timeline.PreviewManager
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

    val editorViewModel: EditorViewModel = viewModel(
        factory = EditorViewModelFactory(
            context = context,
            videoUri = videoUri,
            projectId = projectId
        )
    )

    // Estados do ViewModel
    val project by editorViewModel.project.collectAsStateWithLifecycle()
    val videoInfo by editorViewModel.videoInfo.collectAsStateWithLifecycle()
    val currentVideoUri by editorViewModel.currentVideoUri.collectAsStateWithLifecycle()
    val edits by editorViewModel.edits.collectAsStateWithLifecycle()
    val canUndo by editorViewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by editorViewModel.canRedo.collectAsStateWithLifecycle()
    val saveStatus by editorViewModel.saveStatus.collectAsStateWithLifecycle()
    val isExporting by editorViewModel.isExporting.collectAsStateWithLifecycle()
    val exportProgress by editorViewModel.exportProgress.collectAsStateWithLifecycle()
    val exportResult by editorViewModel.exportResult.collectAsStateWithLifecycle()
    val presets by editorViewModel.availablePresets.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeTool by editorViewModel.activeTool.collectAsStateWithLifecycle()
    
    // Estados do Player (novos)
    val currentTimeMs by editorViewModel.currentTimeMs.collectAsStateWithLifecycle()
    val isPlaying by editorViewModel.isPlaying.collectAsStateWithLifecycle()
    val ranges by editorViewModel.ranges.collectAsStateWithLifecycle()
    
    // Estados locais
    var trimRange by remember { mutableStateOf<TrimRange?>(null) }
    var showExportScreen by remember { mutableStateOf(false) }
    var exportScreenState by remember { mutableStateOf(ExportScreenState.SELECTING_PRESET) }
    var lastExportUri by remember { mutableStateOf<Uri?>(null) }
    var lastExportName by remember { mutableStateOf<String?>(null) }
    var lastExportError by remember { mutableStateOf<String?>(null) }

    // Efeitos
    LaunchedEffect(edits) { previewManager.applyEffects(edits) }
    
    LaunchedEffect(Unit) {
        editorViewModel.messageFlow.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT)
                .apply { setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 200) }
                .show()
        }
    }
    
    LaunchedEffect(videoInfo) {
        videoInfo?.let { info ->
            if (info.durationMs > 0 && trimRange == null) {
                trimRange = TrimRange(0L, info.durationMs)
            }
        }
    }
    
    LaunchedEffect(exportResult) {
        exportResult?.getOrNull()?.let { uri ->
            lastExportUri = uri
            lastExportName = uri.lastPathSegment?.substringAfterLast('/') ?: "ChopCut_${System.currentTimeMillis()}.mp4"
            exportScreenState = ExportScreenState.SUCCESS
        } ?: run {
            lastExportError = exportResult?.exceptionOrNull()?.message ?: "Erro desconhecido"
            exportScreenState = ExportScreenState.ERROR
        }
    }
    
    LaunchedEffect(isExporting) {
        if (isExporting && showExportScreen) exportScreenState = ExportScreenState.EXPORTING
    }

    // Status dos efeitos
    val hasTrim = remember(edits) { edits.any { it is EditOperation.Trim } }
    val hasCrop = remember(edits) { edits.any { it is EditOperation.Crop } }
    val hasFilter = remember(edits) { 
        edits.filterIsInstance<EditOperation.Filter>().lastOrNull()?.filterType != FilterType.NONE 
    }
    val hasSpeed = remember(edits) { edits.any { it is EditOperation.Speed && it.speed != 1.0f } }

    ScreenTemplate(
        title = "Editor",
        onBackClick = onNavigateBack,
        actions = {
            TopBarAction(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Desfazer",
                onClick = { editorViewModel.undo() }
            )
            TopBarAction(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Refazer",
                onClick = { editorViewModel.redo() }
            )
            TopBarAction(
                icon = Icons.Default.Share,
                contentDescription = "Exportar",
                onClick = {
                    exportScreenState = ExportScreenState.SELECTING_PRESET
                    showExportScreen = true
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // === PLAYER E TIMELINE ===
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
            ) {
                currentVideoUri?.let { uri ->
                    val duration = videoInfo?.durationMs ?: 0L
                    
                    TimelinePlayer(
                        videoUri = uri,
                        currentTimeMs = currentTimeMs,
                        isPlaying = isPlaying,
                        durationMs = duration,
                        ranges = ranges,
                        onTimeChange = { editorViewModel.seekTo(it) },
                        onPlayPauseClick = { editorViewModel.togglePlay() },
                        onAddRangeClick = {
                            // Adiciona range de 2 segundos na posição atual
                            val start = currentTimeMs.coerceAtMost(duration - 2000)
                            val end = (start + 2000).coerceAtMost(duration)
                            editorViewModel.addRange(start, end)
                        },
                        onRangeClick = { rangeId -> editorViewModel.selectRange(rangeId) }
                    )
                } ?: LoadingState(message = "Carregando vídeo...")
            }

            // === INFORMAÇÕES DO VÍDEO ===
            videoInfo?.let { info ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SpacingTokens.md),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    VideoInfoChip(
                        icon = Icons.Default.Timer,
                        text = formatTime(info.durationMs)
                    )
                    VideoInfoChip(
                        icon = Icons.Default.AspectRatio,
                        text = "${info.width}x${info.height}"
                    )
                    VideoInfoChip(
                        icon = Icons.Default.Speed,
                        text = "${info.frameRate} fps"
                    )
                }
            }

            // === PAINEL DE FERRAMENTAS ===
            ToolPanel(
                activeTool = activeTool,
                onToolChange = { editorViewModel.setActiveTool(it) },
                hasTrim = hasTrim,
                hasCrop = hasCrop,
                hasFilter = hasFilter,
                hasSpeed = hasSpeed,
                isExporting = isExporting,
                trimContent = {
                    TrimContent(
                        currentPosition = currentTimeMs,
                        duration = videoInfo?.durationMs ?: 0L,
                        initialTrim = trimRange,
                        onConfirm = { range ->
                            range?.let { editorViewModel.applyTrim(it) }
                        }
                    )
                },
                cropContent = {
                    val currentCrop = edits.filterIsInstance<EditOperation.Crop>().lastOrNull()
                    CropContent(
                        initialCrop = currentCrop?.let {
                            android.graphics.RectF(
                                it.left / (videoInfo?.width ?: 1).toFloat(),
                                it.top / (videoInfo?.height ?: 1).toFloat(),
                                it.right / (videoInfo?.width ?: 1).toFloat(),
                                it.bottom / (videoInfo?.height ?: 1).toFloat()
                            )
                        },
                        videoWidth = videoInfo?.width ?: 0,
                        videoHeight = videoInfo?.height ?: 0,
                        onConfirm = { op -> op?.let { editorViewModel.addOperation(it) } },
                        onDismiss = { editorViewModel.setActiveTool(EditorTool.NONE) }
                    )
                },
                rotateContent = {
                    val totalRotation = edits.filterIsInstance<EditOperation.Rotation>().sumOf { it.degrees }
                    RotationContent(
                        initialRotation = totalRotation,
                        onConfirm = { editorViewModel.addOperation(it) },
                        onDismiss = { editorViewModel.setActiveTool(EditorTool.NONE) }
                    )
                },
                filterContent = {
                    val currentFilter = edits.filterIsInstance<EditOperation.Filter>().lastOrNull()
                    FilterContent(
                        filterState = rememberFilterState(currentFilter),
                        onConfirm = { op ->
                            op?.let { editorViewModel.addOperation(it) }
                                ?: editorViewModel.addOperation(EditOperation.Filter(FilterType.NONE, 1.0f))
                        },
                        onDismiss = { editorViewModel.setActiveTool(EditorTool.NONE) }
                    )
                },
                speedContent = {
                    val currentSpeed = edits.filterIsInstance<EditOperation.Speed>().lastOrNull()
                    val speedState = rememberSpeedState(currentSpeed)
                    SpeedContent(
                        speedState = speedState,
                        onConfirm = { editorViewModel.addOperation(it) },
                        onDismiss = { editorViewModel.setActiveTool(EditorTool.NONE) },
                        getDisplayColor = {
                            when {
                                speedState.selectedValue < 1.0f -> Color(0xFF4CAF50)
                                speedState.selectedValue > 1.0f -> Color(0xFFFF9800)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        }
                    )
                }
            )

            // === BARRA DE FERRAMENTAS INFERIOR ===
            EditorBottomBar(
                activeTool = activeTool,
                onToolChange = { editorViewModel.setActiveTool(it) },
                hasTrim = hasTrim,
                hasCrop = hasCrop,
                hasFilter = hasFilter,
                hasSpeed = hasSpeed,
                isExporting = isExporting
            )
        }
    }

    // Tela de exportação
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
                if (!isExporting) exportScreenState = ExportScreenState.SELECTING_PRESET
            },
            onBackToEditor = {
                showExportScreen = false
                exportScreenState = ExportScreenState.SELECTING_PRESET
            },
            onRetry = { exportScreenState = ExportScreenState.SELECTING_PRESET }
        )
    }
}

// ============================================================================
// COMPONENTES AUXILIARES
// ============================================================================

@Composable
private fun VideoInfoChip(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(SpacingTokens.radiusMd))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = SpacingTokens.md, vertical = SpacingTokens.sm)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(SpacingTokens.sm))
        SmallText(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ToolPanel(
    activeTool: EditorTool,
    onToolChange: (EditorTool) -> Unit,
    hasTrim: Boolean,
    hasCrop: Boolean,
    hasFilter: Boolean,
    hasSpeed: Boolean,
    isExporting: Boolean,
    trimContent: @Composable () -> Unit,
    cropContent: @Composable () -> Unit,
    rotateContent: @Composable () -> Unit,
    filterContent: @Composable () -> Unit,
    speedContent: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = activeTool != EditorTool.NONE,
        modifier = Modifier.fillMaxWidth()
    ) {
        SurfaceCard {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SpacingTokens.lg)
            ) {
                when (activeTool) {
                    EditorTool.TRIM -> trimContent()
                    EditorTool.CROP -> cropContent()
                    EditorTool.ROTATE -> rotateContent()
                    EditorTool.FILTER -> filterContent()
                    EditorTool.SPEED -> speedContent()
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun EditorBottomBar(
    activeTool: EditorTool,
    onToolChange: (EditorTool) -> Unit,
    hasTrim: Boolean,
    hasCrop: Boolean,
    hasFilter: Boolean,
    hasSpeed: Boolean,
    isExporting: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpacingTokens.md),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolButton(
                icon = Icons.Default.ContentCut,
                label = "Cortar",
                isActive = hasTrim,
                isSelected = activeTool == EditorTool.TRIM,
                enabled = !isExporting,
                onClick = { onToolChange(EditorTool.TRIM) }
            )
            ToolButton(
                icon = Icons.Default.Crop,
                label = "Crop",
                isActive = hasCrop,
                isSelected = activeTool == EditorTool.CROP,
                enabled = !isExporting,
                onClick = { onToolChange(EditorTool.CROP) }
            )
            ToolButton(
                icon = Icons.Default.RotateRight,
                label = "Girar",
                isActive = false,
                isSelected = activeTool == EditorTool.ROTATE,
                enabled = !isExporting,
                onClick = { onToolChange(EditorTool.ROTATE) }
            )
            ToolButton(
                icon = Icons.Default.Palette,
                label = "Filtro",
                isActive = hasFilter,
                isSelected = activeTool == EditorTool.FILTER,
                enabled = !isExporting,
                onClick = { onToolChange(EditorTool.FILTER) }
            )
            ToolButton(
                icon = Icons.Default.Speed,
                label = "Velocidade",
                isActive = hasSpeed,
                isSelected = activeTool == EditorTool.SPEED,
                enabled = !isExporting,
                onClick = { onToolChange(EditorTool.SPEED) }
            )
        }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    ) {
        SurfaceIconBox(
            icon = icon,
            contentDescription = label,
            modifier = Modifier
                .background(
                    when {
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        isActive -> MaterialTheme.colorScheme.secondaryContainer
                        else -> Color.Transparent
                    },
                    CircleShape
                )
        )
        Spacer(Modifier.height(SpacingTokens.xs))
        LabelText(text = label)
    }
}

private fun Modifier.clickable(enabled: Boolean, onClick: () -> Unit): Modifier = this.then(
    androidx.compose.foundation.clickable(enabled = enabled, onClick = onClick)
)

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

// Fix imports
import androidx.compose.animation.AnimatedVisibility
