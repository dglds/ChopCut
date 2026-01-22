package com.chopcut.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import timber.log.Timber
import kotlin.math.roundToLong

/**
 * Estado que gerencia a lógica do FrameSlider.
 *
 * Responsabilidades:
 * - Converter posição do slider (0-1) para tempo do vídeo
 * - Aplicar frame snapping para precisão
 * - Throttling e debouncing para performance
 * - Gerenciar estado de scrubbing
 */
@Stable
class FrameSliderState(
    val durationMs: Long,
    val frameRate: Int = 30
) {
    /** Indica se o usuário está fazendo scrubbing */
    var isScrubbing by mutableStateOf(false)
        private set

    /** Último tempo notificado para evitar redundância */
    private var lastEmittedTime: Long? = null

    /** Timestamp da última atualização para throttle */
    private var lastUpdateTime = 0L

    /** Timestamp do último seek enviado */
    private var lastSeekTime = 0L

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
        Timber.d("FrameSliderState created: duration=${durationMs}ms, frameRate=${frameRate}fps, frameDuration=${frameDurationMs}ms")
        require(durationMs > 0) { "durationMs must be positive" }
        require(frameRate > 0) { "frameRate must be positive" }
    }

    /**
     * Converte valor do slider (0.0 a 1.0) para tempo em milissegundos
     */
    fun sliderToTime(sliderValue: Float): Long {
        val normalizedValue = sliderValue.coerceIn(0f, 1f)
        return (normalizedValue * durationMs).toLong().coerceIn(0, durationMs)
    }

    /**
     * Converte tempo em milissegundos para valor do slider (0.0 a 1.0)
     */
    fun timeToSlider(timeMs: Long): Float {
        val normalizedTime = timeMs.coerceIn(0, durationMs).toFloat()
        return if (durationMs > 0) {
            normalizedTime / durationMs.toFloat()
        } else {
            0f
        }
    }

    /**
     * Aplica frame snapping em um tempo específico
     * Arredonda para o frame boundary mais próximo
     */
    fun snapToFrame(timeMs: Long): Long {
        val frameNumber = (timeMs / frameDurationMs).roundToLong()
        val snappedTime = (frameNumber * frameDurationMs).toLong()
        return snappedTime.coerceIn(0, durationMs)
    }

    /**
     * Verifica se deve atualizar a UI baseado no throttle
     * Retorna true se pode atualizar (passou o tempo mínimo desde a última atualização)
     */
    fun shouldUpdateUi(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < UPDATE_THROTTLE_MS) return false
        lastUpdateTime = now
        return true
    }

    /**
     * Verifica se deve fazer seek baseado no debounce
     * Retorna true se pode fazer seek (passou o tempo mínimo desde o último seek)
     */
    fun shouldSeek(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastSeekTime < SEEK_DEBOUNCE_MS) return false
        lastSeekTime = now
        return true
    }

    /**
     * Inicia o scrubbing manual do usuário
     */
    fun startScrubbing() {
        if (!isScrubbing) {
            isScrubbing = true
            resetLastEmittedTime()
            Timber.d("FrameSlider scrubbing started")
        }
    }

    /**
     * Finaliza o scrubbing manual do usuário
     */
    fun endScrubbing() {
        if (isScrubbing) {
            isScrubbing = false
            Timber.d("FrameSlider scrubbing ended")
        }
    }

    /**
     * Reseta o estado de notificação para uma nova interação.
     */
    fun resetLastEmittedTime() {
        lastEmittedTime = null
    }

    /**
     * Verifica se o tempo mudou significativamente (mais que meio frame)
     * Útil para evitar notificações redundantes
     */
    fun hasSignificantChange(newTimeMs: Long, oldTimeMs: Long): Boolean {
        val diff = kotlin.math.abs(newTimeMs - oldTimeMs)
        return diff > (frameDurationMs / 2)
    }

    /**
     * Obtém o frame number baseado em um tempo específico
     */
    fun timeToFrame(timeMs: Long): Long {
        return (timeMs / frameDurationMs).toLong().coerceIn(0, totalFrames)
    }

    /**
     * Converte um frame number para tempo em milissegundos
     */
    fun frameToTime(frameNumber: Long): Long {
        return (frameNumber * frameDurationMs).toLong().coerceIn(0, durationMs)
    }

    override fun toString(): String {
        return "FrameSliderState(duration=${durationMs}ms, scrubbing=$isScrubbing, frameRate=$frameRate)"
    }
}

/**
 * Função factory para criar e lembrar um FrameSliderState
 */
@Composable
fun rememberFrameSliderState(
    durationMs: Long,
    frameRate: Int = 30
): FrameSliderState {
    return androidx.compose.runtime.remember(durationMs, frameRate) {
        FrameSliderState(durationMs, frameRate)
    }
}
