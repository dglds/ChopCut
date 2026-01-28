package com.chopcut.ui.timeline

import androidx.lifecycle.ViewModel
import com.chopcut.ui.timeline.model.VideoRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel para gerenciar o estado e interações da Timeline.
 * Suporta múltiplos ranges de seleção.
 */
class TimelineViewModel(
    initialDurationMs: Long
) : ViewModel() {

    private val _state = MutableStateFlow(
        com.chopcut.ui.timeline.model.TimelineState(
            totalDurationMs = initialDurationMs
        )
    )
    val state: StateFlow<com.chopcut.ui.timeline.model.TimelineState> = _state.asStateFlow()

    /**
     * Adiciona um novo range com duração de 1 thumbnail (10% do total), iniciando após o playhead.
     */
    fun addRange() {
        _state.update { current ->
            // Duração de 1 thumbnail (10% do total, já que temos 10 thumbnails)
            val oneThumbDuration = (current.totalDurationMs / 10f).toLong().coerceAtLeast(1000L)
            // Iniciar após o playhead
            val startMs = current.playheadPositionMs
            val endMs = (startMs + oneThumbDuration).coerceAtMost(current.totalDurationMs)

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

    /**
     * Retorna o range que contém a posição do playhead, se houver.
     */
    fun getRangeAtPlayhead(): VideoRange? {
        return state.value.ranges.firstOrNull {
            state.value.playheadPositionMs in it.startMs..it.endMs
        }
    }
}
