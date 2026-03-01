package com.chopcut.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chopcut.ui.components.buttons.ChopCutPrimaryButton
import com.chopcut.ui.components.buttons.ChopCutSecondaryButton
import com.chopcut.ui.components.feedback.ErrorState
import com.chopcut.ui.components.feedback.LoadingState
import com.chopcut.ui.theme.ChopCutSpacing
import timber.log.Timber
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    viewModel: PreferencesViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isCacheEnabled by viewModel.isCacheEnabled.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var cacheSizeMB by remember { mutableStateOf(0.0) }
    var cachedVideoCount by remember { mutableStateOf(0) }
    var isLoadingCacheSize by remember { mutableStateOf(false) }
    var shouldRefreshCacheSize by remember { mutableStateOf(false) }

    // Calcular tamanho do cache e quantidade de vídeos ao entrar na tela e quando solicitado
    LaunchedEffect(Unit, shouldRefreshCacheSize) {
        if (shouldRefreshCacheSize) {
            isLoadingCacheSize = true
        }
        cacheSizeMB = viewModel.getThumbnailCacheSize()
        cachedVideoCount = viewModel.getCachedVideoCount()
        isLoadingCacheSize = false
    }

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

            // Cache de Thumbnails
            com.chopcut.ui.components.cards.ChopCutCard(
                modifier = Modifier.fillMaxWidth(),
                showShadow = true
            ) {
                Column(
                    modifier = Modifier.padding(ChopCutSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cache de Thumbnails",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (isLoadingCacheSize) {
                            LinearProgressIndicator(
                                modifier = Modifier.width(100.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = String.format("%.2f MB", cacheSizeMB),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.height(ChopCutSpacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Vídeos cacheados:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$cachedVideoCount vídeo(s)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(ChopCutSpacing.sm))
                    Text(
                        text = "Thumbnails temporárias de vídeos processados",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(ChopCutSpacing.md))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Usar Cache",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Cache acelera carregamento de vídeos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isCacheEnabled,
                            onCheckedChange = { viewModel.setCacheEnabled(it) }
                        )
                    }
                    Spacer(Modifier.height(ChopCutSpacing.md))
                    ChopCutSecondaryButton(
                        text = "Limpar Cache",
                        onClick = {
                            if (cacheSizeMB > 0.01) {
                                showClearCacheDialog = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = cacheSizeMB > 0.01 && !isLoadingCacheSize
                    )
                }
            }

            Spacer(Modifier.height(ChopCutSpacing.md))

            // Informações de Extração (Zoom)
            com.chopcut.ui.components.cards.ChopCutCard(
                modifier = Modifier.fillMaxWidth(),
                showShadow = true
            ) {
                Column(
                    modifier = Modifier.padding(ChopCutSpacing.md)
                ) {
                    Text(
                        text = "Configurações de Extração",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(ChopCutSpacing.sm))
                    
                    // Cálculo do Zoom (Referência 1080p)
                    // MEDIUM preset é 320px. 320/1920 = ~16.6%
                    val zoomLevel = (320f / 1920f * 100).roundToInt()
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Nível de Zoom (Ref: 1080p)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "$zoomLevel%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Taxa de escala aplicada aos frames originais para gerar as thumbnails da timeline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(ChopCutSpacing.md))
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
                    LoadingState(message = "Processando...")
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

    // Dialog de confirmação para deletar vídeos
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

    // Dialog de confirmação para limpar cache
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Limpar Cache de Thumbnails") },
            text = {
                Column {
                    Text("Tem certeza que deseja limpar o cache de thumbnails?")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Tamanho atual: ${String.format("%.2f MB", cacheSizeMB)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Vídeos cacheados: $cachedVideoCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Os thumbnails serão regenerados automaticamente quando você abrir um vídeo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheDialog = false
                        viewModel.clearThumbnailCache()
                        shouldRefreshCacheSize = true
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Limpar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
