package com.chopcut.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.chopcut.data.thumbnail.ThumbnailCache
import com.chopcut.data.thumbnail.ThumbnailExtractor
import com.chopcut.data.local.PreferencesManager
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Timeline data for a single thumbnail
 */
data class TimelineThumbnail(
    val positionMs: Long,
    val bitmap: Bitmap?
)



/**
 * Configurações da timeline com playhead fixo
 */
object TimelineConfig {
    /** Escala: 1 pixel = X milissegundos */
    const val PIXELS_PER_MS: Float = 0.8f

    /** Altura da timeline em dp */
    val TIMELINE_HEIGHT = 100.dp

    /** Largura da linha do playhead em dp */
    val PLAYHEAD_LINE_WIDTH = 3.dp

    /** Opacidade do overlay de trim */
    const val TRIM_OVERLAY_ALPHA = 0.5f

    /** Cor do playhead */
    val PLAYHEAD_COLOR = Color.Red

    /** Cor das bordas de trim */
    val TRIM_BORDER_COLOR = Color.Yellow

    /** Largura das thumbs */
    const val THUMB_WIDTH = 120

    /** Altura das thumbs */
    const val THUMB_HEIGHT = 100
}

/**
 * Video timeline component com playhead fixo no centro (CapCut-style)
 *
 * O playhead (linha vermelha) permanece fixo no centro enquanto a timeline
 * se move horizontalmente por baixo dele.
 *
 * @param uri Video URI
 * @param durationMs Video duration in milliseconds
 * @param currentPositionMs Current playback position in milliseconds
 * @param isPlaying Whether video is playing
 * @param thumbnailExtractor ThumbnailExtractor instance
 * @param thumbnailSize Size preset for thumbnails (not used, using TimelineConfig)
 * @param trimRange Current trim range (null if no trim)
 * @param onTrimRangeChange Callback when trim range changes
 * @param onPositionClick Callback when user clicks on a position
 * @param onSeek Callback when seeking (pauses player)
 * @param onDragStart Callback when drag starts (pauses player)
 * @param onDragEnd Callback when drag ends
 * @param modifier Modifier for the container
 */
