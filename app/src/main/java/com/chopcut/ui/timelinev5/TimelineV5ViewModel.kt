package com.chopcut.ui.timelinev5

import androidx.lifecycle.ViewModel
import com.chopcut.ui.timelinev5.model.TimelineState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel para gerenciar o estado e interações da TimelineV5.
 */
class TimelineV5ViewModel(
    initialDurationMs: Long
) : ViewModel() {

    private val _state = MutableStateFlow(TimelineState(totalDurationMs = initialDurationMs))
    val state: StateFlow<TimelineState> = _state.asStateFlow()

    /**
     * Atualiza o início do intervalo selecionado.
     */
    fun updateSelectedStart(startMs: Long) {
        _state.update { current ->
            val validStart = startMs.coerceIn(0, current.selectedEndMs - 1)
            current.copy(
                selectedStartMs = validStart,
                playheadPositionMs = current.playheadPositionMs.coerceAtLeast(validStart)
            )
        }
    }

    /**
     * Atualiza o fim do intervalo selecionado.
     */
    fun updateSelectedEnd(endMs: Long) {
        _state.update { current ->
            val validEnd = endMs.coerceIn(current.selectedStartMs + 1, current.totalDurationMs)
            current.copy(
                selectedEndMs = validEnd,
                playheadPositionMs = current.playheadPositionMs.coerceAtMost(validEnd)
            )
        }
    }

    /**
     * Atualiza a posição do playhead.
     */
    fun updatePlayheadPosition(positionMs: Long) {
        _state.update { current ->
            current.copy(
                playheadPositionMs = positionMs.coerceIn(0, current.totalDurationMs)
            )
        }
    }
}