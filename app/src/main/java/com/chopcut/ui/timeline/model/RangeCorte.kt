package com.chopcut.ui.timeline.model

import java.util.UUID

/**
 * Representa um intervalo de corte (range) na timeline.
 * Modelo unificado que substitui TrimRangeData e VideoRange.
 *
 * @property id Identificador único do range
 * @property inicioMs Tempo de início em milissegundos (inclusivo)
 * @property fimMs Tempo de fim em milissegundos (exclusivo)
 * @property emEdicao Se true, o range está em edição (alças visíveis)
 * @property confirmado Se true, o range já foi salvo e pode ser deletado
 */
data class RangeCorte(
    val id: String = UUID.randomUUID().toString(),
    val inicioMs: Long,
    val fimMs: Long,
    val emEdicao: Boolean = false,
    val confirmado: Boolean = false
) {
    init {
        require(inicioMs >= 0) { "inicioMs deve ser não-negativo, foi $inicioMs" }
        require(fimMs >= inicioMs) { "fimMs ($fimMs) deve ser >= inicioMs ($inicioMs)" }
    }

    /**
     * Duração do intervalo em milissegundos
     */
    val duracaoMs: Long
        get() = fimMs - inicioMs

    /**
     * Verifica se o intervalo é válido (tem duração mínima de 100ms)
     */
    val isValido: Boolean
        get() = duracaoMs >= 100L

    /**
     * Verifica se um tempo está dentro deste intervalo (inclusivo)
     */
    operator fun contains(tempoMs: Long): Boolean =
        tempoMs in inicioMs..fimMs

    /**
     * Verifica se este range sobrepõe outro range
     */
    fun sobrepoe(outro: RangeCorte): Boolean =
        inicioMs < outro.fimMs && fimMs > outro.inicioMs

    /**
     * Retorna uma cópia deste range com os valores ajustados para não
     * sobrepor o range especificado.
     *
     * @param outro Range a evitar
     * @return Range ajustado ou este range se não houver sobreposição
     */
    fun ajustarParaEvitarSobreposicao(outro: RangeCorte): RangeCorte {
        if (!sobrepoe(outro)) return this

        // Se estiver completamente dentro do outro, retorna mínimo possível
        if (inicioMs >= outro.inicioMs && fimMs <= outro.fimMs) {
            return copy(inicioMs = outro.fimMs, fimMs = (outro.fimMs + 100).coerceAtLeast(fimMs))
        }

        // Ajusta baseado na posição relativa
        return when {
            // Este range está antes ou começa antes
            inicioMs < outro.inicioMs -> {
                val novoFim = outro.inicioMs.coerceAtLeast(inicioMs + 100)
                copy(fimMs = novoFim)
            }
            // Este range está depois
            else -> {
                val novoInicio = outro.fimMs.coerceAtMost(fimMs - 100)
                copy(inicioMs = novoInicio)
            }
        }
    }

    /**
     * Cria uma cópia com valores coercidos aos limites do vídeo
     */
    fun coercer(limiteMs: Long): RangeCorte = copy(
        inicioMs = inicioMs.coerceIn(0, limiteMs - 100),
        fimMs = fimMs.coerceIn(100, limiteMs)
    )

    /**
     * Formata o intervalo para exibição (MM:SS.m)
     */
    fun formatarIntervalo(): String {
        val inicioSeg = inicioMs / 1000
        val inicioMin = inicioSeg / 60
        val inicioSec = inicioSeg % 60
        val inicioDec = (inicioMs % 1000) / 100

        val fimSeg = fimMs / 1000
        val fimMin = fimSeg / 60
        val fimSec = fimSeg % 60
        val fimDec = (fimMs % 1000) / 100

        return "%02d:%02d.%d → %02d:%02d.%d".format(
            inicioMin, inicioSec, inicioDec,
            fimMin, fimSec, fimDec
        )
    }

    companion object {
        /**
         * Duração mínima válida para um range (100ms)
         */
        const val DURACAO_MINIMA_MS = 100L

        /**
         * Cria um RangeCorte vazio (para inicialização)
         */
        val VAZIO = RangeCorte(
            id = "",
            inicioMs = 0,
            fimMs = 0,
            emEdicao = false,
            confirmado = false
        )

        /**
         * Cria um range temporário durante a criação (2 cliques)
         */
        fun criarTemporario(inicioMs: Long, fimMs: Long): RangeCorte =
            RangeCorte(
                id = "temp_${System.currentTimeMillis()}",
                inicioMs = inicioMs,
                fimMs = fimMs,
                emEdicao = true,
                confirmado = false
            )
    }
}

/**
 * Lista de ranges com operações utilitárias
 */
typealias ListaRanges = List<RangeCorte>

/**
 * Extensão para encontrar o range no playhead
 */
fun ListaRanges.noPlayhead(posicaoMs: Long): RangeCorte? =
    firstOrNull { posicaoMs in it }

/**
 * Extensão para verificar se há sobreposição em uma posição
 */
fun ListaRanges.temSobreposicaoEm(posicaoMs: Long, excetoId: String? = null): Boolean =
    any { it.id != excetoId && posicaoMs in it }

/**
 * Extensão para encontrar o range mais próximo à esquerda
 */
fun ListaRanges.anteriorA(posicaoMs: Long, excetoId: String? = null): RangeCorte? =
    filter { it.id != excetoId && it.fimMs <= posicaoMs }
        .maxByOrNull { it.fimMs }

/**
 * Extensão para encontrar o range mais próximo à direita
 */
fun ListaRanges.posteriorA(posicaoMs: Long, excetoId: String? = null): RangeCorte? =
    filter { it.id != excetoId && it.inicioMs >= posicaoMs }
        .minByOrNull { it.inicioMs }
