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
import com.chopcut.data.model.EditOperation
import com.chopcut.data.model.Project
import com.chopcut.data.repository.ProjectRepository
import com.chopcut.ui.components.TimelineEditor
import com.chopcut.ui.components.ThumbnailUtils
import com.chopcut.ui.components.trim.RangeList
import com.chopcut.ui.components.trim.TrimControlPanel
import com.chopcut.ui.components.feedback.ErrorState
import com.chopcut.ui.components.feedback.LoadingState
import com.chopcut.ui.theme.ChopCutSpacing
import com.chopcut.ui.viewmodel.TimelineViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimEditionScreen(
    videoUri: Uri,
    projectId: String? = null,
    viewModel: TimelineViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var loadedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(videoUri, projectId) {
        if (videoUri == Uri.EMPTY && projectId != null) {
            isLoading = true
            withContext(Dispatchers.IO) {
                val repo = ProjectRepository(context)
                val project = repo.getProject(projectId)
                if (project != null) {
                    loadedVideoUri = Uri.parse(project.sourceVideoUri)
                } else {
                    errorMessage = "Projeto não encontrado"
                }
            }
            isLoading = false
        } else {
            loadedVideoUri = videoUri
        }
    }

    when {
        isLoading -> {
            LoadingState(modifier = Modifier.fillMaxSize())
        }
        errorMessage != null -> {
            ErrorState(
                title = "Erro ao carregar",
                message = errorMessage!!,
                modifier = Modifier.fillMaxSize(),
                actionLabel = "Voltar",
                onAction = { /* TODO: Navigate back */ }
            )
        }
        loadedVideoUri != null -> {
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
                                    scope.launch(Dispatchers.IO) {
                                        isSaving = true
                                        try {
                                            val repo = ProjectRepository(context)

                                            // Gerar thumbnail do primeiro frame
                                            val thumbnailPath = try {
                                                val bitmap = ThumbnailUtils.getThumbnail(
                                                    context,
                                                    loadedVideoUri!!,
                                                    0L // Primeiro frame
                                                )
                                                if (bitmap != null) {
                                                    // Salvar thumbnail em arquivo interno
                                                    val thumbnailsDir = java.io.File(context.filesDir, "thumbnails")
                                                    thumbnailsDir.mkdirs()
                                                    val thumbnailFile = java.io.File(
                                                        thumbnailsDir,
                                                        "${System.currentTimeMillis()}_thumb.jpg"
                                                    )
                                                    java.io.FileOutputStream(thumbnailFile).use { out ->
                                                        android.graphics.Bitmap.CompressFormat.JPEG.let { format ->
                                                            bitmap.compress(format, 90, out)
                                                        }
                                                    }
                                                    thumbnailFile.absolutePath
                                                } else null
                                            } catch (e: Exception) {
                                                Timber.e(e, "Failed to generate thumbnail")
                                                null
                                            }

                                            val project = Project(
                                                id = projectId ?: java.util.UUID.randomUUID().toString(),
                                                name = "Projeto ${java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}",
                                                sourceVideoUri = loadedVideoUri.toString(),
                                                duration = state.videoDurationMs,
                                                thumbnail = thumbnailPath
                                            )
                                            val edits = state.trimPosition.completeRanges.map { (start, end) ->
                                                EditOperation.Trim(start, end)
                                            }
                                            repo.saveProject(project, edits)
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Projeto salvo!", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Erro ao salvar: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                },
                                enabled = !isSaving
                            ) {
                                Icon(
                                    if (isSaving) Icons.Default.Check else Icons.Default.Save,
                                    contentDescription = "Salvar",
                                    tint = if (isSaving)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface
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
                    videoUri = loadedVideoUri!!,
                    trimPosition = state.trimPosition,
                    currentPosition = state.currentPosition,
                    onPositionChange = { viewModel.setCurrentPosition(it) },
                    onAddPosition = { viewModel.addPosition(state.currentPosition) },
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
        else -> {
            ErrorState(
                title = "Nenhum vídeo selecionado",
                message = "Selecione um vídeo para começar a editar",
                modifier = Modifier.fillMaxSize(),
                actionLabel = "Voltar",
                onAction = { /* TODO: Navigate back */ }
            )
        }
    }
}
