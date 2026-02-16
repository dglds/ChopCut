package com.chopcut.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chopcut.data.audio.WaveformQuality
import com.chopcut.data.pipeline.CopyPipeline
import com.chopcut.data.repository.VideoRepository
import com.chopcut.data.model.TimeRange
import com.chopcut.ui.components.TimelineEditor
import com.chopcut.ui.components.trim.RangeList
import com.chopcut.ui.components.trim.TrimControlPanel
import com.chopcut.ui.components.feedback.ErrorState
import com.chopcut.ui.theme.ChopCutSpacing
import com.chopcut.ui.screen.TrimViewModel
import com.chopcut.ui.screen.debug.WaveformTestDialog
import com.chopcut.ui.screen.debug.WaveformTestViewModel
import com.chopcut.ui.components.AudioWaveForms
import com.chopcut.ui.components.AudioWaveFormsConfig
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimEditionScreen(
    videoUri: Uri,
    viewModel: TrimViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val videoRepository = remember { VideoRepository(context) }
    val copyPipeline = remember { CopyPipeline(context, videoRepository) }
    val testViewModel = remember {
        WaveformTestViewModel(
            context.applicationContext as android.app.Application
        )
    }

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
            Timber.d("TrimEditionScreen: LaunchedEffect triggered - videoDurationMs=${state.videoDurationMs}, targetBarCount=$targetBarCount")
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
                                onClick = { showTestDialog = true },
                                enabled = !isSaving
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Science,
                                    contentDescription = "Testar WaveForm"
                                )
                            }
                            IconButton(
                                onClick = { showQualityDialog = true },
                                enabled = !isSaving
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Qualidade Waveform")
                            }
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
                        // Novos parâmetros para AudioWaveForms - agora do state
                        audioWaveformsAmplitudes = state.audioWaveformsAmplitudes,
                        isAudioWaveformsLoading = state.isAudioWaveformsLoading,
                        onPositionChange = { viewModel.setCurrentPosition(it) },
                        onAddPosition = { viewModel.addPosition(state.currentPosition) },
                        onRequestNewMedia = { },
                        onVideoDurationChange = { duration -> viewModel.setVideoDuration(duration) },
                        extraContent = {
                            RangeList(
                                ranges = state.trimPosition.completeRanges,
                                currentPosition = state.currentPosition,
                                totalDurationMs = state.videoDurationMs,
                                finalDurationMs = state.finalDurationMs,
                                isDraftMode = state.trimPosition.isDraftMode,
                                draftPosition = state.trimPosition.draftPosition
                            )
                        },
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
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Salvar Vídeo") },
            text = {
                Column {
                    if (isSaving) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Text("Salvando...")
                        }
                    } else {
                        Text("Deseja salvar o vídeo editado?")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isSaving) {
                            return@TextButton
                        }

                        scope.launch(Dispatchers.IO) {
                            isSaving = true
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

                                val outputFile = videoRepository.createTempFile(".mp4")
                                Timber.d("Output file: ${outputFile.absolutePath}")

                                var trimmedFile: File? = null

                                copyPipeline.trim(videoUri, rangesToSave)
                                    .collect { result ->
                                        Timber.d("CopyPipeline result: $result")
                                        result.getOrNull()?.let { trimmedFile = it }
                                    }

                                Timber.d("Trimmed file after collect: $trimmedFile")

                                if (trimmedFile != null) {
                                    Timber.d("Trimmed file exists: ${trimmedFile!!.exists()}, size: ${trimmedFile!!.length()}")
                                    val fileName = videoUri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                                        ?: "video_${System.currentTimeMillis()}"
                                    val finalUri = videoRepository.saveToGallery(trimmedFile!!, "ChopCut_$fileName.mp4")
                                    Timber.d("Final URI: $finalUri")
                                    outputFile.delete()
                                    trimmedFile!!.delete()

                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            if (finalUri != null) "Salvo em /ChopCut/" else "Erro ao salvar na galeria",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        showSaveDialog = false
                                        onNavigateBack()
                                    }
                                } else {
                                    Timber.e("Trimmed file is null after collect")
                                    throw Exception("Failed to trim video: No result")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to save video")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "Erro: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    }
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSaveDialog = false },
                    enabled = !isSaving
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Qualidade do Waveform") },
            text = {
                Column {
                    Text("Selecione a qualidade para regerar o waveform:")
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(WaveformQuality.AllValues) { quality ->
                            AssistChip(
                                onClick = {
                                    try {
                                        viewModel.setWaveformQuality(quality)
                                        viewModel.loadWaveform(videoUri)
                                        showQualityDialog = false
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error changing waveform quality")
                                        Toast.makeText(
                                            context,
                                            "Erro ao mudar qualidade: ${e.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                label = { Text(quality.displayName) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showTestDialog) {
        WaveformTestDialog(
            videoUri = videoUri,
            viewModel = testViewModel,
            onDismiss = { showTestDialog = false }
        )
    }
}
