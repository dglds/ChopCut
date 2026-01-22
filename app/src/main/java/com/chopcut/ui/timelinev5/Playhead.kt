package com.chopcut.ui.timelinev5

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Componente de Playhead (Scrubber) que pode ser arrastado.
 */
@Composable
fun Playhead(
    positionPx: Float,
    onPositionChanged: (Float) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    color: Color = Color.Red,
    width: Dp = 2.dp
) {
    Canvas(
        modifier = Modifier
            .offset { IntOffset(positionPx.roundToInt(), 0) }
            .width(width)
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onPositionChanged(positionPx + dragAmount.x)
                    }
                )
            }
    ) {
        // Linha vertical
        drawLine(
            color = color,
            start = Offset(size.width / 2, 0f),
            end = Offset(size.width / 2, size.height),
            strokeWidth = width.toPx()
        )

        // Triângulo no topo
        val triangleWidth = 10.dp.toPx()
        val triangleHeight = 10.dp.toPx()
        val path = Path().apply {
            moveTo(size.width / 2 - triangleWidth / 2, 0f)
            lineTo(size.width / 2 + triangleWidth / 2, 0f)
            lineTo(size.width / 2, triangleHeight)
            close()
        }
        drawPath(path, color)
    }
}