package com.chopcut.ui.timeline.components

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.chopcut.R
import timber.log.Timber

/**
 * Componente de preview de vídeo com ExoPlayer.
 * 
 * Responsabilidades:
 * - Renderizar o vídeo usando ExoPlayer
 * - Mostrar indicador de estado (LED) no canto superior direito
 * - Permitir play/pause ao clicar
 * - Suportar diferentes estados de carregamento
 *
 * @param videoUri URI do vídeo a ser reproduzido
 * @param exoPlayer Instância do ExoPlayer (gerenciada externamente)
 * @param isReady Se o player está pronto para reprodução
 * @param isPlaying Se o vídeo está sendo reproduzido
 * @param currentPosition Posição atual em ms (para debug/display)
 * @param duration Duração total em ms
 * @param onTogglePlayPause Callback ao clicar para play/pause
 * @param modifier Modifier para customização
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPreview(
    videoUri: Uri?,
    exoPlayer: ExoPlayer?,
    isReady: Boolean,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .drawBehind {
                // LED indicador de estado no canto superior direito
                val ledSize = size.height * 0.04f
                val ledPadding = size.height * 0.03f
                val ledRadius = ledSize / 2

                val centerX = size.width - ledPadding - ledRadius
                val centerY = ledPadding + ledRadius

                // Cor do LED baseada no estado
                val ledColor = when {
                    !isReady -> Color(0xFFD32F2F) // Vermelho - não pronto
                    !isPlaying -> Color(0xFFF57C00) // Laranja - pausado
                    else -> Color(0xFF388E3C) // Verde - reproduzindo
                }

                // Glow multicamadas para efeito visual
                // Camada externa suave
                drawCircle(
                    color = ledColor,
                    radius = ledRadius * 4f,
                    center = Offset(centerX, centerY),
                    alpha = 0.08f
                )
                // Camada média
                drawCircle(
                    color = ledColor,
                    radius = ledRadius * 2.5f,
                    center = Offset(centerX, centerY),
                    alpha = 0.15f
                )
                // Camada interna brilhante
                drawCircle(
                    color = ledColor,
                    radius = ledRadius * 1.3f,
                    center = Offset(centerX, centerY),
                    alpha = 0.4f
                )

                // LED principal
                drawCircle(
                    color = ledColor,
                    radius = ledRadius,
                    center = Offset(centerX, centerY)
                )
                // Brilho central
                drawCircle(
                    color = Color.White,
                    radius = ledRadius * 0.4f,
                    center = Offset(centerX, centerY),
                    alpha = 0.6f
                )
            }
            .clickable { onTogglePlayPause() },
        contentAlignment = Alignment.Center
    ) {
        if (videoUri != null && isReady && exoPlayer != null) {
            // Player de vídeo real
            AndroidView(
                factory = { ctx ->
                    try {
                        val view = LayoutInflater.from(ctx)
                            .inflate(R.layout.player_view, null) as PlayerView
                        view.apply {
                            controllerShowTimeoutMs = 0
                            this.player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Erro ao criar PlayerView")
                        // Fallback para PlayerView simples
                        PlayerView(ctx).apply {
                            this.player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    }
                },
                update = { view ->
                    (view as? PlayerView)?.player = exoPlayer
                },
                modifier = Modifier.fillMaxSize()
            )

            // Overlay de play/pause (visível quando pausado)
            if (!isPlaying) {
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        } else {
            // Estado de carregamento
            if (videoUri != null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = "Selecione um vídeo",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Componente simplificado de preview que gerencia seu próprio PlayerView.
 * Útil para casos onde não há acesso direto ao ExoPlayer.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPreviewSimple(
    videoUri: Uri?,
    isReady: Boolean,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable { onTogglePlayPause() },
        contentAlignment = Alignment.Center
    ) {
        if (videoUri != null) {
            content()

            // Overlay de play/pause
            if (!isPlaying && isReady) {
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            if (!isReady) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Text(
                text = "Carregando...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

/**
 * Indicador de tempo atual do vídeo.
 * Formato: MM:SS.m / MM:SS.m
 */
@Composable
fun TimeIndicator(
    currentMs: Long,
    totalMs: Long,
    modifier: Modifier = Modifier
) {
    Text(
        text = "${formatarTempo(currentMs)} / ${formatarTempo(totalMs)}",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

/**
 * Barra de progresso simples do vídeo.
 */
@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    Box(
        modifier = modifier
            .drawBehind {
                // Fundo inativo
                drawRect(color = inactiveColor)
                // Progresso ativo
                drawRect(
                    color = activeColor,
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(
                        size.width * progress.coerceIn(0f, 1f),
                        size.height
                    )
                )
            }
    )
}

/**
 * Formata milissegundos para MM:SS.m
 */
private fun formatarTempo(ms: Long): String {
    val totalSegundos = ms / 1000
    val minutos = totalSegundos / 60
    val segundos = totalSegundos % 60
    val decimos = (ms % 1000) / 100
    return "%02d:%02d.%d".format(minutos, segundos, decimos)
}
