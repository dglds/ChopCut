package com.chopcut.ui.components.console

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun ScanlineEffect(
    modifier: Modifier = Modifier,
    scanlineColor: Color = Color.Black,
    scanlineSpacing: Int = 2,
    scanlineAlpha: Float = 0.1f
) {
    Canvas(modifier = modifier) {
        val size = size
        val spacing = scanlineSpacing.dp.toPx()
        
        for (y in spacing.toInt() until size.height.toInt() step (spacing * 2).toInt()) {
            drawLine(
                color = scanlineColor.copy(alpha = scanlineAlpha),
                start = Offset(0f, y.toFloat()),
                end = Offset(size.width, y.toFloat()),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@Composable
fun CRTEffect(
    modifier: Modifier = Modifier,
    curvature: Float = 0.15f,
    scanlineIntensity: Float = 0.08f,
    vignetteIntensity: Float = 0.3f,
    glowColor: Color = Color(0xFF00FF00),
    glowIntensity: Float = 0.1f
) {
    Canvas(modifier = modifier) {
        val size = size
        val centerX = size.width / 2
        val centerY = size.height / 2
        val maxDist = kotlin.math.sqrt(centerX * centerX + centerY * centerY)
        
        withTransform({
            translate(left = -centerX, top = -centerY)
        }) {
            for (x in (-centerX).toInt()..centerX.toInt() step 2) {
                for (y in (-centerY).toInt()..centerY.toInt() step 2) {
                    val dist = kotlin.math.sqrt((x * x + y * y).toDouble())
                    val normalizedDist = dist / maxDist
                    
                    val scanline = if (y % 4 < 2) 1f - scanlineIntensity else 1f
                    val vignette = 1f - (normalizedDist * vignetteIntensity).toFloat()
                    val colorIntensity = scanline * vignette
                    
                    if (colorIntensity > 0.3f) {
                        drawCircle(
                            color = glowColor.copy(alpha = colorIntensity * glowIntensity),
                            radius = 1f,
                            center = Offset(x.toFloat(), y.toFloat())
                        )
                    }
                }
            }
        }
        
        val gradient = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = vignetteIntensity * 0.5f)
            ),
            center = Offset(centerX, centerY),
            radius = maxDist * 1.2f
        )
        drawRect(brush = gradient, size = size, blendMode = BlendMode.Multiply)
    }
}

@Composable
fun GlowEffect(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF00FF00),
    intensity: Float = 0.3f,
    blurRadius: Float = 8f
) {
    Canvas(modifier = modifier) {
        drawRect(
            color = color.copy(alpha = intensity * 0.1f),
            size = size,
            blendMode = BlendMode.Screen
        )
        
        drawRect(
            color = color.copy(alpha = intensity * 0.05f),
            size = size,
            style = Stroke(width = blurRadius.dp.toPx()),
            blendMode = BlendMode.Screen
        )
    }
}

@Composable
fun FlickerEffect(
    modifier: Modifier = Modifier,
    intensity: Float = 0.02f
) {
    val flicker = (Math.random() * intensity).toFloat()
    
    Canvas(modifier = modifier) {
        if (flicker > 0.01f) {
            drawRect(
                color = Color.White.copy(alpha = flicker * 0.1f),
                size = size,
                blendMode = BlendMode.Overlay
            )
        }
    }
}

fun getCRTColorOffset(y: Int, height: Int, intensity: Float = 2f): Float {
    val normalized = (y.toFloat() / height) * 2f - 1f
    return sin(normalized * PI.toFloat() * intensity) * 0.5f
}