package com.chopcut.ui.components.timeline

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chopcut.ui.components.waveform.WaveformBaseline
import com.chopcut.ui.components.waveform.WaveformRenderer

/**
 * Componente modular de visualização de áudio (Waveform) desacoplado da timeline.
 *
 * Renderiza os picos de amplitude do sinal de áudio do vídeo para feedback visual do usuário.
 *
 * @param amplitudes Array de floats representando a amplitude do áudio.
 * @param durationMs Duração total do vídeo em milissegundos.
 * @param modifier Modificador Compose.
 * @param height Altura desejada para o gráfico.
 * @param waveColor Cor customizada do waveform.
 */
@Composable
fun TimelineWaveform(
    amplitudes: FloatArray,
    durationMs: Long,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    waveColor: Color = Color.White.copy(alpha = 0.55f)
) {
    if (amplitudes.isEmpty()) return

    WaveformRenderer(
        amplitudes = amplitudes,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        barWidth = 2.5.dp,
        barGap = 1.dp,
        minHeight = 1.5.dp,
        color = waveColor,
        mirrored = true,
        baseline = WaveformBaseline.Center,
        animate = false
    )
}
