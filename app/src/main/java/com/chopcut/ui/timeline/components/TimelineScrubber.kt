package com.chopcut.ui.timeline.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chopcut.ui.timeline.util.ConfiguracaoTimeline
import kotlin.math.roundToInt

/**
 * Componente de scrubber de timeline (faixa scrollável).
 *
 * Responsabilidades:
 * - Renderizar a faixa de tempo scrollável
 * - Desenhar ticks de tempo (marcadores de segundos)
 * - Mostrar áreas "fora dos limites" do vídeo
 * - Detectar gestos de scroll e reportar posição
 * - Suportar efeito visual de "relevo" (sunken)
 *
 * @param durationMs Duração total do vídeo em milissegundos
 * @param positionMs Posição atual do playhead em milissegundos
 * @param onPositionChange Callback quando a posição muda (scroll)
 * @param onScrollStart Callback quando o scroll começa
 * @param onScrollEnd Callback quando o scroll termina
 * @param modifier Modifier para customização
 * @param height Altura do scrubber (default: 80.dp)
 */
@Composable
fun TimelineScrubber(
    durationMs: Long,
    positionMs: Long,
    onPositionChange: (Long) -> Unit,
    onScrollStart: () -> Unit = {},
    onScrollEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
    height: Dp = ConfiguracaoTimeline.ALTURA_FAIXA_DP
) {
    val density = LocalDensity.current
    val pxPorSegundo = with(density) { ConfiguracaoTimeline.PX_POR_SEGUNDO_DP.toPx() }
    
    // Estado de scroll em pixels
    var scrollOffsetPx by remember { mutableFloatStateOf(0f) }
    
    // Dimensões calculadas
    val durationSeconds = (durationMs / 1000f).coerceAtLeast(0f)
    val totalWidthPx = durationSeconds * pxPorSegundo
    
    // Estado scrollable
    val scrollableState = rememberScrollableState { delta ->
        val oldOffset = scrollOffsetPx
        val newOffset = (oldOffset - delta).coerceIn(0f, totalWidthPx.coerceAtLeast(0f))
        scrollOffsetPx = newOffset
        
        // Converte para tempo e notifica
        if (durationMs > 0) {
            val progress = newOffset / totalWidthPx
            val newTimeMs = (progress * durationMs).toLong()
            onPositionChange(newTimeMs)
        }
        
        oldOffset - newOffset // Retorna a quantidade consumida
    }
    
    // Detecta início/fim do scroll
    androidx.compose.runtime.LaunchedEffect(scrollableState.isScrollInProgress) {
        if (scrollableState.isScrollInProgress) {
            onScrollStart()
        } else {
            onScrollEnd()
        }
    }
    
    // Sincroniza scrollOffset com positionMs quando não está scrollando
    androidx.compose.runtime.LaunchedEffect(positionMs, durationMs) {
        if (!scrollableState.isScrollInProgress && durationMs > 0) {
            val progress = positionMs.toFloat() / durationMs
            scrollOffsetPx = progress * totalWidthPx
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .scrollable(
                orientation = Orientation.Horizontal,
                state = scrollableState
            )
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val centerOffset = containerWidth / 2f
        
        // Cores
        val corTickPrincipal = Color(0xFF424242)
        val corTickSecundario = Color(0xFF757575)
        val corTickMenor = Color(0xFF9E9E9E)
        val corFundoNeutro = Color(0xFFD6D6D6)
        val corListra = Color(0xFFBDBDBD)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val currentScroll = scrollOffsetPx
                    
                    val videoStartX = -currentScroll + centerOffset
                    val videoEndX = videoStartX + totalWidthPx
                    
                    // ===== EFEITO DE RELEVO (SUNKEN) =====
                    // Sombra superior
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.15f), Color.Transparent),
                            startY = 0f,
                            endY = size.height * 0.2f
                        )
                    )
                    // Sombra inferior
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.1f)),
                            startY = size.height * 0.8f,
                            endY = size.height
                        )
                    )

                    // ===== ÁREAS NEUTRAS (FORA DO VÍDEO) =====
                    // Área esquerda (antes do início do vídeo)
                    if (videoStartX > 0) {
                        drawRect(
                            color = corFundoNeutro,
                            topLeft = Offset(0f, 0f),
                            size = androidx.compose.ui.geometry.Size(videoStartX, size.height)
                        )
                        // Listras diagonais
                        desenharListras(
                            left = 0f,
                            right = videoStartX,
                            cor = corListra
                        )
                        // Divisor vertical
                        drawLine(
                            color = Color.Black.copy(alpha = 0.2f),
                            start = Offset(videoStartX, 0f),
                            end = Offset(videoStartX, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Área direita (após o fim do vídeo)
                    if (videoEndX < size.width) {
                        drawRect(
                            color = corFundoNeutro,
                            topLeft = Offset(videoEndX, 0f),
                            size = androidx.compose.ui.geometry.Size(size.width - videoEndX, size.height)
                        )
                        // Listras diagonais
                        desenharListras(
                            left = videoEndX,
                            right = size.width,
                            cor = corListra
                        )
                        // Divisor vertical
                        drawLine(
                            color = Color.Black.copy(alpha = 0.2f),
                            start = Offset(videoEndX, 0f),
                            end = Offset(videoEndX, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // ===== TICKS DE TEMPO =====
                    val tickAlturaMaior = size.height * 0.4f
                    val tickAlturaMenor = size.height * 0.25f
                    
                    // Calcula range visível em segundos
                    val inicioVisivelSec = ((0 - centerOffset + currentScroll) / pxPorSegundo).toInt()
                    val fimVisivelSec = ((size.width - centerOffset + currentScroll) / pxPorSegundo).toInt() + 1
                    
                    val duracaoSec = (durationMs / 1000).toInt()
                    val inicioLoop = inicioVisivelSec.coerceAtLeast(0)
                    val fimLoop = fimVisivelSec.coerceAtMost(duracaoSec + 1)

                    for (sec in inicioLoop..fimLoop) {
                        val xPos = (sec * pxPorSegundo) - currentScroll + centerOffset
                        
                        // Tick principal (cada segundo)
                        val isTickPrincipal = sec % 5 == 0
                        drawLine(
                            color = if (isTickPrincipal) corTickPrincipal else corTickSecundario,
                            start = Offset(xPos, 0f),
                            end = Offset(xPos, if (isTickPrincipal) tickAlturaMaior else tickAlturaMenor),
                            strokeWidth = if (isTickPrincipal) 2.dp.toPx() else 1.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        
                        // Tick menor (0.5s)
                        val xPosMeio = xPos + (pxPorSegundo / 2)
                        val tempoMeio = sec + 0.5
                        if (tempoMeio * 1000 <= durationMs) {
                            drawLine(
                                color = corTickMenor,
                                start = Offset(xPosMeio, 0f),
                                end = Offset(xPosMeio, tickAlturaMenor * 0.6f),
                                strokeWidth = 1.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
        )
    }
}

/**
 * Extension function para desenhar listras diagonais (hachura)
 * Desenha dentro do clipRect existente do DrawScope
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.desenharListras(
    left: Float,
    right: Float,
    cor: Color
) {
    val espacamento = 15.dp.toPx()
    var x = left - size.height
    
    // Usa clipRect do próprio DrawScope
    clipRect(
        left = left,
        top = 0f,
        right = right,
        bottom = size.height
    ) {
        while (x < right) {
            drawLine(
                color = cor,
                start = Offset(x, size.height),
                end = Offset(x + size.height, 0f),
                strokeWidth = 3.dp.toPx()
            )
            x += espacamento
        }
    }
}

/**
 * Componente de scrubber com suporte a thumbnails.
 * Versão mais avançada que carrega frames do vídeo.
 */
@Composable
fun TimelineScrubberWithThumbnails(
    durationMs: Long,
    positionMs: Long,
    thumbnails: List<ThumbnailFrame>,
    onPositionChange: (Long) -> Unit,
    onScrollStart: () -> Unit = {},
    onScrollEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Implementação futura com LazyRow e carregamento de thumbnails
    // Por enquanto, delega para o scrubber simples
    TimelineScrubber(
        durationMs = durationMs,
        positionMs = positionMs,
        onPositionChange = onPositionChange,
        onScrollStart = onScrollStart,
        onScrollEnd = onScrollEnd,
        modifier = modifier
    )
}

/**
 * Representa um frame de thumbnail na timeline.
 */
data class ThumbnailFrame(
    val timeMs: Long,
    val bitmap: android.graphics.Bitmap? = null
)

/**
 * Calcula o número ótimo de frames baseado na duração.
 */
fun calcularNumeroFrames(duracaoMs: Long): Int {
    if (duracaoMs <= 0) return 0
    val framesPorSegundo = duracaoMs / 1000
    return framesPorSegundo.coerceIn(
        ConfiguracaoTimeline.MIN_FRAMES.toLong(),
        ConfiguracaoTimeline.MAX_FRAMES.toLong()
    ).toInt()
}
