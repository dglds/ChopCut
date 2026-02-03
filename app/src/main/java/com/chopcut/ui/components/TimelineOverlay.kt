package com.chopcut.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TimelineOverlay(
    ranges: List<Pair<Long, Long>>,
    draftStart: Long?,
    currentPosition: Long,
    pxPerSecond: Float,
    scrollOffsetPx: Float,
    timelineWidth: Float,
    modifier: Modifier = Modifier
) {
    val draftColor = Color(0xFF2196F3)  // Azul para draft
    val rangeColor = Color(0xFFE91E63)    // Rosa para ranges confirmados

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerOffset = timelineWidth / 2f

        fun timeToX(timeMs: Long): Float {
            return (timeMs / 1000f) * pxPerSecond - scrollOffsetPx + centerOffset
        }

        // Desenha ranges confirmados (rosa)
        ranges.forEach { (start, end) ->
            val startX = timeToX(start)
            val endX = timeToX(end)
            if (endX >= 0 && startX <= size.width) {
                drawLine(
                    color = rangeColor,
                    start = Offset(startX, 0f),
                    end = Offset(startX, size.height),
                    strokeWidth = 3.dp.toPx()
                )
                drawLine(
                    color = rangeColor,
                    start = Offset(endX, 0f),
                    end = Offset(endX, size.height),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }

        // Desenha draft start (azul)
        draftStart?.let { start ->
            val startX = timeToX(start)
            if (startX >= 0 && startX <= size.width) {
                drawLine(
                    color = draftColor,
                    start = Offset(startX, 0f),
                    end = Offset(startX, size.height),
                    strokeWidth = 4.dp.toPx()
                )
            }
        }
    }
}
