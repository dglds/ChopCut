package com.chopcut.ui.components.waveform

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuração visual do componente AudioWaveForms
 *
 * @param barColor Cor das barras (usa Primary do tema por padrão)
 * @param barWidth Largura fixa de cada barra (null = auto baseado no espaço)
 * @param barGap Espaço entre barras (null = auto baseado no espaço)
 * @param barCornerRadius Raio dos cantos das barras
 * @param minHeight Altura mínima para barras de silêncio (0.0-1.0)
 * @param maxHeight Escala máxima de altura (0.0-1.0)
 * @param animationEnabled Habilita animação de entrada
 * @param animationDuration Duração da animação em ms
 * @param gradient Gradiente opcional para as barras
 * @param baseline Posição da linha de base
 */
data class AudioWaveFormsConfig(
    val barColor: Color = Color.Unspecified,  // Unspecified = usa Primary do tema
    val barWidth: Dp? = null,                 // null = calculado automaticamente
    val barGap: Dp? = null,                   // null = calculado automaticamente
    val barCornerRadius: Dp = 2.dp,
    val minHeight: Float = 0.01f,             // Altura mínima para silêncio (quase invisível)
    val maxHeight: Float = 1.0f,              // Escala máxima
    val animationEnabled: Boolean = true,
    val animationDuration: Int = 600,
    val gradient: Brush? = null,
    val baseline: Baseline = Baseline.Bottom
) {
    enum class Baseline {
        /** Barras crescem para baixo a partir do topo */
        Top,
        /** Barras crescem do centro para ambos os lados */
        Center,
        /** Barras crescem para cima a partir da base (padrão) */
        Bottom
    }

    companion object {
        /**
         * Configuração minimalista - barras finas, sem animação
         */
        val Minimal = AudioWaveFormsConfig(
            barWidth = 1.dp,
            barGap = 1.dp,
            barCornerRadius = 0.dp,
            animationEnabled = false
        )

        /**
         * Configuração padrão do app
         */
        val Default = AudioWaveFormsConfig()

        /**
         * Configuração com gradiente laranja-vermelho
         */
        val Gradient = AudioWaveFormsConfig(
            gradient = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFF6B6B),
                    Color(0xFFFF8E53)
                )
            )
        )

        /**
         * Configuração compacta para telas pequenas
         */
        val Compact = AudioWaveFormsConfig(
            barWidth = 2.dp,
            barGap = 1.dp,
            barCornerRadius = 1.dp,
            minHeight = 0.03f
        )
    }
}
