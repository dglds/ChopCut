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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chopcut.ui.timeline.util.ConfiguracaoTimeline
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Componente de scrubber de timeline (faixa scrollável) - VERSÃO OTIMIZADA.
 *
 * Otimizações para Celeron N5095A:
 * - Cores memorizadas para evitar recriação
 * - Dimensões calculadas via derivedStateOf
 * - Throttling de 16ms para atualizações de posição
 * - Objetos de desenho pré-alocados
 * - Loop de ticks otimizado (só desenha o visível)
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
    val densityValue = density.density
    
    // ==== CONSTANTES MEMORIZADAS ====
    val pxPorSegundo = remember(densityValue) { 
        densityValue * ConfiguracaoTimeline.PX_POR_SEGUNDO_DP.value 
    }
    
    // Cores memorizadas (evita recriação a cada recomposition)
    val cores = remember {
        TimelineCores(
            tickPrincipal = Color(0xFF424242),
            tickSecundario = Color(0xFF757575),
            tickMenor = Color(0xFF9E9E9E),
            fundoNeutro = Color(0xFFD6D6D6),
            listra = Color(0xFFBDBDBD),
            sombraSuperior = Color.Black.copy(alpha = 0.15f),
            sombraInferior = Color.Black.copy(alpha = 0.1f),
            divisor = Color.Black.copy(alpha = 0.2f)
        )
    }
    
    // ==== ESTADO DE SCROLL ====
    var scrollOffsetPx by remember { mutableFloatStateOf(0f) }
    var lastPositionUpdate by remember { mutableFloatStateOf(0f) }
    
    // Dimensões calculadas via derivedStateOf (só recalcula quando necessário)
    val dimensoes by remember(durationMs) {
        derivedStateOf {
            val durationSeconds = (durationMs / 1000f).coerceAtLeast(0f)
            DimensaoTimeline(
                totalWidthPx = durationSeconds * pxPorSegundo,
                durationMs = durationMs
            )
        }
    }
    
    // ==== THROTTLING DE 16ms (60 FPS) ====
    val throttledOnPositionChange = remember(onPositionChange) {
        { newTimeMs: Long ->
            val currentTime = System.nanoTime()
            // 16ms = 16_000_000 nanos
            if (currentTime - lastPositionUpdate >= 16_000_000) {
                lastPositionUpdate = currentTime.toFloat()
                onPositionChange(newTimeMs)
            }
        }
    }
    
    // Estado scrollable
    val scrollableState = rememberScrollableState { delta ->
        val oldOffset = scrollOffsetPx
        val newOffset = (oldOffset - delta).coerceIn(0f, dimensoes.totalWidthPx.coerceAtLeast(0f))
        scrollOffsetPx = newOffset
        
        // Converte para tempo e notifica (com throttling)
        if (durationMs > 0) {
            val progress = newOffset / dimensoes.totalWidthPx
            val newTimeMs = (progress * durationMs).toLong()
            throttledOnPositionChange(newTimeMs)
        }
        
        oldOffset - newOffset
    }
    
    // Detecta início/fim do scroll
    LaunchedEffect(scrollableState.isScrollInProgress) {
        if (scrollableState.isScrollInProgress) {
            onScrollStart()
        } else {
            onScrollEnd()
            // Força atualização final
            if (durationMs > 0) {
                val progress = scrollOffsetPx / dimensoes.totalWidthPx
                onPositionChange((progress * durationMs).toLong())
            }
        }
    }
    
    // Sincroniza scrollOffset com positionMs quando não está scrollando
    LaunchedEffect(positionMs, durationMs) {
        if (!scrollableState.isScrollInProgress && durationMs > 0) {
            val progress = positionMs.toFloat() / durationMs
            scrollOffsetPx = progress * dimensoes.totalWidthPx
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
        
        // ==== VALORES DERIVADOS (memoizados) ====
        val containerHeight = constraints.maxHeight.toFloat()
        
        val tickAlturas by remember(containerHeight, density) {
            derivedStateOf {
                TickAlturas(
                    maior = containerHeight * 0.4f,
                    menor = containerHeight * 0.25f,
                    meio = containerHeight * 0.15f
                )
            }
        }
        
        val strokeWidths by remember(densityValue) {
            derivedStateOf {
                StrokeWidths(
                    principal = 2f * densityValue,
                    secundario = 1.5f * densityValue,
                    menor = 1f * densityValue,
                    divisor = 1f * densityValue
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val currentScroll = scrollOffsetPx
                    val totalWidth = dimensoes.totalWidthPx
                    
                    val videoStartX = -currentScroll + centerOffset
                    val videoEndX = videoStartX + totalWidth
                    
                    // ==== EFEITO DE RELEVO (SUNKEN) - Usando brushes pré-criados ====
                    drawRelevoSunken(
                        height = size.height,
                        sombraSuperior = cores.sombraSuperior,
                        sombraInferior = cores.sombraInferior
                    )

                    // ==== ÁREAS NEUTRAS (FORA DO VÍDEO) ====
                    desenharAreaNeutraEsquerda(
                        videoStartX = videoStartX,
                        height = size.height,
                        corFundo = cores.fundoNeutro,
                        corListra = cores.listra,
                        corDivisor = cores.divisor,
                        strokeWidth = strokeWidths.divisor
                    )

                    desenharAreaNeutraDireita(
                        videoEndX = videoEndX,
                        width = size.width,
                        height = size.height,
                        corFundo = cores.fundoNeutro,
                        corListra = cores.listra,
                        corDivisor = cores.divisor,
                        strokeWidth = strokeWidths.divisor
                    )

                    // ==== TICKS DE TEMPO (só o visível) ====
                    desenharTicksOtimizado(
                        scrollOffset = currentScroll,
                        centerOffset = centerOffset,
                        containerWidth = size.width,
                        pxPorSegundo = pxPorSegundo,
                        durationMs = durationMs,
                        alturas = tickAlturas,
                        strokeWidths = strokeWidths,
                        cores = cores
                    )
                }
        )
    }
}

// ==== DATA CLASSES PARA REUSO DE OBJETOS ====
private data class TimelineCores(
    val tickPrincipal: Color,
    val tickSecundario: Color,
    val tickMenor: Color,
    val fundoNeutro: Color,
    val listra: Color,
    val sombraSuperior: Color,
    val sombraInferior: Color,
    val divisor: Color
)

private data class DimensaoTimeline(
    val totalWidthPx: Float,
    val durationMs: Long
)

private data class TickAlturas(
    val maior: Float,
    val menor: Float,
    val meio: Float
)

private data class StrokeWidths(
    val principal: Float,
    val secundario: Float,
    val menor: Float,
    val divisor: Float
)

/**
 * Desenha efeito de relevo sunken usando brushes.
 */
private fun DrawScope.drawRelevoSunken(
    height: Float,
    sombraSuperior: Color,
    sombraInferior: Color
) {
    // Sombra superior
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(sombraSuperior, Color.Transparent),
            startY = 0f,
            endY = height * 0.2f
        )
    )
    // Sombra inferior
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, sombraInferior),
            startY = height * 0.8f,
            endY = height
        )
    )
}

