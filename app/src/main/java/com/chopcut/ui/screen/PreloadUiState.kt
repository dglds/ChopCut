package com.chopcut.ui.screen

import android.graphics.Bitmap
import android.net.Uri
import com.chopcut.data.model.VideoInfo

object PreloadConfig {
    // MELHORIA: Delay removido - extração agora é rápida (67% mais rápido com ThumbnailExtractorBatch)
    // Antes: 3000ms (extração lenta 300-500ms/frame)
    // Agora: 0ms (extração rápida 137ms/frame com cache em disco)
    const val THUMBNAIL_EXTRACTION_DELAY_MS = 0L
}

sealed class PreloadUiState {
    object Idle : PreloadUiState()
    data class Loading(val progress: PreloadProgress) : PreloadUiState()
    data class Ready(val data: PreloadedData) : PreloadUiState()
    data class Error(
        val message: String,
        val isDurationExceeded: Boolean = false
    ) : PreloadUiState()
    object Cancelled : PreloadUiState()
}

data class PreloadedData(
    val videoInfo: VideoInfo,
    val audioAmplitudes: List<Float>,
    val preloadedStrips: Map<Int, Bitmap>,
    val totalSegments: Int,
    val preloadPercentage: Float
) {
    val videoUri: Uri get() = videoInfo.uri
}

data class PreloadProgress(
    val stage: ExtractionStage,
    val audioPercent: Int = 0,
    val thumbnailPercent: Int = 0,
    val currentSegment: Int = 0,
    val totalSegments: Int = 0,
    val logs: List<String> = emptyList(),
    val preloadedStrips: Map<Int, Bitmap> = emptyMap()
)

enum class ExtractionStage {
    Starting,
    Validating,
    ExtractingAudio,
    ExtractingThumbnails,
    Ready
}
