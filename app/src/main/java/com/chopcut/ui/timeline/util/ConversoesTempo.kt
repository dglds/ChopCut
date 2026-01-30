package com.chopcut.ui.timeline.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Constantes de configuração visual da timeline
 */
object ConfiguracaoTimeline {
    /**
     * Largura de cada frame/thumbnail na timeline
     */
    val LARGURA_FRAME_DP = 80.dp

    /**
     * Altura da faixa de timeline
     */
    val ALTURA_FAIXA_DP = 80.dp

    /**
     * Largura das alças de edição
     */
    val LARGURA_ALCA_DP = 16.dp

    /**
     * Altura das alças de edição
     */
    val ALTURA_ALCA_DP = 24.dp

    /**
     * Área de toque expandida para alças
     */
    val AREA_TOQUE_ALCA_DP = 32.dp

    /**
     * Pixels por segundo padrão (para timeline contínua)
     */
    val PX_POR_SEGUNDO_DP = 60.dp

    /**
     * Duração mínima de um range em ms
     */
    const val DURACAO_MINIMA_RANGE_MS = 100L

    /**
     * Máximo de frames a exibir
     */
    const val MAX_FRAMES = 60

    /**
     * Mínimo de frames a exibir
     */
    const val MIN_FRAMES = 10
}

/**
 * Converte milissegundos para formato MM:SS.m
 */
fun formatarTempo(ms: Long): String {
    val totalSegundos = ms / 1000
    val minutos = totalSegundos / 60
    val segundos = totalSegundos % 60
    val decimos = (ms % 1000) / 100
    return "%02d:%02d.%d".format(minutos, segundos, decimos)
}

/**
 * Converte milissegundos para formato MM:SS (sem decimais)
 */
fun formatarTempoCurto(ms: Long): String {
    val totalSegundos = ms / 1000
    val minutos = totalSegundos / 60
    val segundos = totalSegundos % 60
    return "%02d:%02d".format(minutos, segundos)
}

/**
 * Converte milissegundos para segundos (com decimais)
 */
fun Long.paraSegundos(): Float = this / 1000f

/**
 * Converte segundos para milissegundos
 */
fun Float.paraMs(): Long = (this * 1000).toLong()

/**
 * Converte Double para milissegundos
 */
fun Double.paraMs(): Long = (this * 1000).toLong()

/**
 * Classe de conversão tempo <-> pixels <-> dp
 * Centraliza toda a lógica de conversão da timeline
 */
class ConversorTempoPx(
    private val duracaoTotalMs: Long,
    private val pxPorSegundo: Float,
    private val densidade: Float
) {
    /**
     * Largura total do vídeo em pixels
     */
    val larguraTotalPx: Float
        get() = (duracaoTotalMs / 1000f) * pxPorSegundo

    /**
     * Converte tempo (ms) para posição em pixels (relativa ao início)
     */
    fun tempoParaPx(tempoMs: Long): Float =
        (tempoMs / 1000f) * pxPorSegundo

    /**
     * Converte posição em pixels para tempo (ms)
     */
    fun pxParaTempo(px: Float): Long =
        ((px / pxPorSegundo) * 1000).toLong()
            .coerceIn(0, duracaoTotalMs)

    /**
     * Converte tempo (ms) para posição X na tela considerando scroll
     *
     * @param tempoMs Tempo a converter
     * @param scrollOffsetPx Offset atual do scroll
     * @param centroOffset Offset do centro da tela (playhead)
     */
    fun tempoParaX(
        tempoMs: Long,
        scrollOffsetPx: Float,
        centroOffset: Float
    ): Float = tempoParaPx(tempoMs) - scrollOffsetPx + centroOffset

    /**
     * Converte posição X na tela para tempo (ms)
     *
     * @param x Posição X na tela
     * @param scrollOffsetPx Offset atual do scroll
     * @param centroOffset Offset do centro da tela (playhead)
     */
    fun xParaTempo(
        x: Float,
        scrollOffsetPx: Float,
        centroOffset: Float
    ): Long = pxParaTempo(x + scrollOffsetPx - centroOffset)

    /**
     * Converte progresso (0.0 a 1.0) para tempo (ms)
     */
    fun progressoParaTempo(progresso: Float): Long =
        (progresso * duracaoTotalMs).toLong()

    /**
     * Converte tempo (ms) para progresso (0.0 a 1.0)
     */
    fun tempoParaProgresso(tempoMs: Long): Float =
        if (duracaoTotalMs > 0) tempoMs.toFloat() / duracaoTotalMs else 0f

    /**
     * Calcula número de frames baseado na duração
     */
    fun calcularNumeroFrames(): Int {
        if (duracaoTotalMs <= 0) return 0
        val framesPorSegundo = duracaoTotalMs / 1000
        return framesPorSegundo.coerceIn(
            ConfiguracaoTimeline.MIN_FRAMES.toLong(),
            ConfiguracaoTimeline.MAX_FRAMES.toLong()
        ).toInt()
    }

    /**
     * Converte Dp para pixels
     */
    fun dpParaPx(dp: Dp): Float = dp.value * densidade

    /**
     * Converte pixels para Dp
     */
    fun pxParaDp(px: Float): Dp = (px / densidade).dp

    companion object {
        /**
         * Cria um conversor com configurações padrão
         */
        fun criar(
            duracaoTotalMs: Long,
            dpPorSegundo: Dp = ConfiguracaoTimeline.PX_POR_SEGUNDO_DP,
            densidade: Float
        ): ConversorTempoPx {
            val pxPorSegundo = dpPorSegundo.value * densidade
            return ConversorTempoPx(duracaoTotalMs, pxPorSegundo, densidade)
        }
    }
}

