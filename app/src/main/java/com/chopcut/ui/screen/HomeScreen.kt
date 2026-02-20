package com.chopcut.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Delete
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import com.chopcut.ui.components.gallery.BottomSheetGallery
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chopcut.ui.components.atoms.formatDuration
import com.chopcut.ui.components.buttons.ChopCutPrimaryButton
import com.chopcut.ui.components.buttons.ChopCutSecondaryButton
import com.chopcut.ui.components.cards.ChopCutCard
import com.chopcut.ui.components.feedback.EmptyState
import com.chopcut.ui.components.feedback.ErrorState
import com.chopcut.ui.theme.ChopCutSpacing
import com.chopcut.ui.theme.Primary
import timber.log.Timber

/**
 * Home screen - Main entry point for video editing
 *
 * @param viewModel HomeViewModel
 * @param onNavigateToEditor Callback to navigate to editor screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToEditor: (android.net.Uri) -> Unit = {},
    onNavigateToTests: () -> Unit = {},
    onNavigateToPreferences: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedUri by viewModel.selectedVideoUri.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Video picker launcher
    // State for permission handling and gallery sheet
    var showGallery by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasPermission = isGranted
        if (isGranted) {
            showGallery = true
        } else {
            // Show rationale or error (simplified for now)
            Timber.w("Permission denied for gallery access")
        }
    }

    // Check initial permission status (simplified)
    /*
    LaunchedEffect(Unit) {
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33) {
            android.Manifest.permission.READ_MEDIA_VIDEO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val result = androidx.core.content.ContextCompat.checkSelfPermission(context, permission)
        hasPermission = result == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    */


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ChopCut") },
                actions = {
                    IconButton(onClick = onNavigateToPreferences) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Preferências"
                        )
                    }
                    IconButton(onClick = onNavigateToTests) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Testes"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(ChopCutSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ChopCutSpacing.md)
        ) {
            // Header
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = ChopCutSpacing.lg)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = Primary
                    )
                    Spacer(Modifier.height(ChopCutSpacing.md))
                    Text(
                        text = "ChopCut",
                        style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Editor de Vídeo Android",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Select Video Card - usando ChopCutCard
            item {
                ChopCutCard(
                    modifier = Modifier.fillMaxWidth(),
                    showShadow = true
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Começar a Editar",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(ChopCutSpacing.md))

                        ChopCutPrimaryButton(
                            text = "Selecionar Vídeo (Galeria Pro)",
                            onClick = {
                                val permission = if (android.os.Build.VERSION.SDK_INT >= 33) {
                                    android.Manifest.permission.READ_MEDIA_VIDEO
                                } else {
                                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                                }
                                permissionLauncher.launch(permission)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Default.VideoLibrary
                        )

                        // Show selected video info
                        val uri = selectedUri
                        if (uri != null) {
                            Spacer(Modifier.height(ChopCutSpacing.md))

                            when (val state = uiState) {
                                is HomeUiState.VideoLoaded -> {
                                    VideoInfoPreview(state.videoInfo)
                                }
                                is HomeUiState.Loading -> {
                                    Text(
                                        text = "Carregando...",
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                        color = Primary
                                    )
                                }
                                else -> {
                                    Text(
                                        text = "Vídeo selecionado: ${uri.lastPathSegment}",
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                        color = Primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Open Editor Button
            val uri = selectedUri
            if (uri != null && uiState is HomeUiState.VideoLoaded) {
                item {
                    ChopCutPrimaryButton(
                        text = "Abrir Editor",
                        onClick = { onNavigateToEditor(uri) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Features Card
            item {
                ChopCutCard(
                    modifier = Modifier.fillMaxWidth(),
                    showShadow = true
                ) {
                    Column {
                        Text(
                            text = "Recursos",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(ChopCutSpacing.sm))
                        FeatureItem("✂️", "Trim", "Cortar vídeos em partes")
                        FeatureItem("🔗", "Join", "Concatenar vídeos")
                        FeatureItem("🗜️", "Compress", "Reduzir tamanho/bitrate")
                        FeatureItem("📐", "Resize", "Alterar resolução")
                        FeatureItem("⬛", "Crop", "Recortar área do vídeo")
                        FeatureItem("🎵", "Áudio", "Extrair trilha de áudio")
                    }
                }
            }

            // Maintenance / Debug
            item {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                val snackbarHostState = androidx.compose.material3.SnackbarHostState()
                
                ChopCutSecondaryButton(
                    text = "Limpar Cache de Thumbnails",
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                           com.chopcut.data.thumbnail.ThumbnailStripManager.clearCache(context)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Delete
                )
            }

            // Status Messages
            when (uiState) {
                is HomeUiState.Error -> {
                    item {
                        ErrorState(
                            title = "Erro",
                            message = (uiState as HomeUiState.Error).message,
                            actionLabel = "Dispensar",
                            onAction = { viewModel.resetState() }
                        )
                    }
                }
                else -> {}
            }
            }
        }

        if (showGallery) {
             BottomSheetGallery(
                 onDismiss = { showGallery = false },
                 onVideoSelected = { uri ->
                     showGallery = false
                     viewModel.selectVideo(uri)
                 }
             )
         }
    }

@Composable
private fun FeatureItem(icon: String, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ChopCutSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun VideoInfoPreview(videoInfo: com.chopcut.data.model.VideoInfo) {
    ChopCutCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Text(
                text = videoInfo.fileName,
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(ChopCutSpacing.xs))
            InfoRowPreview("Resolução", "${videoInfo.width}x${videoInfo.height}")
            InfoRowPreview("Duração", formatDuration(videoInfo.durationMs))
            InfoRowPreview("Codec", videoInfo.videoCodec ?: "Unknown")
        }
    }
}

@Composable
private fun InfoRowPreview(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
        )
        Text(
            text = value,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}
