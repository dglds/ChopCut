package com.chopcut.ui.timeline

import androidx.lifecycle.ViewModel
import com.chopcut.ui.components.TrimRangeData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * ViewModel para gerenciar o estado e interações da Timeline.
 * Suporta múltiplos ranges de remoção (áreas vermelhas).
 */
class TimelineViewModel(
    initialDurationMs: Long
) : ViewModel() {

    data class TimelineEditorState(
        val totalDurationMs: Long,
        val playheadPositionMs: Long = 0L,
        val ranges: List<TrimRangeData> = emptyList()
    ) {
        val selectedRange: TrimRangeData?
            get() = ranges.firstOrNull { it.isSelected }
    }

    private val _state = MutableStateFlow(
        TimelineEditorState(totalDurationMs = initialDurationMs)
    )
    val state: StateFlow<TimelineEditorState> = _state.asStateFlow()

    /**
     * Adiciona um novo range de 1 segundo centrado no playhead.
     * Auto-ajusta se houver sobreposição com ranges existentes.
     */
    fun addRange() {
        _state.update { current ->
            val durationMs = 1000L // 1 segundo
            val halfDuration = durationMs / 2
            
            var startMs = (current.playheadPositionMs - halfDuration).coerceAtLeast(0L)
            var endMs = (startMs + durationMs).coerceAtMost(current.totalDurationMs)
            
            // Ajusta se ficou menor que 100ms
            if (endMs - startMs < 100L) {
                return@update current
            }

            val newRange = TrimRangeData(
                id = UUID.randomUUID().toString(),
                startMs = startMs,
                endMs = endMs,
                isSelected = true,
                isDraft = true,
                isConfirmed = false
            )

            current.copy(
                ranges = current.ranges.map { it.copy(isSelected = false) } + newRange
            )
        }
    }

    /**
     * Remove o range pelo ID.
     */
    fun removeRange(rangeId: String) {
        _state.update { current ->
            current.copy(ranges = current.ranges.filterNot { it.id == rangeId })
        }
    }

    /**
     * Confirma/salva um range draft.
     */
    fun confirmRange(rangeId: String) {
        _state.update { current ->
            current.copy(
                ranges = current.ranges.map { range ->
                    if (range.id == rangeId) {
                        range.copy(isDraft = false, isConfirmed = true, isSelected = false)
                    } else range
                }
            )
        }
    }

    /**
     * Seleciona um range específico.
     */
    fun selectRange(rangeId: String?) {
        _state.update { current ->
            current.copy(
                ranges = current.ranges.map { range ->
                    range.copy(isSelected = range.id == rangeId)
                }
            )
        }
    }

    /**
     * Atualiza um range (início e fim) com validação de sobreposição.
     */
    fun updateRange(rangeId: String, newStart: Long, newEnd: Long) {
        _state.update { current ->
            val rangeToUpdate = current.ranges.find { it.id == rangeId } ?: return@update current
            
            // Validações básicas
            val validStart = newStart.coerceIn(0L, rangeToUpdate.endMs - 100L)
            val validEnd = newEnd.coerceIn(rangeToUpdate.startMs + 100L, current.totalDurationMs)
            
            // Auto-ajuste: não permite sobreposição com outros ranges
            val otherRanges = current.ranges.filter { it.id != rangeId }
            
            val adjustedStart = calculateValidStart(validStart, validEnd, rangeId, otherRanges, current.totalDurationMs)
            val adjustedEnd = calculateValidEnd(adjustedStart, validEnd, rangeId, otherRanges, current.totalDurationMs)

            current.copy(
                ranges = current.ranges.map { range ->
                    if (range.id == rangeId) {
                        range.copy(startMs = adjustedStart, endMs = adjustedEnd)
                    } else range
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
    fun getRangeAtPlayhead(): TrimRangeData? {
        return state.value.ranges.firstOrNull {
            state.value.playheadPositionMs in it.startMs..it.endMs
        }
    }

    // ===== Funções auxiliares de auto-ajuste =====
    
    private fun calculateValidStart(
        rawStart: Long,
        endMs: Long,
        currentId: String,
        otherRanges: List<TrimRangeData>,
        videoDurationMs: Long
    ): Long {
        val minStart = 0L
        val maxStart = (endMs - 100L).coerceAtLeast(0L)
        
        // Encontra o range anterior (mais próximo à esquerda)
        val nearestEndBefore = otherRanges
            .filter { it.endMs <= rawStart }
            .maxByOrNull { it.endMs }
            ?.endMs ?: minStart
        
        return rawStart.coerceIn(nearestEndBefore, maxStart)
    }

    private fun calculateValidEnd(
        startMs: Long,
        rawEnd: Long,
        currentId: String,
        otherRanges: List<TrimRangeData>,
        videoDurationMs: Long
    ): Long {
        val minEnd = (startMs + 100L).coerceAtMost(videoDurationMs)
        val maxEnd = videoDurationMs
        
        // Encontra o range seguinte (mais próximo à direita)
        val nearestStartAfter = otherRanges
            .filter { it.startMs >= rawEnd }
            .minByOrNull { it.startMs }
            ?.startMs ?: maxEnd
        
        return rawEnd.coerceIn(minEnd, nearestStartAfter)
    }
}
