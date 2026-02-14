package com.chopcut.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chopcut.ui.components.buttons.ChopCutPrimaryButton
import com.chopcut.ui.components.feedback.ErrorState
import com.chopcut.ui.components.feedback.LoadingState
import com.chopcut.ui.theme.ChopCutSpacing
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    viewModel: PreferencesViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preferências") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
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
                .padding(ChopCutSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ChopCutSpacing.md)
        ) {
            Text(
                text = "Gerenciar Armazenamento",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(Modifier.height(ChopCutSpacing.lg))

            com.chopcut.ui.components.cards.ChopCutCard(
                modifier = Modifier.fillMaxWidth(),
                showShadow = true
            ) {
                Column(
                    modifier = Modifier.padding(ChopCutSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Vídeos Salvos",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(ChopCutSpacing.sm))
                    Text(
                        text = "Todos os vídeos processados são salvos na pasta Movies/ChopCut",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(ChopCutSpacing.md))
                    ChopCutPrimaryButton(
                        text = "Remover Todos os Vídeos",
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Delete
                    )
                }
            }

            when (val state = uiState) {
                is PreferencesUiState.Loading -> {
                    LoadingState(message = "Removendo vídeos...")
                }
                is PreferencesUiState.Success -> {
                    com.chopcut.ui.components.feedback.SuccessState(
                        title = "Sucesso",
                        message = state.message,
                        actionLabel = "OK",
                        onAction = { viewModel.resetState() }
                    )
                }
                is PreferencesUiState.Error -> {
                    ErrorState(
                        title = "Erro",
                        message = state.message,
                        actionLabel = "Dispensar",
                        onAction = { viewModel.resetState() }
                    )
                }
                else -> {}
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Confirmar Exclusão") },
            text = { Text("Tem certeza que deseja remover todos os vídeos salvos na pasta Movies/ChopCut? Esta ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteSavedVideos()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Remover")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
