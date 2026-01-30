package com.chopcut.ui.timeline.model

import android.net.Uri

/**
 * Estado consolidado do editor de vídeo.
 * Única fonte de verdade para toda a UI do editor.
 */
data class EstadoEditor(
    /**
     * URI do vídeo sendo editado
     */
    val videoUri: Uri? = null,

    /**
     * Duração total do vídeo em milissegundos
     */
    val duracaoTotalMs: Long = 0,

    /**
     * Posição atual do playhead em milissegundos
     */
    val posicaoPlayheadMs: Long = 0,

    /**
     * Lista de ranges de corte (áreas a serem removidas)
     */
    val ranges: ListaRanges = emptyList(),

    /**
     * Estado do fluxo de criação de range (2 cliques)
     */
    val estadoCriacao: EstadoCriacao = EstadoCriacao.OCIOSO,

    /**
     * Estado do player de vídeo
     */
    val estadoPlayer: EstadoPlayer = EstadoPlayer.PAUSADO,

    /**
     * Se a timeline está sendo arrastada (scrubbing)
     */
    val emArraste: Boolean = false,

    /**
     * ID do range atualmente selecionado (para edição)
     */
    val rangeSelecionadoId: String? = null
) {
    /**
     * Range que está em edição no momento, se houver
     */
    val rangeEmEdicao: RangeCorte?
        get() = ranges.firstOrNull { it.emEdicao }

    /**
     * Range confirmado atualmente selecionado
     */
    val rangeSelecionado: RangeCorte?
        get() = rangeSelecionadoId?.let { id ->
            ranges.firstOrNull { it.id == id && it.confirmado }
        }

    /**
     * Range que contém o playhead atual (se houver)
     */
    val rangeNoPlayhead: RangeCorte?
        get() = ranges.noPlayhead(posicaoPlayheadMs)

    /**
     * Se há um range no playhead que pode ser deletado
     */
    val podeDeletarNoPlayhead: Boolean
        get() = rangeNoPlayhead?.confirmado == true

    /**
     * Progresso do vídeo (0.0 a 1.0)
     */
    val progresso: Float
        get() = if (duracaoTotalMs > 0) {
            posicaoPlayheadMs.toFloat() / duracaoTotalMs
        } else 0f

    /**
     * Se o editor está pronto para interação
     */
    val isPronto: Boolean
        get() = duracaoTotalMs > 0 && videoUri != null

    /**
     * Se pode iniciar criação de um novo range
     */
    val podeIniciarNovoRange: Boolean
        get() = estadoCriacao is EstadoCriacao.OCIOSO && rangeNoPlayhead == null

    /**
     * Se está no meio do fluxo de criação de range
     */
    val emCriacaoRange: Boolean
        get() = estadoCriacao is EstadoCriacao.AguardandoFim

    companion object {
        /**
         * Estado inicial vazio
         */
        val INICIAL = EstadoEditor()
    }
}

/**
 * Estados do fluxo de criação de range (2 cliques no FAB)
 */
sealed class EstadoCriacao {
    /**
     * Nenhuma criação em andamento
     */
    object OCIOSO : EstadoCriacao()

    /**
     * Primeiro clique realizado, aguardando segundo clique
     * para definir o fim do range
     */
    data class AguardandoFim(
        val inicioMs: Long,
        val rangeTemporario: RangeCorte
    ) : EstadoCriacao()

    override fun toString(): String = when (this) {
        is OCIOSO -> "Ocioso"
        is AguardandoFim -> "AguardandoFim(inicio=${inicioMs}ms)"
    }
}

/**
 * Estados do player de vídeo
 */
sealed class EstadoPlayer {
    object CARREGANDO : EstadoPlayer()
    object PRONTO : EstadoPlayer()
    object PAUSADO : EstadoPlayer()
    object REPRODUZINDO : EstadoPlayer()
    object ERRO : EstadoPlayer()

    override fun toString(): String = when (this) {
        is CARREGANDO -> "Carregando"
        is PRONTO -> "Pronto"
        is PAUSADO -> "Pausado"
        is REPRODUZINDO -> "Reproduzindo"
        is ERRO -> "Erro"
    }
}

/**
 * Estados do FAB baseados no contexto atual
 */
sealed class EstadoFab {
    /**
     * Estado inicial - pode adicionar novo range
     */
    object ADICIONAR : EstadoFab()

    /**
     * Aguardando segundo clique para finalizar range
     */
    object CONFIRMAR : EstadoFab()

    /**
     * Pode deletar range existente no playhead
     */
    object DELETAR : EstadoFab()

    override fun toString(): String = when (this) {
        is ADICIONAR -> "Adicionar"
        is CONFIRMAR -> "Confirmar"
        is DELETAR -> "Deletar"
    }
}

/**
 * Calcula o estado do FAB baseado no estado do editor
 */
fun EstadoEditor.calcularEstadoFab(): EstadoFab = when {
    emCriacaoRange -> EstadoFab.CONFIRMAR
    podeDeletarNoPlayhead -> EstadoFab.DELETAR
    else -> EstadoFab.ADICIONAR
}

/**
 * Eventos de usuário que podem ser emitidos para o ViewModel
 */
sealed class EventoEditor {
    /**
     * Preparar editor com novo vídeo
     */
    data class PrepararVideo(val uri: Uri, val duracaoMs: Long) : EventoEditor()

    /**
     * Atualizar posição do playhead
     */
    data class AtualizarPosicao(val posicaoMs: Long, val deArraste: Boolean = false) : EventoEditor()

    /**
     * Iniciar/parar arraste na timeline
     */
    data class Arraste(val ativo: Boolean) : EventoEditor()

    /**
     * Iniciar criação de range no playhead atual
     */
    object IniciarCriacaoRange : EventoEditor()

    /**
     * Finalizar criação de range com posição atual do playhead
     */
    object FinalizarCriacaoRange : EventoEditor()

    /**
     * Cancelar criação de range em andamento
     */
    object CancelarCriacaoRange : EventoEditor()

    /**
     * Selecionar um range existente
     */
    data class SelecionarRange(val rangeId: String?) : EventoEditor()

    /**
     * Atualizar limites de um range
     */
    data class AtualizarRange(
        val rangeId: String,
        val novoInicioMs: Long,
        val novoFimMs: Long
    ) : EventoEditor()

    /**
     * Deletar range pelo ID
     */
    data class DeletarRange(val rangeId: String) : EventoEditor()

    /**
     * Deletar range no playhead atual
     */
    object DeletarRangeNoPlayhead : EventoEditor()

    /**
     * Atualizar estado do player
     */
    data class AtualizarEstadoPlayer(val estado: EstadoPlayer) : EventoEditor()

    /**
     * Play/Pause no player
     */
    object AlternarReproducao : EventoEditor()

    /**
     * Parar reprodução
     */
    object Parar : EventoEditor()

    /**
     * Seek para posição específica
     */
    data class Seek(val posicaoMs: Long) : EventoEditor()
}
