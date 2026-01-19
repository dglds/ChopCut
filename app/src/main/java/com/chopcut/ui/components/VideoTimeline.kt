package com.chopcut.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chopcut.data.thumbnail.ThumbnailExtractor
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Timeline data for a single thumbnail
 */
data class TimelineThumbnail(
    val positionMs: Long,
    val bitmap: Bitmap?
)

/**
 * Trim range data
 */
data class TrimRange(
    val startMs: Long,
    val endMs: Long
)

/**
 * Video timeline component with thumbnails and trim range selection
 *
 * @param uri Video URI
 * @param durationMs Video duration in milliseconds
 * @param thumbnailExtractor ThumbnailExtractor instance
 * @param thumbnailSize Size preset for thumbnails (default: NORMAL)
 * @param trimRange Current trim range (null if no trim)
 * @param onTrimRangeChange Callback when trim range changes
 * @param onPositionClick Callback when user clicks on a position
 * @param modifier Modifier for the container
 */
@Composable
fun VideoTimeline(
    uri: android.net.Uri,
    durationMs: Long,
    thumbnailExtractor: ThumbnailExtractor,
    thumbnailSize: ThumbnailExtractor.ThumbnailSize = ThumbnailExtractor.ThumbnailSize.NORMAL,
    trimRange: TrimRange? = null,
    onTrimRangeChange: (TrimRange?) -> Unit = {},
    onPositionClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var thumbnails by remember { mutableStateOf<List<TimelineThumbnail>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val thumbnailCount = remember(durationMs) {
        ThumbnailExtractor.RECOMMENDED_THUMB_COUNT
    }

    Timber.d("VideoTimeline: durationMs=$durationMs, thumbnailCount=$thumbnailCount")

    // Extract thumbnails when URI or duration changes
    LaunchedEffect(uri, durationMs, thumbnailSize) {
        if (durationMs > 0) {
            isLoading = true
            errorMessage = null
            coroutineScope.launch {
                try {
                    val interval = durationMs / (thumbnailCount + 1)
                    val positions = (1..thumbnailCount).map { i -> i * interval }

                    Timber.d("Extracting ${positions.size} thumbnails at positions: $positions (size: ${thumbnailSize.width}x${thumbnailSize.height})")

                    val bitmaps = thumbnailExtractor.extractAtPositions(
                        uri = uri,
                        positionsMs = positions,
                        width = thumbnailSize.width,
                        height = thumbnailSize.height
                    )

                    thumbnails = positions.mapIndexed { index, positionMs ->
                        TimelineThumbnail(positionMs, bitmaps[index])
                    }

                    Timber.d("Loaded ${thumbnails.size} thumbnails for timeline")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load timeline thumbnails")
                    errorMessage = "Error: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        } else {
            Timber.w("VideoTimeline: durationMs is 0, skipping thumbnail extraction")
            isLoading = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thumbnailSize.height.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        when {
            isLoading -> {
                Text(
                    text = "Loading timeline...",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            errorMessage != null -> {
                Text(
                    text = errorMessage ?: "Unknown error",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            thumbnails.isEmpty() -> {
                Text(
                    text = "No thumbnails available (duration: ${durationMs}ms)",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                Timber.d("Rendering ${thumbnails.size} thumbnails in LazyRow")
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(thumbnails.size) { index ->
                        val thumbnail = thumbnails[index]
                        Timber.d("Rendering thumbnail $index: ${thumbnail.positionMs}ms, bitmap=${thumbnail.bitmap != null}")
                        TimelineThumbnailItem(
                            thumbnail = thumbnail,
                            trimRange = trimRange,
                            durationMs = durationMs,
                            thumbnailSize = thumbnailSize,
                            onClick = { onPositionClick(thumbnail.positionMs) }
                        )
                    }
                }
            }
        }

        // Trim range overlay
        if (trimRange != null && durationMs > 0) {
            TrimRangeOverlay(
                trimRange = trimRange,
                durationMs = durationMs,
                height = thumbnailSize.height.dp
            )
        }
    }
}

/**
 * Single timeline thumbnail item
 */
@Composable
fun TimelineThumbnailItem(
    thumbnail: TimelineThumbnail,
    trimRange: TrimRange?,
    durationMs: Long,
    thumbnailSize: ThumbnailExtractor.ThumbnailSize,
    onClick: () -> Unit
) {
    val isInRange = trimRange?.let { range ->
        thumbnail.positionMs >= range.startMs && thumbnail.positionMs <= range.endMs
    } ?: true

    Timber.v("TimelineThumbnailItem: positionMs=${thumbnail.positionMs}, hasBitmap=${thumbnail.bitmap != null}, width=${thumbnail.bitmap?.width}, height=${thumbnail.bitmap?.height}")

    // Use the actual bitmap dimensions or the expected size from preset
    val bitmapWidth = thumbnail.bitmap?.width ?: thumbnailSize.width
    val bitmapHeight = thumbnail.bitmap?.height ?: thumbnailSize.height

    Box(
        modifier = Modifier
            .width(bitmapWidth.dp)  // Largura exata da thumb (sem gap)
            .height(bitmapHeight.dp) // Altura dinâmica baseada no bitmap
            .clickable(onClick = onClick)
            .then(
                if (!isInRange) {
                    Modifier.background(Color.Black.copy(alpha = 0.5f))
                } else {
                    Modifier
                }
            )
    ) {
        thumbnail.bitmap?.let { bitmap ->
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Thumbnail at ${thumbnail.positionMs}ms",
                modifier = Modifier.fillMaxSize()
            )
        } ?: Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Gray),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "X",
                fontSize = 12.sp,
                color = Color.White
            )
        }
    }
}

/**
 * Overlay showing trim range on timeline
 */
@Composable
fun TrimRangeOverlay(
    trimRange: TrimRange,
    durationMs: Long,
    height: androidx.compose.ui.unit.Dp
) {
    val startFraction = trimRange.startMs.toFloat() / durationMs.toFloat()
    val endFraction = trimRange.endMs.toFloat() / durationMs.toFloat()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        // Trimmed area overlay (left)
        if (startFraction > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(startFraction)
                    .height(height)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .align(Alignment.CenterStart)
            )
        }

        // Trimmed area overlay (right)
        if (endFraction < 1f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .align(Alignment.CenterEnd)
            )
        }
    }
}

/**
 * Timeline with trim handles for manual trim range adjustment
 */
@Composable
fun VideoTimelineWithTrims(
    uri: android.net.Uri,
    durationMs: Long,
    thumbnailExtractor: ThumbnailExtractor,
    trimRange: TrimRange?,
    onTrimRangeChange: (TrimRange) -> Unit,
    modifier: Modifier = Modifier
) {
    // TODO: Implement interactive trim handles
    // This will allow users to drag trim handles to adjust the trim range
    VideoTimeline(
        uri = uri,
        durationMs = durationMs,
        thumbnailExtractor = thumbnailExtractor,
        trimRange = trimRange,
        onTrimRangeChange = { newRange ->
            if (newRange != null) {
                onTrimRangeChange(newRange)
            }
        },
        modifier = modifier
    )
}

/**
 * Timeline mode enum
 */
enum class TimelineMode {
    /** Individual thumbnails (LazyRow) */
    INDIVIDUAL,
    /** Single filmstrip image (no gaps) */
    FILMSTRIP
}

/**
 * Filmstrip timeline component using extractFilmstrip for seamless thumbnails
 *
 * @param uri Video URI
 * @param durationMs Video duration in milliseconds
 * @param thumbnailExtractor ThumbnailExtractor instance
 * @param trimRange Current trim range (null if no trim)
 * @param onTrimRangeChange Callback when trim range changes
 * @param onPositionClick Callback when user clicks on a position
 * @param thumbsPerSecond Number of thumbnails per second to extract
 * @param modifier Modifier for the container
 */
@Composable
fun TimelineFilmstrip(
    uri: android.net.Uri,
    durationMs: Long,
    thumbnailExtractor: ThumbnailExtractor,
    trimRange: TrimRange? = null,
    onTrimRangeChange: (TrimRange?) -> Unit = {},
    onPositionClick: (Long) -> Unit = {},
    thumbsPerSecond: Int = 2,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var filmstripBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var clickPosition by remember { mutableStateOf(0f) }

    Timber.d("TimelineFilmstrip: durationMs=$durationMs, thumbsPerSecond=$thumbsPerSecond")

    // Extract filmstrip when URI or duration changes
    LaunchedEffect(uri, durationMs, thumbsPerSecond) {
        if (durationMs > 0) {
            isLoading = true
            errorMessage = null
            coroutineScope.launch {
                try {
                    val settings = com.chopcut.data.model.ThumbnailSettings(
                        thumbsPerSecond = thumbsPerSecond,
                        dimensionPreset = com.chopcut.data.model.DimensionPreset.SMALL  // 240x135
                    )

                    thumbnailExtractor.extractFilmstrip(
                        uri = uri,
                        durationMs = durationMs,
                        settings = settings
                    ).collect { (progress, bitmap) ->
                        if (bitmap != null) {
                            filmstripBitmap = bitmap
                            Timber.d("Filmstrip loaded: ${bitmap.width}x${bitmap.height}")
                        }
                        if (progress.isComplete) {
                            isLoading = false
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load filmstrip")
                    errorMessage = "Error: ${e.message}"
                    isLoading = false
                }
            }
        } else {
            Timber.w("TimelineFilmstrip: durationMs is 0, skipping extraction")
            isLoading = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                onClick = {
                    if (filmstripBitmap != null) {
                        // Calculate position in video based on click (simplified to center for now)
                        val positionMs = durationMs / 2
                        onPositionClick(positionMs)
                    }
                }
            )
    ) {
        when {
            isLoading -> {
                Text(
                    text = "Loading filmstrip...",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            errorMessage != null -> {
                Text(
                    text = errorMessage ?: "Unknown error",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            filmstripBitmap != null -> {
                androidx.compose.foundation.Image(
                    bitmap = filmstripBitmap!!.asImageBitmap(),
                    contentDescription = "Video filmstrip",
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Text(
                    text = "No filmstrip available",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Trim range overlay
        if (trimRange != null && durationMs > 0) {
            TrimRangeOverlay(
                trimRange = trimRange,
                durationMs = durationMs,
                height = 50.dp  // Altura fixa para TimelineFilmstrip
            )
        }
    }
}
