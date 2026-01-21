package com.chopcut.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.chopcut.data.thumbnail.ThumbnailExtractor
import com.chopcut.ui.timeline.ScrubbingState
import com.chopcut.ui.timeline.TimelineState
import com.chopcut.ui.timeline.rememberSeekThrottler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Configurações da timeline V2 com Box + Offset
 */
object TimelineConfigV2 {
    /** Altura da timeline em dp (thumbs quadradas) */
    val TIMELINE_HEIGHT = 64.dp

    /** Largura da linha do playhead em dp */
    val PLAYHEAD_LINE_WIDTH = 1.5.dp

    /** Cor do playhead */
    val PLAYHEAD_COLOR = Color.White

    /** Cada thumb representa exatamente 1 segundo */
    const val THUMB_DURATION_MS: Long = 1000L

    /** Pixels por milissegundo (será calculado dinamicamente baseado na largura da thumb) */
    var PIXELS_PER_MS: Float = 0f
        private set

    /** Atualiza a escala baseado na largura da thumb em pixels */
    fun updateScale(thumbWidthPx: Int) {
        PIXELS_PER_MS = thumbWidthPx.toFloat() / THUMB_DURATION_MS
    }
}

/**
 * Video timeline component V2 com Box + Offset (CapCut-style)
 *
 * @param uri Video URI
 * @param durationMs Video duration in milliseconds
 * @param videoWidth Original video width for aspect ratio calculation
 * @param videoHeight Original video height for aspect ratio calculation
 * @param currentPositionMs Current playback position in milliseconds
 * @param isPlaying Whether video is playing
 * @param thumbnailExtractor ThumbnailExtractor instance
 * @param trimRange Current trim range (ignored in V2 visualization for now)
 * @param onSeek Callback when seeking
 * @param onDragStart Callback when drag starts
 * @param onDragEnd Callback when drag ends
 * @param modifier Modifier for the container
 */
