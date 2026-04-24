package com.chopcut.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chopcut.ui.components.loading.LoadingConstants
import com.chopcut.ui.components.loading.LoadingOverlay
import kotlinx.coroutines.delay
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import com.chopcut.data.pipeline.TransformerPipeline
import com.chopcut.data.pipeline.TrimProgress
import com.chopcut.data.repository.VideoRepository
import com.chopcut.ui.components.timeline.VideoFileInfo
import com.chopcut.ui.components.timeline.VideoTimeline
import com.chopcut.ui.components.timeline.SeekbarProgress
import com.chopcut.ui.components.timeline.CurrentTimeDisplay
import com.chopcut.ui.components.timeline.VideoPreview
import com.chopcut.ui.components.trim.TrimControlPanel
import com.chopcut.ui.components.trim.SaveDialogState
import com.chopcut.ui.components.trim.TrimSaveDialog
import com.chopcut.ui.components.feedback.ErrorState
import com.chopcut.ui.theme.ChopCutSpacing
import com.chopcut.util.FileNameUtils
import com.chopcut.util.RangeUtils
import com.chopcut.util.FormatUtils
import com.chopcut.ui.viewmodel.TrimViewModel
import com.chopcut.ui.viewmodel.TrimEditorState
import com.chopcut.ui.viewmodel.AudioViewModel
import com.chopcut.ui.viewmodel.PreloadViewModel
import com.chopcut.ui.viewmodel.PreloadUiState
import com.chopcut.ui.viewmodel.PreloadProgress
import com.chopcut.ui.viewmodel.ExtractionStage
import com.chopcut.ui.viewmodel.ThumbnailViewModel
import com.chopcut.ui.viewmodel.VideoTimelineViewModel
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimScreen(
    videoUri: Uri,
    preloadViewModel: PreloadViewModel,
    thumbnailViewModel: ThumbnailViewModel,
    audioViewModel: AudioViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
 
    // Observar PreloadViewModel com lifecycle awareness
    val preloadUiState by preloadViewModel.uiState.collectAsStateWithLifecycle()

    // Observar ThumbnailViewModel com lifecycle awareness
    val thumbnailStrips by thumbnailViewModel.strips.collectAsStateWithLifecycle()
    val thumbnailProgress by thumbnailViewModel.thumbnailProgress.collectAsStateWithLifecycle()

    // Observar AudioViewModel com lifecycle awareness
    val audioAmplitudes by audioViewModel.amplitudes.collectAsStateWithLifecycle()
    // Criar TrimViewModel
    val viewModel: TrimViewModel = viewModel(
        factory = TrimViewModel.TrimViewModelFactory(videoUri)
    )
    val state by viewModel.state.collectAsState()

    var saveDialogState by remember { mutableStateOf(SaveDialogState()) }
    var showSaveDialog by remember { mutableStateOf(false) }

    // Sincronizar dados das ViewModels para TrimViewModel
    LaunchedEffect(audioAmplitudes) {
        if (audioAmplitudes.isNotEmpty()) {
            viewModel.updateAudioAmplitudes(audioAmplitudes)
        }
    }

    // Estados de controle do overlay
    var showLoadingOverlay by remember { mutableStateOf(true) }
    var elapsedTimeMs by remember { mutableStateOf(0L) }
    var isReadyToHide by remember { mutableStateOf(false) }

    // Verificar se dados já estão completos (cache hit) - simplificado
    val isDataAlreadyCached = remember(thumbnailStrips) {
        val requiredStrips = 6
        val hasThumbnails = thumbnailStrips.size >= requiredStrips
        hasThumbnails
    }

    // Calcular tempo mínimo de loading dinamicamente (5% da duração do vídeo)
    val minLoadingDurationMs = remember(state.videoDurationMs) {
        if (state.videoDurationMs > 0) {
            val calculatedMin = (state.videoDurationMs * LoadingConstants.MIN_LOADING_PERCENTAGE).toLong()
            val clampedMin = calculatedMin.coerceIn(500L, LoadingConstants.MAX_LOADING_DURATION_MS)
            clampedMin
        } else {
            2_000L
        }
    }

    // Se já estiver no cache, esconder o overlay imediatamente
    LaunchedEffect(isDataAlreadyCached) {
        if (isDataAlreadyCached) {
            showLoadingOverlay = false
        }
    }

    // Monitoramento REATIVO para esconder overlay (substitui while loop)
    // OTIMIZAÇÃO: Usa snapshotFlow para reagir apenas a mudanças reais, eliminando polling
    LaunchedEffect(showLoadingOverlay, isDataAlreadyCached) {
        if (!showLoadingOverlay || isDataAlreadyCached) {
            return@LaunchedEffect
        }

        val startTime = System.currentTimeMillis()

        // Timer para atualizar elapsedTimeMs periodicamente
        scope.launch {
            while (showLoadingOverlay) {
                elapsedTimeMs = System.currentTimeMillis() - startTime
                delay(100) // Apenas para atualizar o timer visual
            }
        }

        // Observação REATIVA de mudanças nos dados (substitui while loop)
        snapshotFlow {
            val currentElapsed = System.currentTimeMillis() - startTime
            val minTimeReached = currentElapsed >= minLoadingDurationMs
            val maxTimeReached = currentElapsed >= LoadingConstants.MAX_LOADING_DURATION_MS
            val hasSufficientThumbnails = thumbnailStrips.size >= 6
            val hasAudio = audioAmplitudes.isNotEmpty()

            val shouldHide = shouldHideLoadingOverlay(
                state = preloadUiState,
                minTimeReached = minTimeReached,
                maxTimeReached = maxTimeReached,
                hasSufficientThumbnails = hasSufficientThumbnails,
                hasAudio = true // Ignorar áudio temporariamente conforme solicitado
            )

            Triple(shouldHide, currentElapsed, hasSufficientThumbnails)
        }
            .distinctUntilChanged()
            .collect { (shouldHide, currentElapsed, hasSufficientThumbnails) ->
                if (shouldHide) {
                    val minTimeReached = currentElapsed >= minLoadingDurationMs
                    val maxTimeReached = currentElapsed >= LoadingConstants.MAX_LOADING_DURATION_MS
                    val hasAudio = audioAmplitudes.isNotEmpty()

                    val reason = getHideReason(
                        state = preloadUiState,
                        maxTimeReached = maxTimeReached,
                        minTimeReached = minTimeReached,
                        hasSufficientThumbnails = hasSufficientThumbnails,
                        elapsedTimeMs = currentElapsed,
                        thumbnailProgress = thumbnailProgress
                    )

                    isReadyToHide = true
                    delay(LoadingConstants.CROSS_FADE_START_DELAY_MS)
                    showLoadingOverlay = false
                }
            }
    }

    // Cancelar preload ao pressionar voltar durante loading
    BackHandler(enabled = showLoadingOverlay) {
        preloadViewModel.cancelPreload()
        showLoadingOverlay = false
        onNavigateBack()
    }

    val videoRepository = remember { VideoRepository(context) }
    val transformerPipeline = remember { TransformerPipeline(context, videoRepository) }
    
    // Carregar waveform de áudio via AudioViewModel (Activity-scoped)
    LaunchedEffect(videoUri) {
        if (videoUri != Uri.EMPTY) {
            audioViewModel.loadWaveform(videoUri)
        }
    }

    when {
        videoUri == Uri.EMPTY -> {
            ErrorState(
                title = "Nenhum vídeo selecionado",
                message = "Selecione um vídeo para começar a editar",
                modifier = Modifier.fillMaxSize(),
                actionLabel = "Voltar",
                onAction = onNavigateBack
            )
        }
        else -> {
            // Box wrapper para permitir overlay
            Box(modifier = Modifier.fillMaxSize()) {
                // Animação de entrada suave para TrimScreen
                AnimatedVisibility(
                    visible = !showLoadingOverlay,
                    enter = fadeIn(
                        animationSpec = tween(
                            durationMillis = LoadingConstants.TRIM_FADE_IN_DURATION_MS,
                            easing = FastOutSlowInEasing
                        )
                    ) + scaleIn(
                        initialScale = LoadingConstants.TRIM_SCALE_IN_START,
                        animationSpec = tween(
                            durationMillis = LoadingConstants.TRIM_FADE_IN_DURATION_MS,
                            easing = FastOutSlowInEasing
                        )
                    ),
                    exit = fadeOut(
                        animationSpec = tween(
                            durationMillis = 300,
                            easing = LinearEasing
                        )
                    )
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Editor", fontWeight = FontWeight.Bold) },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.Black,
                                    titleContentColor = Color.White,
                                    navigationIconContentColor = Color.White
                                ),
                                navigationIcon = {
                                    IconButton(onClick = onNavigateBack) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = {
                                            val ranges = state.trimPosition.completeRanges
                                            if (ranges.isEmpty()) {
                                                Toast.makeText(context, "Adicione pelo menos um corte", Toast.LENGTH_SHORT).show()
                                                return@IconButton
                                            }
                                            showSaveDialog = true
                                        },
                                        enabled = !saveDialogState.isSaving
                                    ) {
                                        Icon(
                                            if (saveDialogState.isSaving) Icons.Default.Check else Icons.Default.Save,
                                            contentDescription = "Salvar",
                                            tint = if (saveDialogState.isSaving) Color(0xFF00E5FF) else Color.White
                                        )
                                    }
                                }
                            )
                        }
                    ) { paddingValues ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0A0A0A)) // Fundo premium
                                .padding(paddingValues)
                        ) {
                            // Area do Video Player
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1.3f), // Dá mais peso ao player
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                VideoFileInfo(
                                    fileInfo = FormatUtils.getFileInfo(context, videoUri, state.videoDurationMs)
                                )

                                VideoPreview(
                                    exoPlayer = state.exoPlayer ?: return@Column,
                                    isPlaying = state.isPlaying,
                                    isInsideRange = state.isInsideRange,
                                    playerError = state.playerError,
                                    isSecurityError = state.isSecurityError,
                                    currentTimeMs = state.currentPosition,
                                    onRequestNewMedia = { },
                                    onRetry = { viewModel.retryPlayer() },
                                    onTogglePlayPause = {
                                        if (state.isPlaying) viewModel.pause() else viewModel.play()
                                    },
                                    modifier = Modifier.fillMaxWidth().weight(1f)
                                )

                                // Passive Seekbar
                                val progress = if (state.videoDurationMs > 0) state.currentPosition.toFloat() / state.videoDurationMs.toFloat() else 0f
                                SeekbarProgress(progress = progress, modifier = Modifier.padding(vertical = 8.dp))
                            }

                            Spacer(Modifier.height(16.dp))

                            // Timeline Area
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.3f))
                                    .padding(vertical = 12.dp)
                            ) {
                                if (state.videoDurationMs > 0) {
                                    VideoTimeline(
                                        modifier = Modifier.fillMaxWidth(),
                                        videoUri = videoUri,
                                        durationMs = state.videoDurationMs,
                                        currentPositionMs = state.currentPosition,
                                        onSeek = { viewModel.setCurrentPosition(it) },
                                        onScrubStart = { viewModel.startScrubbing() },
                                        onScrubStop = { finalPos -> viewModel.stopScrubbing(finalPos) },
                                        trimRanges = state.trimPosition.completeRanges,
                                        audioAmplitudes = audioAmplitudes,
                                        showWaveform = true,
                                        videoWidth = state.videoWidth,
                                        videoHeight = state.videoHeight
                                    )
                                }
                            }

                            val isInsideRange = state.trimPosition.isPositionInRange(state.currentPosition)

                            TrimControlPanel(
                                modifier = Modifier.padding(bottom = 24.dp),
                                isDraftMode = state.trimPosition.isDraftMode,
                                isInsideRange = isInsideRange,
                                onAddPosition = { viewModel.addPosition(state.currentPosition) },
                                onDelete = { viewModel.removeRangeAt(state.currentPosition) }
                            )
                        }
                    }
                } // Fecha AnimatedVisibility

                // LoadingOverlay renderizado quando visível
                if (showLoadingOverlay) {
                    LoadingOverlay(
                        progress = when (val state = preloadUiState) {
                            is PreloadUiState.Loading -> state.progress
                            else -> PreloadProgress(stage = ExtractionStage.Starting)
                        },
                        elapsedTimeMs = elapsedTimeMs,
                        isReadyToHide = isReadyToHide
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        TrimSaveDialog(
            state = saveDialogState,
            onDismiss = {
                if (saveDialogState.canDismiss) {
                    showSaveDialog = false
                    saveDialogState = SaveDialogState()
                }
            },
            onSave = {
                scope.launch(Dispatchers.IO) {
                    saveDialogState = saveDialogState.copy(isSaving = true, progress = 0)
                    try {
                        val trimRanges = state.trimPosition.completeRanges.sortedBy { it.first }


                        val rangesToSave = RangeUtils.calculateKeepRanges(trimRanges, state.videoDurationMs)


                        if (rangesToSave.isEmpty()) {
                            throw Exception("No ranges to save - video is empty")
                        }

                        transformerPipeline.trim(videoUri, rangesToSave)
                            .collect { progress ->
                                when (progress) {
                                    is TrimProgress.InProgress -> {
                                        saveDialogState = saveDialogState.copy(progress = progress.percent)
                                    }
                                    is TrimProgress.Completed -> {
                                        val trimmedFile = progress.file

                                        val originalFileName = FileNameUtils.extractBaseNameFromUri(videoUri)
                                        val fileName = FileNameUtils.generateTimestampedFileName(originalFileName)

                                        videoRepository.saveToGallery(trimmedFile, fileName)
                                        trimmedFile.delete()

                                        saveDialogState = saveDialogState.copy(isCompleted = true, isSaving = false)
                                    }
                                    is TrimProgress.Failed -> {
                                        saveDialogState = saveDialogState.copy(
                                            error = progress.error.message ?: "Erro desconhecido",
                                            isSaving = false
                                        )
                                    }
                                }
                            }
                    } catch (e: Exception) {
                        saveDialogState = saveDialogState.copy(
                            error = e.message ?: "Erro desconhecido",
                            isSaving = false
                        )
                    }
                }
            },
            onNavigateBack = onNavigateBack
        )
    }
}