/**
 * Desenha área neutra esquerda (antes do vídeo).
 */
private fun DrawScope.desenharAreaNeutraEsquerda(
    videoStartX: Float,
    height: Float,
    corFundo: Color,
    corListra: Color,
    corDivisor: Color,
    strokeWidth: Float
) {
    if (videoStartX <= 0) return
    
    drawRect(
        color = corFundo,
        topLeft = Offset(0f, 0f),
        size = Size(videoStartX, height)
    )
    
    desenharListrasOtimizado(
        left = 0f,
        right = videoStartX,
        height = height,
        cor = corListra,
        espacamento = 15f * (size.width / 400f).coerceIn(0.5f, 2f) // Escala com tela
    )
    
    drawLine(
        color = corDivisor,
        start = Offset(videoStartX, 0f),
        end = Offset(videoStartX, height),
        strokeWidth = strokeWidth
    )
}

/**
 * Desenha área neutra direita (após o vídeo).
 */
private fun DrawScope.desenharAreaNeutraDireita(
    videoEndX: Float,
    width: Float,
    height: Float,
    corFundo: Color,
    corListra: Color,
    corDivisor: Color,
    strokeWidth: Float
) {
    if (videoEndX >= width) return
    
    drawRect(
        color = corFundo,
        topLeft = Offset(videoEndX, 0f),
        size = Size(width - videoEndX, height)
    )
    
    desenharListrasOtimizado(
        left = videoEndX,
        right = width,
        height = height,
        cor = corListra,
        espacamento = 15f * (width / 400f).coerceIn(0.5f, 2f)
    )
    
    drawLine(
        color = corDivisor,
        start = Offset(videoEndX, 0f),
        end = Offset(videoEndX, height),
        strokeWidth = strokeWidth
    )
}

