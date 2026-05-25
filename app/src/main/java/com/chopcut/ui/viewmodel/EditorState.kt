package com.chopcut.ui.viewmodel

import androidx.media3.exoplayer.ExoPlayer
import com.chopcut.ui.components.trim.TrimPosition
import com.chopcut.ui.components.waveform.WaveformData
import com.chopcut.ui.state.EditorTool
import com.chopcut.ui.state.CompressionLevel

/**
 * Estado global do Editor Unificado
 */
data class EditorState(
    val activeTool: EditorTool = EditorTool.NONE,
    val aspectRatio: Float? = null,
    val compressionLevel: CompressionLevel = CompressionLevel.ORIGINAL,
    val trimPosition: TrimPosition = TrimPosition.Empty,
    val currentPosition: Long = 0L,
    val videoDurationMs: Long = 0L,
    val waveformData: WaveformData = WaveformData.empty(),
    val isWaveformLoading: Boolean = false,
    val waveformError: String? = null,
    // Novos campos para AudioWaveForms
    val audioWaveformsAmplitudes: FloatArray = floatArrayOf(),
    val isAudioWaveformsLoading: Boolean = false,
    
    // Dimensions
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,

    // Player related states
    val exoPlayer: ExoPlayer? = null,
    val isPlaying: Boolean = false,
    val playerError: String? = null,
    val isSecurityError: Boolean = false,

    // Scrubbing: true enquanto o usuário está arrastando a timeline
    val isScrubbing: Boolean = false
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

