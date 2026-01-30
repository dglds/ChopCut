package com.chopcut.ui.timeline

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chopcut.ui.timeline.model.EstadoCriacao
import com.chopcut.ui.timeline.model.EstadoEditor
import com.chopcut.ui.timeline.model.EstadoFab
import com.chopcut.ui.timeline.model.EstadoPlayer
import com.chopcut.ui.timeline.model.EventoEditor
import com.chopcut.ui.timeline.model.ListaRanges
import com.chopcut.ui.timeline.model.RangeCorte
import com.chopcut.ui.timeline.model.calcularEstadoFab
import com.chopcut.ui.timeline.model.noPlayhead
import com.chopcut.ui.timeline.util.ConfiguracaoTimeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel consolidado para o editor de timeline.
 * 
 * Esta classe é a única fonte de verdade para o estado do editor,
 * processando eventos de usuário e mantendo sincronização com o
 * PreviewManager (player de vídeo).
 */
class EditorTimelineViewModel(
    private val context: Context,
    private val previewManager: PreviewManager
) : ViewModel() {

    // ============================================================================
    // ESTADO PRIVADO (Fonte da Verdade)
    // ============================================================================

    private val _estadoEditor = MutableStateFlow(EstadoEditor.INICIAL)

    /**
     * Estado completo do editor exposto como StateFlow.
     * Todas as atualizações passam por aqui.
     */
    val estadoEditor: StateFlow<EstadoEditor> = _estadoEditor.asStateFlow()

    // ============================================================================
    // ESTADOS DERIVADOS (Otimizados com derivedStateOf via StateFlow)
    // ============================================================================

    /**
     * Estado do FAB calculado automaticamente baseado no contexto atual.
     * Deriva de: estadoCriacao + ranges + posicaoPlayheadMs
     */
    val estadoFab: StateFlow<EstadoFab> = estadoEditor
        .map { it.calcularEstadoFab() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = EstadoFab.ADICIONAR
        )

    /**
     * Range que está em criação no momento (se houver).
     * Atualiza dinamicamente conforme o playhead se move.
     */
    val rangeEmCriacao: StateFlow<RangeCorte?> = estadoEditor
        .map { estado ->
            val criacao = estado.estadoCriacao
            if (criacao is EstadoCriacao.AguardandoFim) {
                val inicio = criacao.inicioMs
                val fim = estado.posicaoPlayheadMs
                // Ordena para garantir que inicio <= fim
                val (realInicio, realFim) = if (inicio <= fim) {
                    inicio to fim
                } else {
                    fim to inicio
                }
                // Garante duração mínima
                val fimAjustado = if (realFim - realInicio < ConfiguracaoTimeline.DURACAO_MINIMA_RANGE_MS) {
                    (realInicio + ConfiguracaoTimeline.DURACAO_MINIMA_RANGE_MS)
                        .coerceAtMost(estado.duracaoTotalMs)
                } else realFim

                RangeCorte.criarTemporario(realInicio, fimAjustado)
            } else null
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * Range atualmente no playhead (se houver).
     */
    val rangeNoPlayhead: StateFlow<RangeCorte?> = estadoEditor
        .map { it.rangeNoPlayhead }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // ============================================================================
    // SINCRONIZAÇÃO COM PREVIEW MANAGER
    // ============================================================================

    init {
        // Observa posição do player e atualiza estado (exceto durante scrubbing)
        viewModelScope.launch {
            previewManager.currentPosition
                .collect { posicaoMs ->
                    val estadoAtual = _estadoEditor.value
                    if (!estadoAtual.emArraste && estadoAtual.isPronto) {
                        _estadoEditor.update { it.copy(posicaoPlayheadMs = posicaoMs) }
                    }
                }
        }

        // Observa estado do player e converte para EstadoPlayer
        viewModelScope.launch {
            combine(
                previewManager.isReady,
                previewManager.isPlaying,
                previewManager.playerState
            ) { isReady, isPlaying, playerState ->
                when {
                    !isReady -> EstadoPlayer.CARREGANDO
                    isPlaying -> EstadoPlayer.REPRODUZINDO
                    playerState == PlayerState.STOPPED -> EstadoPlayer.PAUSADO
                    else -> EstadoPlayer.PAUSADO
                }
            }.collect { estadoPlayer ->
                _estadoEditor.update { it.copy(estadoPlayer = estadoPlayer) }
            }
        }

        // Observa duração do vídeo
        viewModelScope.launch {
            previewManager.duration
                .collect { duracaoMs ->
                    if (duracaoMs > 0) {
                        _estadoEditor.update { it.copy(duracaoTotalMs = duracaoMs) }
                        Timber.d("Duração do vídeo atualizada: ${duracaoMs}ms")
                    }
                }
        }
    }

    // ============================================================================
    // PROCESSAMENTO DE EVENTOS (Máquina de Estados)
    // ============================================================================

    /**
     * Processa eventos de usuário e atualiza o estado do editor.
     * Único ponto de entrada para modificações no estado.
     */
    fun processarEvento(evento: EventoEditor) {
        Timber.d("Evento: $evento")

        when (evento) {
            is EventoEditor.PrepararVideo -> handlePrepararVideo(evento)
            is EventoEditor.AtualizarPosicao -> handleAtualizarPosicao(evento)
            is EventoEditor.Arraste -> handleArraste(evento)
            is EventoEditor.IniciarCriacaoRange -> handleIniciarCriacaoRange()
            is EventoEditor.FinalizarCriacaoRange -> handleFinalizarCriacaoRange()
            is EventoEditor.CancelarCriacaoRange -> handleCancelarCriacaoRange()
            is EventoEditor.SelecionarRange -> handleSelecionarRange(evento)
            is EventoEditor.AtualizarRange -> handleAtualizarRange(evento)
            is EventoEditor.DeletarRange -> handleDeletarRange(evento)
            is EventoEditor.DeletarRangeNoPlayhead -> handleDeletarRangeNoPlayhead()
            is EventoEditor.AlternarReproducao -> handleAlternarReproducao()
            is EventoEditor.Parar -> handleParar()
            is EventoEditor.Seek -> handleSeek(evento)
            is EventoEditor.AtualizarEstadoPlayer -> handleAtualizarEstadoPlayer(evento)
        }
    }

    // ============================================================================
    // HANDLERS DE EVENTOS
    // ============================================================================

    private fun handlePrepararVideo(evento: EventoEditor.PrepararVideo) {
        _estadoEditor.update {
            it.copy(
                videoUri = evento.uri,
                duracaoTotalMs = evento.duracaoMs,
                posicaoPlayheadMs = 0,
                ranges = emptyList(),
                estadoCriacao = EstadoCriacao.OCIOSO,
                emArraste = false
            )
        }
        // Prepara o player
        previewManager.prepare(context, evento.uri, viewModelScope)
    }

    private fun handleAtualizarPosicao(evento: EventoEditor.AtualizarPosicao) {
        _estadoEditor.update {
            it.copy(
                posicaoPlayheadMs = evento.posicaoMs.coerceIn(0, it.duracaoTotalMs),
                emArraste = evento.deArraste
            )
        }

        // Se estiver arrastando, pausa o player
        if (evento.deArraste) {
            previewManager.pause()
            previewManager.setScrubbing(true)
        }
    }

    private fun handleArraste(evento: EventoEditor.Arraste) {
        _estadoEditor.update { it.copy(emArraste = evento.ativo) }
        previewManager.setScrubbing(evento.ativo)

        if (!evento.ativo) {
            // Arraste terminou, faz seek para posição final
            val posicao = _estadoEditor.value.posicaoPlayheadMs
            previewManager.seekTo(posicao)
        }
    }

    private fun handleIniciarCriacaoRange() {
        val estado = _estadoEditor.value

        // Só inicia se estiver ocioso e não houver range no playhead
        if (estado.estadoCriacao !is EstadoCriacao.OCIOSO) {
            Timber.w("Tentativa de iniciar criação quando não está ocioso")
            return
        }

        if (estado.rangeNoPlayhead != null) {
            Timber.w("Tentativa de iniciar criação quando já existe range no playhead")
            return
        }

        // Verifica sobreposição com ranges existentes
        val temSobreposicao = estado.ranges.any { range ->
            estado.posicaoPlayheadMs in range
        }

        if (temSobreposicao) {
            Timber.w("Posição já ocupada por outro range")
            return
        }

        // Cria range temporário
        val posicao = estado.posicaoPlayheadMs
        val rangeTemp = RangeCorte.criarTemporario(posicao, posicao + 100)

        _estadoEditor.update {
            it.copy(
                estadoCriacao = EstadoCriacao.AguardandoFim(
                    inicioMs = posicao,
                    rangeTemporario = rangeTemp
                )
            )
        }

        Timber.d("Criação de range iniciada em ${posicao}ms")
    }

    private fun handleFinalizarCriacaoRange() {
        val estado = _estadoEditor.value
        val criacao = estado.estadoCriacao

        if (criacao !is EstadoCriacao.AguardandoFim) {
            Timber.w("Tentativa de finalizar criação sem estar em criação")
            return
        }

        val inicio = criacao.inicioMs
        val fim = estado.posicaoPlayheadMs
        val duracaoTotal = estado.duracaoTotalMs

        // Ordena inicio/fim
        val (realInicio, realFim) = if (inicio <= fim) inicio to fim else fim to inicio

        // Garante duração mínima
        val fimAjustado = if (realFim - realInicio < ConfiguracaoTimeline.DURACAO_MINIMA_RANGE_MS) {
            (realInicio + ConfiguracaoTimeline.DURACAO_MINIMA_RANGE_MS)
                .coerceAtMost(duracaoTotal)
        } else realFim

        // Verifica sobreposição no início
        val sobreposicaoInicio = estado.ranges.any { range ->
            realInicio in range.inicioMs..range.fimMs
        }

        if (sobreposicaoInicio) {
            Timber.w("Início do range sobrepõe range existente")
            return
        }

        // Auto-ajuste: se fim sobrepõe, encolhe até o limite
        val rangePosterior = estado.ranges
            .filter { it.inicioMs >= fimAjustado }
            .minByOrNull { it.inicioMs }

        val fimFinal = if (rangePosterior != null && fimAjustado > rangePosterior.inicioMs) {
            rangePosterior.inicioMs
        } else fimAjustado

        // Cria range confirmado
        val novoRange = RangeCorte(
            inicioMs = realInicio,
            fimMs = fimFinal.coerceAtMost(duracaoTotal),
            emEdicao = false,
            confirmado = true
        )

        _estadoEditor.update {
            it.copy(
                ranges = it.ranges + novoRange,
                estadoCriacao = EstadoCriacao.OCIOSO,
                rangeSelecionadoId = novoRange.id
            )
        }

        Timber.d("Range criado: ${novoRange.inicioMs}ms - ${novoRange.fimMs}ms")
    }

    private fun handleCancelarCriacaoRange() {
        _estadoEditor.update {
            it.copy(estadoCriacao = EstadoCriacao.OCIOSO)
        }
        Timber.d("Criação de range cancelada")
    }

    private fun handleSelecionarRange(evento: EventoEditor.SelecionarRange) {
        _estadoEditor.update {
            it.copy(
                rangeSelecionadoId = evento.rangeId,
                ranges = it.ranges.map { range ->
                    range.copy(emEdicao = range.id == evento.rangeId)
                }
            )
        }
    }

    private fun handleAtualizarRange(evento: EventoEditor.AtualizarRange) {
        val estado = _estadoEditor.value
        val rangeExistente = estado.ranges.find { it.id == evento.rangeId }
            ?: return

        // Não permite atualizar range não-confirmado (temporário)
        if (!rangeExistente.confirmado) {
            Timber.w("Tentativa de atualizar range não confirmado")
            return
        }

        // Outros ranges para validação de sobreposição
        val outrosRanges = estado.ranges.filter { it.id != evento.rangeId }

        // Valida limites
        val novoInicio = evento.novoInicioMs.coerceIn(0, evento.novoFimMs - ConfiguracaoTimeline.DURACAO_MINIMA_RANGE_MS)
        val novoFim = evento.novoFimMs.coerceIn(
            novoInicio + ConfiguracaoTimeline.DURACAO_MINIMA_RANGE_MS,
            estado.duracaoTotalMs
        )

        // Auto-ajuste para evitar sobreposição
        val fimAnterior = outrosRanges
            .filter { it.fimMs <= novoInicio }
            .maxByOrNull { it.fimMs }
            ?.fimMs ?: 0

        val inicioPosterior = outrosRanges
            .filter { it.inicioMs >= novoFim }
            .minByOrNull { it.inicioMs }
            ?.inicioMs ?: estado.duracaoTotalMs

        val inicioAjustado = novoInicio.coerceIn(fimAnterior, novoFim - ConfiguracaoTimeline.DURACAO_MINIMA_RANGE_MS)
        val fimAjustado = novoFim.coerceIn(novoInicio + ConfiguracaoTimeline.DURACAO_MINIMA_RANGE_MS, inicioPosterior)

        _estadoEditor.update { estadoAtual ->
            estadoAtual.copy(
                ranges = estadoAtual.ranges.map { range ->
                    if (range.id == evento.rangeId) {
                        range.copy(inicioMs = inicioAjustado, fimMs = fimAjustado)
                    } else range
                }
            )
        }
    }

    private fun handleDeletarRange(evento: EventoEditor.DeletarRange) {
        _estadoEditor.update { estado ->
            estado.copy(
                ranges = estado.ranges.filter { it.id != evento.rangeId },
                rangeSelecionadoId = if (estado.rangeSelecionadoId == evento.rangeId) null else estado.rangeSelecionadoId
            )
        }
        Timber.d("Range deletado: ${evento.rangeId}")
    }

    private fun handleDeletarRangeNoPlayhead() {
        val estado = _estadoEditor.value
        val rangeNoPlayhead = estado.rangeNoPlayhead

        if (rangeNoPlayhead?.confirmado == true) {
            handleDeletarRange(EventoEditor.DeletarRange(rangeNoPlayhead.id))
        }
    }

    private fun handleAlternarReproducao() {
        previewManager.togglePlayPause()
    }

    private fun handleParar() {
        previewManager.stop()
        _estadoEditor.update { it.copy(posicaoPlayheadMs = 0) }
    }

    private fun handleSeek(evento: EventoEditor.Seek) {
        val posicao = evento.posicaoMs.coerceIn(0, _estadoEditor.value.duracaoTotalMs)
        previewManager.seekTo(posicao)
        _estadoEditor.update { it.copy(posicaoPlayheadMs = posicao) }
    }

    private fun handleAtualizarEstadoPlayer(evento: EventoEditor.AtualizarEstadoPlayer) {
        // Este evento é mais para sincronização externa
        // O estado do player é atualizado automaticamente via PreviewManager
        _estadoEditor.update { it.copy(estadoPlayer = evento.estado) }
    }

    // ============================================================================
    // MÉTODOS PÚBLICOS DE CONVENIÊNCIA
    // ============================================================================

    /**
     * Prepara o editor com um novo vídeo.
     * Método de conveniência que dispara o evento apropriado.
     */
    fun prepararVideo(uri: Uri, duracaoMs: Long = 0) {
        processarEvento(EventoEditor.PrepararVideo(uri, duracaoMs))
    }

    /**
     * Atualiza posição do playhead.
     * Método de conveniência que dispara o evento apropriado.
     */
    fun atualizarPosicao(posicaoMs: Long, deArraste: Boolean = false) {
        processarEvento(EventoEditor.AtualizarPosicao(posicaoMs, deArraste))
    }

    /**
     * Inicia ou para o arraste na timeline.
     */
    fun setEmArraste(ativo: Boolean) {
        processarEvento(EventoEditor.Arraste(ativo))
    }

    /**
     * Libera recursos do ViewModel.
     * Deve ser chamado quando o editor for fechado.
     */
    fun liberar() {
        previewManager.release()
    }
}