// Funções auxiliares para lógica de loading

private fun shouldHideLoadingOverlay(
    state: PreloadUiState,
    minTimeReached: Boolean,
    maxTimeReached: Boolean,
    hasSufficientThumbnails: Boolean,
    hasAudio: Boolean
): Boolean {
    return when (state) {
        is PreloadUiState.Ready -> {
            true
        }
        is PreloadUiState.Loading -> {
            // Sempre esconder no timeout máximo
            maxTimeReached || (minTimeReached && hasSufficientThumbnails && hasAudio)
        }
        is PreloadUiState.Error, is PreloadUiState.Cancelled -> {
            true
        }
        is PreloadUiState.Idle -> {
            // Fallback: se está em Idle por mais de 2 segundos, esconder para não ficar preso
            true
        }
    }
}

private fun getHideReason(
    state: PreloadUiState,
    maxTimeReached: Boolean,
    minTimeReached: Boolean,
    hasSufficientThumbnails: Boolean,
    elapsedTimeMs: Long,
    thumbnailProgress: Float
): String {
    return when {
        state is PreloadUiState.Error -> "Error state"
        state is PreloadUiState.Cancelled -> "Cancelled by user"
        state is PreloadUiState.Idle -> "Idle state (fallback)"
        maxTimeReached -> "Max timeout (${LoadingConstants.MAX_LOADING_DURATION_MS / 1000}s)"
        state is PreloadUiState.Ready -> "Ready (${elapsedTimeMs / 1000}s, ${thumbnailProgress.toInt()}% thumbnails)"
        minTimeReached && hasSufficientThumbnails -> "Min time reached + sufficient data"
        else -> "Unknown reason"
    }
}
