package com.chopcut.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.ui.components.TrimRangeData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class TimelineUiState(
    val ranges: List<TrimRangeData> = emptyList(),
    val isDefining: Boolean = false,
    val activeRangeId: String? = null,
    val currentPlayheadMs: Long = 0L
)

class TimelineViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    companion object {
        private const val BLOCK_DISTANCE_MS = 100L
    }

    /**
     * Atualiza a posição atual do playhead.
     */
    fun setPlayheadPosition(positionMs: Long) {
        _uiState.update { it.copy(currentPlayheadMs = positionMs) }
    }

    /**
     * Inicia a definição de um novo range na posição atual do playhead.
     */
    fun startRange() {
        val currentState = _uiState.value

        // Não permite iniciar se já está definindo
        if (currentState.isDefining) return

        val rangeId = UUID.randomUUID().toString()
        val newRange = TrimRangeData(
            id = rangeId,
            startMs = currentState.currentPlayheadMs,
            endMs = currentState.currentPlayheadMs,
            isDefining = true,
            isDraft = true,
            isConfirmed = false
        )

        _uiState.update { state ->
            state.copy(
                ranges = state.ranges + newRange,
                isDefining = true,
                activeRangeId = rangeId
            )
        }
    }

    /**
     * Atualiza a posição final do range sendo definido.
     */
    fun updateRangeEnd(endMs: Long) {
        val currentState = _uiState.value
        val activeId = currentState.activeRangeId ?: return

        _uiState.update { state ->
            val updatedRanges = state.ranges.map { range ->
                if (range.id == activeId) {
                    range.copy(endMs = endMs)
                } else {
                    range
                }
            }
            state.copy(ranges = updatedRanges)
        }
    }

    /**
     * Finaliza a definição do range atual com o endMs na posição atual do playhead.
     */
    fun endRange() {
        val currentState = _uiState.value
        val activeId = currentState.activeRangeId ?: return

        val activeRange = currentState.ranges.find { it.id == activeId } ?: return

        // Atualiza o endMs para a posição atual do playhead
        val updatedActiveRange = activeRange.copy(endMs = currentState.currentPlayheadMs)

        // Validar se não sobrepõe com ranges confirmados existentes (excluindo o próprio range)
        val confirmedRanges = currentState.ranges.filter { it.isConfirmed && it.id != activeId }
        val wouldOverlap = confirmedRanges.any { confirmedRange ->
            updatedActiveRange.overlaps(confirmedRange)
        }

        if (wouldOverlap) {
            // Remove o range não confirmado
            removeRange(activeId)
            return
        }

        _uiState.update { state ->
            val updatedRanges = state.ranges.map { range ->
                if (range.id == activeId) {
                    range.copy(
                        endMs = state.currentPlayheadMs,
                        isDefining = false,
                        isConfirmed = true,
                        isDraft = false
                    )
                } else {
                    range
                }
            }
            state.copy(
                ranges = updatedRanges,
                isDefining = false,
                activeRangeId = null
            )
        }
    }

    /**
     * Remove um range pelo ID.
     */
    fun removeRange(id: String) {
        _uiState.update { state ->
            val updatedRanges = state.ranges.filterNot { it.id == id }
            val wasActive = state.activeRangeId == id

            state.copy(
                ranges = updatedRanges,
                isDefining = if (wasActive) false else state.isDefining,
                activeRangeId = if (wasActive) null else state.activeRangeId
            )
        }
    }

    /**
     * Valida se uma nova posição de scroll é permitida.
     * Retorna false se estiver dentro de 100ms de um range confirmado.
     */
    fun validateScrollPosition(newPositionMs: Long): Boolean {
        val currentState = _uiState.value
        val confirmedRanges = currentState.ranges.filter { it.isConfirmed }

        for (range in confirmedRanges) {
            val min = kotlin.math.min(range.startMs, range.endMs)
            val max = kotlin.math.max(range.startMs, range.endMs)

            // Verificar se está dentro da zona de bloqueio (100ms antes ou depois)
            if (newPositionMs >= (min - BLOCK_DISTANCE_MS) &&
                newPositionMs <= (max + BLOCK_DISTANCE_MS)) {
                return false
            }
        }

        return true
    }

    /**
     * Retorna o range na posição especificada (para seleção).
     */
    fun findRangeAt(positionMs: Long): TrimRangeData? {
        return _uiState.value.ranges.find { it.contains(positionMs) }
    }
}
