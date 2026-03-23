package com.chopcut.ui.components.trim

/**
 * Representa um intervalo de tempo para trim/recorte de vídeo
 * 
 * @property startMs Tempo de início em milissegundos (inclusivo)
 * @property endMs Tempo de fim em milissegundos (exclusivo)
 */
data class TrimRange(
    val startMs: Long,
    val endMs: Long
) {
    companion object {
        /** Cria um TrimRange com validação automática */
        fun create(startMs: Long, endMs: Long): TrimRange {
            require(startMs >= 0) { "startMs must be non-negative, was $startMs" }
            require(endMs >= startMs) { "endMs ($endMs) must be >= startMs ($startMs)" }
            return TrimRange(startMs, endMs)
        }
        
        /** TrimRange vazio (duração zero) */
        val Empty = TrimRange(0, 0)
    }
    
    /** Duração do intervalo em milissegundos */
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0)
    
    /** Verifica se o intervalo é válido (início < fim) */
    val isValid: Boolean
        get() = startMs >= 0 && endMs > startMs
    
    /** Verifica se o intervalo é vazio (duração zero) */
    val isEmpty: Boolean
        get() = durationMs == 0L
    
    /** Verifica se um tempo está dentro deste intervalo */
    fun contains(timeMs: Long): Boolean {
        return timeMs in startMs..endMs
    }
    
    /** Normaliza o intervalo para garantir que startMs <= endMs */
    fun normalize(): TrimRange {
        return if (startMs <= endMs) this else copy(startMs = endMs, endMs = startMs)
    }
    
    /** Interpola um valor baseado em uma duração total */
    fun scaledTo(totalDurationMs: Long): TrimRange {
        return copy(
            startMs = (startMs * totalDurationMs / durationMs).coerceAtLeast(0),
            endMs = (endMs * totalDurationMs / durationMs).coerceAtMost(totalDurationMs)
        )
    }
    
    override fun toString(): String {
        return "TrimRange(${formatTime(startMs)} - ${formatTime(endMs)})"
    }
    
    private fun formatTime(ms: Long): String {
        val seconds = ms / 1000
        val decis = (ms % 1000) / 100
        return "${seconds}s${decis}"
    }
}