/**
 * Composable para obter ConversorTempoPx com remember
 */
@Composable
fun lembrarConversorTempo(
    duracaoTotalMs: Long,
    dpPorSegundo: Dp = ConfiguracaoTimeline.PX_POR_SEGUNDO_DP
): ConversorTempoPx {
    val density = LocalDensity.current.density
    return remember(duracaoTotalMs, dpPorSegundo, density) {
        ConversorTempoPx.criar(duracaoTotalMs, dpPorSegundo, density)
    }
}

/**
 * Extensões para cálculos de range
 */

/**
 * Calcula o início válido para um range considerando ranges vizinhos
 *
 * @param inicioDesejado Início desejado em ms
 * @param fimAtual Fim atual do range em ms
 * @param outrosRanges Outros ranges para considerar
 * @param minimoMs Valor mínimo permitido (default: 0)
 */
fun calcularInicioValido(
    inicioDesejado: Long,
    fimAtual: Long,
    outrosRanges: List<com.chopcut.ui.timeline.model.RangeCorte>,
    minimoMs: Long = 0
): Long {
    val maxInicio = (fimAtual - ConfiguracaoTimeline.DURACAO_MINIMA_RANGE_MS)
        .coerceAtLeast(minimoMs)

    // Encontra o range anterior mais próximo
    val fimAnterior = outrosRanges
        .filter { it.fimMs <= inicioDesejado }
        .maxByOrNull { it.fimMs }
        ?.fimMs ?: minimoMs

    return inicioDesejado.coerceIn(fimAnterior, maxInicio)
}

/**
 * Calcula o fim válido para um range considerando ranges vizinhos
 *
 * @param inicioAtual Início atual do range em ms
 * @param fimDesejado Fim desejado em ms
 * @param outrosRanges Outros ranges para considerar
 * @param duracaoTotalMs Duração total do vídeo
 */
fun calcularFimValido(
    inicioAtual: Long,
    fimDesejado: Long,
    outrosRanges: List<com.chopcut.ui.timeline.model.RangeCorte>,
    duracaoTotalMs: Long
): Long {
    val minFim = (inicioAtual + ConfiguracaoTimeline.DURACAO_MINIMA_RANGE_MS)
        .coerceAtMost(duracaoTotalMs)

    // Encontra o range posterior mais próximo
    val inicioPosterior = outrosRanges
        .filter { it.inicioMs >= fimDesejado }
        .minByOrNull { it.inicioMs }
        ?.inicioMs ?: duracaoTotalMs

    return fimDesejado.coerceIn(minFim, inicioPosterior)
}
