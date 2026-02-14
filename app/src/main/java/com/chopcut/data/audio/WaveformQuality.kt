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
     * Limitado para não sobrecarregar a renderização
     */
    fun calculateBarCount(durationMs: Long, screenWidthDp: Float = 400f): Int {
        val durationSeconds = durationMs / 1000f
        val totalBars = (durationSeconds * barsPerSecond).toInt()
        
        // Limita baseado na largura da tela (~4dp por barra)
        val maxBarsByWidth = (screenWidthDp / 4f).toInt()
        
        // Limites absolutos por qualidade
        val minBars = when (this) {
            Minimal -> 20
            Low -> 50
            Medium -> 100
            High -> 150
        }
        
        val maxBars = when (this) {
            Minimal -> 200
            Low -> 400
            Medium -> 800
            High -> 1200
        }
        
        return minOf(totalBars, maxBarsByWidth).coerceIn(minBars, maxBars)
    }

    companion object {
        val Default = Medium
        val AllValues = listOf(Minimal, Low, Medium, High)
    }
}
