package com.chopcut.ui.screen

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chopcut.ui.components.TimelinePlayer
import com.chopcut.ui.components.TrimRangeData
import com.chopcut.ui.theme.ChopCutTheme
import com.chopcut.ui.viewmodel.TimelineViewModel

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
    val uiState by viewModel.uiState.collectAsState()

    // Estado local para seleção de range na UI
    var selectedRangeId by remember { mutableStateOf<String?>(null) }

    // Converter ranges do ViewModel para o formato do TimelinePlayer
    // Adiciona isSelected baseado no selectedRangeId
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
        if (videoUri != Uri.EMPTY) {
            TimelinePlayer(
                videoUri = videoUri,
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
