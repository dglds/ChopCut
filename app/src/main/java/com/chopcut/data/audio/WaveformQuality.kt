package com.chopcut.data.audio

/**
 * Configuração de qualidade para geração de waveform
 * Determina o nível de detalhe e performance da extração
 */
sealed class WaveformQuality(val displayName: String, val barsPerSecond: Float) {

    /**
     * Qualidade mínima - para vídeos muito longos ou dispositivos lentos
     * ~2 bars/segundo, target sample rate ~3 Hz
     */
    data object Minimal : WaveformQuality("Mínima", 2f)

    /**
     * Qualidade baixa - para vídeos longos (5+ minutos)
     * ~5 bars/segundo, target sample rate ~5 Hz
     */
    data object Low : WaveformQuality("Baixa", 5f)

    /**
     * Qualidade média - para vídeos normais (1-5 minutos)
     * ~10 bars/segundo, target sample rate ~10 Hz
     */
    data object Medium : WaveformQuality("Média", 10f)

    /**
     * Qualidade alta - para vídeos curtos (<1 minuto)
     * ~15 bars/segundo, target sample rate ~15 Hz
     */
    data object High : WaveformQuality("Alta", 15f)

    /**
     * Calcula o target sample rate ideal para esta qualidade
     */
    fun calculateTargetSampleRate(): Int {
        return when (this) {
            Minimal -> 3
            Low -> 5
            Medium -> 10
            High -> 15
        }
    }

    /**
     * Calcula o número ideal de barras baseado na duração
     * Sem limites absolutos - apenas pela duração e largura da tela
     */
    fun calculateBarCount(durationMs: Long, screenWidthDp: Float = 400f): Int {
        val durationSeconds = durationMs / 1000f
        val totalBars = (durationSeconds * barsPerSecond).toInt()
        
        // Apenas limita baseado na largura da tela (~4dp por barra)
        val maxBarsByWidth = (screenWidthDp / 4f).toInt()
        
        return minOf(totalBars, maxBarsByWidth)
    }

    companion object {
        val Default = Medium
        val AllValues = listOf(Minimal, Low, Medium, High)
    }
}
