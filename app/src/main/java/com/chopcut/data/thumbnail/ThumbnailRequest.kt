package com.chopcut.data.thumbnail

import android.graphics.Bitmap
import android.net.Uri

/**
 * Representa uma prioridade de extração de thumbnail
 */
enum class ThumbnailPriority(val value: Int) {
    VISIBLE(1),    // Visível no viewport atual
    PREFETCH(2),   // Próximo ao viewport
    DISTANT(3);    // Longe do viewport
}

/**
 * Solicitação de extração de thumbnail
 */
data class ThumbnailRequest(
    val uri: Uri,
    val timestamp: Long,
    val priority: ThumbnailPriority,
    val width: Int,
    val height: Int,
    val callback: (Bitmap) -> Unit
) : Comparable<ThumbnailRequest> {
    override fun compareTo(other: ThumbnailRequest): Int {
        // Menor valor numérico (VISIBLE=1) tem maior prioridade
        return this.priority.value.compareTo(other.priority.value)
    }
}
