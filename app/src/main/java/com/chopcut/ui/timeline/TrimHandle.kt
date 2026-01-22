package com.chopcut.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Handle customizável para seleção de intervalo (Trim).
 */
@Composable
fun TrimHandle(
    positionPx: Float,
    onPositionChanged: (Float) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    isStart: Boolean,
    color: Color = MaterialTheme.colorScheme.primary,
    width: Dp = 16.dp
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(positionPx.roundToInt(), 0) }
            .width(width)
            .fillMaxHeight()
            .clip(
                if (isStart) RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                else RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
            )
            .background(color)
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
            },
        contentAlignment = Alignment.Center
    ) {
        // Detalhe visual (dois tracinhos brancos)
        Box(
            modifier = Modifier
                .size(2.dp, 20.dp)
                .offset(x = (-2).dp)
                .background(Color.White.copy(alpha = 0.5f))
        )
        Box(
            modifier = Modifier
                .size(2.dp, 20.dp)
                .offset(x = 2.dp)
                .background(Color.White.copy(alpha = 0.5f))
        )
    }
}
