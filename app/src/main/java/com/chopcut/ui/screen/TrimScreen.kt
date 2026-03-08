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
import com.chopcut.ui.components.TimelineEditor
import com.chopcut.ui.components.trim.TrimControlPanel
import com.chopcut.ui.components.trim.SaveDialogState
import com.chopcut.ui.components.trim.TrimSaveDialog
import com.chopcut.ui.components.feedback.ErrorState
import com.chopcut.ui.theme.ChopCutSpacing
import com.chopcut.utils.FileNameUtils
import com.chopcut.utils.RangeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

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

    // Observar PreloadViewModel com lifecycle awareness
    val preloadUiState by preloadViewModel.uiState.collectAsStateWithLifecycle()

    // Observar ThumbnailViewModel com lifecycle awareness
    val thumbnailStrips by thumbnailViewModel.strips.collectAsStateWithLifecycle()
    val thumbnailProgress by thumbnailViewModel.thumbnailProgress.collectAsStateWithLifecycle()

    // Observar AudioViewModel com lifecycle awareness
    val audioAmplitudes by audioViewModel.amplitudes.collectAsStateWithLifecycle()
    val audioWaveform by audioViewModel.waveform.collectAsStateWithLifecycle()

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
        Timber.d("Cache check: thumbnails=${thumbnailStrips.size}/$requiredStrips, ready=$hasThumbnails")
        hasThumbnails
    }

    // Calcular tempo mínimo de loading dinamicamente (5% da duração do vídeo)
    val minLoadingDurationMs = remember(state.videoDurationMs) {
        if (state.videoDurationMs > 0) {
            val calculatedMin = (state.videoDurationMs * LoadingConstants.MIN_LOADING_PERCENTAGE).toLong()
            val clampedMin = calculatedMin.coerceIn(500L, LoadingConstants.MAX_LOADING_DURATION_MS)
            Timber.d("Min loading duration calculado: ${clampedMin}ms (${calculatedMin}ms base, " +
                    "video=${state.videoDurationMs}ms)")
            clampedMin
        } else {
            2_000L
        }
    }

    // Se já estiver no cache, esconder o overlay imediatamente
    LaunchedEffect(isDataAlreadyCached) {
        Timber.d("LaunchedEffect isDataAlreadyCached: $isDataAlreadyCached, showLoadingOverlay=$showLoadingOverlay")
        if (isDataAlreadyCached) {
            Timber.i("Dados já em cache, escondendo overlay de loading")
            showLoadingOverlay = false
        }
    }

    // Monitoramento REATIVO para esconder overlay (substitui while loop)
    // OTIMIZAÇÃO: Usa snapshotFlow para reagir apenas a mudanças reais, eliminando polling
    LaunchedEffect(showLoadingOverlay, isDataAlreadyCached) {
        Timber.d("LaunchedEffect monitoring: showLoadingOverlay=$showLoadingOverlay, isDataAlreadyCached=$isDataAlreadyCached")
        if (!showLoadingOverlay || isDataAlreadyCached) {
            Timber.d("Skipping monitoring: overlay hidden or data cached")
            return@LaunchedEffect
        }

        val startTime = System.currentTimeMillis()

        // Timer para atualizar elapsedTimeMs periodicamente
        launch {
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
                    Timber.i("🎯 LoadingOverlay pronto para esconder: $reason (thumbnails=${thumbnailStrips.size}, audio=$hasAudio)")

                    isReadyToHide = true
                    delay(LoadingConstants.CROSS_FADE_START_DELAY_MS)
                    showLoadingOverlay = false
                    Timber.i("✅ LoadingOverlay escondido: $reason")
                }
            }
    }

    // Cancelar preload ao pressionar voltar durante loading
    BackHandler(enabled = showLoadingOverlay) {
        preloadViewModel.cancelPreload()
        showLoadingOverlay = false
        onNavigateBack()
    }

    val scope = rememberCoroutineScope()

    val videoRepository = remember { VideoRepository(context) }
    val transformerPipeline = remember { TransformerPipeline(context, videoRepository) }
    
    // Calcular número de barras baseado na duração do vídeo
    val targetBarCount = remember(state.videoDurationMs) {
        if (state.videoDurationMs > 0) {
            // Para vídeos curtos (< 30s): mais barras para detalhe
            // Para vídeos longos: menos barras para performance
            when {
                state.videoDurationMs < 30000 -> (state.videoDurationMs / 50).toInt().coerceAtLeast(100)
                state.videoDurationMs < 120000 -> (state.videoDurationMs / 100).toInt().coerceAtLeast(200)
                else -> (state.videoDurationMs / 200).toInt().coerceAtLeast(300).coerceAtMost(800)
            }
        } else {
            300 // valor padrão antes de conhecer a duração
        }
    }

    /* 
    LaunchedEffect(videoUri) {
        if (videoUri != Uri.EMPTY) {
            viewModel.loadWaveform(videoUri)
        }
    }
    */

    // OTIMIZAÇÃO: Removido LaunchedEffect que chamava loadAudioWaveforms()
    // Os dados de áudio já são carregados pelo AudioViewModel e sincronizados via updateAudioAmplitudes()
    // Isso elimina extração duplicada de áudio (economia de 66% de I/O)

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
                                title = { Text("Editor de Trim") },
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
                                            contentDescription = "Salvar"
                                        )
                                    }
                                }
                            )
                        }
                    ) { paddingValues ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                        ) {
                            // Área do Timeline com contador
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(Color(0xFF1A1A1A))
                            ) {
                                TimelineEditor(
                                    videoUri = videoUri,
                                    trimPosition = state.trimPosition,
                                    currentPosition = state.currentPosition,
                                    waveformData = state.waveformData,
                                    isWaveformLoading = state.isWaveformLoading,
                                    waveformError = state.waveformError,
                                    waveformStyle = com.chopcut.ui.components.WaveformStyle(),
                                    audioWaveformsAmplitudes = state.audioWaveformsAmplitudes,
                                    isAudioWaveformsLoading = state.isAudioWaveformsLoading,
                                    preloadedStrips = thumbnailStrips,
                                    aspectRatio = 16f/9f,
                                    onPositionChange = { viewModel.setCurrentPosition(it) },
                                    onAddPosition = { viewModel.addPosition(state.currentPosition) },
                                    onRequestNewMedia = { },
                                    onVideoDurationChange = { duration -> viewModel.setVideoDuration(duration) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            val isInsideRange = state.trimPosition.isPositionInRange(state.currentPosition)

                            TrimControlPanel(
                                isDraftMode = state.trimPosition.isDraftMode,
                                isInsideRange = isInsideRange,
                                onAddPosition = { viewModel.addPosition(state.currentPosition) },
                                onDelete = { viewModel.removeRangeAt(state.currentPosition) }
                            )

                            Spacer(modifier = Modifier.height(ChopCutSpacing.xxl))
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

                        Timber.d("Trim ranges: $trimRanges")
                        Timber.d("Video duration: ${state.videoDurationMs}")

                        val rangesToSave = RangeUtils.calculateKeepRanges(trimRanges, state.videoDurationMs)

                        Timber.d("Keep ranges to save: $rangesToSave")

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
                                        Timber.d("Trimmed file exists: ${trimmedFile.exists()}, size: ${trimmedFile.length()}")

                                        val originalFileName = FileNameUtils.extractBaseNameFromUri(videoUri)
                                        val fileName = FileNameUtils.generateTimestampedFileName(originalFileName)

                                        videoRepository.saveToGallery(trimmedFile, fileName)
                                        trimmedFile.delete()

                                        saveDialogState = saveDialogState.copy(isCompleted = true, isSaving = false)
                                    }
                                    is TrimProgress.Failed -> {
                                        Timber.e(progress.error, "TransformerPipeline trim error")
                                        saveDialogState = saveDialogState.copy(
                                            error = progress.error.message ?: "Erro desconhecido",
                                            isSaving = false
                                        )
                                    }
                                }
                            }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to save video")
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
            hasSufficientThumbnails
        }
        is PreloadUiState.Loading -> {
            // Sempre esconder no timeout máximo
            maxTimeReached || (minTimeReached && hasSufficientThumbnails && hasAudio)
        }
        is PreloadUiState.Error, is PreloadUiState.Cancelled -> {
            Timber.w("Error/Cancelled state: hiding overlay")
            true
        }
        is PreloadUiState.Idle -> {
            // Fallback: se está em Idle por mais de 2 segundos, esconder para não ficar preso
            Timber.w("Idle state: hiding overlay (fallback)")
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

private fun formatAspectRatio(ratio: Float): String {
    return when {
        (ratio - 16f / 9f).let { kotlin.math.abs(it) } < 0.01f -> "16:9"
        (ratio - 9f / 16f).let { kotlin.math.abs(it) } < 0.01f -> "9:16"
        (ratio - 4f / 3f).let { kotlin.math.abs(it) } < 0.01f -> "4:3"
        (ratio - 1f).let { kotlin.math.abs(it) } < 0.01f -> "1:1"
        else -> "%.2f".format(ratio)
    }
}
