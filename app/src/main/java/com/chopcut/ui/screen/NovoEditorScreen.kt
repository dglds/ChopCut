package com.chopcut.ui.screen

import android.net.Uri
import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.chopcut.ui.components.EditorSplitLayout
import com.chopcut.ui.components.ToolPanelContainer
import com.chopcut.ui.timeline.PreviewManager
import com.chopcut.ui.timeline.EditorTimelineViewModel
import com.chopcut.ui.timeline.model.EstadoFab
import com.chopcut.ui.timeline.model.EstadoPlayer
import com.chopcut.ui.timeline.model.EventoEditor
import com.chopcut.ui.timeline.model.EstadoCriacao
import com.chopcut.ui.timeline.components.VideoPreview
import com.chopcut.ui.timeline.components.TimelineScrubber
import com.chopcut.ui.timeline.components.PlayheadIndicator
import com.chopcut.ui.timeline.components.FabRangeController
import com.chopcut.ui.timeline.components.RangeOverlay
import com.chopcut.ui.timeline.components.ProgressBar
import com.chopcut.ui.timeline.util.ConfiguracaoTimeline
import com.chopcut.ui.filter.TrimContent
import com.chopcut.ui.filter.CropContent
import com.chopcut.ui.filter.FilterContent
import com.chopcut.ui.filter.RotationContent
import com.chopcut.ui.filter.SpeedContent
import com.chopcut.ui.filter.rememberFilterState
import com.chopcut.ui.filter.rememberSpeedState
import timber.log.Timber

