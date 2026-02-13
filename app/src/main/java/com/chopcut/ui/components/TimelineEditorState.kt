package com.chopcut.ui.components

import com.chopcut.data.model.EditOperation
import com.chopcut.data.model.FilterType

/**
 * Enum para ferramentas ativas no editor
 */
enum class EditorTool {
    FILTER,
    SPEED,
    EXPORT,
    NONE
}

data class TimelineEditorState(
    val trimPosition: TrimPosition = TrimPosition.Empty,
    val currentPosition: Long = 0L,
    val videoDurationMs: Long = 0L,
    val waveformData: WaveformData = WaveformData.empty(),
    val isWaveformLoading: Boolean = false,
    val waveformError: String? = null,

    // NOVOS CAMPOS - Fase 1 MVP
    val currentFilter: EditOperation.Filter? = null,
    val currentSpeed: Float = 1.0f,
    val activeTool: EditorTool = EditorTool.NONE
) {
    val totalTrimmedMs: Long
        get() = trimPosition.completeRanges.sumOf { it.second - it.first }

    val finalDurationMs: Long
        get() = (videoDurationMs - totalTrimmedMs).coerceAtLeast(0L)

    /**
     * Duração final considerando a velocidade aplicada
     */
    val adjustedDurationMs: Long
        get() = (finalDurationMs / currentSpeed).toLong()

    val isDraftMode: Boolean get() = trimPosition.isDraftMode

    val isInsideRange: Boolean get() = trimPosition.isPositionInRange(currentPosition)

    val isDraftInsideRange: Boolean
        get() = isDraftMode && trimPosition.draftPosition?.let { draft ->
            trimPosition.completeRanges.any { (s, e) -> draft in s..e }
        } ?: false

    val playheadColor: PlayheadColor
        get() = when {
            isDraftInsideRange -> PlayheadColor.Gray
            isInsideRange -> PlayheadColor.Red
            isDraftMode -> PlayheadColor.Yellow
            else -> PlayheadColor.Green
        }

    /**
     * Indica se há algum filtro aplicado
     */
    val hasFilter: Boolean
        get() = currentFilter != null

    /**
     * Indica se a velocidade está diferente de 1.0x
     */
    val hasSpeedChange: Boolean
        get() = currentSpeed != 1.0f

    /**
     * Indica se há alguma edição aplicada
     */
    val hasEdits: Boolean
        get() = hasFilter || hasSpeedChange || trimPosition.completeRanges.isNotEmpty()
}

enum class PlayheadColor {
    Green,
    Yellow,
    Red,
    Gray
}
