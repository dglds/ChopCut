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
import com.chopcut.ui.components.RangeManager
import com.chopcut.ui.components.TrimRange
import com.chopcut.ui.components.TimelinePlayer
import com.chopcut.ui.filter.TrimContent
import com.chopcut.ui.filter.CropContent
import com.chopcut.ui.components.EditorSplitLayout
import com.chopcut.ui.components.ToolPanelContainer
import com.chopcut.ui.timeline.PreviewManager
import com.chopcut.ui.timeline.PlayerState
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

    val activeTool by editorViewModel.activeTool.collectAsStateWithLifecycle()
    val playerState by previewManager.playerState.collectAsStateWithLifecycle()

    // Range Manager para edição de vídeo - recriado quando a duração do vídeo mudar
    val rangeManager = remember(videoInfo?.durationMs) {
        RangeManager(
            minRangeDurationMs = 500L,
            videoDurationMs = videoInfo?.durationMs ?: 0L
        )
    }

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

    LaunchedEffect(isExporting) {
        if (isExporting && showExportScreen) {
            exportScreenState = ExportScreenState.EXPORTING
        }
    }

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
                        Text("ChopCut Editor")
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
        EditorSplitLayout(
            modifier = Modifier.padding(paddingValues),
            topWeight = 0.88f,
            bottomWeight = 0.12f,
            topContent = {
                Column(modifier = Modifier.fillMaxSize()) {
                    val currentVideoUri by editorViewModel.currentVideoUri.collectAsStateWithLifecycle()
                    val videoUri = currentVideoUri
                    
                    // Area do Player e Timeline (Fixo)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.Black)
                    ) {
                        if (videoUri != null) {
                            // Função para adicionar range na posição atual
                            fun addRangeAtCurrentPosition() {
                                val durationMs = videoInfo?.durationMs ?: 0L
                                if (durationMs > 0) {
                                    val currentPosition = 0L // TODO: Obter posição atual do player
                                    val defaultDurationMs = 2000L // 2 segundos padrão

                                    val halfDuration = defaultDurationMs / 2
                                    val startMs = maxOf(0L, currentPosition - halfDuration)
                                    val endMs = minOf(durationMs, currentPosition + halfDuration)

                                    rangeManager.addRangeAt(startMs, endMs)
                                        .onFailure { error ->
                                            Timber.e("Erro ao adicionar range: ${error.message}")
                                        }
                                }
                            }

                            TimelinePlayer(
                                videoUri = videoUri,
                                rangeManager = rangeManager,
                                onAddRangeRequest = { addRangeAtCurrentPosition() },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }

                    // Area de Ferramentas e Info (Scrollable)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        val info = videoInfo
                        if (info != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                VideoInfoBadge(Icons.Default.PlayArrow, formatTime(info.durationMs))
                                VideoInfoBadge(Icons.Default.Edit, "${info.width}x${info.height}")
                                VideoInfoBadge(Icons.Default.Notifications, "${info.frameRate} fps")
                            }
                        }

                        if (activeTool != EditorTool.NONE) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                tonalElevation = 2.dp,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                ToolPanelContainer(currentState = activeTool) { targetTool ->
                                    when (targetTool) {
                                        EditorTool.TRIM -> {
                                            TrimContent(
                                                currentPosition = 0L,
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
                                                    if (op != null) editorViewModel.addOperation(op)
                                                },
                                                onDismiss = { editorViewModel.setActiveTool(EditorTool.NONE) }
                                            )
                                        }
                                        EditorTool.ROTATE -> {
                                            val currentRotations = edits.filterIsInstance<EditOperation.Rotation>()
                                            val totalRotation = currentRotations.sumOf { it.degrees }

                                            RotationContent(
                                                initialRotation = totalRotation,
                                                onConfirm = { op -> editorViewModel.addOperation(op) },
                                                onDismiss = { editorViewModel.setActiveTool(EditorTool.NONE) }
                                            )
                                        }
                                        EditorTool.FILTER -> {
                                            val currentFilter = edits.filterIsInstance<EditOperation.Filter>().lastOrNull()
                                            val state = rememberFilterState(currentFilter)
                                            FilterContent(
                                                filterState = state,
                                                onConfirm = { op ->
                                                    if (op != null) editorViewModel.addOperation(op)
                                                    else editorViewModel.addOperation(EditOperation.Filter(FilterType.NONE, 1.0f))
                                                },
                                                onDismiss = { editorViewModel.setActiveTool(EditorTool.NONE) }
                                            )
                                        }
                                        EditorTool.SPEED -> {
                                            val currentSpeed = edits.filterIsInstance<EditOperation.Speed>().lastOrNull()
                                            val state = rememberSpeedState(currentSpeed)
                                            SpeedContent(
                                                speedState = state,
                                                onConfirm = { op -> editorViewModel.addOperation(op) },
                                                onDismiss = { editorViewModel.setActiveTool(EditorTool.NONE) },
                                                getDisplayColor = {
                                                    if (state.selectedValue < 1.0f) Color(0xFF4CAF50)
                                                    else if (state.selectedValue > 1.0f) Color(0xFFFF9800)
                                                    else MaterialTheme.colorScheme.primary
                                                }
                                            )
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                }
            },
            bottomContent = {
                EditorControlsPanel(
                    activeTool = activeTool,
                    onToolChange = { tool -> editorViewModel.setActiveTool(tool) },
                    currentPosition = 0L,
                    duration = videoInfo?.durationMs ?: 0L,
                    isExporting = isExporting,
                    hasTrim = hasTrim,
                    hasCrop = hasCrop,
                    hasFilter = hasFilter,
                    hasSpeed = hasSpeed,
                    videoInfo = videoInfo,
                    trimRange = trimRange,
                    edits = edits,
                    onApplyEdit = { op -> editorViewModel.addOperation(op) },
                    onTrimClick = { range -> if (range != null) editorViewModel.applyTrim(range) }
                )
            }
        )
    }

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

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

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
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FeatureButton(Icons.Default.Check, "Trim", hasTrim, activeTool == EditorTool.TRIM, !isExporting) { onToolChange(EditorTool.TRIM) }
            FeatureButton(Icons.Default.Create, "Crop", hasCrop, activeTool == EditorTool.CROP, !isExporting) { onToolChange(EditorTool.CROP) }
            FeatureButton(Icons.Default.Refresh, "Girar", edits.any { it is EditOperation.Rotation }, activeTool == EditorTool.ROTATE, !isExporting) { onToolChange(EditorTool.ROTATE) }
            FeatureButton(Icons.Default.Settings, "Filtro", hasFilter, activeTool == EditorTool.FILTER, !isExporting) { onToolChange(EditorTool.FILTER) }
            FeatureButton(Icons.Default.PlayArrow, "Veloc", hasSpeed, activeTool == EditorTool.SPEED, !isExporting) { onToolChange(EditorTool.SPEED) }
        }
    }
}

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
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (isToolActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    else if (isActive) MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent,
                    CircleShape
                )
                .then(if (isToolActive) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, label, Modifier.size(24.dp),
                tint = if (enabled) (if (isToolActive || isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun VideoInfoBadge(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        Icon(icon, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(3.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
