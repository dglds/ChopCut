package com.chopcut.ui.components.timeline

import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.RectangleShape
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@Composable
fun VideoPreview(
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    isInsideRange: Boolean,
    playerError: String?,
    isSecurityError: Boolean,
    currentTimeMs: Long = 0L,
    onRequestNewMedia: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onTogglePlayPause: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(androidx.compose.foundation.shape.RectangleShape)
            .background(Color.Black)
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), androidx.compose.foundation.shape.RectangleShape)
    ) {
        if (playerError != null) {
            VideoErrorState(
                error = playerError,
                isSecurityError = isSecurityError,
                onRequestNewMedia = onRequestNewMedia,
                onRetry = onRetry
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isInsideRange) {
                TrimRangeOverlay()
            }

            // Timer Overlay (Garantido estar no topo do vídeo)
            Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.TopCenter) {
                CurrentTimeDisplay(
                    currentTimeMs = currentTimeMs,
                    isInsideRange = isInsideRange
                )
            }

            VideoControls(
                isInsideRange = isInsideRange,
                isPlaying = isPlaying,
                onTogglePlayPause = onTogglePlayPause
            )
        }
    }
}

@Composable
private fun VideoErrorState(
    error: String,
    isSecurityError: Boolean,
    onRequestNewMedia: (() -> Unit)?,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = Color.Red,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            color = Color.White,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp)
        )

        if (isSecurityError && onRequestNewMedia != null) {
            Button(
                onClick = onRequestNewMedia,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text("Re-Localizar Arquivo (Necessário)")
            }
        } else {
            Button(onClick = onRetry) {
                Text("Tentar Novamente")
            }
        }

        if (!isSecurityError && onRequestNewMedia != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onRequestNewMedia) {
                Text("Localizar Arquivo")
            }
        }
    }
}

@Composable
private fun TrimRangeOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(Color.Red.copy(alpha = 0.1f))

        val stripeSpacing = 28.dp.toPx()
        val stripeWidth = 6.dp.toPx()
        val stripeColor = Color.Red.copy(alpha = 0.25f)
        val maxDim = size.width + size.height
        var offset = -size.height
        while (offset < maxDim) {
            drawLine(
                color = stripeColor,
                start = Offset(offset, size.height),
                end = Offset(offset + size.height, 0f),
                strokeWidth = stripeWidth
            )
            offset += stripeSpacing
        }

        drawRect(
            color = Color.Red.copy(alpha = 0.5f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
private fun VideoControls(
    isInsideRange: Boolean,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (isInsideRange) 0.1f else 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        if (isInsideRange) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Trecho será removido",
                tint = Color.Red.copy(alpha = 0.5f),
                modifier = Modifier.size(72.dp)
            )
        } else {
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.RectangleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White
                )
            }
        }
    }
}