/**
 * Desenha ticks de tempo - versão otimizada que só processa o visível.
 */
private fun DrawScope.desenharTicksOtimizado(
    scrollOffset: Float,
    centerOffset: Float,
    containerWidth: Float,
    pxPorSegundo: Float,
    durationMs: Long,
    alturas: TickAlturas,
    strokeWidths: StrokeWidths,
    cores: TimelineCores
) {
    if (durationMs <= 0) return
    
    // Calcula apenas o range visível (evita loop sobre segundos invisíveis)
    val inicioVisivelSec = ((0 - centerOffset + scrollOffset) / pxPorSegundo).toInt() - 1
    val fimVisivelSec = ((containerWidth - centerOffset + scrollOffset) / pxPorSegundo).toInt() + 2
    
    val duracaoSec = (durationMs / 1000).toInt()
    val inicioLoop = inicioVisivelSec.coerceAtLeast(0)
    val fimLoop = fimVisivelSec.coerceAtMost(duracaoSec + 1)

    for (sec in inicioLoop..fimLoop) {
        val xPos = (sec * pxPorSegundo) - scrollOffset + centerOffset
        
        // Tick principal (cada segundo)
        val isTickPrincipal = sec % 5 == 0
        drawLine(
            color = if (isTickPrincipal) cores.tickPrincipal else cores.tickSecundario,
            start = Offset(xPos, 0f),
            end = Offset(xPos, if (isTickPrincipal) alturas.maior else alturas.menor),
            strokeWidth = if (isTickPrincipal) strokeWidths.principal else strokeWidths.secundario,
            cap = StrokeCap.Round
        )
        
        // Tick menor (0.5s) - só desenha se estiver dentro da duração
        if (sec < duracaoSec) {
            val xPosMeio = xPos + (pxPorSegundo / 2)
            drawLine(
                color = cores.tickMenor,
                start = Offset(xPosMeio, 0f),
                end = Offset(xPosMeio, alturas.meio),
                strokeWidth = strokeWidths.menor,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Desenha listras diagonais - versão otimizada.
 */
private fun DrawScope.desenharListrasOtimizado(
    left: Float,
    right: Float,
    height: Float,
    cor: Color,
    espacamento: Float
) {
    val strokeWidth = 3f * (size.width / 400f).coerceIn(0.5f, 2f)
    var x = left - height
    
    clipRect(
        left = left,
        top = 0f,
        right = right,
        bottom = height
    ) {
        while (x < right) {
            drawLine(
                color = cor,
                start = Offset(x, height),
                end = Offset(x + height, 0f),
                strokeWidth = strokeWidth
            )
            x += espacamento
        }
    }
}

/**
 * Extension function para desenhar listras diagonais (hachura) - LEGADO
 * @deprecated Use desenharListrasOtimizado
 */
private fun DrawScope.desenharListras(
    left: Float,
    right: Float,
    cor: Color
) {
    desenharListrasOtimizado(
        left = left,
        right = right,
        height = size.height,
        cor = cor,
        espacamento = 15.dp.toPx()
    )
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
