package com.chopcut.ui.timeline.model

import android.net.Uri

/**
 * Estado consolidado do editor de vídeo.
 * Única fonte de verdade para toda a UI do editor.
 */
data class EditorState(
    val videoUri: Uri? = null,
    val totalDurationMs: Long = 0,
    val playheadPositionMs: Long = 0,
    val ranges: ListaRanges = emptyList(),
    val creationState: CreationState = CreationState.IDLE,
    val playerState: PlayerState = PlayerState.PAUSED,
    val isDragging: Boolean = false,
    val selectedRangeId: String? = null
) {
    val editingRange: RangeCorte?
        get() = ranges.firstOrNull { it.isEditing }

    val selectedRange: RangeCorte?
        get() = selectedRangeId?.let { id ->
            ranges.firstOrNull { it.id == id && it.isConfirmed }
        }

    val rangeAtPlayhead: RangeCorte?
        get() = ranges.noPlayhead(playheadPositionMs)

    val canDeleteAtPlayhead: Boolean
        get() = rangeAtPlayhead?.isConfirmed == true

    val progress: Float
        get() = if (totalDurationMs > 0) {
            playheadPositionMs.toFloat() / totalDurationMs
        } else 0f

    val isReady: Boolean
        get() = totalDurationMs > 0 && videoUri != null

    val canStartNewRange: Boolean
        get() = creationState is CreationState.IDLE && rangeAtPlayhead == null

    val isCreatingRange: Boolean
        get() = creationState is CreationState.WAITING_FOR_END

    companion object {
        val INITIAL = EditorState()
    }
}

sealed class CreationState {
    object IDLE : CreationState()

    data class WAITING_FOR_END(
        val startMs: Long,
        val temporaryRange: RangeCorte
    ) : CreationState()

    override fun toString(): String = when (this) {
        is IDLE -> "Idle"
        is WAITING_FOR_END -> "WaitingForEnd(start=${startMs}ms)"
    }
}

sealed class PlayerState {
    object LOADING : PlayerState()
    object READY : PlayerState()
    object PAUSED : PlayerState()
    object PLAYING : PlayerState()
    object ERROR : PlayerState()

    override fun toString(): String = when (this) {
        is LOADING -> "Loading"
        is READY -> "Ready"
        is PAUSED -> "Paused"
        is PLAYING -> "Playing"
        is ERROR -> "Error"
    }
}

sealed class FabState {
    object ADD : FabState()
    object CONFIRM : FabState()
    object DELETE : FabState()

    override fun toString(): String = when (this) {
        is ADD -> "Add"
        is CONFIRM -> "Confirm"
        is DELETE -> "Delete"
    }
}

fun EditorState.calculateFabState(): FabState = when {
    isCreatingRange -> FabState.CONFIRM
    canDeleteAtPlayhead -> FabState.DELETE
    else -> FabState.ADD
}

sealed class EditorEvent {
    data class PrepareVideo(val uri: Uri, val durationMs: Long) : EditorEvent()
    data class UpdatePosition(val positionMs: Long, val isDragging: Boolean = false) : EditorEvent()
    data class Dragging(val isActive: Boolean) : EditorEvent()
    object StartRangeCreation : EditorEvent()
    object FinishRangeCreation : EditorEvent()
    object CancelRangeCreation : EditorEvent()
    data class SelectRange(val rangeId: String?) : EditorEvent()
    data class UpdateRange(
        val rangeId: String,
        val newStartMs: Long,
        val newEndMs: Long
    ) : EditorEvent()
    data class DeleteRange(val rangeId: String) : EditorEvent()
    object DeleteRangeAtPlayhead : EditorEvent()
    data class UpdatePlayerState(val state: PlayerState) : EditorEvent()
    object TogglePlayback : EditorEvent()
    object Stop : EditorEvent()
    data class Seek(val positionMs: Long) : EditorEvent()
}
