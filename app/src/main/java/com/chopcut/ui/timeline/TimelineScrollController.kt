package com.chopcut.ui.timeline

import com.chopcut.ui.components.TimelineCalculator
import com.chopcut.ui.components.TimelineConfigV2
import kotlin.math.roundToLong

/**
 * Controlador responsável por gerenciar a lógica de scrubbing da timeline.
 * Converte a posição de scroll da lista em tempo de vídeo.
 */
class TimelineScrollController(
    private val durationMs: Long,
    private val frameRate: Int = 30,
    private val onSeek: (Long) -> Unit
) {
    /**
     * Atualiza o tempo baseado na posição do scroll.
     * Deve ser chamado sempre que o scroll mudar durante uma interação do usuário.
     */
    fun onScrollChanged(
        index: Int,
        offset: Int,
        thumbSizePx: Int,
        screenWidthPx: Int
    ) {
        if (thumbSizePx <= 0 || screenWidthPx <= 0) return

        val paddingPx = screenWidthPx / 2
        
        val timeMs = TimelineCalculator.calculateTimeFromScroll(
            index = index,
            offset = offset,
            thumbWidthPx = thumbSizePx,
            msPerThumb = TimelineConfigV2.THUMB_DURATION_MS,
            spacerWidthPx = paddingPx
        )

        // Calculate frame duration in ms
        val frameDurationMs = if (frameRate > 0) 1000.0 / frameRate else 33.33

        // Snap to nearest frame
        val frameCount = (timeMs / frameDurationMs).roundToLong()
        val snappedTimeMs = (frameCount * frameDurationMs).toLong()

        // Garante que o tempo esteja dentro dos limites e notifica
        val clampedTime = snappedTimeMs.coerceIn(0, durationMs)
        onSeek(clampedTime)
    }
}