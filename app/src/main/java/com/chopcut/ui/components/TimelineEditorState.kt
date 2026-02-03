package com.chopcut.ui.components

data class TimelineEditorState(
    val trimPosition: TrimPosition = TrimPosition.Empty,
    val currentPosition: Long = 0L,
    val videoDurationMs: Long = 0L
) {
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
}

enum class PlayheadColor {
    Green,
    Yellow,
    Red,
    Gray
}
