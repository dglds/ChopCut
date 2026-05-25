package com.chopcut.ui.components.waveform

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import timber.log.Timber

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