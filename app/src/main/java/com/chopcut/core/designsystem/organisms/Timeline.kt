package com.chopcut.core.designsystem.organisms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chopcut.core.designsystem.theme.ChopCutTheme
import com.chopcut.core.designsystem.tokens.ColorTokens
import com.chopcut.core.designsystem.tokens.SizeTokens
import com.chopcut.core.designsystem.tokens.SpacingTokens
import kotlin.math.roundToInt

/**
 * Representa um range/segmento na timeline.
 */
data class TimelineRange(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val color: Color? = null
) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * Timeline simplificada - apenas playhead, ranges e scrolling.
 *
 * @param currentTimeMs Posição atual do playhead em ms
 * @param durationMs Duração total do vídeo em ms
 * @param ranges Lista de ranges a exibir
 * @param onTimeChange Callback quando o usuário arrasta/scrola (novo tempo em ms)
 * @param onRangeClick Callback quando um range é clicado
 * @param modifier Modifier para customização
 * @param pixelsPerSecond Escala da timeline (default: 60dp/segundo)
 */
@Composable
fun SimpleTimeline(
    currentTimeMs: Long,
    durationMs: Long,
    ranges: List<TimelineRange>,
    onTimeChange: (Long) -> Unit,
    onRangeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    pixelsPerSecond: Dp = 60.dp
) {
    val density = LocalDensity.current
    val pxPerSecond = with(density) { pixelsPerSecond.toPx() }
    val timelineHeight = SizeTokens.timelineHeight

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(timelineHeight)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val width = constraints.maxWidth.toFloat()
        val centerX = width / 2f

        // Calcula o scroll baseado no tempo atual
        val scrollX = remember(currentTimeMs, pxPerSecond) {
            (currentTimeMs / 1000f) * pxPerSecond
        }

        // Estado para drag
        var dragStartX by remember { mutableFloatStateOf(0f) }
        var dragStartTime by remember { mutableFloatStateOf(0f) }

        val draggableState = rememberDraggableState { delta ->
            val newScrollX = (scrollX - delta).coerceIn(0f, (durationMs / 1000f) * pxPerSecond)
            val newTimeMs = ((newScrollX / pxPerSecond) * 1000).roundToInt().toLong()
            onTimeChange(newTimeMs)
        }

        // Canvas da timeline
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal
                )
        ) {
            // Desenha as marcas de tempo
            drawTimelineTicks(
                scrollX = scrollX,
                centerX = centerX,
                pxPerSecond = pxPerSecond,
                durationMs = durationMs
            )

            // Desenha os ranges
            ranges.forEach { range ->
                drawRange(
                    range = range,
                    scrollX = scrollX,
                    centerX = centerX,
                    pxPerSecond = pxPerSecond,
                    timelineHeight = timelineHeight.toPx()
                )
            }
        }

        // Playhead (linha central fixa)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.error)
        )

        // Handle do playhead (triângulo no topo)
        PlayheadHandle(
            modifier = Modifier.align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * Versão ainda mais minimalista - só playhead e ticks.
 */
@Composable
fun MinimalTimeline(
    currentTimeMs: Long,
    durationMs: Long,
    onTimeChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    pixelsPerSecond: Dp = 60.dp
) {
    SimpleTimeline(
        currentTimeMs = currentTimeMs,
        durationMs = durationMs,
        ranges = emptyList(),
        onTimeChange = onTimeChange,
        onRangeClick = {},
        modifier = modifier,
        pixelsPerSecond = pixelsPerSecond
    )
}

// ============================================================================
// FUNÇÕES DE DESENHO
// ============================================================================

private fun DrawScope.drawTimelineTicks(
    scrollX: Float,
    centerX: Float,
    pxPerSecond: Float,
    durationMs: Long
) {
    val tickColor = ColorTokens.timelineText.copy(alpha = 0.5f)
    val majorTickColor = ColorTokens.timelineText
    val majorHeight = 16.dp.toPx()
    val minorHeight = 8.dp.toPx()

    val startVisibleSec = ((scrollX - centerX) / pxPerSecond).toInt() - 1
    val endVisibleSec = ((scrollX + centerX) / pxPerSecond).toInt() + 2
    val durationSec = (durationMs / 1000).toInt()

    for (sec in startVisibleSec..endVisibleSec) {
        if (sec < 0 || sec > durationSec) continue

        val x = centerX + (sec * pxPerSecond) - scrollX
        val isMajor = sec % 5 == 0

        drawLine(
            color = if (isMajor) majorTickColor else tickColor,
            start = Offset(x, 0f),
            end = Offset(x, if (isMajor) majorHeight else minorHeight),
            strokeWidth = if (isMajor) 2f else 1f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawRange(
    range: TimelineRange,
    scrollX: Float,
    centerX: Float,
    pxPerSecond: Float,
    timelineHeight: Float
) {
    val rangeStartX = centerX + (range.startMs / 1000f) * pxPerSecond - scrollX
    val rangeEndX = centerX + (range.endMs / 1000f) * pxPerSecond - scrollX
    val rangeWidth = rangeEndX - rangeStartX

    // Só desenha se está visível
    if (rangeEndX < 0 || rangeStartX > size.width) return

    val color = range.color ?: ColorTokens.timelineWaveform
    val clampedStart = rangeStartX.coerceIn(0f, size.width)
    val clampedEnd = rangeEndX.coerceIn(0f, size.width)
    val clampedWidth = clampedEnd - clampedStart

    // Background do range
    drawRect(
        color = color.copy(alpha = 0.3f),
        topLeft = Offset(clampedStart, 20.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(clampedWidth, timelineHeight - 40.dp.toPx())
    )

    // Bordas
    drawLine(
        color = color,
        start = Offset(clampedStart, 20.dp.toPx()),
        end = Offset(clampedStart, timelineHeight - 20.dp.toPx()),
        strokeWidth = 3.dp.toPx()
    )
    drawLine(
        color = color,
        start = Offset(clampedEnd, 20.dp.toPx()),
        end = Offset(clampedEnd, timelineHeight - 20.dp.toPx()),
        strokeWidth = 3.dp.toPx()
    )
}

@Composable
private fun PlayheadHandle(
    modifier: Modifier = Modifier,
    color: Color
) {
    Canvas(
        modifier = modifier
            .width(12.dp)
            .height(8.dp)
    ) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width / 2, size.height)
            close()
        }
        drawPath(path, color)
    }
}

// ============================================================================
// PREVIEWS
// ============================================================================

@Preview(showBackground = true, widthDp = 400, heightDp = 120)
@Composable
private fun SimpleTimelinePreview() {
    ChopCutTheme {
        SimpleTimeline(
            currentTimeMs = 5000,
            durationMs = 60000,
            ranges = listOf(
                TimelineRange("1", 2000, 8000, ColorTokens.timelineWaveform),
                TimelineRange("2", 15000, 25000)
            ),
            onTimeChange = {},
            onRangeClick = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 120)
@Composable
private fun MinimalTimelinePreview() {
    ChopCutTheme {
        MinimalTimeline(
            currentTimeMs = 3000,
            durationMs = 30000,
            onTimeChange = {}
        )
    }
}
