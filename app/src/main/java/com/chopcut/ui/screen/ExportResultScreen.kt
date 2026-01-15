package com.chopcut.ui.screen

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.chopcut.data.model.ExportPreset
import com.chopcut.ui.components.TrimRange
import com.chopcut.ui.share.ShareManager
import com.chopcut.ui.share.VideoFileInfo
import timber.log.Timber

/**
 * Estados do fluxo de exportação
 */
enum class ExportScreenState {
    /** Selecionando preset de exportação */
    SELECTING_PRESET,
    /** Exportando em andamento */
    EXPORTING,
    /** Exportação concluída com sucesso */
    SUCCESS,
    /** Erro na exportação */
    ERROR
}

/**
 * Dados necessários para iniciar a exportação
 */
data class ExportInputData(
    val trimRange: TrimRange?,
    val originalWidth: Int,
    val originalHeight: Int,
    val originalBitrate: Int
)

/**
 * Tela unificada de Exportação e Resultado.
 *
 * Fluxo:
 * 1. SELECTING_PRESET: Usuário seleciona preset de exportação
 * 2. EXPORTING: Mostra progresso da exportação
 * 3. SUCCESS: Preview do vídeo com ações (compartilhar, abrir, deletar)
 * 4. ERROR: Mensagem de erro
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportResultScreen(
    state: ExportScreenState = ExportScreenState.SELECTING_PRESET,
    presets: List<ExportPreset> = emptyList(),
    inputData: ExportInputData? = null,
    resultUri: Uri? = null,
    resultName: String? = null,
    exportProgress: Int = 0,
    exportError: String? = null,
    onPresetSelected: (ExportPreset) -> Unit = {},
    onDismiss: () -> Unit = {},
    onShare: () -> Unit = {},
    onDelete: () -> Unit = {},
    onBackToEditor: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val context = LocalContext.current
    val shareManager = remember { ShareManager(context) }

    var videoFileInfo by remember { mutableStateOf<VideoFileInfo?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showShareMenu by remember { mutableStateOf(false) }

    // Carregar informações do arquivo quando tiver resultado
    LaunchedEffect(resultUri) {
        resultUri?.let {
            videoFileInfo = shareManager.getVideoFileInfo(it)
        }
    }

    // Lista de apps disponíveis para compartilhamento
    val availableApps = remember { shareManager.getAvailableShareApps() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 650.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            when (state) {
                ExportScreenState.SELECTING_PRESET -> {
                    PresetSelectionContent(
                        presets = presets,
                        inputData = inputData,
                        onPresetSelected = onPresetSelected,
                        onDismiss = onDismiss
                    )
                }
                ExportScreenState.EXPORTING -> {
                    ExportingContent(
                        progress = exportProgress,
                        onDismiss = onDismiss
                    )
                }
                ExportScreenState.SUCCESS -> {
                    if (resultUri != null && resultName != null) {
                        SuccessContent(
                            resultUri = resultUri,
                            resultName = resultName,
                            videoFileInfo = videoFileInfo,
                            onDismiss = onDismiss,
                            onShareClick = { showShareMenu = true },
                            onOpenClick = {
                                shareManager.openInPlayer(resultUri)
                                onShare()
                            },
                            onBackToEditor = onBackToEditor,
                            onDeleteClick = { showDeleteDialog = true }
                        )
                    }
                }
                ExportScreenState.ERROR -> {
                    ErrorContent(
                        error = exportError ?: "Erro desconhecido",
                        onDismiss = onDismiss,
                        onRetry = onRetry
                    )
                }
            }
        }
    }

    // Menu de compartilhamento
    if (showShareMenu && resultUri != null) {
        ShareMenuDialog(
            availableApps = availableApps,
            videoUri = resultUri,
            onAppSelected = { app ->
                showShareMenu = false
                when (app) {
                    is com.chopcut.ui.share.ShareApp.Instagram -> {
                        shareManager.shareToInstagram(resultUri)
                    }
                    is com.chopcut.ui.share.ShareApp.TikTok -> {
                        shareManager.shareToTikTok(resultUri)
                    }
                    is com.chopcut.ui.share.ShareApp.YouTube -> {
                        shareManager.shareToYouTube(resultUri)
                    }
                    is com.chopcut.ui.share.ShareApp.WhatsApp -> {
                        shareManager.shareToWhatsApp(resultUri)
                    }
                    is com.chopcut.ui.share.ShareApp.Twitter -> {
                        shareManager.shareToApp(resultUri, "com.twitter.android")
                    }
                    is com.chopcut.ui.share.ShareApp.Generic -> {
                        shareManager.shareVideo(resultUri)
                        onShare()
                    }
                }
            },
            onDismiss = { showShareMenu = false }
        )
    }

    // Diálogo de confirmação de exclusão
    if (showDeleteDialog && resultUri != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Deletar vídeo?") },
            text = { Text("Deseja deletar o vídeo exportado? Esta ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        shareManager.deleteExportedVideo(resultUri)
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Deletar")
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

/**
 * Conteúdo para seleção de preset
 */
