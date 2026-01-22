package com.chopcut.ui.timeline

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.chopcut.ui.components.TimelineCalculator
import com.chopcut.ui.components.TimelineConfigV2
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Estado que gerencia a lógica e sincronização da VideoTimeline.
 * 
 * Responsabilidades:
 * - Converter posição de scroll em tempo de vídeo
 * - Aplicar snapping de frames para precisão
 * - Evitar notificações redundantes de seek
 * - Gerenciar estado de scrubbing
 */
class VideoTimelineState(
    val durationMs: Long,
    val frameRate: Int,
    val listState: LazyListState
) {
    /** Posição atual calculada a partir do scroll (usada durante o scrub) */
    var scrubPositionMs by mutableStateOf(0L)
        private set

    /** Indica se o usuário está fazendo scrubbing */
    var isScrubbing by mutableStateOf(false)
        private set

    /** Último tempo notificado para evitar redundância */
    private var lastEmittedTime: Long? = null

    /** Contador de chamadas de update para debugging */
    private var updateCount = 0

    /** Timestamp da última atualização para throttle */
    private var lastUpdateTime = 0L

    /** Timestamp do último seek enviado */
    private var lastSeekTime = 0L

    /** Callback para notificar seek ao player */
    private var onSeekCallback: ((Long) -> Unit)? = null

    /** Período mínimo entre atualizações (ms) para scrubbing suave */
    private val UPDATE_THROTTLE_MS = 16L // ~60fps

    /** Período mínimo entre seeks (ms) para evitar sobrecarga do player */
    private val SEEK_DEBOUNCE_MS = 33L // ~30fps para seeks

    /** Duração de um frame em milissegundos (calculado do frameRate) */
    val frameDurationMs: Float
        get() = if (frameRate > 0) 1000f / frameRate else 33.33f

    /** Quantidade total de frames no vídeo */
    val totalFrames: Long
        get() = (durationMs / frameDurationMs).toLong()

    init {
        Timber.d("VideoTimelineState created: duration=${durationMs}ms, frameRate=${frameRate}fps, frameDuration=${frameDurationMs}ms")
        require(durationMs > 0) { "durationMs must be positive" }
        require(frameRate > 0) { "frameRate must be positive" }
    }

    /**
     * Atualiza a posição baseada no scroll da lista.
     * Retorna o tempo se ele tiver mudado, ou null caso contrário.
     * 
     * Scrubbing suave:
     * - Usa throttle para evitar atualizações excessivas
     * - Snapping suave em vez de agressivo
     * - Validação de bounds
     */
    fun updateFromScroll(
        index: Int,
        offset: Int,
        thumbSizePx: Int,
        screenWidthPx: Int
    ): Long? {
        if (thumbSizePx <= 0 || screenWidthPx <= 0) {
            Timber.w("updateFromScroll: invalid sizes - thumbSize=$thumbSizePx, screen=$screenWidthPx")
            return null
        }

        val currentTime = System.currentTimeMillis()
        
        // Throttle: limitar atualizações a ~60fps para scrubbing suave
        if (currentTime - lastUpdateTime < UPDATE_THROTTLE_MS) {
            return null
        }
        lastUpdateTime = currentTime

        val paddingPx = screenWidthPx / 2
        val timeMs = TimelineCalculator.calculateTimeFromScroll(
            index = index,
            offset = offset,
            thumbWidthPx = thumbSizePx,
            msPerThumb = TimelineConfigV2.THUMB_DURATION_MS,
            spacerWidthPx = paddingPx
        )

        // Snapping suave para frame boundaries
        // Durante scrubbing, usar snapping mais flexível para suavidade
        val frameCount = (timeMs / frameDurationMs).roundToLong()
        val snappedTimeMs = (frameCount * frameDurationMs).toLong().coerceIn(0, durationMs)

        // Apenas atualizar se mudou significativamente (mais que 1 frame)
        val frameDiff = kotlin.math.abs(snappedTimeMs - (scrubPositionMs))
        if (frameDiff < frameDurationMs && lastEmittedTime != null) {
            // Mudança muito pequena, ignorar para evitar saltos
            return null
        }

        scrubPositionMs = snappedTimeMs
        updateCount++

        val shouldEmit = snappedTimeMs != lastEmittedTime
        if (shouldEmit && updateCount % 10 == 0) {
            Timber.v("Timeline scrub: index=$index, offset=$offset, time=${timeMs}ms -> ${snappedTimeMs}ms (frame $frameCount)")
        }

        return if (shouldEmit) {
            lastEmittedTime = snappedTimeMs
            snappedTimeMs
        } else {
            null
        }
    }

    /**
     * Inicia o scrubbing manual do usuário
     */
    fun startScrubbing() {
        if (!isScrubbing) {
            isScrubbing = true
            resetLastEmittedTime()
            Timber.d("Scrubbing started")
        }
    }

    /**
     * Finaliza o scrubbing manual do usuário
     */
    fun endScrubbing() {
        if (isScrubbing) {
            isScrubbing = false
            Timber.d("Scrubbing ended at ${scrubPositionMs}ms")
        }
    }

    /**
     * Reseta o estado de notificação para uma nova interação.
     */
    fun resetLastEmittedTime() {
        lastEmittedTime = null
    }

    /**
     * Define o callback que será chamado quando precisar fazer seek no player
     */
    fun setOnSeekCallback(callback: (Long) -> Unit) {
        this.onSeekCallback = callback
    }

    /**
     * Faz seek imediato para uma posição específica (usado ao tocar na timeline)
     */
    fun seekToPosition(positionMs: Long) {
        val snappedTimeMs = positionMs.toLong().coerceIn(0, durationMs)
        scrubPositionMs = snappedTimeMs
        performSeek(snappedTimeMs)
        Timber.d("Direct seek to ${snappedTimeMs}ms")
    }

    /**
     * Executa o seek no player através do callback
     */
    private fun performSeek(timeMs: Long) {
        if (onSeekCallback != null) {
            onSeekCallback?.invoke(timeMs)
            lastEmittedTime = timeMs
        } else {
            Timber.w("No seek callback set, seek to ${timeMs}ms ignored")
        }
    }

    /**
     * Atualiza a posição da timeline baseada na posição atual do player
     * (chamado automaticamente durante a reprodução)
     */
    suspend fun syncWithPlayerPosition(positionMs: Long, thumbSizePx: Int, screenWidthPx: Int) {
        if (isScrubbing) return // Não sincronizar durante scrubbing
        
        val paddingPx = screenWidthPx / 2
        val (index, offset) = TimelineCalculator.calculateLazyListScroll(
            positionMs,
            thumbSizePx,
            TimelineConfigV2.THUMB_DURATION_MS,
            paddingPx
        )
        // Usar scrollToItem para sincronização instantânea e precisa
        // Não usar animateScrollToItem pois interfere no scrubbing
        listState.scrollToItem(index, offset)
        
        scrubPositionMs = positionMs
    }

    /**
     * Obtém a posição atual do playhead em pixels relativos ao início da timeline
     */
    fun getPlayheadPixelPosition(thumbWidthPx: Int, msPerThumb: Long): Int {
        return TimelineCalculator.calculateScrollOffset(
            currentMs = scrubPositionMs,
            thumbWidthPx = thumbWidthPx,
            msPerThumb = msPerThumb
        )
    }

    /**
     * Obtém o frame number atual baseado na posição de scrub
     */
    fun getCurrentFrame(): Long {
        return (scrubPositionMs / frameDurationMs).toLong()
    }

    /**
     * Converte uma posição em milissegundos para frame number
     */
    fun timeToFrame(timeMs: Long): Long {
        return (timeMs / frameDurationMs).toLong().coerceIn(0, totalFrames)
    }

    /**
     * Converte um frame number para posição em milissegundos
     */
    fun frameToTime(frameNumber: Long): Long {
        return (frameNumber * frameDurationMs).toLong().coerceIn(0, durationMs)
    }

    override fun toString(): String {
        return "VideoTimelineState(duration=${durationMs}ms, scrub=${scrubPositionMs}ms, scrubbing=$isScrubbing)"
    }
}

@Composable
fun rememberVideoTimelineState(
    durationMs: Long,
    frameRate: Int = 30,
    onSeek: (Long) -> Unit = {},
    listState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
): VideoTimelineState {
    val state = remember(durationMs, frameRate) {
        VideoTimelineState(durationMs, frameRate, listState)
    }
    
    // Atualizar o callback sempre que mudar
    androidx.compose.runtime.SideEffect {
        state.setOnSeekCallback(onSeek)
    }
    
    return state
}

/**
 * Função auxiliar para calcular a posição de scroll para um tempo específico
 */
fun calculateScrollForTime(
    timeMs: Long,
    durationMs: Long,
    thumbWidthPx: Int,
    screenWidthPx: Int
): Pair<Int, Int> {
    val paddingPx = screenWidthPx / 2
    return TimelineCalculator.calculateLazyListScroll(
        currentMs = timeMs,
        thumbWidthPx = thumbWidthPx,
        msPerThumb = TimelineConfigV2.THUMB_DURATION_MS,
        spacerWidthPx = paddingPx
    )
}
