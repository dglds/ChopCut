package com.chopcut.ui.screen

import com.chopcut.ui.components.TrimPosition
import com.chopcut.ui.components.WaveformData

/**
 * Estado do editor de trim
 */
data class TrimEditorState(
    val trimPosition: TrimPosition = TrimPosition.Empty,
    val currentPosition: Long = 0L,
    val videoDurationMs: Long = 0L,
    val waveformData: WaveformData = WaveformData.empty(),
    val isWaveformLoading: Boolean = false,
    val waveformError: String? = null,
    // Novos campos para AudioWaveForms
    val audioWaveformsAmplitudes: List<Float> = emptyList(),
    val isAudioWaveformsLoading: Boolean = false
) {
    val totalTrimmedMs: Long
        get() = trimPosition.completeRanges.sumOf { it.second - it.first }

    val finalDurationMs: Long
        get() = (videoDurationMs - totalTrimmedMs).coerceAtLeast(0L)

    val isDraftMode: Boolean get() = trimPosition.isDraftMode

    val isInsideRange: Boolean get() = trimPosition.isPositionInRange(currentPosition)

    val isDraftInsideRange: Boolean
        get() = isDraftMode && trimPosition.draftPosition?.let { draft ->
            trimPosition.completeRanges.any { (s, e) -> draft in s..e }
        } ?: false

    val hasTrims: Boolean
        get() = trimPosition.completeRanges.isNotEmpty()
}
