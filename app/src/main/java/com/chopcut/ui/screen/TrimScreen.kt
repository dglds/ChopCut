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

    val scope = rememberCoroutineScope()
    val videoRepository = remember { VideoRepository(context) }
    val transformerPipeline = remember { TransformerPipeline(context, videoRepository) }
    
    // Calcular número de barras baseado na duração do vídeo
    val targetBarCount = remember(state.videoDurationMs) {
        if (state.videoDurationMs > 0) {
            when {
                state.videoDurationMs < 30000 -> (state.videoDurationMs / 50).toInt().coerceAtLeast(100)
                state.videoDurationMs < 120000 -> (state.videoDurationMs / 100).toInt().coerceAtLeast(200)
                else -> (state.videoDurationMs / 200).toInt().coerceAtLeast(300).coerceAtMost(800)
            }
        } else {
            300
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

private fun formatAspectRatio(ratio: Float): String {
    return when {
        (ratio - 16f / 9f).let { kotlin.math.abs(it) } < 0.01f -> "16:9"
        (ratio - 9f / 16f).let { kotlin.math.abs(it) } < 0.01f -> "9:16"
        (ratio - 4f / 3f).let { kotlin.math.abs(it) } < 0.01f -> "4:3"
        (ratio - 1f).let { kotlin.math.abs(it) } < 0.01f -> "1:1"
        else -> "%.2f".format(ratio)
    }
}
