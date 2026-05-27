package com.chopcut

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import timber.log.Timber


// --- Merged from AudioWaveForms.kt ---


/**
 * Componente de visualização de áudio com barras verticais
 *
 * Utiliza os dados extraídos pelo WaveformExtractor para renderizar
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

// --- Merged from AudioWaveFormsConfig.kt ---


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

// --- Merged from WaveForm.kt ---


/**
 * Visualizador de Waveform com efeitos neon e animação
 *
 * Estilo osciloscópio com gradiente dark, glow e cores vibrantes
 *
 * @param amplitudes Lista de amplitudes do áudio (0.0 a 1.0)
 * @param maxAmp Amplitude máxima para normalização
 * @param avgAmp Amplitude média para cálculo de thresholds
 * @param mirrored Se true, espelha verticalmente (centrado)
 * @param modifier Modificador
 */
@Composable
fun WaveForm(
    amplitudes: FloatArray,
    maxAmp: Float = 0.5f,
    avgAmp: Float = 0.1f,
    style: WaveformStyle = WaveformStyle(),
    modifier: Modifier = Modifier
) {
    if (amplitudes.isEmpty()) return

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(amplitudes) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600)
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
    ) {
        if (amplitudes.isEmpty()) return@Canvas

        val path = androidx.compose.ui.graphics.Path()
        val barWidth = size.width / amplitudes.size.coerceAtLeast(1)
        
        val centerY = when (style.baseline) {
            WaveformStyle.Baseline.Top -> 0f
            WaveformStyle.Baseline.Center -> size.height / 2f
            WaveformStyle.Baseline.Bottom -> size.height
        }
        
        val maxAvailableHeight = when (style.baseline) {
            WaveformStyle.Baseline.Center -> size.height / 2f
            else -> size.height
        }

        path.moveTo(0f, centerY)

        amplitudes.forEachIndexed { index, amp ->
            val normalizedAmp = if (maxAmp > 0) (amp / maxAmp).coerceIn(0f, 1f) else 0f
            val value = normalizedAmp * animatedProgress.value * style.heightScale
            
            val x = index * barWidth
            
            // Y is offset from baseline based on baseline position
            // Center: Subtract value * height (goes up)
            // Bottom: Subtract value * height (goes up)
            // Top: Add value * height (goes down)
            
            val yOffset = value * maxAvailableHeight
            val y = if (style.baseline == WaveformStyle.Baseline.Top) {
                centerY + yOffset
            } else {
                centerY - yOffset
            }
            
            if (index == 0) {
                path.lineTo(x, y)
            } else {
                 if (style.isSmoothed) {
                    val prevX = (index - 1) * barWidth
                    val prevAmp = (if (maxAmp > 0) (amplitudes[index - 1] / maxAmp).coerceIn(0f, 1f) else 0f) * animatedProgress.value * style.heightScale
                    val prevYOffset = prevAmp * maxAvailableHeight
                    val prevY = if (style.baseline == WaveformStyle.Baseline.Top) {
                        centerY + prevYOffset
                    } else {
                        centerY - prevYOffset
                    }
                    
                    val midX = (prevX + x) / 2
                    val midY = (prevY + y) / 2
                    path.quadraticTo(prevX, prevY, midX, midY)
                 } else {
                     path.lineTo(x, y)
                 }
            }
        }
        
        // Final point
        val lastX = amplitudes.size * barWidth
        path.lineTo(lastX, centerY) // Return to baseline if loop? Or just lineTo last point?
        // To fill or close, we usually return to baseline.
        // For a line graph, we don't necessarily close. But path.close() closes to start.
        
        // If filled, close the path to baseline
        if (style.style == WaveformStyle.Style.Filled) {
            path.lineTo(lastX, centerY)
            path.lineTo(0f, centerY)
            path.close()
            
            drawPath(
                path = path,
                color = style.color.copy(alpha = 0.5f),
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
            // Also draw stroke on top?
            drawPath(
                path = path,
                color = style.color,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = style.strokeWidth.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        } else {
            // Line Style
            drawPath(
                path = path,
                color = style.color,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = style.strokeWidth.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        }

        // Add mirrored part if requested
        if (style.isMirrored) {
            val mirrorPath = androidx.compose.ui.graphics.Path()
            mirrorPath.moveTo(0f, centerY)
            
            amplitudes.forEachIndexed { index, amp ->
                val normalizedAmp = if (maxAmp > 0) (amp / maxAmp).coerceIn(0f, 1f) else 0f
                val value = normalizedAmp * animatedProgress.value * style.heightScale
                val x = index * barWidth
                val yOffset = value * maxAvailableHeight
                
                 val y = if (style.baseline == WaveformStyle.Baseline.Top) {
                    centerY - yOffset // Invert for mirror
                } else {
                    centerY + yOffset // Invert for mirror (down if baseline is center/bottom but center usually mirrors down)
                }

                if (index == 0) {
                    mirrorPath.lineTo(x, y)
                } else {
                    if (style.isSmoothed) {
                        val prevX = (index - 1) * barWidth
                        val prevAmp = (if (maxAmp > 0) (amplitudes[index - 1] / maxAmp).coerceIn(0f, 1f) else 0f) * animatedProgress.value * style.heightScale
                        val prevYOffset = prevAmp * maxAvailableHeight
                        val prevY = if (style.baseline == WaveformStyle.Baseline.Top) centerY - prevYOffset else centerY + prevYOffset
                        
                        val midX = (prevX + x) / 2
                        val midY = (prevY + y) / 2
                        mirrorPath.quadraticTo(prevX, prevY, midX, midY)
                    } else {
                        mirrorPath.lineTo(x, y)
                    }
                }
            }
            mirrorPath.lineTo(lastX, centerY)
            if (style.style == WaveformStyle.Style.Filled) {
                mirrorPath.lineTo(0f, centerY)
                mirrorPath.close()
                drawPath(
                    path = mirrorPath, 
                    color = style.color.copy(alpha = 0.3f), 
                    style = Fill
                )
            }
            drawPath(
                path = mirrorPath,
                color = style.color.copy(alpha = 0.6f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = style.strokeWidth.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        }
    }
}



// --- Merged from WaveformConfigPanel.kt ---


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveformConfigPanel(
    currentStyle: WaveformStyle,
    onStyleChange: (WaveformStyle) -> Unit,
    modifier: Modifier = Modifier,
    onApply: () -> Unit = {}
) {
    var tempStyle by remember(currentStyle) { mutableStateOf(currentStyle) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Wave Config:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

            // Mirrored Toggle
            FilterChip(
                selected = tempStyle.isMirrored,
                onClick = { tempStyle = tempStyle.copy(isMirrored = !tempStyle.isMirrored) },
                label = { Text("Espelhado") }
            )

            // Smoothed Toggle
            FilterChip(
                selected = tempStyle.isSmoothed,
                onClick = { tempStyle = tempStyle.copy(isSmoothed = !tempStyle.isSmoothed) },
                label = { Text("Suavizado") }
            )

            // Height Scale
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Escala: ${(tempStyle.heightScale * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = tempStyle.heightScale,
                    onValueChange = { tempStyle = tempStyle.copy(heightScale = it) },
                    valueRange = 0.1f..3.0f,
                    modifier = Modifier.width(100.dp)
                )
            }

            // Stroke Width
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                 Text("Traço: ${tempStyle.strokeWidth.value.toInt()}dp", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = tempStyle.strokeWidth.value,
                    onValueChange = { tempStyle = tempStyle.copy(strokeWidth = it.dp) },
                    valueRange = 0.5f..5.0f,
                    modifier = Modifier.width(100.dp)
                )
            }
            
            // Baseline
            Column {
                Text("Alinhamento", style = MaterialTheme.typography.labelSmall)
                Row {
                    WaveformStyle.Baseline.values().forEach { baseline ->
                        IconToggleButton(
                            checked = tempStyle.baseline == baseline,
                            onCheckedChange = { tempStyle = tempStyle.copy(baseline = baseline) }
                        ) {
                            Text(baseline.name.take(1), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    }
                }
            }

            // APPLY BUTTON
            Button(
                onClick = { 
                    onStyleChange(tempStyle)
                    onApply() 
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("APLICAR")
            }
        }
    }
}

// --- Merged from WaveformRenderer.kt ---


@Composable
fun WaveformRenderer(
    amplitudes: FloatArray,
    modifier: Modifier = Modifier,
    barWidth: Dp = 2.5.dp,
    barGap: Dp = 1.dp,
    minHeight: Dp = 1.5.dp,
    color: Color = Color.White.copy(alpha = 0.55f),
    mirrored: Boolean = true,
    baseline: WaveformBaseline = WaveformBaseline.Center,
    animate: Boolean = true,
    showSkeleton: Boolean = false
) {
    Timber.d("WaveformRenderer: called with ${amplitudes.size} amplitudes, showSkeleton=$showSkeleton")
    if (amplitudes.isEmpty() && !showSkeleton) {
        Timber.w("WaveformRenderer: amplitudes is empty and showSkeleton is false, returning")
        return
    }

    val animatedProgress = remember(animate) { 
        if (animate) Animatable(0f) else null 
    }

    LaunchedEffect(amplitudes, animate) {
        if (animate && animatedProgress != null) {
            animatedProgress.snapTo(0f)
            animatedProgress.animateTo(1f, tween(durationMillis = 600))
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
        ) {
            if (amplitudes.isEmpty()) {
                if (showSkeleton) {
                    drawSkeletonWaveform()
                }
                return@Canvas
            }

            drawWaveformBars(
                amplitudes = amplitudes,
                progress = animatedProgress?.value ?: 1f,
                barWidth = barWidth.toPx(),
                barGap = barGap.toPx(),
                minHeight = minHeight.toPx(),
                color = color,
                mirrored = mirrored,
                baseline = baseline
            )
        }
    }
}

@Composable
fun WaveformSkeleton(
    modifier: Modifier = Modifier,
    height: Dp = 40.dp
) {
    val shimmer = remember { Animatable(0.04f) }
    
    LaunchedEffect(Unit) {
        shimmer.animateTo(0.18f, tween(durationMillis = 800))
        while (true) {
            shimmer.animateTo(0.04f, tween(durationMillis = 800))
            shimmer.animateTo(0.18f, tween(durationMillis = 800))
        }
    }

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        drawSkeletonWaveform()
    }
}

private fun DrawScope.drawWaveformBars(
    amplitudes: FloatArray,
    progress: Float,
    barWidth: Float,
    barGap: Float,
    minHeight: Float,
    color: Color,
    mirrored: Boolean,
    baseline: WaveformBaseline
) {
    Timber.d("drawWaveformBars: amplitudes.size=${amplitudes.size}, size=$size, progress=$progress")
    val step = barWidth + barGap
    val visibleBars = (size.width / step).toInt().coerceAtLeast(1)
    if (visibleBars == 0) {
        Timber.w("drawWaveformBars: visibleBars is 0, returning")
        return
    }

    val peak = calculatePeak(amplitudes)
    val normFactor = if (peak > 0.05f) peak else 1f

    val centerY = when (baseline) {
        WaveformBaseline.Top -> 0f
        WaveformBaseline.Center -> size.height / 2f
        WaveformBaseline.Bottom -> size.height
    }

    val halfHeight = when (baseline) {
        WaveformBaseline.Center -> size.height / 2f
        else -> size.height
    }

    Timber.d("drawWaveformBars: size.width=${size.width}, size.height=${size.height}, centerY=$centerY, halfHeight=$halfHeight")

    var x = 0f
    var idx = 0
    var barsDrawn = 0

    while (x < size.width && idx < amplitudes.size) {
        val amp = amplitudes[idx]
        val normalized = (amp / normFactor).coerceIn(0f, 1f)
        val boosted = kotlin.math.sqrt(normalized.toDouble()).toFloat()
        val barHeight = (boosted * progress * halfHeight).coerceAtLeast(minHeight)
        barsDrawn++

        val topY = when (baseline) {
            WaveformBaseline.Top -> centerY
            WaveformBaseline.Center -> centerY - barHeight
            WaveformBaseline.Bottom -> centerY - barHeight
        }

        drawRoundRect(
            color = color,
            topLeft = Offset(x, topY),
            size = Size(barWidth, barHeight * (if (mirrored && baseline == WaveformBaseline.Center) 2f else 1f)),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        )

        if (mirrored && baseline == WaveformBaseline.Center && x + barWidth <= size.width) {
            drawRoundRect(
                color = color,
                topLeft = Offset(x, centerY),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }

        x += step
        idx = (idx + amplitudes.size / visibleBars).coerceAtMost(amplitudes.size - 1)
    }
}

private fun DrawScope.drawSkeletonWaveform() {
    val step = 4f
    val barWidth = 2f
    val minHeight = 2f
    val centerY = size.height / 2f
    var x = 0f

    while (x < size.width) {
        val height = minHeight + (kotlin.math.sin(x * 0.1).toFloat() + 1f) * minHeight
        drawRoundRect(
            color = Color.White.copy(alpha = 0.1f),
            topLeft = Offset(x, centerY - height),
            size = Size(barWidth, height * 2f),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        )
        x += step
    }
}

private fun calculatePeak(amplitudes: FloatArray): Float {
    if (amplitudes.isEmpty()) return 0.01f
    var max = 0.01f
    for (amp in amplitudes) {
        if (amp > max) max = amp
    }
    return max
}

enum class WaveformBaseline {
    Top, Center, Bottom
}
// --- Merged from WaveformStyle.kt ---


data class WaveformStyle(
    val style: Style = Style.Line,
    val strokeWidth: Dp = 1.5.dp,
    val color: Color = Color(0xFF00D9FF), // Cyan Neon
    val isMirrored: Boolean = false,
    val isSmoothed: Boolean = true,
    val heightScale: Float = 0.8f,
    val baseline: Baseline = Baseline.Center
) {
    enum class Style {
        Line,
        Filled,
        Bars
    }

    enum class Baseline {
        Top,
        Center,
        Bottom
    }
}
