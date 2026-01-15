package com.chopcut.ui.screen

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.chopcut.ui.share.ShareManager
import com.chopcut.ui.share.VideoFileInfo
import timber.log.Timber

/**
 * Dados necessários para exibir o resultado da exportação
 */
data class ExportResultData(
    val videoUri: Uri,
    val outputName: String,
    val fileSize: Long? = null,
    val durationMs: Long? = null
)

/**
 * Tela de resultado após exportação bem-sucedida.
 *
 * Exibe:
 * - Preview do vídeo exportado
 * - Informações (tamanho, duração, nome)
 * - Ações: compartilhar, abrir, deletar, voltar ao editor
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportResultScreen(
    resultData: ExportResultData,
    onDismiss: () -> Unit = {},
    onShare: () -> Unit = {},
    onDelete: () -> Unit = {},
    onBackToEditor: () -> Unit = {}
) {
    val context = LocalContext.current
    val shareManager = remember { ShareManager(context) }

    var videoFileInfo by remember { mutableStateOf<VideoFileInfo?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showShareMenu by remember { mutableStateOf(false) }

    // Carregar informações do arquivo
    LaunchedEffect(resultData.videoUri) {
        videoFileInfo = shareManager.getVideoFileInfo(resultData.videoUri)
    }

    // Lista de apps disponíveis para compartilhamento
    val availableApps = remember { shareManager.getAvailableShareApps() }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
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
                    uri = resultData.videoUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                Spacer(Modifier.height(16.dp))

                // Informações do arquivo
                VideoInfoCard(
                    fileName = resultData.outputName,
                    fileSize = videoFileInfo?.sizeFormatted,
                    duration = resultData.durationMs?.let { formatTime(it) }
                )

                Spacer(Modifier.height(20.dp))

                // Ações principais
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Compartilhar
                    Box(modifier = Modifier.weight(1f)) {
                        ActionButton(
                            icon = Icons.Default.Share,
                            text = "Compartilhar",
                            onClick = { showShareMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            primary = true
                        )
                    }

                    // Abrir
                    Box(modifier = Modifier.weight(1f)) {
                        ActionButton(
                            icon = Icons.Default.PlayArrow,
                            text = "Abrir",
                            onClick = {
                                shareManager.openInPlayer(resultData.videoUri)
                                onShare()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Ações secundárias
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Voltar ao editor
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedActionButtonButton(
                            icon = Icons.Default.Edit,
                            text = "Editar mais",
                            onClick = onBackToEditor,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Deletar
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedActionButtonButton(
                            icon = Icons.Default.Delete,
                            text = "Deletar",
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            isError = true
                        )
                    }
                }
            }
        }
    }

    // Menu de compartilhamento
    if (showShareMenu) {
        ShareMenuDialog(
            availableApps = availableApps,
            onAppSelected = { app ->
                showShareMenu = false
                when (app) {
                    is com.chopcut.ui.share.ShareApp.Instagram -> {
                        shareManager.shareToInstagram(resultData.videoUri)
                    }
                    is com.chopcut.ui.share.ShareApp.TikTok -> {
                        shareManager.shareToTikTok(resultData.videoUri)
                    }
                    is com.chopcut.ui.share.ShareApp.YouTube -> {
                        shareManager.shareToYouTube(resultData.videoUri)
                    }
                    is com.chopcut.ui.share.ShareApp.WhatsApp -> {
                        shareManager.shareToWhatsApp(resultData.videoUri)
                    }
                    is com.chopcut.ui.share.ShareApp.Twitter -> {
                        shareManager.shareToApp(resultData.videoUri, "com.twitter.android")
                    }
                    is com.chopcut.ui.share.ShareApp.Generic -> {
                        shareManager.shareVideo(resultData.videoUri)
                        onShare()
                    }
                }
            },
            onDismiss = { showShareMenu = false }
        )
    }

    // Diálogo de confirmação de exclusão
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Deletar vídeo?") },
            text = { Text("Deseja deletar o vídeo exportado? Esta ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        shareManager.deleteExportedVideo(resultData.videoUri)
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
    fileSize: String?,
    duration: String?
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

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                fileSize?.let {
                    InfoItem(label = "Tamanho", value = it)
                }
                duration?.let {
                    InfoItem(label = "Duração", value = it)
                }
            }
        }
    }
}

/**
 * Item de informação individual
 */
@Composable
private fun InfoItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
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
private fun OutlinedActionButtonButton(
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
        is com.chopcut.ui.share.ShareApp.Instagram -> Icons.Default.PhotoCamera
        is com.chopcut.ui.share.ShareApp.TikTok -> Icons.Default.MusicNote
        is com.chopcut.ui.share.ShareApp.YouTube -> Icons.Default.PlayArrow
        is com.chopcut.ui.share.ShareApp.WhatsApp -> Icons.Default.Chat
        is com.chopcut.ui.share.ShareApp.Twitter -> Icons.Default.Share
        is com.chopcut.ui.share.ShareApp.Generic -> Icons.Default.MoreHoriz
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

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
