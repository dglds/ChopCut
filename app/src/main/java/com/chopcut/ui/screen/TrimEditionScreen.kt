package com.chopcut.ui.screen

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chopcut.data.repository.ProjectRepository
import com.chopcut.ui.components.TimelineEditor
import com.chopcut.ui.components.trim.RangeList
import com.chopcut.ui.components.trim.TrimControlPanel
import com.chopcut.ui.viewmodel.TimelineViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TrimEditionScreen(
    videoUri: Uri,
    projectId: String? = null,
    viewModel: TimelineViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var loadedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }
        errorMessage != null -> {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { Text(errorMessage!!, color = MaterialTheme.colorScheme.error) }
        }
        loadedVideoUri != null -> {
            Column(modifier = Modifier.fillMaxSize()) {
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
                
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
        else -> {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { Text("Nenhum vídeo selecionado") }
        }
    }
}
