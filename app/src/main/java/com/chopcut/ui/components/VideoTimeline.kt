package com.chopcut.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.chopcut.R
import com.chopcut.ui.preview.PreviewManager
import com.chopcut.ui.timeline.ThumbnailProvider
import com.chopcut.ui.timeline.Timeline
import com.chopcut.ui.timeline.TimelineViewModel
import com.chopcut.ui.timeline.model.Thumbnail
import timber.log.Timber
import android.view.LayoutInflater
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope

/**
 * Componente unificado de Video + Timeline.
 *
 * Combina VideoPreview e Timeline em um único componente com sincronização automática.
 * Minimiza o uso de LaunchedEffects usando derivedStateOf e callbacks diretos.
 *
 * @param uri Video URI
 * @param previewManager PreviewManager instance
 * @param modifier Modifier
 * @param rotationDegrees Rotação do vídeo
 * @param onVideoClick Callback ao clicar no vídeo
 * @param videoWidth Largura original do vídeo (para aspect ratio)
 * @param videoHeight Altura original do vídeo (para aspect ratio)
 */
@Composable
fun VideoTimeline(
    uri: Uri,
    previewManager: PreviewManager,
    modifier: Modifier = Modifier,
    rotationDegrees: Float = 0f,
    onVideoClick: () -> Unit = {},
    videoWidth: Int = 0,
    videoHeight: Int = 0
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Estados do Player
    val currentPosition by previewManager.currentPosition.collectAsStateWithLifecycle()
    val duration by previewManager.duration.collectAsStateWithLifecycle()
    val isReady by previewManager.isReady.collectAsStateWithLifecycle()

    // Estado de scrubbing para controlar direção da sincronização
    var isScrubbing by remember { mutableStateOf(false) }

    // ViewModel da Timeline (criado apenas quando duration está disponível)
    val timelineViewModel = remember(duration) {
        TimelineViewModel(initialDurationMs = duration)
    }

    // Atualizar duração quando mudar (usando snapshot flow)
    LaunchedEffect(duration) {
        if (duration > 0) {
            timelineViewModel.updateTotalDuration(duration)
        }
    }

    // Estado para armazenar thumbnails (inicialmente vazio)
    var thumbnails by remember { mutableStateOf<List<Thumbnail>>(emptyList()) }

    // Estado derivado: posição do slider (0f a 1f)
    val sliderPosition by remember {
        derivedStateOf {
            if (duration > 0) currentPosition.toFloat() / duration.toFloat()
            else 0f
        }
    }

    // Inicializar Player
    LaunchedEffect(uri) {
        previewManager.prepare(context, uri, coroutineScope)
    }

    // Sincronização unificada Player ↔ Timeline
    // Durante playback: Player → Timeline
    // Durante scrubbing: Timeline → Player
    LaunchedEffect(currentPosition, duration, isScrubbing) {
        if (!isScrubbing && duration > 0) {
            // Player lidera: atualiza Timeline
            timelineViewModel.updatePlayheadPosition(currentPosition)
        }
    }

    LaunchedEffect(isScrubbing) {
        // Pausar/retomar player baseado no estado de scrubbing
        if (isScrubbing) {
            previewManager.setScrubbing(true)
        } else {
            previewManager.setScrubbing(false)
        }
    }

    // Cleanup
    DisposableEffect(previewManager) {
        onDispose {
            Timber.d("VideoTimeline disposed")
        }
    }

    Column(modifier = modifier) {
        // ===== VIDEO PREVIEW =====
        VideoPlayerView(
            previewManager = previewManager,
            isReady = isReady,
            rotationDegrees = rotationDegrees,
            onVideoClick = onVideoClick,
            modifier = Modifier.fillMaxWidth()
        )

        // Barra de progresso visual
        if (isReady && duration > 0) {
            VideoProgressBar(
                progress = sliderPosition,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ===== TIMELINE =====
        if (duration > 0) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val density = LocalDensity.current

                // Altura da Timeline é 80.dp (definido no Timeline.kt linha 47)
                val timelineHeightDp = 80.dp
                val timelineHeightPx = with(density) { timelineHeightDp.toPx().toInt() }

                // Calcular largura do thumbnail respeitando o aspect ratio do vídeo
                // Se não tiver dimensões do vídeo, usa 1:1 como fallback
                val aspectRatio = if (videoWidth > 0 && videoHeight > 0) {
                    videoWidth.toFloat() / videoHeight.toFloat()
                } else {
                    1f // Quadrado como fallback
                }

                val thumbnailWidthPx = (timelineHeightPx * aspectRatio).toInt()
                val thumbnailWidthDp = with(density) { thumbnailWidthPx.toDp() }

                // Extrair thumbnails com o tamanho correto
                LaunchedEffect(uri, duration, thumbnailWidthPx, timelineHeightPx) {
                    if (duration > 0 && thumbnailWidthPx > 0 && timelineHeightPx > 0) {
                        val thumbnailProvider = ThumbnailProvider(context)
                        thumbnailProvider.extractThumbnails(
                            uri = uri,
                            durationMs = duration,
                            count = 10,
                            width = thumbnailWidthPx,
                            height = timelineHeightPx
                        ).collect { fetchedThumbnails ->
                            thumbnails = fetchedThumbnails
                        }
                    }
                }

                Timeline(
                    viewModel = timelineViewModel,
                    thumbnails = thumbnails,
                    modifier = Modifier.fillMaxWidth(),
                    thumbnailWidth = thumbnailWidthDp,
                    onScrubStart = {
                        isScrubbing = true
                    },
                    onScrubEnd = {
                        isScrubbing = false
                        // Ao soltar, garantir posição final
                        val finalPosition = timelineViewModel.state.value.playheadPositionMs
                        previewManager.seekTo(finalPosition)
                    }
                )
            }
        }
    }
}

/**
 * Componente interno do Player View.
 */
@Composable
private fun VideoPlayerView(
    previewManager: PreviewManager,
    isReady: Boolean,
    rotationDegrees: Float,
    onVideoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lastTapTime by remember { mutableStateOf(0L) }
    val doubleTapTimeout = 300L

    Box(
        modifier = modifier
            .height(220.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        val currentTime = System.currentTimeMillis()
                        val timeSinceLastTap = currentTime - lastTapTime

                        if (timeSinceLastTap < doubleTapTimeout) {
                            // Double tap - ignorar ou ação futura
                            lastTapTime = 0
                        } else {
                            // Single tap
                            onVideoClick()
                            lastTapTime = currentTime
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (isReady) {
            AndroidView(
                factory = { ctx ->
                    val view = LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView
                    view.apply {
                        controllerShowTimeoutMs = 0
                        player = previewManager.exoPlayer
                        useController = false
                    }
                },
                update = { view ->
                    (view as PlayerView).player = previewManager.exoPlayer
                },
                modifier = Modifier.fillMaxSize().rotate(rotationDegrees)
            )
        } else {
            Text(
                text = "Loading...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Barra de progresso visual simples.
 */
@Composable
private fun VideoProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val activeColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .height(8.dp)
            .drawBehind {
                drawRect(color = inactiveColor, size = size)
                drawRect(
                    color = activeColor,
                    size = androidx.compose.ui.geometry.Size(size.width * progress, size.height)
                )
            }
    )
}