@Composable
fun VideoTimelineV2(
    uri: android.net.Uri,
    durationMs: Long,
    videoWidth: Int = 0,
    videoHeight: Int = 0,
    currentPositionMs: Long = 0L,
    isPlaying: Boolean = false,
    thumbnailExtractor: ThumbnailExtractor,
    trimRange: TrimRange? = null,
    onSeek: (Long) -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Thumbnails management
    var thumbnails by remember { mutableStateOf<List<Bitmap?>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Layout & Scrolling
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    var screenWidthPx by remember { mutableStateOf(0) }

    // Interaction State
    var isUserInteracting by remember { mutableStateOf(false) }

    // Timeline State (Playing, Scrubbing, SeekSettling, Idle)
    var timelineState by remember { mutableStateOf<TimelineState>(TimelineState.Idle) }
    var scrubbingState by remember { mutableStateOf<ScrubbingState>(ScrubbingState.Idle) }

    val currentPosState = androidx.compose.runtime.rememberUpdatedState(currentPositionMs)

    // Seek Throttler para performance (~30fps)
    val seekThrottler = rememberSeekThrottler(delayMs = 33L)

    // Thumbs QUADRADAS: largura = altura
    val thumbSizeDp = TimelineConfigV2.TIMELINE_HEIGHT
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }.toInt()

    // Atualizar a escala da timeline (1 thumb = 1 segundo)
    LaunchedEffect(thumbSizePx) {
        TimelineConfigV2.updateScale(thumbSizePx)
    }

    // Recalcular Thumbnails: 1 thumb por segundo
    LaunchedEffect(uri, durationMs, thumbSizePx) {
        if (durationMs > 0 && thumbSizePx > 0) {
            isLoading = true

            // Calcular número de thumbs: 1 thumb por segundo (arredondar para cima)
            val durationSeconds = (durationMs.toFloat() / 1000f)
            val count = (durationSeconds).toInt() + 1

            // Cada thumb representa 1 segundo: thumb[0] = 0s, thumb[1] = 1s, etc.
            val positions = (0 until count).map { it * TimelineConfigV2.THUMB_DURATION_MS }

            Timber.d("VideoTimelineV2: Extracting $count thumbnails (1 per second) for ${durationMs}ms (size: ${thumbSizePx}x${thumbSizePx})")
            try {
                // Usar resolução completa para thumbs nítidas
                val bitmaps = thumbnailExtractor.extractAtPositions(
                    uri = uri,
                    positionsMs = positions,
                    width = thumbSizePx,
                    height = thumbSizePx
                )
                thumbnails = positions.mapIndexed { i, _ -> bitmaps.getOrNull(i) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load thumbnails")
            } finally {
                isLoading = false
            }
        }
    }

    // Sync completo: Player ↔ Timeline (SIMPLIFICADO)
    LaunchedEffect(Unit) {
        // Monitorar posição do player e atualizar timeline
        snapshotFlow { currentPosState.value }
            .collect { posMs ->
                if (!isUserInteracting && thumbSizePx > 0 && screenWidthPx > 0 && durationMs > 0) {
                    val spacerWidthPx = screenWidthPx / 2
                    val (lazyIndex, offset) = TimelineCalculator.calculateLazyListScroll(
                        posMs,
                        thumbSizePx,
                        TimelineConfigV2.THUMB_DURATION_MS,
                        spacerWidthPx
                    )

                    Timber.v("TimelineSync: ${posMs}ms → lazyIndex=$lazyIndex, offset=${offset}px")

                    listState.scrollToItem(lazyIndex, offset)
                }
            }
    }

    // User-scroll: Timeline → Player
    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .map { (index, offset) ->
                if (isUserInteracting && thumbSizePx > 0 && screenWidthPx > 0) {
                    val spacerWidthPx = screenWidthPx / 2
                    val timeMs = TimelineCalculator.calculateTimeFromScroll(
                        index,
                        offset,
                        thumbSizePx,
                        TimelineConfigV2.THUMB_DURATION_MS,
                        spacerWidthPx
                    ).coerceIn(0, durationMs)

                    timeMs
                } else {
                    null
                }
            }
            .distinctUntilChanged()
            .collectLatest { timeMs ->
                if (timeMs != null) {
                    Timber.v("TimelineScrub: seeking to ${timeMs}ms")
                    // Bypass throttler for debugging
                    onSeek(timeMs)
                    /*
                    seekThrottler.throttle(timeMs) { pos ->
                        onSeek(pos)
                    }
                    */
                }
            }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TimelineConfigV2.TIMELINE_HEIGHT)
            .background(Color.Black) // Fundo escuro para vídeo
            .onSizeChanged { screenWidthPx = it.width }
    ) {
        if (isLoading) {
             LinearProgressIndicator(
                 modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                 color = MaterialTheme.colorScheme.primary,
                 trackColor = Color.Transparent
             )
        } 
        
        if (screenWidthPx > 0) {
            val paddingPx = screenWidthPx / 2
            val paddingDp = with(density) { paddingPx.toDp() }
            
            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // O toque foi detectado
                            isUserInteracting = true
                            scrubbingState = ScrubbingState.Dragging
                            timelineState = TimelineState.Scrubbing
                            onDragStart()

                            try {
                                waitForUpOrCancellation()
                            } finally {
                                // O toque terminou (up ou cancel)
                                isUserInteracting = false
                                scrubbingState = ScrubbingState.Idle
                                timelineState = TimelineState.SeekSettling
                                seekThrottler.cancelPending()
                                onDragEnd()
                            }
                        }
                    }
            ) {
                // Start Spacer (Half Screen)
                item { Spacer(Modifier.width(paddingDp)) }
                
                // Thumbnails
                items(thumbnails.size) { i ->
                    val bitmap = thumbnails.getOrNull(i)
                    Box(
                        modifier = Modifier
                            .width(thumbSizeDp)
                            .height(thumbSizeDp) // Thumbs quadradas
                            .background(Color.DarkGray), // Placeholder visual
                        contentAlignment = Alignment.Center
                    ) {
                         if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop, // Crop mantém aspect ratio do vídeo
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                
                // End Spacer (Half Screen) - Permite scrollar até o fim
                item { Spacer(Modifier.width(paddingDp)) }
            }

            // Playhead (Center Fixed)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(TimelineConfigV2.PLAYHEAD_LINE_WIDTH)
                    .fillMaxHeight(0.8f) // Não preencher tudo verticalmente, estilo elegante
                    .background(TimelineConfigV2.PLAYHEAD_COLOR)
            )
        }
    }
}