/**
 * NOVA ARQUITETURA - Editor Screen refatorado (Fases 1-4)
 * 
 * Esta é uma reimplementação do EditorScreen usando:
 * - ViewModel consolidado (EditorTimelineViewModel)
 * - Componentes puros (VideoPreview, TimelineScrubber, RangeOverlay, etc)
 * - Estado unificado (EstadoEditor)
 * - Fluxo unidirecional de dados
 * 
 * Para usar esta versão, substitua a chamada de EditorScreen por NovoEditorScreen
 * no ponto de entrada da navegação.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovoEditorScreen(
    videoUri: Uri,
    projectId: String? = null,
    onNavigateBack: () -> Unit = {},
    onExportComplete: (Uri) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // ===== VIEWMODELS =====
    val previewManager = remember { PreviewManager(context) }
    
    val timelineViewModel = remember { 
        EditorTimelineViewModel(context, previewManager) 
    }
    
    val editorViewModel: EditorViewModel = viewModel(
        factory = EditorViewModelFactory(
            context = context,
            videoUri = videoUri,
            projectId = projectId
        )
    )

    // ===== ESTADOS CONSOLIDADOS =====
    val estado by timelineViewModel.estadoEditor.collectAsStateWithLifecycle()
    val estadoFab by timelineViewModel.estadoFab.collectAsStateWithLifecycle()
    val rangeEmCriacao by timelineViewModel.rangeEmCriacao.collectAsStateWithLifecycle()
    val rangeNoPlayhead by timelineViewModel.rangeNoPlayhead.collectAsStateWithLifecycle()

    // ===== ESTADOS DO EDITOR ORIGINAL =====
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

    // ===== ESTADOS LOCAIS =====
    var exportScreenState by remember { mutableStateOf(ExportScreenState.SELECTING_PRESET) }
    var showExportScreen by remember { mutableStateOf(false) }
    var lastExportUri by remember { mutableStateOf<Uri?>(null) }
    var lastExportName by remember { mutableStateOf<String?>(null) }
    var lastExportError by remember { mutableStateOf<String?>(null) }
    var trimRange by remember { mutableStateOf<TrimRange?>(null) }

    // ===== INICIALIZAÇÃO =====
    LaunchedEffect(videoUri) {
        timelineViewModel.prepararVideo(videoUri, videoInfo?.durationMs ?: 0)
    }

    LaunchedEffect(videoInfo) {
        videoInfo?.let { info ->
            if (info.durationMs > 0 && trimRange == null) {
                trimRange = TrimRange(0L, info.durationMs)
            }
        }
    }

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

    LaunchedEffect(exportResult) {
        exportResult?.getOrNull()?.let { outputUri ->
            lastExportUri = outputUri
            lastExportName = outputUri.lastPathSegment?.substringAfterLast('/')
                ?: "ChopCut_${System.currentTimeMillis()}.mp4"
            exportScreenState = ExportScreenState.SUCCESS
        } ?: exportResult?.exceptionOrNull()?.let { error ->
            lastExportError = error.message ?: "Erro desconhecido"
            exportScreenState = ExportScreenState.ERROR
        }
    }

    LaunchedEffect(isExporting) {
        if (isExporting && showExportScreen) {
            exportScreenState = ExportScreenState.EXPORTING
        }
    }

    // ===== DERIVED STATES =====
    val hasTrim by remember { derivedStateOf { edits.any { it is EditOperation.Trim } } }
    val hasCrop by remember { derivedStateOf { edits.any { it is EditOperation.Crop } } }  
    val hasFilter by remember { derivedStateOf { 
        edits.filterIsInstance<EditOperation.Filter>().lastOrNull()?.filterType != FilterType.NONE 
    } }
    val hasSpeed by remember { derivedStateOf { 
        edits.filterIsInstance<EditOperation.Speed>().lastOrNull()?.speed != 1.0f 
    } }

    // ===== UI =====
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ChopCut Editor (Novo)")
                        Text(
                            text = when (saveStatus) {
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
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
        },
        floatingActionButton = {
            FabRangeController(
                estado = estadoFab,
                onClick = {
                    when (estadoFab) {
                        is EstadoFab.ADICIONAR -> {
                            timelineViewModel.processarEvento(EventoEditor.IniciarCriacaoRange)
                        }
                        is EstadoFab.CONFIRMAR -> {
                            timelineViewModel.processarEvento(EventoEditor.FinalizarCriacaoRange)
                        }
                        is EstadoFab.DELETAR -> {
                            timelineViewModel.processarEvento(EventoEditor.DeletarRangeNoPlayhead)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        EditorSplitLayout(
            modifier = Modifier.padding(paddingValues),
            topWeight = 0.8f,
            bottomWeight = 0.2f,
            topContent = {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ===== TIMER =====
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatarTempo(estado.posicaoPlayheadMs)} / ${formatarTempo(estado.duracaoTotalMs)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    }

                    // ===== PLAYER + TIMELINE =====
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.Black)
                    ) {
                        if (estado.videoUri != null) {
                            NovoPlayerTimelineLayout(
                                viewModel = timelineViewModel,
                                estado = estado,
                                estadoFab = estadoFab,
                                rangeEmCriacao = rangeEmCriacao,
                                rangeNoPlayhead = rangeNoPlayhead,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }

                    // ===== INFO + FERRAMENTAS =====
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Info do vídeo
                        videoInfo?.let { info ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                NovoVideoInfoBadge(Icons.Default.PlayArrow, formatTimeLocal(info.durationMs))
                                NovoVideoInfoBadge(Icons.Default.Edit, "${info.width}x${info.height}")
                                NovoVideoInfoBadge(Icons.Default.Notifications, "${info.frameRate} fps")
                            }
                        }

                        // Painel de ferramentas ativo
                        if (activeTool != EditorTool.NONE) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                tonalElevation = 2.dp,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                ToolPanelContainer(currentState = activeTool) { targetTool ->
                                    when (targetTool) {
                                        EditorTool.TRIM -> {
                                            TrimContent(
                                                currentPosition = estado.posicaoPlayheadMs,
                                                duration = estado.duracaoTotalMs,
                                                initialTrim = trimRange,
                                                onConfirm = { range ->
                                                    range?.let { editorViewModel.applyTrim(it) }
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
                                                    op?.let { editorViewModel.addOperation(it) }
                                                },
                                                onDismiss = { editorViewModel.setActiveTool(EditorTool.NONE) }
                                            )
                                        }
                                        EditorTool.ROTATE -> {
                                            val totalRotation = edits.filterIsInstance<EditOperation.Rotation>().sumOf { it.degrees }
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
                                                    op?.let { editorViewModel.addOperation(it) }
                                                        ?: editorViewModel.addOperation(EditOperation.Filter(FilterType.NONE, 1.0f))
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
                                                    when {
                                                        state.selectedValue < 1.0f -> Color(0xFF4CAF50)
                                                        state.selectedValue > 1.0f -> Color(0xFFFF9800)
                                                        else -> MaterialTheme.colorScheme.primary
                                                    }
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
                // Barra inferior (opcional)
            }
        )
    }

    // ===== EXPORT SCREEN =====
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

/**
 * Layout interno do player + timeline usando componentes puros.
 */
