package com.chopcut.ui.timelinev4

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TimelineViewModel : ViewModel() {

    private val _state = MutableStateFlow(TimelineState())
    val state: StateFlow<TimelineState> = _state.asStateFlow()

    fun onEvent(event: TimelineEvent) {
        when (event) {
            is TimelineEvent.Seek -> {
                _state.update { it.copy(currentTimeMs = event.positionMs) }
            }
            is TimelineEvent.ScrubStart -> {
                _state.update { it.copy(isScrubbing = true) }
            }
            is TimelineEvent.ScrubEnd -> {
                _state.update { it.copy(isScrubbing = false) }
            }
            is TimelineEvent.Zoom -> {
                _state.update { it.copy(zoomLevel = event.newZoomLevel) }
            }
            is TimelineEvent.Scroll -> {
                // To be implemented
            }
        }
    }

    fun updateExternalPosition(positionMs: Long) {
        if (!_state.value.isScrubbing) {
            _state.update { it.copy(currentTimeMs = positionMs) }
        }
    }
}
