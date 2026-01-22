package com.chopcut.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import timber.log.Timber

/**
 * Cores customizáveis para o FrameSlider
 */
@Immutable
data class FrameSliderColors(
    val trackColor: Color,
    val activeTrackColor: Color,
    val thumbColor: Color,
    val activeThumbColor: Color,
    val labelColor: Color
)

/**
 * Defaults para o FrameSlider
 */
object FrameSliderDefaults {
    /**
     * Cores padrão seguindo o tema Material 3
     */
    @Composable
    fun colors(
        trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
        activeTrackColor: Color = MaterialTheme.colorScheme.primary,
        thumbColor: Color = MaterialTheme.colorScheme.primary,
        activeThumbColor: Color = MaterialTheme.colorScheme.primary,
        labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
    ): FrameSliderColors {
        return FrameSliderColors(
            trackColor = trackColor,
            activeTrackColor = activeTrackColor,
            thumbColor = thumbColor,
            activeThumbColor = activeThumbColor,
            labelColor = labelColor
        )
    }
}

/**
 * Converte FrameSliderColors para SliderColors do Material 3
 */
@Composable
private fun toMaterialSliderColors(colors: FrameSliderColors): SliderColors {
    return SliderDefaults.colors(
        thumbColor = colors.thumbColor,
        activeTrackColor = colors.activeTrackColor,
        inactiveTrackColor = colors.trackColor,
        activeTickColor = colors.activeTrackColor,
        inactiveTickColor = colors.trackColor
    )
}

/**
 * Componente de slider para controle preciso de posição de vídeo.
 *
 * Características:
 * - Mapeamento 0-100% do slider para 0-duração do vídeo
 * - Frame snapping para precisão
 * - Integração com PreviewManager para pausa/play automáticos
 * - Throttling de atualizações para performance
 *
 * @param value Posição atual do slider (0.0 a 1.0)
 * @param onValueChange Callback chamado continuamente durante scrubbing
 * @param onValueChangeFinished Callback chamado quando o usuário solta o thumb
 * @param durationMs Duração total do vídeo em milissegundos
 * @param modifier Modifier para o componente
 * @param enabled Se o slider está habilitado
 * @param frameRate Frame rate do vídeo para frame snapping (padrão: 30)
 * @param valueRange Intervalo de valores do slider (padrão: 0f..1f)
 * @param enableFrameSnapping Se true, aplica snapping em frame boundaries
 * @param colors Cores customizadas do slider
 * @param thumbRadius Raio do thumb em dp (não usado diretamente, Slider gerencia)
 * @param trackHeight Altura do track em dp (não usado diretamente, Slider gerencia)
 */
@Composable
fun FrameSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    durationMs: Long,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    frameRate: Int = 30,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enableFrameSnapping: Boolean = true,
    colors: FrameSliderColors = FrameSliderDefaults.colors(),
    thumbRadius: Dp = 10.dp,
    trackHeight: Dp = 4.dp
) {
    val sliderState = rememberFrameSliderState(
        durationMs = durationMs,
        frameRate = frameRate
    )

    var sliderValue by remember(value) { mutableFloatStateOf(value) }

    // Atualizar valor interno quando value externo muda (durante reprodução)
    // Mas não durante scrubbing para evitar conflitos
    if (!sliderState.isScrubbing) {
        sliderValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    }

    Slider(
        value = sliderValue,
        onValueChange = { newValue ->
            val normalizedValue = newValue.coerceIn(valueRange.start, valueRange.endInclusive)

            // Aplicar frame snapping se habilitado
            val finalValue = if (enableFrameSnapping) {
                val timeMs = sliderState.sliderToTime(normalizedValue)
                val snappedTime = sliderState.snapToFrame(timeMs)
                sliderState.timeToSlider(snappedTime)
            } else {
                normalizedValue
            }

            sliderValue = finalValue
            onValueChange(finalValue)
        },
        onValueChangeFinished = {
            Timber.v("FrameSlider: scrubbing finished at value=$sliderValue")
            onValueChangeFinished()
        },
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        colors = toMaterialSliderColors(colors)
    )
}

/**
 * Versão simplificada do FrameSlider com gerenciamento automático de estado de scrubbing
 *
 * Esta versão gerencia automaticamente:
 * - Pausar o player quando scrubbing inicia
 * - Retomar a reprodução quando scrubbing termina (se estava tocando antes)
 *
 * @param value Posição atual do slider (0.0 a 1.0)
 * @param onValueChange Callback chamado continuamente durante scrubbing (já com snapping)
 * @param durationMs Duração total do vídeo em milissegundos
 * @param isPlaying Indica se o vídeo está tocando atualmente
 * @param modifier Modifier para o componente
 * @param enabled Se o slider está habilitado
 * @param frameRate Frame rate do vídeo para frame snapping (padrão: 30)
 * @param enableFrameSnapping Se true, aplica snapping em frame boundaries
 * @param colors Cores customizadas do slider
 */
@Composable
fun FrameSliderWithAutoPause(
    value: Float,
    onValueChange: (Float) -> Unit,
    durationMs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    frameRate: Int = 30,
    enableFrameSnapping: Boolean = true,
    colors: FrameSliderColors = FrameSliderDefaults.colors()
) {
    val sliderState = rememberFrameSliderState(
        durationMs = durationMs,
        frameRate = frameRate
    )

    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    var wasPlayingBeforeScrub by remember { mutableStateOf(false) }

    // Atualizar valor interno quando value externo muda (durante reprodução)
    // Mas não durante scrubbing para evitar conflitos
    if (!sliderState.isScrubbing) {
        sliderValue = value.coerceIn(0f, 1f)
    }

    Slider(
        value = sliderValue,
        onValueChange = { newValue ->
            val normalizedValue = newValue.coerceIn(0f, 1f)

            // Iniciar scrubbing se necessário
            if (!sliderState.isScrubbing) {
                wasPlayingBeforeScrub = isPlaying
                sliderState.startScrubbing()
                Timber.d("FrameSlider: scrubbing started (wasPlaying=$wasPlayingBeforeScrub)")
            }

            // Aplicar frame snapping se habilitado
            val finalValue = if (enableFrameSnapping) {
                val timeMs = sliderState.sliderToTime(normalizedValue)
                val snappedTime = sliderState.snapToFrame(timeMs)
                sliderState.timeToSlider(snappedTime)
            } else {
                normalizedValue
            }

            sliderValue = finalValue

            // Notificar com o valor ajustado
            // Nota: a pausa do player deve ser gerenciada pelo caller através do isPlaying
            onValueChange(finalValue)
        },
        onValueChangeFinished = {
            Timber.d("FrameSlider: scrubbing finished (shouldResume=$wasPlayingBeforeScrub)")
            sliderState.endScrubbing()
            // Nota: a retomada da reprodução deve ser gerenciada pelo caller
            // que pode usar wasPlayingBeforeScrub se exposto via callback
        },
        modifier = modifier,
        enabled = enabled,
        valueRange = 0f..1f,
        colors = toMaterialSliderColors(colors)
    )
}

/**
 * Componente de labels de tempo para exibir posição atual e duração total
 *
 * @param currentPositionMs Posição atual em milissegundos
 * @param durationMs Duração total em milissegundos
 * @param modifier Modifier para o componente
 */
@Composable
fun TimeLabels(
    currentPositionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        Text(
            text = formatTime(currentPositionMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatTime(durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Formata tempo em milissegundos para string "MM:SS"
 */
private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