@Composable
private fun NovoPlayerTimelineLayout(
    viewModel: EditorTimelineViewModel,
    estado: com.chopcut.ui.timeline.model.EstadoEditor,
    estadoFab: EstadoFab,
    rangeEmCriacao: com.chopcut.ui.timeline.model.RangeCorte?,
    rangeNoPlayhead: com.chopcut.ui.timeline.model.RangeCorte?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    
    // Estados de scroll
    var scrollOffsetPx by remember { mutableFloatStateOf(0f) }
    var isScrolling by remember { mutableStateOf(false) }
    
    // Dimensões
    val pxPorSegundo = remember(density) { 
        density.density * ConfiguracaoTimeline.PX_POR_SEGUNDO_DP.value 
    }

    Column(modifier = modifier) {
        // ===== VIDEO PREVIEW =====
        VideoPreview(
            videoUri = estado.videoUri,
            exoPlayer = viewModel.previewManager.exoPlayer,
            isReady = estado.estadoPlayer != EstadoPlayer.CARREGANDO,
            isPlaying = estado.estadoPlayer == EstadoPlayer.REPRODUZINDO,
            currentPosition = estado.posicaoPlayheadMs,
            duration = estado.duracaoTotalMs,
            onTogglePlayPause = {
                viewModel.processarEvento(EventoEditor.AlternarReproducao)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.75f)
        )

        // ===== BARRA DE PROGRESSO =====
        if (estado.duracaoTotalMs > 0) {
            ProgressBar(
                progress = estado.progresso,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            )
        }

        // ===== CONTROLES =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { viewModel.processarEvento(EventoEditor.AlternarReproducao) },
                    enabled = estado.isPronto,
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = if (estado.estadoPlayer == EstadoPlayer.REPRODUZINDO) "⏸" else "▶",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Button(
                    onClick = { viewModel.processarEvento(EventoEditor.Parar) },
                    enabled = estado.isPronto,
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(text = "■", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        // ===== TIMELINE COM OVERLAY =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ConfiguracaoTimeline.ALTURA_FAIXA_DP)
        ) {
            // Scrubber
            TimelineScrubber(
                durationMs = estado.duracaoTotalMs,
                positionMs = estado.posicaoPlayheadMs,
                onPositionChange = { novaPosicaoMs ->
                    viewModel.atualizarPosicao(novaPosicaoMs, deArraste = true)
                },
                onScrollStart = {
                    isScrolling = true
                    viewModel.setEmArraste(true)
                },
                onScrollEnd = {
                    isScrolling = false
                    viewModel.setEmArraste(false)
                },
                modifier = Modifier.fillMaxSize()
            )

            // Overlay de ranges
            if (estado.duracaoTotalMs > 0) {
                val containerWidthPx = context.resources.displayMetrics.widthPixels.toFloat()
                
                RangeOverlay(
                    ranges = estado.ranges,
                    rangeEmCriacao = rangeEmCriacao,
                    rangeSelecionadoId = estado.rangeSelecionadoId,
                    posicaoPlayheadMs = estado.posicaoPlayheadMs,
                    duracaoMs = estado.duracaoTotalMs,
                    scrollOffsetPx = scrollOffsetPx,
                    containerWidthPx = containerWidthPx,
                    pxPorSegundo = pxPorSegundo,
                    onRangeSelect = { rangeId ->
                        viewModel.processarEvento(EventoEditor.SelecionarRange(rangeId))
                    },
                    onRangeUpdate = { id, inicio, fim ->
                        viewModel.processarEvento(EventoEditor.AtualizarRange(id, inicio, fim))
                    },
                    onRangeDelete = { id ->
                        viewModel.processarEvento(EventoEditor.DeletarRange(id))
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Playhead fixo
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                PlayheadIndicator(
                    isRelevo = estado.emCriacaoRange,
                    modifier = Modifier.fillMaxHeight()
                )
            }
        }
    }
}

// ===== FUNÇÕES AUXILIARES =====

@Composable
private fun NovoVideoInfoBadge(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(3.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatarTempo(ms: Long): String {
    val totalSegundos = ms / 1000
    val minutos = totalSegundos / 60
    val segundos = totalSegundos % 60
    val decimos = (ms % 1000) / 100
    return "%02d:%02d.%d".format(minutos, segundos, decimos)
}

// Usa a função formatTime do EditorScreen.kt
private fun formatTimeLocal(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
