package com.chopcut.ui.screen

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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

    Scaffold(
        floatingActionButton = {
            TrimEditionFab(
                isDefining = uiState.isDefining,
                onStartRange = { viewModel.startRange() },
                onEndRange = { viewModel.endRange() },
                onDeleteRange = {
                    uiState.activeRangeId?.let { viewModel.removeRange(it) }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ==================== VIDEO PLAYER (PLACEHOLDER) ====================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.7f)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (videoUri != Uri.EMPTY) {
                    // TODO: Implementar player real (TimelinePlayer ou ExoPlayer)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Player de vídeo\n${videoUri.path?.takeLast(30) ?: "..."}",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhum vídeo selecionado",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ==================== TIMELINE (PLACEHOLDER) ====================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.3f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    )
            ) {
                // TODO: Implementar TimelineRangesOverlay ou TimelinePlayer
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Timeline (${uiState.ranges.size} ranges)",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (uiState.ranges.isNotEmpty()) {
                        uiState.ranges.forEach { range ->
                            Text(
                                text = "${range.id.take(8)}: ${range.startMs}ms - ${range.endMs}ms ${if (range.isConfirmed) "✓" else "✏"}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                    if (uiState.isDefining) {
                        Text(
                            text = "Definindo: pos=${uiState.currentPlayheadMs}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * FAB da tela de trim com estados dinâmicos.
 */
@Composable
private fun TrimEditionFab(
    isDefining: Boolean,
    onStartRange: () -> Unit,
    onEndRange: () -> Unit,
    onDeleteRange: () -> Unit
) {
    when {
        isDefining -> {
            // Estado: Definindo fim do range (Mark B)
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
        else -> {
            // Estado: Adicionar novo range
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
        TrimEditionScreen(
            videoUri = Uri.EMPTY
        )
    }
}
