package com.chopcut.ui.components.waveform

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius

/**
 * Componente de visualização de áudio com barras verticais
 *
 * Utiliza os dados extraídos pelo AudioDataExtractor para renderizar
 * uma representação visual do áudio em forma de barras verticais.
 *
 * @param amplitudes Lista de amplitudes normalizadas (0.0 a 1.0)
 * @param modifier Modificador de layout
 * @param config Configuração visual do componente
 */
@Composable
fun AudioWaveForms(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    config: AudioWaveFormsConfig = AudioWaveFormsConfig()
) {
    if (amplitudes.isEmpty()) return

    // Animação de entrada
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(amplitudes) {
        if (config.animationEnabled) {
            animatedProgress.snapTo(0f)
            animatedProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = config.animationDuration)
            )
        } else {
            animatedProgress.snapTo(1f)
        }
    }

    // Cor das barras (usa Primary do tema se não especificado)
    val barColor = if (config.barColor == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else {
        config.barColor
    }

    Canvas(
        modifier = modifier.fillMaxWidth()
    ) {
        if (amplitudes.isEmpty()) return@Canvas

        val availableWidth = size.width
        val availableHeight = size.height

        // Calcular dimensões das barras
        val totalBars = amplitudes.size
        val barSlotWidth = availableWidth / totalBars.coerceAtLeast(1)

        val actualBarWidth = config.barWidth?.toPx()
            ?: (barSlotWidth * 0.8f)  // 80% do slot se não especificado

        // Posição Y da baseline
        val baselineY = when (config.baseline) {
            AudioWaveFormsConfig.Baseline.Top -> 0f
            AudioWaveFormsConfig.Baseline.Center -> availableHeight / 2f
            AudioWaveFormsConfig.Baseline.Bottom -> availableHeight
        }

        // OTIMIZAÇÃO: Batch drawing
        // Ao invés de chamar drawRoundRect N vezes (o que causa N draw calls na GPU),
        // criamos um único Path com todos os retângulos e mandamos pra GPU de uma vez só.
        val wavePath = androidx.compose.ui.graphics.Path()

        // Desenhar cada barra no Path
        amplitudes.forEachIndexed { index, amplitude ->
            val x = index * barSlotWidth + (barSlotWidth - actualBarWidth) / 2

            // Normalizar amplitude com altura mínima
            val normalizedAmp = amplitude
                .coerceAtLeast(config.minHeight)
                .coerceAtMost(config.maxHeight)

            val barHeight = normalizedAmp * availableHeight * animatedProgress.value

            // Calcular posição Y e altura final baseado na baseline
            val (y, finalBarHeight) = when (config.baseline) {
                AudioWaveFormsConfig.Baseline.Top -> {
                    baselineY to barHeight
                }
                AudioWaveFormsConfig.Baseline.Center -> {
                    baselineY - barHeight / 2f to barHeight
                }
                AudioWaveFormsConfig.Baseline.Bottom -> {
                    baselineY - barHeight to barHeight
                }
            }

            // Adicionar ao Path em lote
            wavePath.addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = x,
                    top = y,
                    right = x + actualBarWidth,
                    bottom = y + finalBarHeight,
                    cornerRadius = CornerRadius(
                        config.barCornerRadius.toPx(),
                        config.barCornerRadius.toPx()
                    )
                )
            )
        }

        // Fazer UM único draw call pra toda a waveform!
        if (config.gradient != null) {
            drawPath(
                path = wavePath,
                brush = config.gradient
            )
        } else {
            drawPath(
                path = wavePath,
                color = barColor
            )
        }
    }
}
