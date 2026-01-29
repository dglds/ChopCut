package com.chopcut.data.model

import androidx.compose.ui.graphics.Color
import java.util.UUID

/**
 * Representa um intervalo de tempo selecionável em um vídeo.
 *
 * @property id Identificador único do range
 * @property startMs Posição inicial do range em milissegundos
 * @property endMs Posição final do range em milissegundos
 * @property color Cor do range para visualização
 * @property isSelected Indica se o range está selecionado
 */
data class VideoRange(
    val id: String = UUID.randomUUID().toString(),
    val startMs: Long,
    val endMs: Long,
    val color: Color = Color(0xFF2196F3),
    val isSelected: Boolean = false
) {
    /**
     * Duração do range em milissegundos.
     */
    val durationMs: Long get() = endMs - startMs

    /**
     * Verifica se este range sobrepõe outro range.
     *
     * @param other O outro range para verificar sobreposição
     * @return true se os ranges se sobrepõem
     */
    fun overlapsWith(other: VideoRange): Boolean {
        return startMs < other.endMs && endMs > other.startMs
    }

    /**
     * Verifica se este range sobrepõe um intervalo específico.
     *
     * @param startMs Início do intervalo
     * @param endMs Fim do intervalo
     * @return true se há sobreposição
     */
    fun overlapsWith(startMs: Long, endMs: Long): Boolean {
        return this.startMs < endMs && this.endMs > startMs
    }

    /**
     * Verifica se uma posição está dentro deste range.
     *
     * @param positionMs Posição em milissegundos
     * @return true se a posição está dentro do range
     */
    fun contains(positionMs: Long): Boolean {
        return positionMs in startMs..endMs
    }

    /**
     * Retorna uma cópia deste range com nova posição.
     *
     * @param newStartMs Nova posição inicial
     * @param newEndMs Nova posição final
     * @return Nova instância de VideoRange com as posições atualizadas
     */
    fun withPosition(newStartMs: Long, newEndMs: Long): VideoRange {
        return copy(startMs = newStartMs, endMs = newEndMs)
    }

    /**
     * Retorna uma cópia deste range com o estado de seleção alterado.
     *
     * @param selected Novo estado de seleção
     * @return Nova instância de VideoRange com seleção atualizada
     */
    fun withSelection(selected: Boolean): VideoRange {
        return copy(isSelected = selected)
    }
}
