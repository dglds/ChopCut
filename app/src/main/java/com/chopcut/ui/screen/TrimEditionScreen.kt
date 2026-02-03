package com.chopcut.ui.screen

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chopcut.data.repository.ProjectRepository
import com.chopcut.ui.components.TimelinePlayer
import com.chopcut.ui.components.TrimRangeData
import com.chopcut.ui.theme.ChopCutTheme
import com.chopcut.ui.viewmodel.TimelineViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tela de edição de trim de vídeo.
 *
 * Layout:
 * - Player de vídeo no topo (70% da tela)
 * - Timeline com ranges na parte inferior (30% da tela)
 * - FAB flutuante para ações de adicionar/definir/deletar ranges
 *
 * @param videoUri URI do vídeo sendo editado
 * @param projectId ID do projeto (opcional)
 * @param onNavigateBack Callback para voltar à tela anterior
 * @param onExportComplete Callback quando exportação é concluída
 */
@Composable
fun TrimEditionScreen(
    videoUri: Uri,
    projectId: String? = null,
    onNavigateBack: () -> Unit = {},
    onExportComplete: () -> Unit = {},
    viewModel: TimelineViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Estado local para seleção de range na UI
    var selectedRangeId by remember { mutableStateOf<String?>(null) }

    // Estado para carregar URI do projeto (quando videoUri é vazio)
    var loadedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Carrega vídeo do projeto se necessário
    LaunchedEffect(videoUri, projectId) {
        if (videoUri == Uri.EMPTY && projectId != null) {
            isLoading = true
            errorMessage = null

            withContext(Dispatchers.IO) {
                val repository = ProjectRepository(context)
                val project = repository.getProject(projectId)

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

    // URI final a ser usado (do parâmetro ou carregado do projeto)
    val finalVideoUri = loadedVideoUri ?: Uri.EMPTY

    // Converter ranges do ViewModel para o formato do TimelinePlayer
    val rangesForPlayer = remember(uiState.ranges, selectedRangeId) {
        uiState.ranges.map { range ->
            range.copy(isSelected = range.id == selectedRangeId)
        }
    }

    Scaffold(
        floatingActionButton = {
            TrimEditionFab(
                isDefining = uiState.isDefining,
                selectedRangeId = selectedRangeId,
                onStartRange = { viewModel.startRange() },
                onEndRange = { viewModel.endRange() },
                onDeleteRange = {
                    selectedRangeId?.let { id ->
                        viewModel.removeRange(id)
                        selectedRangeId = null
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage ?: "Erro desconhecido",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            finalVideoUri != Uri.EMPTY -> {
                TimelinePlayer(
                    videoUri = finalVideoUri,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                ranges = rangesForPlayer,
                selectedRangeId = selectedRangeId,
                onRangesChange = { updatedRanges ->
                    // Sincroniza alterações de posição do TimelinePlayer com ViewModel
                    updatedRanges.forEach { updatedRange ->
                        val originalRange = uiState.ranges.find { it.id == updatedRange.id }
                        if (originalRange != null && 
                            (originalRange.startMs != updatedRange.startMs || 
                             originalRange.endMs != updatedRange.endMs)) {
                            // Range foi movido/redimensionado no TimelinePlayer
                            // Atualiza no ViewModel se for um range draft
                            if (updatedRange.isDraft && !updatedRange.isConfirmed) {
                                viewModel.updateRangeEnd(updatedRange.endMs)
                            }
                        }
                    }
                },
                onRangeSelect = { rangeId ->
                    selectedRangeId = rangeId
                },
                onRangeDelete = { rangeId ->
                    viewModel.removeRange(rangeId)
                    if (selectedRangeId == rangeId) {
                        selectedRangeId = null
                    }
                }
            )
        }
        else -> {
            // Sem vídeo disponível
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nenhum vídeo selecionado",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
}

/**
 * FAB da tela de trim com estados dinâmicos.
 * Estados: Add (novo range) -> Confirm (definindo) -> Delete (range selecionado)
 */
@Composable
private fun TrimEditionFab(
    isDefining: Boolean,
    selectedRangeId: String?,
    onStartRange: () -> Unit,
    onEndRange: () -> Unit,
    onDeleteRange: () -> Unit
) {
    when {
        isDefining -> {
            // Estado: Definindo fim do range (Mark B) - ícone de check
            FloatingActionButton(
                onClick = onEndRange,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Definir fim do range",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        selectedRangeId != null -> {
            // Estado: Range confirmado selecionado - ícone de delete
            FloatingActionButton(
                onClick = onDeleteRange,
                containerColor = MaterialTheme.colorScheme.error
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Deletar range selecionado",
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        }
        else -> {
            // Estado: Adicionar novo range - ícone de add
            FloatingActionButton(
                onClick = onStartRange,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Adicionar range de trim",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TrimEditionScreenPreview() {
    ChopCutTheme {
        // Preview com placeholder pois precisa de Context para ExoPlayer
        androidx.compose.material3.Surface {
            androidx.compose.material3.Text(
                text = "TrimEditionScreen Preview\n(Requires video file)",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