@Composable
private fun PresetSelectionContent(
    presets: List<ExportPreset>,
    inputData: ExportInputData?,
    onPresetSelected: (ExportPreset) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Exportar Vídeo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Fechar")
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Escolha o formato de exportação:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        // Lista de presets
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Opção "Original"
            item {
                PresetCard(
                    name = "Original",
                    description = "Mesma qualidade do vídeo original",
                    width = inputData?.originalWidth ?: 0,
                    height = inputData?.originalHeight ?: 0,
                    icon = Icons.Default.Phone,
                    onClick = { onPresetSelected(ExportPreset.ORIGINAL) }
                )
            }

            // Presets configurados
            items(presets) { preset ->
                PresetCard(
                    name = preset.name,
                    description = buildPresetDescription(preset, inputData),
                    width = preset.width,
                    height = preset.height,
                    icon = getPresetIcon(preset.name),
                    onClick = { onPresetSelected(preset) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Card de preset de exportação
 */
@Composable
private fun PresetCard(
    name: String,
    description: String,
    width: Int,
    height: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (width > 0 && height > 0) {
                Text(
                    text = "${width}x$height",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Conteúdo para exportação em andamento
 */
@Composable
private fun ExportingContent(
    progress: Int,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(60.dp),
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Exportando vídeo...",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "$progress%",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        TextButton(onClick = onDismiss) {
            Text("Executando em segundo plano")
        }
    }
}

/**
 * Conteúdo para sucesso na exportação
 */
@Composable
private fun SuccessContent(
    resultUri: Uri,
    resultName: String,
    videoFileInfo: VideoFileInfo?,
    onDismiss: () -> Unit,
    onShareClick: () -> Unit,
    onOpenClick: () -> Unit,
    onBackToEditor: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Exportado!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Fechar")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Preview do vídeo
        VideoPreviewPlayer(
            uri = resultUri,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Spacer(Modifier.height(16.dp))

        // Informações do arquivo
        VideoInfoCard(
            fileName = resultName,
            fileSize = videoFileInfo?.sizeFormatted
        )

        Spacer(Modifier.height(20.dp))

        // Ações principais
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton(
                icon = Icons.Default.Share,
                text = "Compartilhar",
                onClick = onShareClick,
                modifier = Modifier.weight(1f),
                primary = true
            )

            ActionButton(
                icon = Icons.Default.PlayArrow,
                text = "Abrir",
                onClick = onOpenClick,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        // Ações secundárias
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedActionButton(
                icon = Icons.Default.Edit,
                text = "Editar mais",
                onClick = onBackToEditor,
                modifier = Modifier.weight(1f)
            )

            OutlinedActionButton(
                icon = Icons.Default.Delete,
                text = "Deletar",
                onClick = onDeleteClick,
                modifier = Modifier.weight(1f),
                isError = true
            )
        }
    }
}

/**
 * Conteúdo para erro na exportação
 */
@Composable
private fun ErrorContent(
    error: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Erro na exportação",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text("Fechar")
            }

            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f)
            ) {
                Text("Tentar novamente")
            }
        }
    }
}

/**
 * Player de vídeo para preview usando ExoPlayer
 */
@Composable
fun VideoPreviewPlayer(
    uri: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(uri)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                controllerAutoShow = true
            }
        },
        modifier = modifier
    )
}

/**
 * Card com informações do arquivo exportado
 */
@Composable
private fun VideoInfoCard(
    fileName: String,
    fileSize: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )

            fileSize?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tamanho: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Botão de ação principal
 */
@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = if (primary) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        }
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp)
    }
}

/**
 * Botão de ação outlined
 */
@Composable
private fun OutlinedActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp)
    }
}

/**
 * Diálogo de menu de compartilhamento
 */
@Composable
private fun ShareMenuDialog(
    availableApps: List<com.chopcut.ui.share.ShareApp>,
    videoUri: Uri,
    onAppSelected: (com.chopcut.ui.share.ShareApp) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compartilhar com") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                availableApps.forEach { app ->
                    ShareAppItem(
                        app = app,
                        onClick = { onAppSelected(app) }
                    )
                }

                // Opção genérica
                ShareAppItem(
                    app = com.chopcut.ui.share.ShareApp.Generic,
                    onClick = { onAppSelected(com.chopcut.ui.share.ShareApp.Generic) }
                )
            }
        },
        confirmButton = {}
    )
}

/**
 * Item de app para compartilhamento
 */
@Composable
private fun ShareAppItem(
    app: com.chopcut.ui.share.ShareApp,
    onClick: () -> Unit
) {
    val icon = when (app) {
        is com.chopcut.ui.share.ShareApp.Instagram -> Icons.Default.Favorite
        is com.chopcut.ui.share.ShareApp.TikTok -> Icons.Default.Notifications
        is com.chopcut.ui.share.ShareApp.YouTube -> Icons.Default.PlayArrow
        is com.chopcut.ui.share.ShareApp.WhatsApp -> Icons.Default.Email
        is com.chopcut.ui.share.ShareApp.Twitter -> Icons.Default.Share
        is com.chopcut.ui.share.ShareApp.Generic -> Icons.Default.MoreVert
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                app.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Retorna o ícone apropriado para um preset
 */
private fun getPresetIcon(presetName: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (presetName.lowercase()) {
        "instagram", "instagram reels" -> Icons.Default.Favorite
        "tiktok" -> Icons.Default.Notifications
        "youtube" -> Icons.Default.PlayArrow
        "whatsapp" -> Icons.Default.Email
        "twitter", "x" -> Icons.Default.Share
        else -> Icons.Default.Phone
    }
}

/**
 * Constrói a descrição do preset
 */
private fun buildPresetDescription(preset: ExportPreset, inputData: ExportInputData?): String {
    val aspectRatio = when {
        preset.width == 1080 && preset.height == 1920 -> "9:16 (Vertical)"
        preset.width == 1920 && preset.height == 1080 -> "16:9 (Horizontal)"
        preset.width == preset.height -> "1:1 (Quadrado)"
        preset.width > preset.height -> "16:9"
        else -> "9:16"
    }

    val bitrate = "${preset.bitrate / 1_000_000} Mbps"
    val fps = "${preset.frameRate}fps"

    return buildString {
        append(aspectRatio)
        append(" • $bitrate")
        append(" • $fps")
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
