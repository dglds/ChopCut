package com.chopcut.ui.timeline.model

import android.graphics.Bitmap
import java.util.UUID

/**
 * Representa uma miniatura de frame do vídeo na timeline.
 */
data class Thumbnail(
    val timeMs: Long,
    val bitmap: Bitmap?
)

/**
 * Representa um intervalo de corte (range) com ID único.
 */
data class VideoRange(
    val id: String = UUID.randomUUID().toString(),
    val startMs: Long,
    val endMs: Long,
    val isSelected: Boolean = false
) {
    /**
     * Duração deste range.
     */
    val durationMs: Long
        get() = endMs - startMs

    /**
     * Retorna uma cópia deste range com a seleção alterada.
     */
    fun withSelected(selected: Boolean): VideoRange = copy(isSelected = selected)
}

/**
 * Estado que define a posição do playhead e lista de ranges.
 */
data class TimelineState(
    val totalDurationMs: Long,
    val playheadPositionMs: Long = 0L,
    val ranges: List<VideoRange> = emptyList()
) {
    /**
     * Range atualmente selecionado, se houver.
     */
    val selectedRange: VideoRange?
        get() = ranges.firstOrNull { it.isSelected }
}