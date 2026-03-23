package com.chopcut.ui.components.trim

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.chopcut.data.model.VideoRange
import kotlin.math.max
import kotlin.math.min

/**
 * Gerenciador de ranges para edição de vídeo.
 *
 * Responsável por:
 * - Manter lista de ranges
 * - Validar sobreposições
 * - Redimensionar ranges
 * - Adicionar/remover ranges
 *
 * @property minRangeDurationMs Duração mínima permitida para um range (padrão: 500ms)
 * @property videoDurationMs Duração total do vídeo em milissegundos
 */
class RangeManager(
    private val minRangeDurationMs: Long = 500L,
    private val videoDurationMs: Long
) {
    private val _ranges = mutableStateListOf<VideoRange>()
    val ranges: SnapshotStateList<VideoRange> = _ranges

    /**
     * Adiciona um novo range nas posições especificadas.
     *
     * @param startMs Posição inicial em milissegundos
     * @param endMs Posição final em milissegundos
     * @return Result.success com o range criado ou Result.failure com mensagem de erro
     */
    fun addRangeAt(startMs: Long, endMs: Long): Result<VideoRange> {
        // Validar limites do vídeo
        if (startMs < 0 || endMs > videoDurationMs) {
            return Result.failure(IllegalArgumentException("Range fora dos limites do vídeo"))
        }

        // Validar ordem das posições
        if (startMs >= endMs) {
            return Result.failure(IllegalArgumentException("Início deve ser menor que fim"))
        }

        // Validar duração mínima
        if (endMs - startMs < minRangeDurationMs) {
            return Result.failure(IllegalArgumentException("Duração mínima: ${minRangeDurationMs}ms"))
        }

        // Validar sobreposição com ranges existentes
        if (!validateNoOverlap(startMs, endMs)) {
            return Result.failure(IllegalArgumentException("Range sobrepõe range existente"))
        }

        // Criar e adicionar o range
        val newRange = VideoRange(
            startMs = startMs,
            endMs = endMs
        )
        _ranges.add(newRange)
        _ranges.sortBy { it.startMs }

        return Result.success(newRange)
    }

    /**
     * Adiciona um range centralizado na posição especificada.
     *
     * @param positionMs Posição central do range em milissegundos
     * @param defaultDurationMs Duração padrão do range (padrão: 2000ms)
     * @return Result.success com o range criado ou Result.failure com mensagem de erro
     */
    fun addCenteredRangeAt(positionMs: Long, defaultDurationMs: Long = 2000L): Result<VideoRange> {
        val halfDuration = defaultDurationMs / 2
        val startMs = max(0L, positionMs - halfDuration)
        val endMs = min(videoDurationMs, positionMs + halfDuration)

        // Ajustar se estiver nos limites
        val adjustedStartMs = if (endMs == videoDurationMs) {
            max(0L, videoDurationMs - defaultDurationMs)
        } else {
            startMs
        }

        val adjustedEndMs = if (startMs == 0L) {
            min(videoDurationMs, defaultDurationMs)
        } else {
            endMs
        }

        return addRangeAt(adjustedStartMs, adjustedEndMs)
    }

    /**
     * Remove um range pelo ID.
     *
     * @param rangeId ID do range a ser removido
     * @return true se o range foi removido, false se não foi encontrado
     */
    fun removeRange(rangeId: String): Boolean {
        return _ranges.removeIf { it.id == rangeId }
    }

    /**
     * Redimensiona um range especificado.
     *
     * @param rangeId ID do range a ser redimensionado
     * @param newStartMs Nova posição inicial (ou null para manter atual)
     * @param newEndMs Nova posição final (ou null para manter atual)
     * @return Result.success com o range atualizado ou Result.failure com mensagem de erro
     */
    fun resizeRange(
        rangeId: String,
        newStartMs: Long? = null,
        newEndMs: Long? = null
    ): Result<VideoRange> {
        val rangeIndex = _ranges.indexOfFirst { it.id == rangeId }
        if (rangeIndex == -1) {
            return Result.failure(IllegalArgumentException("Range não encontrado: $rangeId"))
        }

        val currentRange = _ranges[rangeIndex]
        val startMs = newStartMs ?: currentRange.startMs
        val endMs = newEndMs ?: currentRange.endMs

        // Validar ordem das posições
        if (startMs >= endMs) {
            return Result.failure(IllegalArgumentException("Início deve ser menor que fim"))
        }

        // Validar limites do vídeo
        if (startMs < 0 || endMs > videoDurationMs) {
            return Result.failure(IllegalArgumentException("Range fora dos limites do vídeo"))
        }

        // Validar duração mínima
        if (endMs - startMs < minRangeDurationMs) {
            return Result.failure(IllegalArgumentException("Duração mínima: ${minRangeDurationMs}ms"))
        }

        // Validar sobreposição com outros ranges (excluindo este range)
        if (!validateNoOverlap(startMs, endMs, excludeId = rangeId)) {
            return Result.failure(IllegalArgumentException("Range sobrepõe range existente"))
        }

        // Atualizar o range
        val updatedRange = currentRange.withPosition(startMs, endMs)
        _ranges[rangeIndex] = updatedRange
        _ranges.sortBy { it.startMs }

        return Result.success(updatedRange)
    }

    /**
     * Move um range por um delta em milissegundos.
     *
     * @param rangeId ID do range a ser movido
     * @param deltaMs Delta de movimento em milissegundos (positivo = direita, negativo = esquerda)
     * @return Result.success com o range movido ou Result.failure com mensagem de erro
     */
    fun moveRange(rangeId: String, deltaMs: Long): Result<VideoRange> {
        val range = _ranges.find { it.id == rangeId }
            ?: return Result.failure(IllegalArgumentException("Range não encontrado: $rangeId"))

        val newStartMs = range.startMs + deltaMs
        val newEndMs = range.endMs + deltaMs

        return resizeRange(rangeId, newStartMs, newEndMs)
    }

    /**
     * Seleciona ou desseleciona um range.
     *
     * @param rangeId ID do range
     * @param selected true para selecionar, false para desselecionar
     * @return true se o range foi atualizado, false se não foi encontrado
     */
    fun selectRange(rangeId: String, selected: Boolean): Boolean {
        val rangeIndex = _ranges.indexOfFirst { it.id == rangeId }
        if (rangeIndex == -1) return false

        _ranges[rangeIndex] = _ranges[rangeIndex].withSelection(selected)

        // Desselecionar todos os outros se estiver selecionando este
        if (selected) {
            for (i in _ranges.indices) {
                if (i != rangeIndex && _ranges[i].isSelected) {
                    _ranges[i] = _ranges[i].withSelection(false)
                }
            }
        }

        return true
    }

    /**
     * Desseleciona todos os ranges.
     */
    fun clearSelection() {
        for (i in _ranges.indices) {
            if (_ranges[i].isSelected) {
                _ranges[i] = _ranges[i].withSelection(false)
            }
        }
    }

    /**
     * Retorna o range selecionado atualmente.
     *
     * @return O range selecionado ou null se nenhum estiver selecionado
     */
    fun getSelectedRange(): VideoRange? {
        return _ranges.find { it.isSelected }
    }

    /**
     * Verifica se uma posição está dentro de algum range.
     *
     * @param positionMs Posição em milissegundos
     * @return O range que contém a posição ou null se não houver
     */
    fun findRangeAt(positionMs: Long): VideoRange? {
        return _ranges.find { it.contains(positionMs) }
    }

    /**
     * Valida se um intervalo não sobrepõe ranges existentes.
     *
     * @param startMs Início do intervalo
     * @param endMs Fim do intervalo
     * @param excludeId ID de um range para excluir da validação (útil ao mover/redimensionar)
     * @return true se não há sobreposição, false se há sobreposição
     */
    fun validateNoOverlap(startMs: Long, endMs: Long, excludeId: String? = null): Boolean {
        val otherRanges = if (excludeId != null) {
            _ranges.filterNot { it.id == excludeId }
        } else {
            _ranges
        }

        return otherRanges.none { it.overlapsWith(startMs, endMs) }
    }

    /**
     * Remove todos os ranges.
     */
    fun clear() {
        _ranges.clear()
    }

    /**
     * Retorna o número de ranges.
     */
    fun count(): Int = _ranges.size

    /**
     * Verifica se há ranges selecionados.
     */
    fun hasSelection(): Boolean = _ranges.any { it.isSelected }
}
