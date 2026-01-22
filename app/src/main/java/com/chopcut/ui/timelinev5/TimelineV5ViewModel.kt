package com.chopcut.ui.timelinev5

import androidx.lifecycle.ViewModel
import com.chopcut.ui.timelinev5.model.VideoRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel para gerenciar o estado e interações da TimelineV5.
 * Suporta múltiplos ranges de seleção.
 */
class TimelineV5ViewModel(
    initialDurationMs: Long
) : ViewModel() {

    private val _state = MutableStateFlow(
        com.chopcut.ui.timelinev5.model.TimelineState(
            totalDurationMs = initialDurationMs
        )
    )
    val state: StateFlow<com.chopcut.ui.timelinev5.model.TimelineState> = _state.asStateFlow()

    /**
     * Adiciona um novo range com 25% da duração total, centralizado.
     */
    fun addRange() {
        _state.update { current ->
            val duration25 = (current.totalDurationMs * 0.25f).toLong().coerceAtLeast(1000L)
            val startMs = ((current.totalDurationMs - duration25) / 2).coerceAtLeast(0)
            val endMs = (startMs + duration25).coerceAtMost(current.totalDurationMs)

            val newRange = VideoRange(
                startMs = startMs,
                endMs = endMs,
                isSelected = false
            )

            current.copy(ranges = current.ranges + newRange)
        }
    }

    /**
     * Remove o range selecionado atualmente.
     */
    fun removeSelectedRange() {
        _state.update { current ->
            current.copy(ranges = current.ranges.filterNot { it.isSelected })
        }
    }

    /**
     * Seleciona um range específico.
     */
    fun selectRange(rangeId: String) {
        _state.update { current ->
            current.copy(
                ranges = current.ranges.map { range ->
                    range.withSelected(range.id == rangeId)
                }
            )
        }
    }

    /**
     * Atualiza o início do range selecionado.
     */
    fun updateSelectedRangeStart(startMs: Long) {
        _state.update { current ->
            val selectedIdx = current.ranges.indexOfFirst { it.isSelected }
            if (selectedIdx == -1) return@update current

            val selectedRange = current.ranges[selectedIdx]
            val validStart = startMs.coerceIn(0, selectedRange.endMs - 1)

            current.copy(
                ranges = current.ranges.toMutableList().apply {
                    set(selectedIdx, selectedRange.copy(startMs = validStart))
                }
            )
        }
    }

    /**
     * Atualiza o fim do range selecionado.
     */
    fun updateSelectedRangeEnd(endMs: Long) {
        _state.update { current ->
            val selectedIdx = current.ranges.indexOfFirst { it.isSelected }
            if (selectedIdx == -1) return@update current

            val selectedRange = current.ranges[selectedIdx]
            val validEnd = endMs.coerceIn(selectedRange.startMs + 1, current.totalDurationMs)

            current.copy(
                ranges = current.ranges.toMutableList().apply {
                    set(selectedIdx, selectedRange.copy(endMs = validEnd))
                }
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

    /**
     * Atualiza a duração total do vídeo.
     */
    fun updateTotalDuration(durationMs: Long) {
        _state.update { current ->
            current.copy(totalDurationMs = durationMs)
        }
    }
}
