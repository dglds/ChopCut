package com.chopcut.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.chopcut.ui.components.TrimPosition
import com.chopcut.ui.components.TimelineEditorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

class TimelineViewModel : ViewModel() {

    private val _state = MutableStateFlow(TimelineEditorState())
    val state: StateFlow<TimelineEditorState> = _state.asStateFlow()

    fun addPosition(pos: Long) {
        val current = _state.value.trimPosition
        if (pos in current.positions) {
            Timber.d("Timeline: posição $pos já existe na lista")
            return
        }
        val newPosition = current.withPosition(pos)
        Timber.d("Timeline: addPosition $pos -> ${newPosition.positions}")
        _state.update {
            it.copy(trimPosition = newPosition)
        }
    }

    fun setCurrentPosition(pos: Long) {
        _state.update { it.copy(currentPosition = pos) }
    }

    fun setVideoDuration(duration: Long) {
        _state.update { it.copy(videoDurationMs = duration) }
    }

    fun removeRangeAt(pos: Long) {
        val current = _state.value.trimPosition
        if (current.isDraftMode) {
            val newPositions = current.positions.dropLast(1)
            _state.update { it.copy(trimPosition = current.copy(positions = newPositions)) }
        } else {
            val newTrim = current.removeRangeAt(pos)
            _state.update { it.copy(trimPosition = newTrim) }
        }
    }

    fun clear() {
        _state.value = TimelineEditorState()
        Timber.d("Timeline: estado limpo")
    }

    fun getCompleteRanges(): List<Pair<Long, Long>> {
        return _state.value.trimPosition.completeRanges
    }
}
