package com.chopcut.data.audio.model

/**
 * Modelo único de dados de waveform
 * 
 * Contém os dados finais prontos para renderização,
 * já com downsampling e threshold aplicados.
 */
data class WaveformData(
    val amplitudes: FloatArray,  // Dados finais, prontos para renderizar
    val durationMs: Long
) {
    val barCount: Int get() = amplitudes.size
    val isEmpty: Boolean get() = amplitudes.isEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as WaveformData
        if (!amplitudes.contentEquals(other.amplitudes)) return false
        if (durationMs != other.durationMs) return false
        return true
    }

    override fun hashCode(): Int {
        var result = amplitudes.contentHashCode()
        result = 31 * result + durationMs.hashCode()
        return result
    }

    companion object {
        fun empty() = WaveformData(floatArrayOf(), 0)
    }
}
