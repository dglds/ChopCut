package com.chopcut.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chopcut.data.pipeline.CopyPipeline
import com.chopcut.data.repository.VideoRepository
import com.chopcut.data.model.TimeRange
import com.chopcut.ui.components.TimelineEditor
import com.chopcut.ui.components.trim.RangeList
import com.chopcut.ui.components.trim.TrimControlPanel
import com.chopcut.ui.components.feedback.ErrorState
import com.chopcut.ui.theme.ChopCutSpacing
import com.chopcut.ui.screen.TrimViewModel
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
    var videoName by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val videoRepository = remember { VideoRepository(context) }
    val copyPipeline = remember { CopyPipeline(context, videoRepository) }

    LaunchedEffect(videoUri) {
        if (videoUri != Uri.EMPTY) {
            viewModel.loadWaveform(videoUri)
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
                                    val fileName = videoUri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                                        ?: "video_${System.currentTimeMillis()}"
                                    videoName = fileName
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
                    Text("Nome do arquivo:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = videoName,
                        onValueChange = { videoName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Meu vídeo") }
                    )
                    if (isSaving) {
                        Spacer(modifier = Modifier.height(16.dp))
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
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (videoName.isBlank() || isSaving) {
                            return@TextButton
                        }

                        scope.launch(Dispatchers.IO) {
                            isSaving = true
                            try {
                                // Invert logic: completeRanges are ranges to REMOVE
                                // We need to calculate the ranges to KEEP
                                val trimRanges = state.trimPosition.completeRanges
                                    .sortedBy { it.first }

                                Timber.d("Original trim ranges to REMOVE: $trimRanges")

                                val keepRanges = mutableListOf<Pair<Long, Long>>()
                                var lastEndMs = 0L

                                trimRanges.forEach { (start, end) ->
                                    if (start > lastEndMs) {
                                        keepRanges.add(lastEndMs to start)
                                    }
                                    lastEndMs = end
                                }

                                // Add final range if there's remaining video
                                if (lastEndMs < state.videoDurationMs) {
                                    keepRanges.add(lastEndMs to state.videoDurationMs)
                                }

                                Timber.d("Keep ranges to SAVE: $keepRanges")
                                Timber.d("Video duration: ${state.videoDurationMs}ms")

                                val rangesToSave = keepRanges
                                    .map { (start, end) -> TimeRange(start, end) }

                                Timber.d("Saving video with ${rangesToSave.size} keep ranges (removed ${trimRanges.size} ranges)")
                                rangesToSave.forEachIndexed { idx, range ->
                                    Timber.d("  Range $idx: ${range.startMs}ms - ${range.endMs}ms (${range.durationMs}ms)")
                                }

                                if (rangesToSave.isEmpty()) {
                                    throw Exception("No ranges to save - video is empty")
                                }

                                val outputFile = videoRepository.createTempFile(".mp4")
                                Timber.d("Temp file created: ${outputFile.absolutePath}")
                                var trimmedFile: File? = null

                                copyPipeline.trim(videoUri, rangesToSave)
                                    .collect { result ->
                                        result.getOrNull()?.let { file ->
                                            trimmedFile = file
                                            Timber.d("Trim result file: ${file.absolutePath}, size: ${file.length()} bytes")
                                        }
                                    }

                                if (trimmedFile != null) {
                                    Timber.d("Copying ${trimmedFile!!.length()} bytes to gallery...")
                                    val finalUri = videoRepository.saveToGallery(trimmedFile!!, "$videoName.mp4")
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
                    enabled = videoName.isNotBlank() && !isSaving
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
}
