package com.chopcut.core.designsystem.organisms

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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
import com.chopcut.core.designsystem.theme.ChopCutTheme
import com.chopcut.core.designsystem.tokens.SizeTokens
import com.chopcut.core.designsystem.tokens.SpacingTokens

/**
 * Player de vídeo simples com controles básicos.
 *
 * @param videoUri URI do vídeo
 * @param isPlaying Se está reproduzindo
 * @param onPlayPauseClick Callback do botão play/pause
 * @param modifier Modifier para customização
 * @param showControls Se mostra controles sobrepostos
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    videoUri: android.net.Uri,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    modifier: Modifier = Modifier,
    showControls: Boolean = true
) {
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(videoUri) {
        exoPlayer.prepare()
        onDispose { exoPlayer.release() }
    }

    DisposableEffect(isPlaying) {
        if (isPlaying) exoPlayer.play() else exoPlayer.pause()
        onDispose { }
    }

    Box(modifier = modifier.background(Color.Black)) {
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

        if (showControls) {
            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                        CircleShape
                    ),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Play",
                    modifier = Modifier.size(SizeTokens.iconXl)
                )
            }
        }
    }
}

/**
 * Mini player para previews em cards.
 */
@OptIn(UnstableApi::class)
@Composable
fun MiniVideoPlayer(
    videoUri: android.net.Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    DisposableEffect(videoUri) {
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        exoPlayer.volume = 0f
        onDispose { exoPlayer.release() }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ============================================================================
// PREVIEWS
// ============================================================================

@Preview(showBackground = true, widthDp = 300, heightDp = 200)
@Composable
private fun VideoPlayerPreview() {
    ChopCutTheme {
        VideoPlayer(
            videoUri = android.net.Uri.EMPTY,
            isPlaying = false,
            onPlayPauseClick = {}
        )
    }
}
