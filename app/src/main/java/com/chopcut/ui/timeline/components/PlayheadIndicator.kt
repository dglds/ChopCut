package com.chopcut.ui.timeline.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Indicador de playhead fixo no centro da timeline.
 *
 * Responsabilidades:
 * - Desenhar linha vertical no centro (posição atual do vídeo)
 * - Mostrar triângulo no topo indicando a posição
 * - Suportar estado de "relevo" (destaque visual durante criação de range)
 * - Animações suaves de transição entre estados
 *
 * @param isRelevo Se o playhead deve estar em destaque (alto relevo)
 * @param modifier Modifier para customização
 * @param color Cor padrão do playhead
 * @param relevoColor Cor quando em estado de relevo
 * @param width Largura da linha (padrão: 2.dp)
 * @param relevoWidth Largura quando em relevo (padrão: 4.dp)
 */
@Composable
fun PlayheadIndicator(
    isRelevo: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    relevoColor: Color = Color.Yellow,
    width: Dp = 2.dp,
    relevoWidth: Dp = 4.dp
) {
    // Animações
    val elevation by animateFloatAsState(
        targetValue = if (isRelevo) 8f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "elevation"
    )
    
    val lineWidth by animateDpAsState(
        targetValue = if (isRelevo) relevoWidth else width,
        animationSpec = tween(durationMillis = 200),
        label = "width"
    )
    
    val lineColor = if (isRelevo) relevoColor else color

    Box(
        modifier = modifier
            .width(lineWidth)
            .fillMaxHeight()
            .shadow(
                elevation = elevation.dp,
                shape = RectangleShape
            )
            .background(lineColor)
            .drawBehind {
                // Triângulo no topo do playhead
                val triangleWidth = 10.dp.toPx()
                val triangleHeight = 10.dp.toPx()
                
                val path = Path().apply {
                    moveTo(size.width / 2 - triangleWidth / 2, 0f)
                    lineTo(size.width / 2 + triangleWidth / 2, 0f)
                    lineTo(size.width / 2, triangleHeight)
                    close()
                }
                drawPath(path, lineColor)
            }
    )
}

/**
 * Indicador de playhead com suporte a drag manual.
 * Permite ao usuário arrastar o playhead para fazer seek.
 */
@Composable
fun PlayheadIndicatorDraggable(
    isRelevo: Boolean,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDrag: (deltaX: Float) -> Unit = {},
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    relevoColor: Color = Color.Yellow
) {
    val elevation by animateFloatAsState(
        targetValue = if (isRelevo) 8f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "elevation"
    )
    
    val lineWidth by animateDpAsState(
        targetValue = if (isRelevo) 4.dp else 2.dp,
        animationSpec = tween(durationMillis = 200),
        label = "width"
    )
    
    val lineColor = if (isRelevo) relevoColor else color

    Box(
        modifier = modifier
            .width(lineWidth)
            .fillMaxHeight()
            .shadow(elevation.dp, RectangleShape)
            .background(lineColor)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x)
                    }
                )
            }
            .drawBehind {
                // Triângulo no topo
                val triangleWidth = 10.dp.toPx()
                val triangleHeight = 10.dp.toPx()
                
                val path = Path().apply {
                    moveTo(size.width / 2 - triangleWidth / 2, 0f)
                    lineTo(size.width / 2 + triangleWidth / 2, 0f)
                    lineTo(size.width / 2, triangleHeight)
                    close()
                }
                drawPath(path, lineColor)
                
                // Círculo indicador no centro (opcional)
                drawCircle(
                    color = lineColor,
                    radius = 6.dp.toPx(),
                    center = Offset(size.width / 2, size.height / 2),
                    alpha = 0.3f
                )
            }
    )
}

/**
 * Versão minimalista do playhead (só linha).
 */
@Composable
fun PlayheadIndicatorMinimal(
    color: Color = MaterialTheme.colorScheme.primary,
    width: Dp = 2.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(color)
    )
}
