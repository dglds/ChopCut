package com.chopcut.ui.screen

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chopcut.data.repository.ProjectRepository
import com.chopcut.ui.components.TimelineEditor
import com.chopcut.ui.viewmodel.TimelineViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

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
                            isDraftMode = state.trimPosition.isDraftMode,
                            draftPosition = state.trimPosition.draftPosition
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                val isInsideRange = state.trimPosition.isPositionInRange(state.currentPosition)

                RangeControls(
                    isDraftMode = state.trimPosition.isDraftMode,
                    isInsideRange = isInsideRange,
                    onAddPosition = { viewModel.addPosition(state.currentPosition) },
                    onClear = { viewModel.removeRangeAt(state.currentPosition) }
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

@Composable
private fun RangeList(
    ranges: List<Pair<Long, Long>>,
    currentPosition: Long,
    isDraftMode: Boolean,
    draftPosition: Long?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (ranges.isEmpty() && !isDraftMode) {
            Text(
                text = "Nenhum range definido",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        } else {
            ranges.forEachIndexed { index, (start, end) ->
                Text(
                    text = "#${index + 1} ${formatTime(start)} → ${formatTime(end)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }

            if (isDraftMode && draftPosition != null) {
                Text(
                    text = "Draft: ${formatTime(draftPosition)} → ${formatTime(currentPosition)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFF9800),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RangeControls(
    isDraftMode: Boolean,
    isInsideRange: Boolean,
    onAddPosition: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Botão CONFIRMAR (Draft) ou INICIAR RANGE (Tesoura)
            // A Tesoura só aparece se NÃO estivermos em Draft e NÃO estivermos dentro de um range
            if (isDraftMode) {
                IconButton(
                    onClick = onAddPosition,
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color(0xFFFF9800), androidx.compose.foundation.shape.CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Confirmar")
                }
            } else if (!isInsideRange) {
                IconButton(
                    onClick = onAddPosition,
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = "Iniciar Range")
                }
            } else {
                // Espaço reservado para manter layout se necessário, ou apenas não mostrar nada
                Spacer(modifier = Modifier.size(64.dp)) 
            }

            Spacer(modifier = Modifier.width(32.dp))

            // Botão LIXEIRA
            // Aparece se estivermos em Draft (cancelar) OU dentro de um range (deletar range)
            if (isInsideRange || isDraftMode) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Limpar")
                }
            } else {
                 Spacer(modifier = Modifier.size(64.dp))
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val centis = (ms % 1000) / 10
    // Formato MM:SS.cc (Minutos totais : Segundos . Centésimos)
    return String.format("%02d:%02d.%02d", minutes, seconds, centis)
}
