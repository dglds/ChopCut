package com.chopcut.ui.timelinev5.model

import android.graphics.Bitmap

/**
 * Representa uma miniatura de frame do vídeo na timeline.
 */
data class Thumbnail(
    val timeMs: Long,
    val bitmap: Bitmap?
)

/**
 * Estado que define o intervalo de corte (trim) e a posição do playhead.
 */
data class TimelineState(
    val totalDurationMs: Long,
    val selectedStartMs: Long = 0L,
    val selectedEndMs: Long = totalDurationMs,
    val playheadPositionMs: Long = 0L
) {
    /**
     * Duração total do trecho selecionado.
     */
    val selectedDurationMs: Long
        get() = selectedEndMs - selectedStartMs
}