@Composable
fun VideoTimeline(
    uri: android.net.Uri,
    durationMs: Long,
    currentPositionMs: Long = 0L,
    isPlaying: Boolean = false,
    thumbnailExtractor: ThumbnailExtractor,
    thumbnailSize: ThumbnailExtractor.ThumbnailSize = ThumbnailExtractor.ThumbnailSize.NORMAL,
    trimRange: TrimRange? = null,
    onTrimRangeChange: (TrimRange?) -> Unit = {},
    onPositionClick: (Long) -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Cache de thumbnails (INATIVO por padrão, precisa ser habilitado em preferences)
    val preferencesManager = remember { PreferencesManager(context) }
    val thumbnailCache = remember { ThumbnailCache() }
    val cacheEnabled = remember { preferencesManager.thumbnailCacheEnabled }

    if (cacheEnabled) {
        Timber.d("Thumbnail cache ENABLED (size: ${thumbnailCache.size()})")
    } else {
        Timber.d("Thumbnail cache DISABLED")
    }

    var thumbnails by remember { mutableStateOf<List<TimelineThumbnail>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Estado da timeline - offset em pixels
    var timelineOffset by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var screenWidth by remember { mutableStateOf(0f) }

    // Calcular thumbnails baseado na duração (1 por segundo)
    val thumbnailCount = remember(durationMs) {
        (durationMs / 1000).coerceAtLeast(10)
    }

    Timber.d("VideoTimeline: durationMs=$durationMs, thumbnailCount=$thumbnailCount")

    // Extract thumbnails when URI or duration changes
    LaunchedEffect(uri, durationMs) {
        if (durationMs > 0) {
            isLoading = true
            errorMessage = null
            coroutineScope.launch {
                try {
                    // Corrigido: distribuir thumbnails uniformemente de 0 a durationMs
                    val interval = durationMs / (thumbnailCount - 1).coerceAtLeast(1)
                    val positions = (0 until thumbnailCount).map { i ->
                        (i * interval).coerceAtMost(durationMs)
                    }

                    Timber.d("Extracting ${positions.size} thumbnails (cache enabled: $cacheEnabled)")

                    val uriString = uri.toString()

                    // Extrair thumbnails usando cache se habilitado
                    val bitmaps: List<Bitmap?> = if (cacheEnabled) {
                        // Usar cache: verificar quais já estão em cache
                        positions.map { positionMs ->
                            thumbnailCache.get(uriString, positionMs)
                                ?: thumbnailExtractor.extractAt(
                                    uri = uri,
                                    positionMs = positionMs,
                                    width = TimelineConfig.THUMB_WIDTH,
                                    height = TimelineConfig.THUMB_HEIGHT
                                )?.also { bitmap ->
                                    // Adicionar ao cache se extraído com sucesso
                                    thumbnailCache.put(uriString, positionMs, bitmap)
                                }
                        }
                    } else {
                        // Sem cache: extrair todos normalmente
                        thumbnailExtractor.extractAtPositions(
                            uri = uri,
                            positionsMs = positions,
                            width = TimelineConfig.THUMB_WIDTH,
                            height = TimelineConfig.THUMB_HEIGHT
                        )
                    }

                    thumbnails = positions.mapIndexed { index, positionMs ->
                        TimelineThumbnail(positionMs, bitmaps[index])
                    }

                    Timber.d("Loaded ${thumbnails.size} thumbnails")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load timeline thumbnails")
                    errorMessage = "Error: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        } else {
            isLoading = false
        }
    }

    // Sincronizar offset com player quando não estiver arrastando
    LaunchedEffect(Unit) {
        snapshotFlow { currentPositionMs }
            .collect { pos ->
                if (!isDragging && screenWidth > 0) {
                    val centerOffset = screenWidth / 2
                    val positionOffset = pos * TimelineConfig.PIXELS_PER_MS
                    val targetOffset = centerOffset - positionOffset
                    Timber.d("Timeline offset: pos=${pos}ms, offset=${targetOffset}px")
                    timelineOffset = targetOffset
                }
            }
    }

    // Também atualizar quando isPlaying mudar
    LaunchedEffect(isPlaying) {
        if (!isDragging && screenWidth > 0) {
            val centerOffset = screenWidth / 2
            val positionOffset = currentPositionMs * TimelineConfig.PIXELS_PER_MS
            val targetOffset = centerOffset - positionOffset
            timelineOffset = targetOffset
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TimelineConfig.TIMELINE_HEIGHT)
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                screenWidth = size.width.toFloat()
            }
    ) {
        when {
            isLoading -> {
                Text(
                    text = "Carregando timeline...",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            errorMessage != null -> {
                Text(
                    text = errorMessage ?: "Erro",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            thumbnails.isEmpty() -> {
                Text(
                    text = "Nenhum thumbnail",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                // Timeline movível com thumbnails
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset {
                            IntOffset(
                                x = timelineOffset.roundToInt(),
                                y = 0
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val centerOffset = screenWidth / 2
                                    val relativeX = offset.x - centerOffset
                                    val positionOffset = centerOffset - timelineOffset + relativeX
                                    val positionMs = (positionOffset / TimelineConfig.PIXELS_PER_MS).toLong()
                                    onSeek(positionMs.coerceIn(0, durationMs))
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    onDragStart()
                                },
                                onDragEnd = {
                                    isDragging = false
                                    onDragEnd()
                                },
                                onDragCancel = {
                                    isDragging = false
                                    onDragEnd()
                                }
                            ) { change: PointerInputChange, dragAmount: Offset ->
                                change.consume()
                                timelineOffset += dragAmount.x

                                // Calcular posição do player baseada no novo offset
                                val centerOffset = screenWidth / 2
                                val positionOffset = centerOffset - timelineOffset
                                val positionMs = (positionOffset / TimelineConfig.PIXELS_PER_MS).toLong()
                                onSeek(positionMs.coerceIn(0, durationMs))
                            }
                        }
                    ) {
                    // Desenhar thumbnails lado a lado
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        var xOffset = 0f
                        thumbnails.forEach { thumbnail ->
                            thumbnail.bitmap?.let { bitmap ->
                                drawImage(
                                    image = bitmap.asImageBitmap(),
                                    topLeft = Offset(xOffset, 0f)
                                )
                            }
                            xOffset += TimelineConfig.THUMB_WIDTH
                        }
                    }
                }

                // Overlay de trim range
                if (trimRange != null && durationMs > 0) {
                    val density = LocalDensity.current
                    val borderWidth = with(density) { 3.dp.toPx() }
                    val startOffset = trimRange.startMs * TimelineConfig.PIXELS_PER_MS + timelineOffset
                    val endOffset = trimRange.endMs * TimelineConfig.PIXELS_PER_MS + timelineOffset

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Área esquerda (removida)
                        if (trimRange.startMs > 0 && startOffset > 0) {
                            drawRect(
                                color = Color.Black.copy(alpha = TimelineConfig.TRIM_OVERLAY_ALPHA),
                                topLeft = Offset(0f, 0f),
                                size = androidx.compose.ui.geometry.Size(startOffset.coerceAtLeast(0f), size.height)
                            )
                        }

                        // Área direita (removida)
                        if (trimRange.endMs < durationMs && endOffset < size.width) {
                            drawRect(
                                color = Color.Black.copy(alpha = TimelineConfig.TRIM_OVERLAY_ALPHA),
                                topLeft = Offset(endOffset, 0f),
                                size = androidx.compose.ui.geometry.Size(
                                    (size.width - endOffset).coerceAtLeast(0f),
                                    size.height
                                )
                            )
                        }

                        // Borda esquerda
                        if (startOffset > 0 && startOffset < size.width) {
                            drawLine(
                                color = TimelineConfig.TRIM_BORDER_COLOR,
                                start = Offset(startOffset, 0f),
                                end = Offset(startOffset, size.height),
                                strokeWidth = borderWidth
                            )
                        }

                        // Borda direita
                        if (endOffset > 0 && endOffset < size.width) {
                            drawLine(
                                color = TimelineConfig.TRIM_BORDER_COLOR,
                                start = Offset(endOffset, 0f),
                                end = Offset(endOffset, size.height),
                                strokeWidth = borderWidth
                            )
                        }
                    }
                }

                // Playhead fixo no centro
                val density = LocalDensity.current
                val playheadWidth = with(density) { TimelineConfig.PLAYHEAD_LINE_WIDTH.toPx() }
                val triangleHeight = with(density) { 12.dp.toPx() }
                val triangleWidth = with(density) { 8.dp.toPx() }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2

                    // Linha vermelha vertical
                    drawLine(
                        color = TimelineConfig.PLAYHEAD_COLOR,
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, size.height),
                        strokeWidth = playheadWidth
                    )

                    // Triângulo no topo
                    val path = Path().apply {
                        moveTo(centerX - triangleWidth, 0f)
                        lineTo(centerX + triangleWidth, 0f)
                        lineTo(centerX, triangleHeight)
                        close()
                    }
                    drawPath(path, TimelineConfig.PLAYHEAD_COLOR)
                }
            }
        }
    }
}
