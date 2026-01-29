package com.chopcut.core.designsystem.organisms

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chopcut.core.designsystem.theme.ChopCutTheme
import com.chopcut.core.designsystem.tokens.ColorTokens
import com.chopcut.core.designsystem.tokens.SizeTokens
import kotlin.math.roundToInt

/**
 * Timeline usando APENAS componentes Compose - sem Canvas!
 *
 * Vantagens:
 * - Mais fácil de manter
 * - Compose otimiza recomposition
 * - Melhor acessibilidade automática
 * - Menos código
 *
 * Desvantagens:
 * - Pode ter performance inferior com MUITOS ranges (>100)
 * - Menos controle sobre desenho pixel-perfect
 */
@Composable
fun ComposeTimeline(
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(SizeTokens.timelineHeight)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val width = constraints.maxWidth.toFloat()
        val centerX = width / 2f
        val scrollX = (currentTimeMs / 1000f) * pxPerSecond

        val draggableState = rememberDraggableState { delta ->
            val newScrollX = (scrollX - delta).coerceIn(0f, (durationMs / 1000f) * pxPerSecond)
            val newTimeMs = ((newScrollX / pxPerSecond) * 1000).roundToInt().toLong()
            onTimeChange(newTimeMs)
        }

        // Container draggable
        Box(
            modifier = Modifier
                .fillMaxSize()
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal
                )
        ) {
            // Ticks usando Row + Divider (substitui Canvas.drawLine)
            TimelineTicksCompose(
                scrollX = scrollX,
                centerX = centerX,
                pxPerSecond = pxPerSecond,
                durationMs = durationMs,
                width = width
            )

            // Ranges usando Box + offset (substitui Canvas.drawRect)
            ranges.forEach { range ->
                RangeBox(
                    range = range,
                    scrollX = scrollX,
                    centerX = centerX,
                    pxPerSecond = pxPerSecond,
                    onClick = { onRangeClick(range.id) }
                )
            }
        }

        // Playhead (já era Box, não Canvas)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.error)
        )

        PlayheadHandle(
            modifier = Modifier.align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * Ticks implementados com Row + Divider em vez de Canvas.drawLine
 */
@Composable
private fun TimelineTicksCompose(
    scrollX: Float,
    centerX: Float,
    pxPerSecond: Float,
    durationMs: Long,
    width: Float
) {
    val startVisibleSec = ((scrollX - centerX) / pxPerSecond).toInt() - 1
    val endVisibleSec = ((scrollX + centerX) / pxPerSecond).toInt() + 2
    val durationSec = (durationMs / 1000).toInt()

    // Container absoluto para posicionar ticks
    Box(modifier = Modifier.fillMaxSize()) {
        for (sec in startVisibleSec..endVisibleSec) {
            if (sec < 0 || sec > durationSec) continue

            val x = centerX + (sec * pxPerSecond) - scrollX
            val isMajor = sec % 5 == 0

            // Tick como Divider vertical
            Divider(
                modifier = Modifier
                    .offset(x = x.dp, y = 0.dp)
                    .width(if (isMajor) 2.dp else 1.dp)
                    .height(if (isMajor) 16.dp else 8.dp)
                    .alpha(if (isMajor) 1f else 0.5f),
                color = if (isMajor) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
        }
    }
}

/**
 * Range implementado com Box em vez de Canvas.drawRect
 */
@Composable
private fun RangeBox(
    range: TimelineRange,
    scrollX: Float,
    centerX: Float,
    pxPerSecond: Float,
    onClick: () -> Unit
) {
    val rangeStartX = centerX + (range.startMs / 1000f) * pxPerSecond - scrollX
    val rangeEndX = centerX + (range.endMs / 1000f) * pxPerSecond - scrollX
    val rangeWidth = rangeEndX - rangeStartX

    // Só renderiza se visível
    if (rangeEndX < 0 || rangeStartX > centerX * 2) return

    val color = range.color ?: ColorTokens.timelineWaveform

    Box(
        modifier = Modifier
            .offset(x = rangeStartX.coerceAtLeast(0f).dp, y = 20.dp)
            .width(rangeWidth.coerceAtLeast(0f).dp)
            .height(SizeTokens.timelineHeight - 40.dp)
            .background(color.copy(alpha = 0.3f))
            .clickable(onClick = onClick)
    ) {
        // Borda esquerda
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(3.dp)
                .fillMaxHeight()
                .background(color)
        )
        // Borda direita
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(3.dp)
                .fillMaxHeight()
                .background(color)
        )
    }
}

@Composable
private fun ClickableBox(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.clickable(onClick = onClick),
        content = { content() }
    )
}

private fun Modifier.clickable(onClick: () -> Unit): Modifier = this.then(
    androidx.compose.foundation.clickable(onClick = onClick)
)

// ============================================================================
// COMPARAÇÃO: O QUE SUBSTITUÍMOS
// ============================================================================

/*
CANVAS (antes) → COMPOSE (agora)

1. drawLine() → Divider() ou Box() com width/height
   Antes: drawLine(color, start, end, strokeWidth)
   Agora: Divider(modifier = Modifier.width(2.dp).height(16.dp))

2. drawRect() → Box() com background()
   Antes: drawRect(color, topLeft, size)
   Agora: Box(modifier = Modifier.offset(x, y).width(w).height(h).background(color))

3. drawCircle() (textura de dots) → Pode ser:
   - Removida (não essencial)
   - Usar uma imagem de fundo repetida
   - Usar um gradiente simples

4. clipRect() → Modifier.clip() ou simplesmente não renderizar fora da tela
   Antes: clipRect { drawLine() }
   Agora: if (x in 0..width) { Divider() }

5. drawPath() (playhead triângulo) → Pode ser:
   - Icon triangular
   - Shape customizado
   - Manter Canvas só para isso (pequeno)

VANTAGENS DO COMPOSE:
- Recomposition inteligente (só o que mudou)
- Acessibilidade automática
- Animações fáceis (AnimatedVisibility, etc)
- Preview melhor
- Mais familiar para devs Android

QUANDO USAR CANVAS:
- Gráficos complexos (charts, plots)
- Waveforms de áudio (muitos pontos)
- Jogos/animacões 2D
- Quando precisa de performance máxima com muitos elementos (>1000)
*/

// ============================================================================
// PREVIEWS
// ============================================================================

@Preview(showBackground = true, widthDp = 400, heightDp = 120)
@Composable
private fun ComposeTimelinePreview() {
    ChopCutTheme {
        ComposeTimeline(
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
private fun ComparisonPreview() {
    ChopCutTheme {
        Column {
            // Versão Canvas
            SimpleTimeline(
                currentTimeMs = 5000,
                durationMs = 60000,
                ranges = listOf(TimelineRange("1", 2000, 8000)),
                onTimeChange = {},
                onRangeClick = {}
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Versão Compose
            ComposeTimeline(
                currentTimeMs = 5000,
                durationMs = 60000,
                ranges = listOf(TimelineRange("1", 2000, 8000)),
                onTimeChange = {},
                onRangeClick = {}
            )
        }
    }
}

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
