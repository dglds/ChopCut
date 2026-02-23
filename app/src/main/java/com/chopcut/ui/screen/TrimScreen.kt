package com.chopcut.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chopcut.data.pipeline.TransformerPipeline
import com.chopcut.data.pipeline.TrimProgress
import com.chopcut.data.repository.VideoRepository
import com.chopcut.data.model.TimeRange
import com.chopcut.ui.components.TimelineEditor
import com.chopcut.ui.components.trim.TrimControlPanel
import com.chopcut.ui.components.feedback.ErrorState
import com.chopcut.ui.theme.ChopCutSpacing
import com.chopcut.ui.screen.TrimViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimScreen(
    videoUri: Uri,
    viewModel: TrimViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var savingProgress by remember { mutableIntStateOf(0) }
    var saveCompleted by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    val videoRepository = remember { VideoRepository(context) }
    val transformerPipeline = remember { TransformerPipeline(context, videoRepository) }

    // Calcular número de barras baseado na duração do vídeo
    // Aproximadamente 1 barra por 100ms de vídeo (ajustável conforme preferência)
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

    LaunchedEffect(videoUri) {
        if (videoUri != Uri.EMPTY) {
            viewModel.loadWaveform(videoUri)
        }
    }

    // Carregar AudioWaveForms quando tivermos a duração do vídeo
    LaunchedEffect(state.videoDurationMs) {
        if (videoUri != Uri.EMPTY && state.videoDurationMs > 0) {
            Timber.d("TrimScreen: LaunchedEffect triggered - videoDurationMs=${state.videoDurationMs}, targetBarCount=$targetBarCount")
            viewModel.loadAudioWaveforms(videoUri, targetBarCount)
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
                                        Toast.makeText(
                                            context,
                                            "Adicione pelo menos um corte",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@IconButton
                                    }
                                    showSaveDialog = true
                                },
                                enabled = !isSaving
                            ) {
                                Icon(
                                    if (isSaving) Icons.Default.Check else Icons.Default.Save,
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
                        onPositionChange = { viewModel.setCurrentPosition(it) },
                        onAddPosition = { viewModel.addPosition(state.currentPosition) },
                        onRequestNewMedia = { },
                        onVideoDurationChange = { duration -> viewModel.setVideoDuration(duration) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )

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
        AlertDialog(
            onDismissRequest = {
                // Impedir dispensar durante processamento
                if (!isSaving) {
                    showSaveDialog = false
                    saveCompleted = false
                    saveError = null
                    savingProgress = 0
                }
            },
            title = {
                Text(
                    when {
                        saveCompleted -> "Vídeo Salvo!"
                        saveError != null -> "Erro ao Salvar"
                        isSaving -> "Exportando..."
                        else -> "Remover Trechos"
                    }
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when {
                        saveCompleted -> {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Vídeo salvo com sucesso!")
                        }
                        saveError != null -> {
                            Text(saveError ?: "Erro desconhecido")
                        }
                        isSaving -> {
                            if (savingProgress > 0) {
                                CircularProgressIndicator(
                                    progress = { savingProgress / 100f },
                                    modifier = Modifier.size(48.dp),
                                    strokeWidth = 4.dp
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    strokeWidth = 4.dp
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("${savingProgress}%")
                        }
                        else -> {
                            Text("Deseja remover os trechos selecionados e salvar o vídeo?")
                        }
                    }
                }
            },
            confirmButton = {
                when {
                    saveCompleted -> {
                        TextButton(onClick = {
                            showSaveDialog = false
                            saveCompleted = false
                            savingProgress = 0
                            onNavigateBack()
                        }) {
                            Text("Ir para Início")
                        }
                    }
                    saveError != null -> {
                        TextButton(onClick = {
                            showSaveDialog = false
                            saveError = null
                            savingProgress = 0
                            isSaving = false
                        }) {
                            Text("Fechar")
                        }
                    }
                    isSaving -> {
                        // Nenhum botão durante processamento
                    }
                    else -> {
                        TextButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    isSaving = true
                                    savingProgress = 0
                                    try {
                                        val trimRanges = state.trimPosition.completeRanges.sortedBy { it.first }

                                        Timber.d("Trim ranges: $trimRanges")
                                        Timber.d("Video duration: ${state.videoDurationMs}")

                                        val keepRanges = mutableListOf<Pair<Long, Long>>()
                                        var lastEndMs = 0L

                                        trimRanges.forEach { (start, end) ->
                                            if (start > lastEndMs) {
                                                keepRanges.add(lastEndMs to start)
                                            }
                                            lastEndMs = end
                                        }

                                        if (lastEndMs < state.videoDurationMs) {
                                            keepRanges.add(lastEndMs to state.videoDurationMs)
                                        }

                                        val rangesToSave = keepRanges.map { (start, end) -> TimeRange(start, end) }

                                        Timber.d("Keep ranges to save: $rangesToSave")

                                        if (rangesToSave.isEmpty()) {
                                            throw Exception("No ranges to save - video is empty")
                                        }

                                        transformerPipeline.trim(videoUri, rangesToSave)
                                            .collect { progress ->
                                                when (progress) {
                                                    is TrimProgress.InProgress -> {
                                                        savingProgress = progress.percent
                                                    }
                                                    is TrimProgress.Completed -> {
                                                        val trimmedFile = progress.file
                                                        Timber.d("Trimmed file exists: ${trimmedFile.exists()}, size: ${trimmedFile.length()}")

                                                        val originalFileName = videoUri.lastPathSegment
                                                            ?.substringAfterLast('/')
                                                            ?.substringBeforeLast('.')
                                                            ?.take(30)
                                                            ?.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                                                            ?.removePrefix("ChopCut_")
                                                            ?: "video"

                                                        val timestamp = java.text.SimpleDateFormat("mmssSSS", java.util.Locale.getDefault())
                                                            .format(java.util.Date())
                                                        val fileName = "ChopCut_${timestamp}_$originalFileName"

                                                        videoRepository.saveToGallery(trimmedFile, "$fileName.mp4")
                                                        trimmedFile.delete()

                                                        saveCompleted = true
                                                        isSaving = false
                                                    }
                                                    is TrimProgress.Failed -> {
                                                        Timber.e(progress.error, "TransformerPipeline trim error")
                                                        saveError = progress.error.message ?: "Erro desconhecido"
                                                        isSaving = false
                                                    }
                                                }
                                            }
                                    } catch (e: Exception) {
                                        Timber.e(e, "Failed to save video")
                                        saveError = e.message ?: "Erro desconhecido"
                                        isSaving = false
                                    }
                                }
                            }
                        ) {
                            Text("Salvar")
                        }
                    }
                }
            },
            dismissButton = {
                if (!isSaving && !saveCompleted && saveError == null) {
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }
}
