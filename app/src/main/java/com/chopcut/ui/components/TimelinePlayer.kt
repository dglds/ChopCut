package com.chopcut.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.chopcut.core.designsystem.atoms.LabelText
import com.chopcut.core.designsystem.atoms.PrimaryButton
import com.chopcut.core.designsystem.atoms.SecondaryButton
import com.chopcut.core.designsystem.organisms.SimpleTimeline
import com.chopcut.core.designsystem.organisms.TimelineRange
import com.chopcut.core.designsystem.theme.ChopCutTheme
import com.chopcut.core.designsystem.tokens.SizeTokens
import com.chopcut.core.designsystem.tokens.SpacingTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * TimelinePlayer simplificada.
 * Apenas orquestra: Player + Timeline + Controles.
 * Toda lógica de negócio deve estar no ViewModel.
 *
 * @param videoUri URI do vídeo
 * @param currentTimeMs Posição atual em ms (controlado externamente)
 * @param isPlaying Estado de reprodução (controlado externamente)
 * @param durationMs Duração total em ms
 * @param ranges Lista de ranges
 * @param onTimeChange Callback quando tempo muda (arrastar timeline)
 * @param onPlayPauseClick Callback quando usuário clica play/pause
 * @param onAddRangeClick Callback quando usuário adiciona range
 * @param onRangeClick Callback quando usuário clica num range
 */
@OptIn(UnstableApi::class)
@Composable
fun TimelinePlayer(
    videoUri: Uri,
    currentTimeMs: Long,
    isPlaying: Boolean,
    durationMs: Long,
    ranges: List<TimelineRange>,
    onTimeChange: (Long) -> Unit,
    onPlayPauseClick: () -> Unit,
    onAddRangeClick: () -> Unit,
    onRangeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Player
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Prepara o player quando o vídeo muda
    DisposableEffect(videoUri) {
        exoPlayer.setMediaItem(MediaItem.fromUri(videoUri))
        exoPlayer.prepare()
        onDispose { exoPlayer.release() }
    }

    // Sincroniza estado externo -> player
    LaunchedEffect(isPlaying) {
        if (isPlaying) exoPlayer.play() else exoPlayer.pause()
    }

    LaunchedEffect(currentTimeMs) {
        if (kotlin.math.abs(exoPlayer.currentPosition - currentTimeMs) > 100) {
            exoPlayer.seekTo(currentTimeMs)
        }
    }

    // Loop de atualização: Player -> externo (só quando playing)
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                onTimeChange(exoPlayer.currentPosition)
                delay(50) // 20fps é suficiente para UI
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column {
            // === VIDEO ===
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Controles sobrepostos
                PlayerOverlay(
                    currentTimeMs = currentTimeMs,
                    isPlaying = isPlaying,
                    onPlayPauseClick = onPlayPauseClick,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // === TIMELINE ===
            SimpleTimeline(
                currentTimeMs = currentTimeMs,
                durationMs = durationMs,
                ranges = ranges,
                onTimeChange = { newTime ->
                    exoPlayer.seekTo(newTime)
                    onTimeChange(newTime)
                },
                onRangeClick = onRangeClick
            )

            // === CONTROLES ===
            PlayerControls(
                isPlaying = isPlaying,
                onPlayPauseClick = onPlayPauseClick,
                onAddRangeClick = onAddRangeClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PlayerOverlay(
    currentTimeMs: Long,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Botão Play/Pause central
        IconButton(
            onClick = onPlayPauseClick,
            modifier = Modifier
                .align(Alignment.Center)
                .size(SizeTokens.iconHero)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pausar" else "Play",
                modifier = Modifier.size(SizeTokens.iconXl),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Tempo atual
        LabelText(
            text = formatTime(currentTimeMs),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = SpacingTokens.lg)
        )
    }
}

@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onAddRangeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .padding(SpacingTokens.lg),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
            SpacingTokens.md
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SecondaryButton(
            onClick = onPlayPauseClick,
            text = if (isPlaying) "Pausar" else "Play",
            modifier = Modifier.weight(1f)
        )

        PrimaryButton(
            onClick = onAddRangeClick,
            text = "Range",
            icon = Icons.Default.Add,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / 60000) % 60
    val hours = ms / 3600000
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

// ============================================================================
// PREVIEW
// ============================================================================

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun PlayerControlsPreview() {
    ChopCutTheme {
        PlayerControls(
            isPlaying = false,
            onPlayPauseClick = {},
            onAddRangeClick = {}
        )
    }
}
