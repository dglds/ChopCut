package com.chopcut.ui.components

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Representa um range de trim (área a ser removida) com ID único.
 *
 * @property id Identificador único do range.
 * @property startMs Ponto de início em milissegundos.
 * @property endMs Ponto de fim em milissegundos.
 * @property isSelected Se true, o range está selecionado na UI.
 * @property isDraft Se true, o range está em edição (alças visíveis).
 * @property isConfirmed Se true, o range já foi salvo.
 * @property isDefining Se true, o range está sendo definido (Mark A definido, Mark B seguindo o playhead).
 */
@Parcelize
data class TrimRangeData(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val isSelected: Boolean = false,
    val isDraft: Boolean = true,
    val isConfirmed: Boolean = false,
    val isDefining: Boolean = false
) : Parcelable {
    val durationMs: Long get() = Math.abs(endMs - startMs)

    fun contains(timeMs: Long): Boolean {
        val min = Math.min(startMs, endMs)
        val max = Math.max(startMs, endMs)
        return timeMs in min..max
    }

    fun overlaps(other: TrimRangeData): Boolean {
        val thisMin = Math.min(startMs, endMs)
        val thisMax = Math.max(startMs, endMs)
        val otherMin = Math.min(other.startMs, other.endMs)
        val otherMax = Math.max(other.startMs, other.endMs)
        return thisMin < otherMax && thisMax > otherMin
    }
}
